import cors from "cors";
import dotenv from "dotenv";
import express from "express";
import admin from "firebase-admin";
import { GoogleGenAI } from "@google/genai";
import { GoogleAuth } from "google-auth-library";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const ee = require("@google/earthengine");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, "..");

// Root .env is the single source of truth for all app/backend settings.
dotenv.config({ path: path.resolve(ROOT_DIR, ".env") });

const app = express();
app.use(express.json({ limit: "1mb" }));
app.use(
  cors({
    origin: process.env.ALLOWED_ORIGIN || "*",
  })
);

const PORT = Number(process.env.PORT || 8080);
const GEMINI_MODEL = process.env.GEMINI_MODEL || "gemini-2.5-flash";
const EARTH_ENGINE_MODE = process.env.EARTH_ENGINE_MODE || "mock";
const EARTH_ENGINE_PROJECT_ID = process.env.EARTH_ENGINE_PROJECT_ID || "";
const EARTH_ENGINE_SERVICE_ACCOUNT_JSON = process.env.EARTH_ENGINE_SERVICE_ACCOUNT_JSON || "";
const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "";
const FIREBASE_SERVICE_ACCOUNT_JSON = process.env.FIREBASE_SERVICE_ACCOUNT_JSON || "";
const FIREBASE_COLLECTION = process.env.FIREBASE_COLLECTION || "fieldInsights";
const FIREBASE_WEB_API_KEY = process.env.FIREBASE_WEB_API_KEY || "";
const VERTEX_PROJECT_ID = process.env.VERTEX_PROJECT_ID || FIREBASE_PROJECT_ID || EARTH_ENGINE_PROJECT_ID || "";
const VERTEX_LOCATION = process.env.VERTEX_LOCATION || "us-central1";
const VERTEX_IMAGE_MODEL = process.env.VERTEX_IMAGE_MODEL || "imagen-4.0-fast-generate-001";
const VERTEX_IMAGE_MODEL_CANDIDATES = (process.env.VERTEX_IMAGE_MODEL_CANDIDATES || "")
  .split(",")
  .map((item) => item.trim())
  .filter(Boolean);
const VERTEX_SERVICE_ACCOUNT_JSON = process.env.VERTEX_SERVICE_ACCOUNT_JSON || "";
const VERTEX_IMAGE_ENABLED = String(process.env.VERTEX_IMAGE_ENABLED || "true").toLowerCase() !== "false";

const ai = process.env.GEMINI_API_KEY
  ? new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY })
  : null;

const EARTH_ENGINE_SCOPE = "https://www.googleapis.com/auth/earthengine.readonly";
const VERTEX_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

let earthEngineStatusCache = {
  checkedAt: 0,
  linked: false,
  reason: "not_checked",
};
let earthEngineInitPromise = null;
let firestoreStatusCache = {
  initialized: false,
  reason: "not_checked",
};
let firestoreDb = null;
let vertexTokenCache = {
  token: "",
  expiresAt: 0,
};

app.get("/health", async (_req, res) => {
  const eeStatus = await resolveEarthEngineStatus();
  const firebaseStatus = resolveFirestoreStatus();
  res.json({
    ok: true,
    service: "field-insights",
    geminiEnabled: Boolean(ai),
    earthEngineMode: EARTH_ENGINE_MODE,
    earthEngineLinked: eeStatus.linked,
    earthEngineReason: eeStatus.reason,
    firestoreEnabled: firebaseStatus.enabled,
    firestoreReason: firebaseStatus.reason,
  });
});

app.post("/api/field-insights", async (req, res) => {
  try {
    const polygon = normalizePolygon(req.body?.polygon);
    if (polygon.length < 3) {
      return res.status(400).json({ error: "Polygon must have at least 3 points." });
    }
    const targetCrops = normalizeTargetCrops(req.body?.targetCrops);
    const totalFarmAreaHectares = normalizeOptionalNumber(req.body?.totalFarmAreaHectares);
    const lotAreaHectares = normalizeOptionalNumber(req.body?.lotAreaHectares);

    const centroid = req.body?.centroid
      ? toLatLngPoint(req.body.centroid)
      : computeCentroid(polygon);

    const earthSummary = await getEarthSummary({ polygon, centroid });
    const recommendations = await getCropRecommendations(earthSummary, targetCrops, {
      totalFarmAreaHectares,
      lotAreaHectares,
    });

    const payload = {
      summary: earthSummary,
      recommendations,
      provider: ai ? "gemini-live" : "gemini-mock",
    };

    void persistInsightRecord({
      userId: normalizeUserId(req.body?.userId),
      requestBody: req.body,
      normalized: {
        polygon,
        centroid,
        targetCrops,
        totalFarmAreaHectares,
        lotAreaHectares,
      },
      response: payload,
    });

    return res.json(payload);
  } catch (error) {
    return res.status(500).json({
      error: "Unable to produce field insights.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/farm-config", async (req, res) => {
  try {
    const db = getFirestoreDb();
    if (!db) {
      return res.status(503).json({
        error: "Firestore is not configured.",
        detail: "Set FIREBASE_PROJECT_ID and/or FIREBASE_SERVICE_ACCOUNT_JSON, then redeploy.",
      });
    }

    const userId = normalizeUserId(req.body?.userId);
    if (!userId) {
      return res.status(400).json({ error: "userId is required." });
    }

    const legacyFarmName = String(req.body?.farmName || "").trim();
    const legacyAddress = String(req.body?.address || "").trim();
    const legacyMapQuery = String(req.body?.mapQuery || "").trim();
    const legacyTotalAreaInput = String(req.body?.totalAreaInput || "").trim();
    const legacyMode = String(req.body?.mode || "").trim();
    const legacyBoundaryPoints = normalizeNormalizedPoints(req.body?.boundaryPoints);
    const legacyLots = normalizeLots(req.body?.lots);
    const farms = normalizeFarms(req.body?.farms, {
      farmName: legacyFarmName,
      address: legacyAddress,
      mapQuery: legacyMapQuery,
      totalAreaInput: legacyTotalAreaInput,
      mode: legacyMode,
      boundaryPoints: legacyBoundaryPoints,
      lots: legacyLots,
    });
    const requestedActiveFarmId = String(req.body?.activeFarmId || "").trim();
    const activeFarm = farms.find((farm) => farm.id === requestedActiveFarmId) || farms[0] || null;
    const activeFarmId = activeFarm?.id || null;
    const timelinePhotoCache = normalizeTimelinePhotoCache(req.body?.timelinePhotoCache);
    const timelineStageVisualCache = normalizeTimelineStageVisualCache(req.body?.timelineStageVisualCache);

    if (farms.length === 0) {
      return res.status(400).json({ error: "At least one farm is required." });
    }

    const payload = {
      userId,
      activeFarmId,
      farms,
      // Keep legacy single-farm fields for backward compatibility.
      farmName: activeFarm?.farmName || "",
      address: activeFarm?.address || "",
      mapQuery: activeFarm?.mapQuery || "",
      totalAreaInput: activeFarm?.totalAreaInput || "",
      mode: activeFarm?.mode || "PLANNING",
      boundaryPoints: activeFarm?.boundaryPoints || [],
      lots: activeFarm?.lots || [],
      timelinePhotoCache,
      timelineStageVisualCache,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    await db.collection("farmConfigs").doc(userId).set(payload, { merge: true });

    return res.status(200).json({ ok: true, userId });
  } catch (error) {
    return res.status(500).json({
      error: "Unable to save farm configuration.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.get("/api/farm-config", async (req, res) => {
  try {
    const db = getFirestoreDb();
    if (!db) {
      return res.status(503).json({
        error: "Firestore is not configured.",
        detail: "Set FIREBASE_PROJECT_ID and/or FIREBASE_SERVICE_ACCOUNT_JSON, then redeploy.",
      });
    }

    const userId = normalizeUserId(req.query?.userId);
    if (!userId) {
      return res.status(400).json({
        error: "userId query param is required.",
        detail: "Use /api/farm-config?userId=YOUR_UID or /api/farm-config/latest?userId=YOUR_UID",
      });
    }

    const doc = await db.collection("farmConfigs").doc(userId).get();
    if (!doc.exists) {
      return res.json({ item: null });
    }

    const data = doc.data() || {};
    const farms = normalizeFarms(data.farms, {
      farmName: String(data.farmName || "").trim(),
      address: String(data.address || "").trim(),
      mapQuery: String(data.mapQuery || "").trim(),
      totalAreaInput: String(data.totalAreaInput || "").trim(),
      mode: String(data.mode || "").trim(),
      boundaryPoints: normalizeNormalizedPoints(data.boundaryPoints),
      lots: normalizeLots(data.lots),
    });
    const activeFarmId = String(data.activeFarmId || "").trim();
    const activeFarm = farms.find((farm) => farm.id === activeFarmId) || farms[0] || null;

    return res.json({
      item: {
        id: doc.id,
        ...data,
        activeFarmId: activeFarm?.id || null,
        farms,
        farmName: activeFarm?.farmName || "",
        address: activeFarm?.address || "",
        mapQuery: activeFarm?.mapQuery || "",
        totalAreaInput: activeFarm?.totalAreaInput || "",
        mode: activeFarm?.mode || "PLANNING",
        boundaryPoints: activeFarm?.boundaryPoints || [],
        lots: activeFarm?.lots || [],
      },
    });
  } catch (error) {
    return res.status(500).json({
      error: "Unable to read farm configuration.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.get("/api/farm-config/latest", async (req, res) => {
  try {
    const db = getFirestoreDb();
    if (!db) {
      return res.status(503).json({
        error: "Firestore is not configured.",
        detail: "Set FIREBASE_PROJECT_ID and/or FIREBASE_SERVICE_ACCOUNT_JSON, then redeploy.",
      });
    }

    const userId = normalizeUserId(req.query?.userId);
    if (!userId) {
      return res.status(400).json({ error: "userId query param is required." });
    }

    const doc = await db.collection("farmConfigs").doc(userId).get();
    if (!doc.exists) {
      return res.json({ item: null });
    }

    const data = doc.data() || {};
    const farms = normalizeFarms(data.farms, {
      farmName: String(data.farmName || "").trim(),
      address: String(data.address || "").trim(),
      mapQuery: String(data.mapQuery || "").trim(),
      totalAreaInput: String(data.totalAreaInput || "").trim(),
      mode: String(data.mode || "").trim(),
      boundaryPoints: normalizeNormalizedPoints(data.boundaryPoints),
      lots: normalizeLots(data.lots),
    });
    const activeFarmId = String(data.activeFarmId || "").trim();
    const activeFarm = farms.find((farm) => farm.id === activeFarmId) || farms[0] || null;

    return res.json({
      item: {
        id: doc.id,
        ...data,
        activeFarmId: activeFarm?.id || null,
        farms,
        farmName: activeFarm?.farmName || "",
        address: activeFarm?.address || "",
        mapQuery: activeFarm?.mapQuery || "",
        totalAreaInput: activeFarm?.totalAreaInput || "",
        mode: activeFarm?.mode || "PLANNING",
        boundaryPoints: activeFarm?.boundaryPoints || [],
        lots: activeFarm?.lots || [],
      },
    });
  } catch (error) {
    return res.status(500).json({
      error: "Unable to read farm configuration.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.get("/api/field-insights/history", async (req, res) => {
  try {
    const db = getFirestoreDb();
    if (!db) {
      return res.status(503).json({
        error: "Firestore is not configured.",
        detail: "Set FIREBASE_PROJECT_ID and/or FIREBASE_SERVICE_ACCOUNT_JSON, then redeploy.",
      });
    }

    const requestedLimit = Number(req.query?.limit);
    const limit = Number.isFinite(requestedLimit)
      ? Math.min(Math.max(Math.trunc(requestedLimit), 1), 100)
      : 20;

    const snapshot = await db
      .collection(FIREBASE_COLLECTION)
      .orderBy("createdAt", "desc")
      .limit(limit)
      .get();

    const items = snapshot.docs.map((doc) => ({
      id: doc.id,
      ...doc.data(),
    }));

    return res.json({ items, count: items.length });
  } catch (error) {
    return res.status(500).json({
      error: "Unable to read field insights history.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/auth/signup", async (req, res) => {
  try {
    if (!FIREBASE_WEB_API_KEY) {
      return res.status(503).json({
        error: "Firebase web API key is not configured.",
        detail: "Set FIREBASE_WEB_API_KEY and redeploy.",
      });
    }

    const firebaseAuth = getFirebaseAuth();
    if (!firebaseAuth) {
      return res.status(503).json({
        error: "Firebase Auth is not configured.",
        detail: "Set FIREBASE_PROJECT_ID and/or FIREBASE_SERVICE_ACCOUNT_JSON, then redeploy.",
      });
    }

    const email = String(req.body?.email || "").trim().toLowerCase();
    const password = String(req.body?.password || "").trim();
    const displayName = String(req.body?.displayName || "").trim();

    if (!email.includes("@")) {
      return res.status(400).json({ error: "Valid email is required." });
    }
    if (password.length < 6) {
      return res.status(400).json({ error: "Password must be at least 6 characters." });
    }
    if (!displayName) {
      return res.status(400).json({ error: "displayName is required." });
    }

    const createdUser = await firebaseAuth.createUser({
      email,
      password,
      displayName,
    });

    const db = getFirestoreDb();
    if (db) {
      await db.collection("users").doc(createdUser.uid).set(
        {
          email,
          displayName,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          provider: "password",
        },
        { merge: true }
      );
    }

    const signInData = await signInWithPassword(email, password);
    return res.status(201).json({
      userId: createdUser.uid,
      email: createdUser.email || email,
      displayName: createdUser.displayName || displayName,
      idToken: signInData?.idToken || null,
      refreshToken: signInData?.refreshToken || null,
    });
  } catch (error) {
    if (error?.code === "auth/email-already-exists") {
      return res.status(409).json({ error: "This email is already registered." });
    }
    return res.status(500).json({
      error: "Unable to sign up user.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/auth/signin", async (req, res) => {
  try {
    if (!FIREBASE_WEB_API_KEY) {
      return res.status(503).json({
        error: "Firebase web API key is not configured.",
        detail: "Set FIREBASE_WEB_API_KEY and redeploy.",
      });
    }

    const firebaseAuth = getFirebaseAuth();
    if (!firebaseAuth) {
      return res.status(503).json({
        error: "Firebase Auth is not configured.",
        detail: "Set FIREBASE_PROJECT_ID and/or FIREBASE_SERVICE_ACCOUNT_JSON, then redeploy.",
      });
    }

    const email = String(req.body?.email || "").trim().toLowerCase();
    const password = String(req.body?.password || "").trim();

    if (!email.includes("@")) {
      return res.status(400).json({ error: "Valid email is required." });
    }
    if (!password) {
      return res.status(400).json({ error: "Password is required." });
    }

    const signInData = await signInWithPassword(email, password);
    if (!signInData?.localId) {
      return res.status(401).json({ error: "Invalid email or password." });
    }

    const userRecord = await firebaseAuth.getUser(signInData.localId);
    const db = getFirestoreDb();
    if (db) {
      await db.collection("users").doc(userRecord.uid).set(
        {
          email: userRecord.email || email,
          displayName: userRecord.displayName || null,
          lastLoginAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          provider: "password",
        },
        { merge: true }
      );
    }

    return res.json({
      userId: userRecord.uid,
      email: userRecord.email || email,
      displayName: userRecord.displayName || null,
      idToken: signInData.idToken || null,
      refreshToken: signInData.refreshToken || null,
    });
  } catch (error) {
    if (isFirebaseInvalidCredentials(error)) {
      return res.status(401).json({ error: "Invalid email or password." });
    }
    return res.status(500).json({
      error: "Unable to sign in user.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/timeline/stage-visual", async (req, res) => {
  try {
    const dayNumber = Math.max(1, Number.parseInt(String(req.body?.dayNumber ?? "1"), 10) || 1);
    const expectedStage = String(req.body?.expectedStage || "Growth progression").trim() || "Growth progression";
    const cropName = String(req.body?.cropName || "Crop").trim() || "Crop";

    const visual = await generateTimelineStageVisual({ dayNumber, expectedStage, cropName });
    return res.json(visual);
  } catch (error) {
    return res.status(500).json({
      error: "Unable to generate stage visual.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/timeline/photo-compare", async (req, res) => {
  try {
    const dayNumber = Math.max(1, Number.parseInt(String(req.body?.dayNumber ?? "1"), 10) || 1);
    const expectedStage = String(req.body?.expectedStage || "Growth progression").trim() || "Growth progression";
    const cropName = String(req.body?.cropName || "Crop").trim() || "Crop";
    const photoMimeType = String(req.body?.photoMimeType || "image/jpeg").trim() || "image/jpeg";
    const photoBase64 = String(req.body?.photoBase64 || "").trim();
    const userMarkedSimilar = typeof req.body?.userMarkedSimilar === "boolean" ? req.body.userMarkedSimilar : null;

    if (!photoBase64) {
      return res.status(400).json({ error: "photoBase64 is required." });
    }

    const assessment = await assessTimelinePhoto({
      dayNumber,
      expectedStage,
      cropName,
      photoMimeType,
      photoBase64,
      userMarkedSimilar,
    });

    return res.json(assessment);
  } catch (error) {
    return res.status(500).json({
      error: "Unable to assess timeline photo.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/chat", async (req, res) => {
  try {
    const message = String(req.body?.message || "").trim();
    if (!message) {
      return res.status(400).json({ error: "message is required." });
    }

    const context = req.body?.context && typeof req.body.context === "object"
      ? {
          farmName: String(req.body.context.farmName || "").trim(),
          cropName: String(req.body.context.cropName || "").trim(),
          mode: String(req.body.context.mode || "").trim(),
          latestRecommendation: String(req.body.context.latestRecommendation || "").trim(),
        }
      : null;

    const rawHistory = Array.isArray(req.body?.history) ? req.body.history : [];
    const history = rawHistory
      .map((item) => ({
        role: String(item?.role || "").trim().toLowerCase(),
        content: String(item?.content || "").trim(),
      }))
      .filter((item) => (item.role === "user" || item.role === "assistant") && item.content.length > 0)
      .slice(-12);

    const reply = await generateAiChatReply({
      message,
      history,
      context,
    });

    return res.json(reply);
  } catch (error) {
    return res.status(500).json({
      error: "Unable to generate AI chat response.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.listen(PORT, () => {
  console.log(`Field insights service listening on ${PORT}`);
});

function normalizePolygon(input) {
  if (!Array.isArray(input)) return [];
  return input
    .map((raw) => toLatLngPoint(raw))
    .filter((p) => Number.isFinite(p.lat) && Number.isFinite(p.lng));
}

function normalizeTargetCrops(input) {
  if (!Array.isArray(input)) return [];
  return input
    .map((item) => String(item ?? "").trim())
    .filter((item) => item.length > 0)
    .slice(0, 8);
}

function normalizeOptionalNumber(input) {
  const num = Number(input);
  return Number.isFinite(num) && num > 0 ? num : null;
}

function resolveFirestoreStatus() {
  const db = getFirestoreDb();
  return {
    enabled: Boolean(db),
    reason: firestoreStatusCache.reason,
  };
}

function getFirestoreDb() {
  if (firestoreDb) return firestoreDb;

  try {
    if (admin.apps.length > 0) {
      firestoreDb = admin.firestore();
      firestoreStatusCache = { initialized: true, reason: "ok_existing_app" };
      return firestoreDb;
    }

    const credentialsJson = FIREBASE_SERVICE_ACCOUNT_JSON;

    if (!FIREBASE_PROJECT_ID && !credentialsJson.trim()) {
      firestoreStatusCache = { initialized: false, reason: "missing_project_id_or_credentials" };
      return null;
    }

    const appOptions = {
      ...(FIREBASE_PROJECT_ID ? { projectId: FIREBASE_PROJECT_ID } : {}),
    };

    if (credentialsJson.trim()) {
      const serviceAccount = JSON.parse(credentialsJson);
      appOptions.credential = admin.credential.cert(serviceAccount);
      if (!appOptions.projectId && serviceAccount.project_id) {
        appOptions.projectId = serviceAccount.project_id;
      }
    } else {
      appOptions.credential = admin.credential.applicationDefault();
    }

    admin.initializeApp(appOptions);
    firestoreDb = admin.firestore();
    firestoreStatusCache = { initialized: true, reason: "ok" };
    return firestoreDb;
  } catch (error) {
    firestoreStatusCache = {
      initialized: false,
      reason: error instanceof Error ? `init_error_${error.message}` : "init_error_unknown",
    };
    return null;
  }
}

function getFirebaseAuth() {
  getFirestoreDb();
  if (admin.apps.length === 0) {
    return null;
  }

  try {
    return admin.auth();
  } catch (_error) {
    return null;
  }
}

function isFirebaseInvalidCredentials(error) {
  const message = String(error instanceof Error ? error.message : error || "").toUpperCase();
  return message.includes("INVALID_LOGIN_CREDENTIALS") || message.includes("INVALID_PASSWORD");
}

async function signInWithPassword(email, password) {
  if (!FIREBASE_WEB_API_KEY) {
    return null;
  }

  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_WEB_API_KEY}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email,
        password,
        returnSecureToken: true,
      }),
    }
  );

  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(`firebase_signin_failed:${payload?.error?.message || response.status}`);
  }

  return {
    localId: String(payload.localId || ""),
    idToken: payload.idToken || null,
    refreshToken: payload.refreshToken || null,
  };
}

async function persistInsightRecord({ userId = null, requestBody, normalized, response }) {
  const db = getFirestoreDb();
  if (!db) return;

  try {
    await db.collection(FIREBASE_COLLECTION).add({
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      userId,
      request: {
        raw: requestBody ?? {},
        normalized,
      },
      response,
    });
  } catch (error) {
    console.error("Failed to persist field insight record:", error);
  }
}

function normalizeUserId(input) {
  const value = String(input || "").trim();
  return value || null;
}

function normalizeNormalizedPoints(input) {
  if (!Array.isArray(input)) return [];

  return input
    .map((raw) => {
      const x = Number(raw?.x);
      const y = Number(raw?.y);
      if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
      return {
        x: clamp(x, 0, 1),
        y: clamp(y, 0, 1),
      };
    })
    .filter(Boolean);
}

function normalizeLots(input) {
  if (!Array.isArray(input)) return [];

  return input
    .map((raw, index) => {
      const points = normalizeNormalizedPoints(raw?.points);
      if (points.length < 3) return null;

      return {
        id: String(raw?.id || `lot-${index + 1}`).trim() || `lot-${index + 1}`,
        name: String(raw?.name || `Lot ${index + 1}`).trim() || `Lot ${index + 1}`,
        points,
        cropPlan: String(raw?.cropPlan || "").trim(),
        soilType: String(raw?.soilType || "").trim(),
        waterAvailability: String(raw?.waterAvailability || "").trim(),
      };
    })
    .filter(Boolean);
}

function normalizeFarms(input, fallback = null) {
  const farms = Array.isArray(input)
    ? input
        .map((raw, index) => {
          const lots = normalizeLots(raw?.lots);
          if (lots.length === 0) return null;

          const boundaryPoints = normalizeNormalizedPoints(raw?.boundaryPoints);
          return {
            id: String(raw?.id || `farm-${index + 1}`).trim() || `farm-${index + 1}`,
            farmName: String(raw?.farmName || "").trim(),
            address: String(raw?.address || "").trim(),
            mapQuery: String(raw?.mapQuery || "").trim(),
            totalAreaInput: String(raw?.totalAreaInput || "").trim(),
            mode: String(raw?.mode || "PLANNING").trim() || "PLANNING",
            boundaryPoints: boundaryPoints.length >= 3 ? boundaryPoints : lots[0]?.points || [],
            lots,
          };
        })
        .filter(Boolean)
    : [];

  if (farms.length > 0) {
    return farms.slice(0, 20);
  }

  if (!fallback) {
    return [];
  }

  const lots = Array.isArray(fallback.lots) ? fallback.lots : [];
  if (lots.length === 0) {
    return [];
  }

  const boundaryPoints = Array.isArray(fallback.boundaryPoints) ? fallback.boundaryPoints : [];

  return [
    {
      id: "farm-legacy",
      farmName: String(fallback.farmName || "").trim(),
      address: String(fallback.address || "").trim(),
      mapQuery: String(fallback.mapQuery || "").trim(),
      totalAreaInput: String(fallback.totalAreaInput || "").trim(),
      mode: String(fallback.mode || "PLANNING").trim() || "PLANNING",
      boundaryPoints: boundaryPoints.length >= 3 ? boundaryPoints : lots[0]?.points || [],
      lots,
    },
  ];
}

function normalizeTimelinePhotoCache(input) {
  if (!Array.isArray(input)) return [];

  return input
    .map((raw) => {
      const dayNumber = Number(raw?.dayNumber);
      const photoBase64 = String(raw?.photoBase64 || "").trim();
      const photoMimeType = String(raw?.photoMimeType || "image/jpeg").trim() || "image/jpeg";
      const updatedAtEpochMs = Number(raw?.updatedAtEpochMs);
      if (!Number.isFinite(dayNumber) || dayNumber <= 0 || !photoBase64) return null;
      return {
        dayNumber: Math.trunc(dayNumber),
        photoBase64,
        photoMimeType,
        updatedAtEpochMs: Number.isFinite(updatedAtEpochMs) ? Math.trunc(updatedAtEpochMs) : Date.now(),
      };
    })
    .filter(Boolean)
    .slice(0, 90);
}

function normalizeTimelineStageVisualCache(input) {
  if (!Array.isArray(input)) return [];

  return input
    .map((raw) => {
      const dayNumber = Number(raw?.dayNumber);
      const imageDataUrl = String(raw?.imageDataUrl || "").trim();
      if (!Number.isFinite(dayNumber) || dayNumber <= 0 || !imageDataUrl) return null;
      const updatedAtEpochMs = Number(raw?.updatedAtEpochMs);
      return {
        dayNumber: Math.trunc(dayNumber),
        title: String(raw?.title || "").trim(),
        description: String(raw?.description || "").trim(),
        imageDataUrl,
        provider: String(raw?.provider || "").trim(),
        updatedAtEpochMs: Number.isFinite(updatedAtEpochMs) ? Math.trunc(updatedAtEpochMs) : Date.now(),
      };
    })
    .filter(Boolean)
    .slice(0, 90);
}

function toLatLngPoint(raw) {
  const x = Number(raw?.x ?? raw?.lng ?? raw?.lon);
  const y = Number(raw?.y ?? raw?.lat);

  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    throw new Error("Invalid point values. Expected x/y or lat/lng numbers.");
  }

  // App currently sends normalized x/y in [0,1]. Convert to Kedah-ish lat/lng for prototype.
  if (x >= 0 && x <= 1 && y >= 0 && y <= 1) {
    return {
      lat: 6.12 + (0.5 - y) * 0.25,
      lng: 100.37 + (x - 0.5) * 0.35,
    };
  }

  // If already geospatial coords, use them directly.
  if (Math.abs(y) <= 90 && Math.abs(x) <= 180) {
    return { lat: y, lng: x };
  }

  throw new Error("Point coordinates are outside valid bounds.");
}

function computeCentroid(points) {
  const sums = points.reduce(
    (acc, point) => {
      acc.lat += point.lat;
      acc.lng += point.lng;
      return acc;
    },
    { lat: 0, lng: 0 }
  );

  return {
    lat: sums.lat / points.length,
    lng: sums.lng / points.length,
  };
}

function polygonArea(points) {
  let sum = 0;
  for (let i = 0; i < points.length; i += 1) {
    const p1 = points[i];
    const p2 = points[(i + 1) % points.length];
    sum += p1.lng * p2.lat - p2.lng * p1.lat;
  }
  return Math.abs(sum / 2);
}

async function getEarthSummary({ polygon, centroid }) {
  const baseSummary = buildGeometrySummary({ polygon, centroid });

  if (EARTH_ENGINE_MODE !== "mock") {
    const eeStatus = await resolveEarthEngineStatus();
    if (eeStatus.linked) {
      try {
        const sampled = await sampleEarthEngineSummary(polygon, centroid);
        if (isSparseEarthSummary(sampled)) {
          return {
            ...baseSummary,
            notes: "Earth Engine returned sparse metrics for this polygon; using geometry fallback summary.",
          };
        }
        return {
          ...sampled,
          notes: "Real Earth Engine datasets sampled (Sentinel-2 NDVI, SMAP soil moisture, CHIRPS rainfall, ERA5 temperature).",
        };
      } catch (error) {
        return {
          ...baseSummary,
          notes: `Earth Engine linked but sampling failed (${error instanceof Error ? error.message : String(error)}); using geometry fallback summary.`,
        };
      }
    }

    return {
      ...baseSummary,
      notes: `Earth Engine live mode requested but unavailable (${eeStatus.reason}); using geometry fallback summary.`,
    };
  }

  return {
    ...baseSummary,
    notes: "Mock Earth summary. Set EARTH_ENGINE_MODE=live and add Earth Engine credentials to verify the link.",
  };
}

function buildGeometrySummary({ polygon, centroid }) {
  const area = polygonArea(polygon);
  const areaScale = Math.min(1.0, area * 20);

  const ndviMean = clamp(0.45 + areaScale * 0.25, 0.3, 0.9);
  const soilMoistureMean = clamp(0.3 + areaScale * 0.45, 0.12, 0.92);
  const rainfallMm7d = clamp(12 + areaScale * 55, 6, 120);
  const averageTempC = clamp(29.5 - areaScale * 2.2, 22, 34);

  return {
    centroidLat: round(centroid.lat, 6),
    centroidLng: round(centroid.lng, 6),
    ndviMean: round(ndviMean, 3),
    soilMoistureMean: round(soilMoistureMean, 3),
    rainfallMm7d: round(rainfallMm7d, 2),
    averageTempC: round(averageTempC, 2),
  };
}

function isSparseEarthSummary(summary) {
  const moisture = Number(summary?.soilMoistureMean ?? 0);
  const rainfall = Number(summary?.rainfallMm7d ?? 0);
  const temp = Number(summary?.averageTempC ?? 0);

  // A triple-zero style payload usually means no usable pixels/dataset gap, not real field conditions.
  return moisture <= 0.001 && rainfall <= 0.01 && temp <= 0.5;
}

async function resolveEarthEngineStatus() {
  const now = Date.now();
  if (now - earthEngineStatusCache.checkedAt < 60_000 && earthEngineStatusCache.reason !== "not_checked") {
    return earthEngineStatusCache;
  }

  if (EARTH_ENGINE_MODE === "mock") {
    earthEngineStatusCache = {
      checkedAt: now,
      linked: false,
      reason: "mock_mode",
    };
    return earthEngineStatusCache;
  }

  const validation = await validateEarthEngineAccess();
  earthEngineStatusCache = {
    checkedAt: now,
    linked: validation.linked,
    reason: validation.reason,
  };
  return earthEngineStatusCache;
}

async function validateEarthEngineAccess() {
  try {
    const credentialsJson = EARTH_ENGINE_SERVICE_ACCOUNT_JSON;

    const credentials = credentialsJson.trim()
      ? JSON.parse(credentialsJson)
      : undefined;

    const projectId = EARTH_ENGINE_PROJECT_ID || credentials?.project_id;
    if (!projectId) {
      return { linked: false, reason: "missing_project_id" };
    }

    const auth = credentials
      ? new GoogleAuth({ credentials, scopes: [EARTH_ENGINE_SCOPE] })
      : new GoogleAuth({ scopes: [EARTH_ENGINE_SCOPE] });

    const client = await auth.getClient();
    const tokenResponse = await client.getAccessToken();
    const accessToken = tokenResponse?.token;
    if (!accessToken) {
      return { linked: false, reason: "token_unavailable" };
    }

    const response = await fetch(
      "https://earthengine.googleapis.com/v1alpha/projects/earthengine-public/assets/LANDSAT",
      {
        method: "GET",
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      }
    );

    if (!response.ok) {
      return { linked: false, reason: `earthengine_api_${response.status}` };
    }

    return { linked: true, reason: "ok" };
  } catch (error) {
    return {
      linked: false,
      reason: error instanceof Error ? `auth_error_${error.message}` : "auth_error_unknown",
    };
  }
}

async function getInitializedEarthEngine() {
  if (earthEngineInitPromise) return earthEngineInitPromise;

  earthEngineInitPromise = new Promise((resolve, reject) => {
    try {
      const credentialsJson = EARTH_ENGINE_SERVICE_ACCOUNT_JSON;

      if (!credentialsJson.trim()) {
        return reject(new Error("missing_service_account_json"));
      }

      const credentials = JSON.parse(credentialsJson);
      ee.data.authenticateViaPrivateKey(
        {
          client_email: credentials.client_email,
          private_key: credentials.private_key,
        },
        () => {
          ee.initialize(
            null,
            null,
            () => resolve(ee),
            (err) => reject(new Error(`ee_initialize_failed:${err}`))
          );
        },
        (err) => reject(new Error(`ee_auth_failed:${err}`))
      );
    } catch (error) {
      reject(error);
    }
  });

  return earthEngineInitPromise;
}

async function sampleEarthEngineSummary(polygon, centroid) {
  const eeClient = await getInitializedEarthEngine();
  const geometry = eeClient.Geometry.Polygon([
    polygon.map((p) => [p.lng, p.lat]),
  ]);

  const now = eeClient.Date(Date.now());
  const ndviStart = now.advance(-90, "day");
  const meteoStart = now.advance(-7, "day");
  const smapStart = now.advance(-14, "day");

  const ndviCollection = eeClient
    .ImageCollection("COPERNICUS/S2_SR_HARMONIZED")
    .filterBounds(geometry)
    .filterDate(ndviStart, now)
    .filter(eeClient.Filter.lt("CLOUDY_PIXEL_PERCENTAGE", 30))
    .map((img) => img.normalizedDifference(["B8", "B4"]).rename("ndvi"));

  const ndviImage = eeClient.Image(
    eeClient.Algorithms.If(
      ndviCollection.size().gt(0),
      ndviCollection.median(),
      eeClient.Image.constant(0).rename("ndvi")
    )
  );

  const soilCollection = eeClient
    .ImageCollection("NASA_USDA/HSL/SMAP10KM_soil_moisture")
    .filterBounds(geometry)
    .filterDate(smapStart, now)
    .select("ssm");

  const soilMoistureImage = eeClient.Image(
    eeClient.Algorithms.If(
      soilCollection.size().gt(0),
      soilCollection.mean().rename("soil_moisture"),
      eeClient.Image.constant(0).rename("soil_moisture")
    )
  );

  const rainCollection = eeClient
    .ImageCollection("UCSB-CHG/CHIRPS/DAILY")
    .filterBounds(geometry)
    .filterDate(meteoStart, now)
    .select("precipitation");

  const rainfallImage = eeClient.Image(
    eeClient.Algorithms.If(
      rainCollection.size().gt(0),
      rainCollection.sum().rename("rainfall_7d"),
      eeClient.Image.constant(0).rename("rainfall_7d")
    )
  );

  const tempCollection = eeClient
    .ImageCollection("ECMWF/ERA5_LAND/DAILY_AGGR")
    .filterBounds(geometry)
    .filterDate(meteoStart, now)
    .select("temperature_2m");

  const tempImage = eeClient.Image(
    eeClient.Algorithms.If(
      tempCollection.size().gt(0),
      tempCollection.mean().subtract(273.15).rename("temp_c"),
      eeClient.Image.constant(0).rename("temp_c")
    )
  );

  const merged = eeClient.Image.cat([ndviImage, soilMoistureImage, rainfallImage, tempImage]);
  const dict = merged.reduceRegion({
    reducer: eeClient.Reducer.mean(),
    geometry,
    scale: 1000,
    maxPixels: 1e9,
    bestEffort: true,
  });

  const stats = await eeEvaluate(dict);

  return {
    centroidLat: round(centroid.lat, 6),
    centroidLng: round(centroid.lng, 6),
    ndviMean: round(Number(stats.ndvi ?? 0), 3),
    soilMoistureMean: round(Number(stats.soil_moisture ?? 0), 3),
    rainfallMm7d: round(Number(stats.rainfall_7d ?? 0), 2),
    averageTempC: round(Number(stats.temp_c ?? 0), 2),
  };
}

function eeEvaluate(obj) {
  return new Promise((resolve, reject) => {
    obj.evaluate((value, err) => {
      if (err) {
        reject(new Error(typeof err === "string" ? err : JSON.stringify(err)));
      } else {
        resolve(value || {});
      }
    });
  });
}

async function getCropRecommendations(summary, targetCrops = [], areaContext = {}) {
  if (!ai) {
    return fallbackRecommendations(summary, targetCrops);
  }

  const cropHint = targetCrops.length
    ? `Prioritize and explicitly evaluate these target crops first: ${targetCrops.join(", ")}.`
    : "No explicit target crops were provided; suggest generally suitable crops.";
  const recommendationCountHint = targetCrops.length > 0
    ? `Return at least ${targetCrops.length} items so each target crop is covered.`
    : "Return exactly 3 items.";

  const prompt = [
    "You are an agronomy assistant for tropical smallholder farms.",
    cropHint,
    "Given this environment summary, provide crop suitability recommendations.",
    recommendationCountHint,
    "Return strict JSON array only with keys: cropName, suitability, rationale.",
    "Suitability must be one of: High, Moderate, Low.",
    "Environment summary:",
    JSON.stringify(summary),
    "Area context (hectares):",
    JSON.stringify({
      totalFarmAreaHectares: areaContext.totalFarmAreaHectares ?? null,
      lotAreaHectares: areaContext.lotAreaHectares ?? null,
    }),
  ].join("\n");

  try {
    const result = await ai.models.generateContent({
      model: GEMINI_MODEL,
      contents: prompt,
    });

    const text = result.text || "";
    const parsed = safeParseJsonArray(text);
    if (!parsed.length) {
      return fallbackRecommendations(summary, targetCrops);
    }

    const normalized = parsed.map((item) => ({
      cropName: String(item.cropName || "Unknown"),
      suitability: normalizeSuitability(item.suitability),
      rationale: String(item.rationale || "No rationale returned."),
    }));

    return ensureTargetCropCoverage(summary, targetCrops, normalized).slice(0, Math.max(3, targetCrops.length));
  } catch (_error) {
    return fallbackRecommendations(summary, targetCrops);
  }
}

function fallbackRecommendations(summary, targetCrops = []) {
  const wet = summary.soilMoistureMean > 0.55;
  const warm = summary.averageTempC > 27;

  const requested = targetCrops
    .map((crop) => crop.trim())
    .filter((crop) => crop.length > 0)
    .map((crop) => ({
      cropName: crop,
      suitability: warm && !wet ? "High" : wet ? "Moderate" : "High",
      rationale: "Requested crop evaluated using fallback heuristics from current moisture and temperature.",
    }));

  if (requested.length > 0) {
    return requested;
  }

  return [
    {
      cropName: "Chili",
      suitability: warm ? "High" : "Moderate",
      rationale: "Performs well in warm tropical conditions with moderate moisture.",
    },
    {
      cropName: "Tomato",
      suitability: !wet ? "High" : "Moderate",
      rationale: "Favorable when moisture is controlled and disease pressure is managed.",
    },
    {
      cropName: "Okra",
      suitability: "Moderate",
      rationale: "Resilient fallback crop for variable rainfall windows.",
    },
  ];
}

function ensureTargetCropCoverage(summary, targetCrops, recommendations) {
  const normalizedTargets = targetCrops
    .map((crop) => crop.trim())
    .filter((crop) => crop.length > 0);

  if (normalizedTargets.length === 0) return recommendations;

  const existing = new Set(recommendations.map((item) => String(item.cropName || "").trim().toLowerCase()));
  const wet = summary.soilMoistureMean > 0.55;
  const warm = summary.averageTempC > 27;

  const missing = normalizedTargets
    .filter((crop) => !existing.has(crop.toLowerCase()))
    .map((crop) => ({
      cropName: crop,
      suitability: warm && !wet ? "High" : wet ? "Moderate" : "High",
      rationale: "Requested crop added to ensure coverage when Gemini did not return this crop explicitly.",
    }));

  return [...recommendations, ...missing];
}

function safeParseJsonArray(text) {
  // Try direct parse first.
  try {
    const data = JSON.parse(text);
    return Array.isArray(data) ? data : [];
  } catch (_error) {
    // Extract first JSON array block if model wrapped with prose.
    const start = text.indexOf("[");
    const end = text.lastIndexOf("]");
    if (start === -1 || end === -1 || end <= start) return [];
    try {
      const data = JSON.parse(text.slice(start, end + 1));
      return Array.isArray(data) ? data : [];
    } catch (_error2) {
      return [];
    }
  }
}

function safeParseJsonObject(text) {
  try {
    const data = JSON.parse(text);
    return data && typeof data === "object" && !Array.isArray(data) ? data : null;
  } catch (_error) {
    const start = text.indexOf("{");
    const end = text.lastIndexOf("}");
    if (start === -1 || end === -1 || end <= start) return null;
    try {
      const data = JSON.parse(text.slice(start, end + 1));
      return data && typeof data === "object" && !Array.isArray(data) ? data : null;
    } catch (_error2) {
      return null;
    }
  }
}

async function generateTimelineStageVisual({ dayNumber, expectedStage, cropName }) {
  const prompt = [
    "Generate a realistic close-up crop growth image for stage monitoring.",
    `Crop: ${cropName}`,
    `Expected stage: ${expectedStage}`,
    `Day number: ${dayNumber}`,
    "Focus on realistic morphology: leaf count, leaf shape, vein structure, stem thickness, and stage-specific traits.",
    "Image style: natural daylight farm photo, close-up framing of the plant, soft background blur.",
    "The image must show the crop plant itself as the main subject.",
    "Do NOT generate maps, field layout plans, charts, tables, legends, labels, text, logos, or UI screens.",
    "Do NOT generate satellite views, top-down parcel diagrams, or infographic posters.",
    "No people, no machinery, no watermarks.",
  ].join("\n");

  const title = `${cropName} - ${expectedStage}`;
  const description = `AI expected morphology for Day ${dayNumber}. Compare leaf/stem/reproductive structures with your real plant photo.`;

  if (VERTEX_IMAGE_ENABLED && isVertexConfigured()) {
    try {
      const imageDataUrl = await generateStageImageWithVertex(prompt);
      return {
        dayNumber,
        expectedStage,
        cropName,
        title,
        description,
        imageDataUrl,
        prompt,
        provider: "vertex-imagen-live",
      };
    } catch (error) {
      console.warn(
        `[timeline-stage-visual] Vertex fallback to Gemini: ${
          error instanceof Error ? error.message : String(error)
        }`
      );
      // Fall through to Gemini SVG generation as secondary strategy.
    }
  }

  if (!ai) {
    throw new Error("wrong connection to server");
  }

  let svg = fallbackStageSvg({ cropName, expectedStage, dayNumber });

  try {
    const svgPrompt = [
      "Generate a clean SVG illustration for crop growth monitoring UI.",
      `Crop: ${cropName}`,
      `Expected stage: ${expectedStage}`,
      `Day number: ${dayNumber}`,
      "Output ONLY raw SVG markup, no markdown, no explanation.",
      "Add clear morphology detail: multiple leaves, visible veins, stem texture, and stage-specific structures.",
      "Use transparent/very light background and no text labels.",
    ].join("\n");

    const result = await ai.models.generateContent({
      model: GEMINI_MODEL,
      contents: svgPrompt,
    });
    const text = (result.text || "").trim();
    const maybeSvg = extractSvg(text);
    if (!maybeSvg) {
      throw new Error("wrong connection to server");
    }
    svg = maybeSvg;
  } catch (_error) {
    throw new Error("wrong connection to server");
  }

  const imageDataUrl = `data:image/svg+xml;base64,${Buffer.from(svg, "utf8").toString("base64")}`;
  return {
    dayNumber,
    expectedStage,
    cropName,
    title,
    description,
    imageDataUrl,
    prompt,
    provider: "gemini-live",
  };
}

function isVertexConfigured() {
  return VERTEX_IMAGE_ENABLED && Boolean(VERTEX_PROJECT_ID && VERTEX_LOCATION && VERTEX_IMAGE_MODEL);
}

async function getVertexAccessToken() {
  const now = Date.now();
  if (vertexTokenCache.token && vertexTokenCache.expiresAt - now > 60_000) {
    return vertexTokenCache.token;
  }

  const credentials = VERTEX_SERVICE_ACCOUNT_JSON.trim()
    ? JSON.parse(VERTEX_SERVICE_ACCOUNT_JSON)
    : undefined;

  const auth = credentials
    ? new GoogleAuth({ credentials, scopes: [VERTEX_SCOPE] })
    : new GoogleAuth({ scopes: [VERTEX_SCOPE] });
  const client = await auth.getClient();
  const tokenResponse = await client.getAccessToken();
  const token = tokenResponse?.token || "";
  if (!token) {
    throw new Error("wrong connection to server");
  }

  vertexTokenCache = {
    token,
    expiresAt: now + 50 * 60_000,
  };
  return token;
}

async function generateStageImageWithVertex(prompt) {
  const accessToken = await getVertexAccessToken();
  const modelCandidates = [
    ...new Set([
      VERTEX_IMAGE_MODEL,
      ...VERTEX_IMAGE_MODEL_CANDIDATES,
      "imagen-3.0-generate-002",
      "imagegeneration@006",
    ]),
  ];

  for (const modelName of modelCandidates) {
    const endpoint = `https://${VERTEX_LOCATION}-aiplatform.googleapis.com/v1/projects/${VERTEX_PROJECT_ID}/locations/${VERTEX_LOCATION}/publishers/google/models/${modelName}:predict`;

    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({
        instances: [
          {
            prompt,
          },
        ],
        parameters: {
          sampleCount: 1,
        },
      }),
    });

    if (!response.ok) {
      const detail = await response.text().catch(() => "");
      console.warn(`[vertex-imagen] ${modelName} failed: ${response.status} ${detail.slice(0, 200)}`);
      continue;
    }

    const payload = await response.json();
    const first = Array.isArray(payload?.predictions) ? payload.predictions[0] : null;
    const bytes =
      first?.bytesBase64Encoded ||
      first?.image?.bytesBase64Encoded ||
      first?.b64Json ||
      "";
    const mimeType = first?.mimeType || first?.image?.mimeType || "image/png";

    if (!bytes) {
      console.warn(`[vertex-imagen] ${modelName} returned no image bytes`);
      continue;
    }

    return `data:${mimeType};base64,${bytes}`;
  }

  throw new Error("wrong connection to server");
}

async function assessTimelinePhoto({
  dayNumber,
  expectedStage,
  cropName,
  photoMimeType,
  photoBase64,
  userMarkedSimilar,
}) {
  const fallback = fallbackPhotoAssessment({ dayNumber, expectedStage, cropName, userMarkedSimilar, seedText: photoBase64 });
  if (!ai) {
    throw new Error("wrong connection to server");
  }

  const prompt = [
    "You are an agronomy visual inspector.",
    `Crop: ${cropName}`,
    `Expected stage/day: ${expectedStage} (Day ${dayNumber})`,
    userMarkedSimilar == null ? "User similarity feedback: not provided" : `User similarity feedback: ${userMarkedSimilar ? "similar" : "not similar"}`,
    "Analyze the uploaded crop photo and return STRICT JSON with keys:",
    "similarityScore (0-100 integer), isSimilar (boolean), observedStage (string), recommendation (string), rationale (string)",
    "Keep recommendation practical for farmers.",
  ].join("\n");

  try {
    const result = await ai.models.generateContent({
      model: GEMINI_MODEL,
      contents: [
        {
          role: "user",
          parts: [
            { text: prompt },
            {
              inlineData: {
                mimeType: photoMimeType,
                data: photoBase64,
              },
            },
          ],
        },
      ],
    });

    const parsed = safeParseJsonObject(result.text || "");
    if (!parsed) {
      throw new Error("wrong connection to server");
    }

    const similarityScore = clamp(Number(parsed.similarityScore ?? fallback.similarityScore), 0, 100);
    const isSimilar = typeof parsed.isSimilar === "boolean"
      ? parsed.isSimilar
      : similarityScore >= 65;

    return {
      dayNumber,
      expectedStage,
      cropName,
      similarityScore: Math.round(similarityScore),
      isSimilar,
      observedStage: String(parsed.observedStage || fallback.observedStage),
      recommendation: String(parsed.recommendation || fallback.recommendation),
      rationale: String(parsed.rationale || fallback.rationale),
      provider: "gemini-live",
    };
  } catch (_error) {
    throw new Error("wrong connection to server");
  }
}

async function generateAiChatReply({ message, history, context }) {
  if (!ai) {
    throw new Error("wrong connection to server");
  }

  const contextLines = context
    ? [
        context.farmName ? `Farm name: ${context.farmName}` : null,
        context.cropName ? `Primary crop: ${context.cropName}` : null,
        context.mode ? `Current mode: ${context.mode}` : null,
        context.latestRecommendation ? `Latest recommendation: ${context.latestRecommendation}` : null,
      ].filter(Boolean)
    : [];

  const historyText = history.length
    ? history.map((item) => `${item.role === "user" ? "User" : "Assistant"}: ${item.content}`).join("\n")
    : "(No prior messages)";

  const prompt = [
    "You are FarmTwin AI, an assistant for farmers.",
    "Give practical, concise answers and focus on agronomy decisions.",
    "When uncertain, suggest a safe next observation or check.",
    contextLines.length ? "Context:" : null,
    contextLines.length ? contextLines.join("\n") : null,
    "Conversation so far:",
    historyText,
    "Latest user message:",
    message,
  ].filter(Boolean).join("\n");

  let text = "";
  try {
    const result = await ai.models.generateContent({
      model: GEMINI_MODEL,
      contents: prompt,
    });
    text = String(result.text || "").trim();
  } catch (error) {
    const raw = String(error instanceof Error ? error.message : error || "");
    if (raw.includes("API_KEY_IP_ADDRESS_BLOCKED")) {
      throw new Error(
        "Gemini API key is blocked by IP restriction. Update API key restrictions to allow this server IP or remove IP restriction."
      );
    }
    if (raw.includes("403")) {
      throw new Error("Gemini request was forbidden. Check API key restrictions and API enablement.");
    }
    throw new Error("Unable to get Gemini response from backend.");
  }

  if (!text) {
    throw new Error("Gemini returned an empty response.");
  }

  return {
    reply: text,
    provider: "gemini-live",
  };
}

function extractSvg(text) {
  const start = text.indexOf("<svg");
  const end = text.lastIndexOf("</svg>");
  if (start === -1 || end === -1 || end <= start) return null;
  const svg = text.slice(start, end + 6).trim();
  return svg.startsWith("<svg") ? svg : null;
}

function fallbackStageSvg({ cropName, expectedStage, dayNumber }) {
  const stage = String(expectedStage || "Growth").replace(/[<>&]/g, "");
  const crop = String(cropName || "Crop").replace(/[<>&]/g, "");
  return `
<svg xmlns="http://www.w3.org/2000/svg" width="640" height="420" viewBox="0 0 640 420">
  <rect width="640" height="420" fill="#f4f8f2" rx="20"/>
  <rect x="0" y="300" width="640" height="120" fill="#d9ead3"/>
  <path d="M320 300 C300 255, 300 210, 320 165 C340 210, 340 255, 320 300" fill="#4f9f59"/>
  <path d="M320 235 C255 220, 230 180, 220 140 C275 150, 305 175, 320 205" fill="#72bf78"/>
  <path d="M320 225 C385 210, 410 170, 420 130 C365 140, 335 165, 320 195" fill="#72bf78"/>
  <circle cx="320" cy="145" r="16" fill="#ffd166"/>
  <text x="28" y="44" font-family="Arial, sans-serif" font-size="30" fill="#1d3b24" font-weight="700">${crop} - Day ${dayNumber}</text>
  <text x="28" y="80" font-family="Arial, sans-serif" font-size="24" fill="#345f3e">Expected stage: ${stage}</text>
</svg>
`.trim();
}

function fallbackPhotoAssessment({ dayNumber, expectedStage, cropName, userMarkedSimilar, seedText }) {
  const seed = Math.abs(String(seedText || "").length + dayNumber * 13 + String(expectedStage).length * 7) % 101;
  let similarityScore = 45 + (seed % 46);
  if (userMarkedSimilar === true) similarityScore = Math.max(similarityScore, 68);
  if (userMarkedSimilar === false) similarityScore = Math.min(similarityScore, 62);
  const isSimilar = similarityScore >= 65;

  return {
    dayNumber,
    expectedStage,
    cropName,
    similarityScore,
    isSimilar,
    observedStage: isSimilar ? expectedStage : "Slightly behind expected stage",
    recommendation: isSimilar
      ? "Maintain current irrigation and nutrient schedule; continue daily monitoring."
      : "Re-check watering pattern and inspect leaf color/uniformity; consider light nutrient correction.",
    rationale: isSimilar
      ? "Visual structure appears aligned with expected morphology for this day."
      : "Detected differences in canopy density/stage cues compared with expected progression.",
  };
}

function normalizeSuitability(value) {
  const raw = String(value || "Moderate").toLowerCase();
  if (raw.includes("high")) return "High";
  if (raw.includes("low")) return "Low";
  return "Moderate";
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function round(value, precision) {
  const factor = 10 ** precision;
  return Math.round(value * factor) / factor;
}
