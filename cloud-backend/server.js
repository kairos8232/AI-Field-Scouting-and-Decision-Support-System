import cors from "cors";
import dotenv from "dotenv";
import express from "express";
import admin from "firebase-admin";
import { GoogleGenAI } from "@google/genai";
import { GoogleAuth, OAuth2Client } from "google-auth-library";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

// Genkit integration
import { expressHandler } from "@genkit-ai/express";
import {
  scoutingLoopFlow,
  fieldInsightsOrchestratorFlow,
  actionTrackerFlow,
  dailyDecisionLoopFlow,
} from "./src/genkit/index.js";

const require = createRequire(import.meta.url);
const ee = require("@google/earthengine");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, "..");

// Root .env is the single source of truth for all app/backend settings.
dotenv.config({ path: path.resolve(ROOT_DIR, ".env") });

const app = express();
app.use(express.json({ limit: "15mb" }));
app.use(express.urlencoded({ limit: "15mb", extended: true }));
app.use(
  cors({
    origin: process.env.ALLOWED_ORIGIN || "*",
  })
);

const PORT = Number(process.env.PORT || 8080);
const GEMINI_MODEL = process.env.GEMINI_MODEL || "gemini-2.5-flash";
const GEMINI_MODEL_CANDIDATES = (process.env.GEMINI_MODEL_CANDIDATES || "")
  .split(",")
  .map((item) => item.trim())
  .filter(Boolean);
const EARTH_ENGINE_MODE = process.env.EARTH_ENGINE_MODE || "live";
const EARTH_ENGINE_PROJECT_ID = process.env.EARTH_ENGINE_PROJECT_ID || "";
const EARTH_ENGINE_SERVICE_ACCOUNT_JSON = process.env.EARTH_ENGINE_SERVICE_ACCOUNT_JSON || "";
const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "";
const FIREBASE_SERVICE_ACCOUNT_JSON = process.env.FIREBASE_SERVICE_ACCOUNT_JSON || "";
const FIREBASE_STORAGE_BUCKET = process.env.FIREBASE_STORAGE_BUCKET || "";
const FIREBASE_COLLECTION = process.env.FIREBASE_COLLECTION || "fieldInsights";
const FIREBASE_HISTORY_COLLECTION = process.env.FIREBASE_HISTORY_COLLECTION || "historyEvents";
const FIREBASE_WEB_API_KEY = process.env.FIREBASE_WEB_API_KEY || "";
const GOOGLE_OAUTH_CLIENT_ID = process.env.GOOGLE_OAUTH_CLIENT_ID || "";
const GOOGLE_OAUTH_CLIENT_SECRET = process.env.GOOGLE_OAUTH_CLIENT_SECRET || "";
const GOOGLE_OAUTH_REDIRECT_URI = process.env.GOOGLE_OAUTH_REDIRECT_URI || "";
const GOOGLE_OAUTH_APP_REDIRECT_URI = process.env.GOOGLE_OAUTH_APP_REDIRECT_URI || "farmtwinai://oauth2redirect/google";
const GOOGLE_MAPS_API_KEY = process.env.GOOGLE_MAPS_API_KEY || "";
const VERTEX_PROJECT_ID = process.env.VERTEX_PROJECT_ID || FIREBASE_PROJECT_ID || EARTH_ENGINE_PROJECT_ID || "";
const VERTEX_LOCATION = process.env.VERTEX_LOCATION || "us-central1";
const VERTEX_IMAGE_MODEL = process.env.VERTEX_IMAGE_MODEL || "imagen-4.0-fast-generate-001";
const VERTEX_IMAGE_MODEL_CANDIDATES = (process.env.VERTEX_IMAGE_MODEL_CANDIDATES || "")
  .split(",")
  .map((item) => item.trim())
  .filter(Boolean);
const VERTEX_SERVICE_ACCOUNT_JSON = process.env.VERTEX_SERVICE_ACCOUNT_JSON || "";
const VERTEX_IMAGE_ENABLED = String(process.env.VERTEX_IMAGE_ENABLED || "true").toLowerCase() !== "false";
const VERTEX_SEARCH_ENABLED = String(process.env.VERTEX_SEARCH_ENABLED || "false").toLowerCase() === "true";
const VERTEX_SEARCH_LOCATION = process.env.VERTEX_SEARCH_LOCATION || "global";
const VERTEX_SEARCH_COLLECTION = process.env.VERTEX_SEARCH_COLLECTION || "default_collection";
const VERTEX_SEARCH_DATA_STORE = process.env.VERTEX_SEARCH_DATA_STORE || "";
const VERTEX_SEARCH_SERVING_CONFIG = process.env.VERTEX_SEARCH_SERVING_CONFIG || "default_search";
const VERTEX_SEARCH_QUERY_EXPANSION_ENABLED = String(process.env.VERTEX_SEARCH_QUERY_EXPANSION_ENABLED || "true").toLowerCase() !== "false";
const VERTEX_SEARCH_QUERY_EXPANSION_VARIANTS = Number.isFinite(Number(process.env.VERTEX_SEARCH_QUERY_EXPANSION_VARIANTS))
  ? Math.min(Math.max(Math.trunc(Number(process.env.VERTEX_SEARCH_QUERY_EXPANSION_VARIANTS)), 0), 8)
  : 3;
const VERTEX_SEARCH_EXPANSION_PAGE_SIZE = Number.isFinite(Number(process.env.VERTEX_SEARCH_EXPANSION_PAGE_SIZE))
  ? Math.min(Math.max(Math.trunc(Number(process.env.VERTEX_SEARCH_EXPANSION_PAGE_SIZE)), 1), 10)
  : 4;

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
    firebaseStorageBucket: resolveFirebaseStorageBucketName(),
    knowledgeBaseEnabled: VERTEX_SEARCH_ENABLED,
    knowledgeBaseConfigured: isVertexSearchConfigured(),
    knowledgeQueryExpansionEnabled: VERTEX_SEARCH_QUERY_EXPANSION_ENABLED,
    knowledgeQueryExpansionVariants: VERTEX_SEARCH_QUERY_EXPANSION_VARIANTS,
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

    const existingDoc = await db.collection("farmConfigs").doc(userId).get();
    const existingData = existingDoc.exists ? (existingDoc.data() || {}) : {};
    const existingFarms = normalizeFarms(
      parseFirestoreArrayField(existingData.farmsJson, existingData.farms),
      {
        farmName: String(existingData.farmName || "").trim(),
        address: String(existingData.address || "").trim(),
        mapQuery: String(existingData.mapQuery || "").trim(),
        totalAreaInput: String(existingData.totalAreaInput || "").trim(),
        mode: String(existingData.mode || "").trim(),
        plantingDate: String(existingData.plantingDate || "").trim(),
        boundaryPoints: normalizeNormalizedPoints(parseFirestoreArrayField(existingData.boundaryPointsJson, existingData.boundaryPoints)),
        lots: normalizeLots(parseFirestoreArrayField(existingData.lotsJson, existingData.lots)),
      },
      new Map(),
    );
    const existingCreatedAtByFarmId = new Map(
      existingFarms
        .filter((farm) => Number.isFinite(farm.createdAtEpochMs) && farm.createdAtEpochMs > 0)
        .map((farm) => [farm.id, farm.createdAtEpochMs])
    );

    const legacyFarmName = String(req.body?.farmName || "").trim();
    const legacyAddress = String(req.body?.address || "").trim();
    const legacyMapQuery = String(req.body?.mapQuery || "").trim();
    const legacyTotalAreaInput = String(req.body?.totalAreaInput || "").trim();
    const legacyMode = String(req.body?.mode || "").trim();
    const legacyPlantingDate = String(req.body?.plantingDate || "").trim();
    const legacyBoundaryPoints = normalizeNormalizedPoints(req.body?.boundaryPoints);
    const legacyLots = normalizeLots(req.body?.lots);
    const farms = normalizeFarms(req.body?.farms, {
      farmName: legacyFarmName,
      address: legacyAddress,
      mapQuery: legacyMapQuery,
      totalAreaInput: legacyTotalAreaInput,
      mode: legacyMode,
      plantingDate: legacyPlantingDate,
      boundaryPoints: legacyBoundaryPoints,
      lots: legacyLots,
    }, existingCreatedAtByFarmId);
    const requestedActiveFarmId = String(req.body?.activeFarmId || "").trim();
    const activeFarm = farms.find((farm) => farm.id === requestedActiveFarmId) || farms[0] || null;
    const activeFarmId = activeFarm?.id || null;
    const timelinePhotoCache = normalizeTimelinePhotoCache(req.body?.timelinePhotoCache);
    const normalizedTimelineStageVisualCache = normalizeTimelineStageVisualCache(req.body?.timelineStageVisualCache);
    const timelineStageVisualCache = await materializeTimelineStageVisualCache({
      entries: normalizedTimelineStageVisualCache,
      userId,
      activeFarmId,
    });
    const timelineAssessmentCache = normalizeTimelineAssessmentCache(req.body?.timelineAssessmentCache);
    const timelineActionDecisionCache = normalizeTimelineActionDecisionCache(req.body?.timelineActionDecisionCache);
    const timelineInsightCache = normalizeTimelineInsightCache(req.body?.timelineInsightCache);

    if (farms.length === 0) {
      return res.status(400).json({ error: "At least one farm is required." });
    }

    const payload = {
      userId,
      activeFarmId,
      farmsJson: JSON.stringify(farms),
      // Keep legacy single-farm fields for backward compatibility.
      farmName: activeFarm?.farmName || "",
      address: activeFarm?.address || "",
      mapQuery: activeFarm?.mapQuery || "",
      totalAreaInput: activeFarm?.totalAreaInput || "",
      mode: activeFarm?.mode || "PLANNING",
      plantingDate: activeFarm?.plantingDate || "",
      boundaryPointsJson: JSON.stringify(activeFarm?.boundaryPoints || []),
      lotsJson: JSON.stringify(activeFarm?.lots || []),
      timelinePhotoCacheJson: JSON.stringify(timelinePhotoCache),
      timelineStageVisualCacheJson: JSON.stringify(compactTimelineStageVisualCache(timelineStageVisualCache)),
      timelineAssessmentCacheJson: JSON.stringify(timelineAssessmentCache),
      timelineActionDecisionCacheJson: JSON.stringify(timelineActionDecisionCache),
      timelineInsightCacheJson: JSON.stringify(timelineInsightCache),
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
    const farms = normalizeFarms(parseFirestoreArrayField(data.farmsJson, data.farms), {
      farmName: String(data.farmName || "").trim(),
      address: String(data.address || "").trim(),
      mapQuery: String(data.mapQuery || "").trim(),
      totalAreaInput: String(data.totalAreaInput || "").trim(),
      mode: String(data.mode || "").trim(),
      plantingDate: String(data.plantingDate || "").trim(),
      boundaryPoints: normalizeNormalizedPoints(parseFirestoreArrayField(data.boundaryPointsJson, data.boundaryPoints)),
      lots: normalizeLots(parseFirestoreArrayField(data.lotsJson, data.lots)),
    });
    const activeFarmId = String(data.activeFarmId || "").trim();
    const activeFarm = farms.find((farm) => farm.id === activeFarmId) || farms[0] || null;
    const timelinePhotoCache = normalizeTimelinePhotoCache(parseFirestoreArrayField(data.timelinePhotoCacheJson, data.timelinePhotoCache));
    const timelineStageVisualCache = normalizeTimelineStageVisualCache(parseFirestoreArrayField(data.timelineStageVisualCacheJson, data.timelineStageVisualCache));
    const timelineAssessmentCache = normalizeTimelineAssessmentCache(parseFirestoreArrayField(data.timelineAssessmentCacheJson, data.timelineAssessmentCache));
    const timelineActionDecisionCache = normalizeTimelineActionDecisionCache(parseFirestoreArrayField(data.timelineActionDecisionCacheJson, data.timelineActionDecisionCache));
    const timelineInsightCache = normalizeTimelineInsightCache(parseFirestoreArrayField(data.timelineInsightCacheJson, data.timelineInsightCache));

    return res.json({
      item: {
        id: doc.id,
        ...data,
        farms,
        activeFarmId: activeFarm?.id || null,
        farmName: activeFarm?.farmName || "",
        address: activeFarm?.address || "",
        mapQuery: activeFarm?.mapQuery || "",
        totalAreaInput: activeFarm?.totalAreaInput || "",
        mode: activeFarm?.mode || "PLANNING",
        plantingDate: activeFarm?.plantingDate || "",
        createdAtEpochMs: activeFarm?.createdAtEpochMs || 0,
        boundaryPoints: activeFarm?.boundaryPoints || [],
        lots: activeFarm?.lots || [],
        timelinePhotoCache,
        timelineStageVisualCache,
        timelineAssessmentCache,
        timelineActionDecisionCache,
        timelineInsightCache,
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
    const farms = normalizeFarms(parseFirestoreArrayField(data.farmsJson, data.farms), {
      farmName: String(data.farmName || "").trim(),
      address: String(data.address || "").trim(),
      mapQuery: String(data.mapQuery || "").trim(),
      totalAreaInput: String(data.totalAreaInput || "").trim(),
      mode: String(data.mode || "").trim(),
      plantingDate: String(data.plantingDate || "").trim(),
      boundaryPoints: normalizeNormalizedPoints(parseFirestoreArrayField(data.boundaryPointsJson, data.boundaryPoints)),
      lots: normalizeLots(parseFirestoreArrayField(data.lotsJson, data.lots)),
    });
    const activeFarmId = String(data.activeFarmId || "").trim();
    const activeFarm = farms.find((farm) => farm.id === activeFarmId) || farms[0] || null;
    const timelinePhotoCache = normalizeTimelinePhotoCache(parseFirestoreArrayField(data.timelinePhotoCacheJson, data.timelinePhotoCache));
    const timelineStageVisualCache = normalizeTimelineStageVisualCache(parseFirestoreArrayField(data.timelineStageVisualCacheJson, data.timelineStageVisualCache));
    const timelineAssessmentCache = normalizeTimelineAssessmentCache(parseFirestoreArrayField(data.timelineAssessmentCacheJson, data.timelineAssessmentCache));
    const timelineActionDecisionCache = normalizeTimelineActionDecisionCache(parseFirestoreArrayField(data.timelineActionDecisionCacheJson, data.timelineActionDecisionCache));
    const timelineInsightCache = normalizeTimelineInsightCache(parseFirestoreArrayField(data.timelineInsightCacheJson, data.timelineInsightCache));

    return res.json({
      item: {
        id: doc.id,
        ...data,
        farms,
        activeFarmId: activeFarm?.id || null,
        farmName: activeFarm?.farmName || "",
        address: activeFarm?.address || "",
        mapQuery: activeFarm?.mapQuery || "",
        totalAreaInput: activeFarm?.totalAreaInput || "",
        mode: activeFarm?.mode || "PLANNING",
        plantingDate: activeFarm?.plantingDate || "",
        createdAtEpochMs: activeFarm?.createdAtEpochMs || 0,
        boundaryPoints: activeFarm?.boundaryPoints || [],
        lots: activeFarm?.lots || [],
        timelinePhotoCache,
        timelineStageVisualCache,
        timelineAssessmentCache,
        timelineActionDecisionCache,
        timelineInsightCache,
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

    const userId = normalizeUserId(req.query?.userId);

    const [eventsSnapshot, legacySnapshot] = await Promise.all([
      db
        .collection(FIREBASE_HISTORY_COLLECTION)
        .orderBy("createdAt", "desc")
        .limit(limit * 6)
        .get(),
      db
        .collection(FIREBASE_COLLECTION)
        .orderBy("createdAt", "desc")
        .limit(limit * 4)
        .get(),
    ]);

    const eventItems = eventsSnapshot.docs
      .map((doc) => mapEventHistoryItem(doc.id, doc.data()))
      .filter(Boolean)
      .filter((item) => !userId || item.userId === userId);

    const legacyItems = legacySnapshot.docs
      .map((doc) => mapLegacyScanHistoryItem(doc.id, doc.data()))
      .filter(Boolean)
      .filter((item) => !userId || item.userId === userId);

    const deduped = new Map();
    [...eventItems, ...legacyItems].forEach((item) => {
      const key = `${item.category}:${item.id}`;
      if (!deduped.has(key)) {
        deduped.set(key, item);
      }
    });

    const items = Array.from(deduped.values())
      .sort((a, b) => b.createdAtEpochMs - a.createdAtEpochMs)
      .slice(0, limit)
      .map(({ createdAtEpochMs, userId: _userId, ...rest }) => rest);

    return res.json({ items, count: items.length });
  } catch (error) {
    return res.status(500).json({
      error: "Unable to read field insights history.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/timeline/action-log", async (req, res) => {
  try {
    const userId = normalizeUserId(req.body?.userId);
    const dayNumber = Math.max(1, Number.parseInt(String(req.body?.dayNumber ?? "1"), 10) || 1);
    const actionType = String(req.body?.actionType || "").trim();
    const actionState = String(req.body?.actionState || "").trim();
    const summary = String(req.body?.summary || "").trim();
    const cropName = String(req.body?.cropName || "").trim();

    if (!userId || !actionType || !actionState) {
      return res.status(400).json({ error: "userId, actionType, and actionState are required." });
    }

    const label = `${actionType.replace(/_/g, " ")} (${actionState})`;
    await persistHistoryEvent({
      userId,
      category: "action_log",
      title: `Day ${dayNumber} action update`,
      summary: summary || label,
      recommendation: label,
      payload: {
        dayNumber,
        actionType,
        actionState,
        cropName,
      },
    });

    return res.json({ ok: true });
  } catch (error) {
    return res.status(500).json({
      error: "Unable to save action log.",
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

app.post("/api/auth/google-signin", async (req, res) => {
  try {
    const firebaseAuth = getFirebaseAuth();
    if (!firebaseAuth) {
      return res.status(503).json({
        error: "Firebase Auth is not configured.",
        detail: "Set FIREBASE_PROJECT_ID and/or FIREBASE_SERVICE_ACCOUNT_JSON, then redeploy.",
      });
    }

    const code = String(req.body?.code || "").trim();
    if (!code) {
      return res.status(400).json({ error: "code is required." });
    }

    if (!GOOGLE_OAUTH_CLIENT_ID || !GOOGLE_OAUTH_CLIENT_SECRET || !GOOGLE_OAUTH_REDIRECT_URI) {
      return res.status(503).json({
        error: "Google OAuth is not configured.",
        detail: "Set GOOGLE_OAUTH_CLIENT_ID, GOOGLE_OAUTH_CLIENT_SECRET, and GOOGLE_OAUTH_REDIRECT_URI.",
      });
    }

    const tokenResponse = await exchangeGoogleAuthorizationCode(code);
    const idToken = String(tokenResponse.id_token || "").trim();
    if (!idToken) {
      return res.status(401).json({ error: "Google did not return an id_token." });
    }

    const oauthClient = new OAuth2Client(GOOGLE_OAUTH_CLIENT_ID, GOOGLE_OAUTH_CLIENT_SECRET, GOOGLE_OAUTH_REDIRECT_URI);
    const ticket = await oauthClient.verifyIdToken({
      idToken,
      audience: GOOGLE_OAUTH_CLIENT_ID,
    });
    const payload = ticket.getPayload();
    const email = String(payload?.email || "").trim().toLowerCase();
    if (!email) {
      return res.status(401).json({ error: "Google account email is missing." });
    }

    const displayName = String(payload?.name || payload?.given_name || "").trim() || null;
    const userRecord = await findOrCreateFirebaseUserByEmail(firebaseAuth, email, displayName);
    const db = getFirestoreDb();
    if (db) {
      await db.collection("users").doc(userRecord.uid).set(
        {
          email,
          displayName: displayName || userRecord.displayName || null,
          provider: "google",
          lastLoginAt: admin.firestore.FieldValue.serverTimestamp(),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    }

    return res.json({
      userId: userRecord.uid,
      email: userRecord.email || email,
      displayName: userRecord.displayName || displayName,
      idToken,
      refreshToken: String(tokenResponse.refresh_token || "").trim() || null,
    });
  } catch (error) {
    return res.status(500).json({
      error: "Unable to sign in with Google.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.get("/api/auth/google-callback", async (req, res) => {
  const code = String(req.query?.code || "").trim();
  const error = String(req.query?.error || "").trim();
  const errorDescription = String(req.query?.error_description || "").trim();

  if (error || errorDescription) {
    const target = new URL(GOOGLE_OAUTH_APP_REDIRECT_URI);
    if (error) target.searchParams.set("error", error);
    if (errorDescription) target.searchParams.set("error_description", errorDescription);
    return res.redirect(302, target.toString());
  }

  if (!code) {
    const target = new URL(GOOGLE_OAUTH_APP_REDIRECT_URI);
    target.searchParams.set("error", "missing_code");
    target.searchParams.set("error_description", "Google callback did not include an authorization code.");
    return res.redirect(302, target.toString());
  }

  const target = new URL(GOOGLE_OAUTH_APP_REDIRECT_URI);
  target.searchParams.set("code", code);
  return res.redirect(302, target.toString());
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

    const userId = normalizeUserId(req.body?.userId);
    if (userId) {
      void persistHistoryEvent({
        userId,
        category: "timeline_comparison",
        title: `Day ${dayNumber} timeline comparison`,
        summary: assessment.recommendation,
        recommendation: `Similarity ${assessment.similarityScore}%`,
        payload: {
          dayNumber,
          expectedStage,
          cropName,
          similarityScore: assessment.similarityScore,
          observedStage: assessment.observedStage,
          isSimilar: assessment.isSimilar,
          provider: assessment.provider,
        },
      });
    }

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

    const userId = normalizeUserId(req.body?.userId);
    if (userId) {
      void persistHistoryEvent({
        userId,
        category: "conversation",
        title: "AI consultation",
        summary: truncateText(reply.reply, 280),
        recommendation: context?.cropName || context?.farmName || "Chat",
        payload: {
          message,
          historyCount: history.length,
          context,
          provider: reply.provider,
        },
      });
    }

    return res.json(reply);
  } catch (error) {
    return res.status(500).json({
      error: "Unable to generate AI chat response.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/knowledge/query", async (req, res) => {
  try {
    if (!isVertexSearchConfigured()) {
      return res.status(503).json({
        error: "Knowledge base is not configured.",
        detail: "Set VERTEX_SEARCH_ENABLED=true and provide VERTEX_SEARCH_DATA_STORE before querying.",
      });
    }

    const query = String(req.body?.query || "").trim();
    if (!query) {
      return res.status(400).json({ error: "query is required." });
    }

    const requestedPageSize = Number(req.body?.pageSize);
    const pageSize = Number.isFinite(requestedPageSize)
      ? Math.min(Math.max(Math.trunc(requestedPageSize), 1), 10)
      : 5;
    const requestedExpand = req.body?.expandQuery;
    const expandQuery = typeof requestedExpand === "boolean"
      ? requestedExpand
      : VERTEX_SEARCH_QUERY_EXPANSION_ENABLED;

    const searchResult = await queryKnowledgeBase({ query, pageSize, expandQuery });
    const ragAnswer = await buildKnowledgeRagAnswer({
      query,
      results: searchResult.results,
    });
    const mergedResults = ragAnswer
      ? [ragAnswer, ...searchResult.results]
      : searchResult.results;

    const userId = normalizeUserId(req.body?.userId);
    if (userId) {
      void persistHistoryEvent({
        userId,
        category: "kb_search",
        title: "Knowledge base search",
        summary: query,
        recommendation: mergedResults[0]?.title || "Knowledge query",
        payload: {
          query,
          totalResults: Math.max(searchResult.totalResults, mergedResults.length),
          provider: ragAnswer ? "vertex-ai-search+gemini-rag" : "vertex-ai-search-live",
        },
      });
    }

    return res.json({
      query,
      results: mergedResults,
      totalResults: Math.max(searchResult.totalResults, mergedResults.length),
      provider: ragAnswer ? "vertex-ai-search+gemini-rag" : "vertex-ai-search-live",
    });
  } catch (error) {
    return res.status(500).json({
      error: "Unable to query knowledge base.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/weather-now", async (req, res) => {
  try {
    const location = String(req.body?.location || "").trim();
    const latitude = Number(req.body?.latitude);
    const longitude = Number(req.body?.longitude);
    const hasCoordinates = Number.isFinite(latitude) && Number.isFinite(longitude);

    if (!location && !hasCoordinates) {
      return res.status(400).json({ error: "location or coordinates are required." });
    }

    if (!hasCoordinates && !GOOGLE_MAPS_API_KEY) {
      return res.status(503).json({
        error: "GOOGLE_MAPS_API_KEY is not configured.",
        detail: "Set GOOGLE_MAPS_API_KEY in backend environment and redeploy.",
      });
    }

    const geocoded = hasCoordinates
      ? {
          lat: latitude,
          lng: longitude,
          resolvedAddress: location || `Lat ${round(latitude, 5)}, Lng ${round(longitude, 5)}`,
        }
      : await geocodeWithGoogleMaps(location);

    const weather = await fetchCurrentWeather(geocoded.lat, geocoded.lng);
    return res.json({
      location: location || geocoded.resolvedAddress,
      resolvedAddress: geocoded.resolvedAddress,
      latitude: geocoded.lat,
      longitude: geocoded.lng,
      condition: weather.condition,
      temperatureC: weather.temperatureC,
      icon: weather.icon,
      rainfallMm: weather.rainfallMm,
      windKmh: weather.windKmh,
      provider: hasCoordinates ? "open-meteo-coordinates" : "google-geocode+open-meteo",
    });
  } catch (error) {
    return res.status(500).json({
      error: "Unable to fetch current weather.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/agents/scouting-loop", async (req, res) => {
  try {
    const photoBase64 = String(req.body?.photoBase64 || "").trim();
    if (!photoBase64) {
      return res.status(400).json({ error: "photoBase64 is required." });
    }

    const latitude = Number(req.body?.latitude);
    const longitude = Number(req.body?.longitude);
    const polygon = normalizePolygon(req.body?.polygon);

    const result = await scoutingLoopFlow({
      userId: normalizeUserId(req.body?.userId),
      dayNumber: Math.max(1, Number.parseInt(String(req.body?.dayNumber ?? "1"), 10) || 1),
      expectedStage: String(req.body?.expectedStage || "Growth progression").trim() || "Growth progression",
      cropName: String(req.body?.cropName || "Crop").trim() || "Crop",
      photoMimeType: String(req.body?.photoMimeType || "image/jpeg").trim() || "image/jpeg",
      photoBase64,
      location: String(req.body?.location || "").trim(),
      latitude: Number.isFinite(latitude) ? latitude : undefined,
      longitude: Number.isFinite(longitude) ? longitude : undefined,
      polygon: polygon.length >= 3 ? polygon : undefined,
      userMarkedSimilar:
        typeof req.body?.userMarkedSimilar === "boolean" ? req.body.userMarkedSimilar : undefined,
    });

    return res.json(result);
  } catch (error) {
    return res.status(500).json({
      error: "Unable to run scouting loop.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/agents/field-insights-orchestrator", async (req, res) => {
  try {
    const polygon = normalizePolygon(req.body?.polygon);
    if (polygon.length < 3) {
      return res.status(400).json({ error: "Polygon must have at least 3 points." });
    }

    const centroid = req.body?.centroid ? toLatLngPoint(req.body.centroid) : computeCentroid(polygon);
    const targetCrops = normalizeTargetCrops(req.body?.targetCrops);
    const totalFarmAreaHectares = normalizeOptionalNumber(req.body?.totalFarmAreaHectares);
    const lotAreaHectares = normalizeOptionalNumber(req.body?.lotAreaHectares);

    const result = await fieldInsightsOrchestratorFlow({
      userId: normalizeUserId(req.body?.userId),
      polygon,
      centroid,
      targetCrops,
      totalFarmAreaHectares,
      lotAreaHectares,
      location: String(req.body?.location || "").trim(),
    });

    return res.json(result);
  } catch (error) {
    return res.status(500).json({
      error: "Unable to run field insights orchestrator.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/agents/action-tracker", async (req, res) => {
  try {
    const userId = normalizeUserId(req.body?.userId);
    if (!userId) {
      return res.status(400).json({ error: "userId is required." });
    }

    const result = await actionTrackerFlow({
      userId,
      dayNumber: Math.max(1, Number.parseInt(String(req.body?.dayNumber ?? "1"), 10) || 1),
      cropName: String(req.body?.cropName || "Crop").trim() || "Crop",
      issueType: String(req.body?.issueType || "crop health issue").trim() || "crop health issue",
      actionTaken: String(req.body?.actionTaken || "").trim() || undefined,
      note: String(req.body?.note || "").trim() || undefined,
    });

    return res.json(result);
  } catch (error) {
    return res.status(500).json({
      error: "Unable to run action tracker agent.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

app.post("/api/agents/daily-decision-loop", async (req, res) => {
  try {
    const photoBase64 = String(req.body?.photoBase64 || "").trim();
    if (!photoBase64) {
      return res.status(400).json({ error: "photoBase64 is required." });
    }

    const latitude = Number(req.body?.latitude);
    const longitude = Number(req.body?.longitude);
    const polygon = normalizePolygon(req.body?.polygon);
    const threshold = String(req.body?.autoTrackThreshold || "medium").trim().toLowerCase();
    const autoTrackThreshold = ["low", "medium", "high"].includes(threshold) ? threshold : "medium";

    const result = await dailyDecisionLoopFlow({
      userId: normalizeUserId(req.body?.userId),
      dayNumber: Math.max(1, Number.parseInt(String(req.body?.dayNumber ?? "1"), 10) || 1),
      expectedStage: String(req.body?.expectedStage || "Growth progression").trim() || "Growth progression",
      cropName: String(req.body?.cropName || "Crop").trim() || "Crop",
      photoMimeType: String(req.body?.photoMimeType || "image/jpeg").trim() || "image/jpeg",
      photoBase64,
      location: String(req.body?.location || "").trim(),
      latitude: Number.isFinite(latitude) ? latitude : undefined,
      longitude: Number.isFinite(longitude) ? longitude : undefined,
      polygon: polygon.length >= 3 ? polygon : undefined,
      userMarkedSimilar:
        typeof req.body?.userMarkedSimilar === "boolean" ? req.body.userMarkedSimilar : undefined,
      issueType: String(req.body?.issueType || "crop health issue").trim() || "crop health issue",
      actionTaken: String(req.body?.actionTaken || "").trim() || undefined,
      note: String(req.body?.note || "").trim() || undefined,
      autoTrackThreshold,
    });

    return res.json(result);
  } catch (error) {
    return res.status(500).json({
      error: "Unable to run daily decision loop.",
      detail: error instanceof Error ? error.message : String(error),
    });
  }
});

// ---------------------------------------------------------------------------
// Genkit Flow HTTP endpoints
// ---------------------------------------------------------------------------
app.use(
  "/api/genkit",
  expressHandler({
    flows: [scoutingLoopFlow, fieldInsightsOrchestratorFlow, actionTrackerFlow, dailyDecisionLoopFlow],
  })
);

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

    const storageBucket = resolveFirebaseStorageBucketName();
    if (storageBucket) {
      appOptions.storageBucket = storageBucket;
    }

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

function resolveFirebaseStorageBucketName() {
  const configured = String(FIREBASE_STORAGE_BUCKET || "").trim();
  if (configured) return configured;
  const projectId = String(FIREBASE_PROJECT_ID || "").trim();
  return projectId ? `${projectId}.appspot.com` : "";
}

function getFirebaseStorageBucket() {
  getFirestoreDb();
  if (admin.apps.length === 0) return null;
  const bucketName = resolveFirebaseStorageBucketName();
  if (!bucketName) return null;

  try {
    return admin.storage().bucket(bucketName);
  } catch (_error) {
    return null;
  }
}

async function materializeTimelineStageVisualCache({ entries, userId, activeFarmId }) {
  const bucket = getFirebaseStorageBucket();
  if (!bucket || !Array.isArray(entries) || entries.length === 0) {
    return entries;
  }

  const materialized = [];
  for (const entry of entries) {
    const imageDataUrl = String(entry?.imageDataUrl || "").trim();
    if (!imageDataUrl.startsWith("data:")) {
      materialized.push(entry);
      continue;
    }

    const uploaded = await uploadStageVisualDataUrl({
      bucket,
      dataUrl: imageDataUrl,
      userId,
      activeFarmId,
      dayNumber: entry.dayNumber,
      updatedAtEpochMs: entry.updatedAtEpochMs,
    });

    materialized.push({
      ...entry,
      imageDataUrl: uploaded || imageDataUrl,
    });
  }

  return materialized;
}

async function uploadStageVisualDataUrl({ bucket, dataUrl, userId, activeFarmId, dayNumber, updatedAtEpochMs }) {
  const parsed = parseDataUrlImage(dataUrl);
  if (!parsed) return "";

  try {
    const farmKey = String(activeFarmId || "farm-legacy").replace(/[^a-zA-Z0-9_-]/g, "_");
    const stamp = Number.isFinite(Number(updatedAtEpochMs)) ? Math.trunc(Number(updatedAtEpochMs)) : Date.now();
    const ext = extensionForMime(parsed.mimeType);
    const objectPath = [
      "timeline-stage-visuals",
      String(userId || "anonymous"),
      farmKey,
      `day-${Math.max(1, Math.trunc(Number(dayNumber) || 1))}-${stamp}.${ext}`,
    ].join("/");

    const file = bucket.file(objectPath);
    await file.save(parsed.buffer, {
      resumable: false,
      metadata: {
        contentType: parsed.mimeType,
        cacheControl: "public, max-age=31536000",
      },
    });

    const [signedUrl] = await file.getSignedUrl({
      action: "read",
      expires: "2100-01-01",
    });
    return String(signedUrl || "").trim();
  } catch (_error) {
    return "";
  }
}

function parseDataUrlImage(input) {
  const text = String(input || "").trim();
  const match = text.match(/^data:([^;,]+);base64,([A-Za-z0-9+/=\n\r]+)$/);
  if (!match) return null;

  const mimeType = String(match[1] || "image/png").trim().toLowerCase();
  const base64 = String(match[2] || "").replace(/\s+/g, "");
  if (!base64) return null;

  try {
    const buffer = Buffer.from(base64, "base64");
    if (!buffer.length) return null;
    return {
      mimeType,
      buffer,
    };
  } catch (_error) {
    return null;
  }
}

function extensionForMime(mimeType) {
  const value = String(mimeType || "").toLowerCase();
  if (value.includes("png")) return "png";
  if (value.includes("webp")) return "webp";
  if (value.includes("gif")) return "gif";
  return "jpg";
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

async function exchangeGoogleAuthorizationCode(code) {
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams({
      code,
      client_id: GOOGLE_OAUTH_CLIENT_ID,
      client_secret: GOOGLE_OAUTH_CLIENT_SECRET,
      redirect_uri: GOOGLE_OAUTH_REDIRECT_URI,
      grant_type: "authorization_code",
    }),
  });

  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(`google_oauth_exchange_failed:${payload?.error || response.status}`);
  }

  return payload;
}

async function findOrCreateFirebaseUserByEmail(firebaseAuth, email, displayName) {
  try {
    const existing = await firebaseAuth.getUserByEmail(email);
    if (displayName && !existing.displayName) {
      return await firebaseAuth.updateUser(existing.uid, { displayName });
    }
    return existing;
  } catch (error) {
    const code = String(error?.code || error?.errorInfo?.code || "").toLowerCase();
    if (code.includes("auth/user-not-found")) {
      return await firebaseAuth.createUser({
        email,
        displayName: displayName || undefined,
      });
    }
    throw error;
  }
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

    void persistHistoryEvent({
      userId,
      category: "scan",
      title: "Past scanned insight",
      summary: String(response?.summary?.notes || "").trim(),
      recommendation: String(response?.recommendations?.[0]?.cropName || "No rec").trim() || "No rec",
      payload: {
        provider: String(response?.provider || ""),
      },
    });
  } catch (error) {
    console.error("Failed to persist field insight record:", error);
  }
}

async function persistHistoryEvent({ userId = null, category, title, summary, recommendation, payload = {} }) {
  const db = getFirestoreDb();
  if (!db) return;

  const normalizedCategory = String(category || "scan").trim().toLowerCase();
  if (!normalizedCategory) return;

  const normalizedUserId = normalizeUserId(userId);

  try {
    await db.collection(FIREBASE_HISTORY_COLLECTION).add({
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      userId: normalizedUserId,
      category: normalizedCategory,
      title: safeTrim(title),
      summary: safeTrim(summary),
      recommendation: safeTrim(recommendation),
      payload,
    });
  } catch (error) {
    console.error("Failed to persist history event:", error);
  }
}

function mapEventHistoryItem(id, data = {}) {
  if (!data || typeof data !== "object") return null;
  const createdAtEpochMs = resolveCreatedAtEpochMs(data.createdAt);
  const category = safeTrim(data.category).toLowerCase() || "scan";
  const summary = safeTrim(data.summary);
  const recommendation = safeTrim(data.recommendation);
  const title = safeTrim(data.title) || defaultCategoryTitle(category);

  return {
    id,
    userId: normalizeUserId(data.userId),
    category,
    title,
    summaryNotes: summary,
    recommendedCrops: recommendation,
    hasConversation: category === "conversation",
    chatMessagesCount: category === "conversation" ? 1 : 0,
    dateString: `Stored TS: ${Math.floor(createdAtEpochMs / 1000)}`,
    createdAtEpochMs,
  };
}

function mapLegacyScanHistoryItem(id, data = {}) {
  if (!data || typeof data !== "object") return null;
  const response = data.response || {};
  const summary = safeTrim(response?.summary?.notes);
  const recommendation = safeTrim(response?.recommendations?.[0]?.cropName) || "No rec";
  const createdAtEpochMs = resolveCreatedAtEpochMs(data.createdAt);

  return {
    id,
    userId: normalizeUserId(data.userId),
    category: "scan",
    title: "Past scanned insight",
    summaryNotes: summary,
    recommendedCrops: recommendation,
    hasConversation: false,
    chatMessagesCount: 0,
    dateString: `Stored TS: ${Math.floor(createdAtEpochMs / 1000)}`,
    createdAtEpochMs,
  };
}

function resolveCreatedAtEpochMs(raw) {
  if (!raw) return Date.now();

  if (typeof raw?.toMillis === "function") {
    return raw.toMillis();
  }

  const seconds = Number(raw?._seconds);
  const nanos = Number(raw?._nanoseconds);
  if (Number.isFinite(seconds)) {
    const millis = seconds * 1000;
    if (Number.isFinite(nanos)) {
      return millis + Math.floor(nanos / 1e6);
    }
    return millis;
  }

  const asNumber = Number(raw);
  if (Number.isFinite(asNumber)) {
    return asNumber > 1e12 ? Math.trunc(asNumber) : Math.trunc(asNumber * 1000);
  }

  return Date.now();
}

function safeTrim(value) {
  return String(value || "").trim();
}

function truncateText(input, maxLength = 240) {
  const text = String(input || "").trim();
  if (text.length <= maxLength) return text;
  return `${text.slice(0, Math.max(0, maxLength - 3)).trimEnd()}...`;
}

function defaultCategoryTitle(category) {
  switch (String(category || "").trim().toLowerCase()) {
    case "conversation":
      return "AI consultation";
    case "kb_search":
      return "Knowledge base search";
    case "timeline_comparison":
      return "Timeline comparison";
    case "action_log":
      return "Action log";
    case "scan":
    default:
      return "Past scanned insight";
  }
}

async function geocodeWithGoogleMaps(location) {
  const response = await fetch(
    `https://maps.googleapis.com/maps/api/geocode/json?address=${encodeURIComponent(location)}&key=${GOOGLE_MAPS_API_KEY}`
  );

  if (!response.ok) {
    throw new Error(`google_geocode_http_${response.status}`);
  }

  const payload = await response.json();
  if (payload.status !== "OK" || !Array.isArray(payload.results) || payload.results.length === 0) {
    throw new Error(`google_geocode_status_${payload.status || "UNKNOWN"}`);
  }

  const first = payload.results[0];
  const lat = Number(first?.geometry?.location?.lat);
  const lng = Number(first?.geometry?.location?.lng);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    throw new Error("google_geocode_invalid_latlng");
  }

  return {
    lat,
    lng,
    resolvedAddress: String(first?.formatted_address || location),
  };
}

async function fetchCurrentWeather(lat, lng) {
  const response = await fetch(
    `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lng}&current=temperature_2m,weather_code,precipitation,wind_speed_10m&timezone=auto`
  );

  if (!response.ok) {
    throw new Error(`open_meteo_http_${response.status}`);
  }

  const payload = await response.json();
  const current = payload?.current || {};
  const weatherCode = Number(current.weather_code);
  const temperatureC = Number(current.temperature_2m);
  const rainfallMm = Number(current.precipitation);
  const windKmh = Number(current.wind_speed_10m);

  const { condition, icon } = describeWeatherCode(weatherCode);

  return {
    condition,
    icon,
    temperatureC: Number.isFinite(temperatureC) ? round(temperatureC, 1) : 0,
    rainfallMm: Number.isFinite(rainfallMm) ? round(rainfallMm, 1) : 0,
    windKmh: Number.isFinite(windKmh) ? round(windKmh, 1) : 0,
  };
}

function describeWeatherCode(code) {
  if (!Number.isFinite(code)) {
    return { condition: "Clear", icon: "sun" };
  }

  if (code === 0) return { condition: "Clear", icon: "sun" };
  if ([1, 2].includes(code)) return { condition: "Partly cloudy", icon: "cloud-sun" };
  if (code === 3) return { condition: "Cloudy", icon: "cloud" };
  if ([45, 48].includes(code)) return { condition: "Foggy", icon: "cloud-fog" };
  if ([51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82].includes(code)) {
    return { condition: "Rainy", icon: "rain" };
  }
  if ([71, 73, 75, 77, 85, 86].includes(code)) return { condition: "Snow", icon: "snow" };
  if ([95, 96, 99].includes(code)) return { condition: "Thunderstorm", icon: "storm" };
  return { condition: "Clear", icon: "sun" };
}

function normalizeUserId(input) {
  const value = String(input || "").trim();
  return value || null;
}

function parseFirestoreArrayField(preferred, fallback = []) {
  if (Array.isArray(preferred)) return preferred;
  if (typeof preferred === "string") {
    const raw = preferred.trim();
    if (raw) {
      try {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) return parsed;
      } catch (_error) {
        // Ignore malformed JSON and fall back to legacy data.
      }
    }
  }
  return Array.isArray(fallback) ? fallback : [];
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
        plantingDate: String(raw?.plantingDate || "").trim(),
      };
    })
    .filter(Boolean);
}

function normalizeFarms(input, fallback = null, existingCreatedAtByFarmId = new Map()) {
  const farms = Array.isArray(input)
    ? input
        .map((raw, index) => {
          const lots = normalizeLots(raw?.lots);
          if (lots.length === 0) return null;

          const boundaryPoints = normalizeNormalizedPoints(raw?.boundaryPoints);
          const id = String(raw?.id || `farm-${index + 1}`).trim() || `farm-${index + 1}`;
          const incomingCreatedAtEpochMs = normalizeOptionalEpochMs(raw?.createdAtEpochMs);
          return {
            id,
            farmName: String(raw?.farmName || "").trim(),
            address: String(raw?.address || "").trim(),
            mapQuery: String(raw?.mapQuery || "").trim(),
            totalAreaInput: String(raw?.totalAreaInput || "").trim(),
            mode: String(raw?.mode || "PLANNING").trim() || "PLANNING",
            plantingDate: String(raw?.plantingDate || "").trim(),
            createdAtEpochMs:
              existingCreatedAtByFarmId.get(id)
              || incomingCreatedAtEpochMs
              || Date.now(),
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
      plantingDate: String(fallback.plantingDate || "").trim(),
      createdAtEpochMs:
        existingCreatedAtByFarmId.get("farm-legacy")
        || normalizeOptionalEpochMs(fallback.createdAtEpochMs)
        || Date.now(),
      boundaryPoints: boundaryPoints.length >= 3 ? boundaryPoints : lots[0]?.points || [],
      lots,
    },
  ];
}

function normalizeOptionalEpochMs(value) {
  const epoch = Number(value);
  if (!Number.isFinite(epoch) || epoch <= 0) return null;
  return Math.trunc(epoch);
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

function compactTimelineStageVisualCache(input) {
  if (!Array.isArray(input)) return [];

  return input
    .map((raw) => {
      const dayNumber = Number(raw?.dayNumber);
      if (!Number.isFinite(dayNumber) || dayNumber <= 0) return null;

      const imageDataUrl = String(raw?.imageDataUrl || "").trim();
      const compactImageDataUrl = imageDataUrl.startsWith("http") && imageDataUrl.length <= 2048
        ? imageDataUrl
        : "";

      const updatedAtEpochMs = Number(raw?.updatedAtEpochMs);

      return {
        dayNumber: Math.trunc(dayNumber),
        title: String(raw?.title || "").trim(),
        description: String(raw?.description || "").trim(),
        imageDataUrl: compactImageDataUrl,
        provider: String(raw?.provider || "").trim(),
        updatedAtEpochMs: Number.isFinite(updatedAtEpochMs) ? Math.trunc(updatedAtEpochMs) : Date.now(),
      };
    })
    .filter(Boolean)
    .slice(0, 90);
}

function normalizeTimelineAssessmentCache(input) {
  if (!Array.isArray(input)) return [];

  return input
    .map((raw) => {
      const dayNumber = Number(raw?.dayNumber);
      if (!Number.isFinite(dayNumber) || dayNumber <= 0) return null;

      const similarityScore = Number(raw?.similarityScore);
      const updatedAtEpochMs = Number(raw?.updatedAtEpochMs);

      return {
        dayNumber: Math.trunc(dayNumber),
        expectedStage: String(raw?.expectedStage || "").trim(),
        cropName: String(raw?.cropName || "").trim(),
        similarityScore: Number.isFinite(similarityScore) ? similarityScore : 0,
        isSimilar: Boolean(raw?.isSimilar),
        observedStage: String(raw?.observedStage || "").trim(),
        recommendation: String(raw?.recommendation || "").trim(),
        rationale: String(raw?.rationale || "").trim(),
        provider: String(raw?.provider || "").trim(),
        updatedAtEpochMs: Number.isFinite(updatedAtEpochMs) ? Math.trunc(updatedAtEpochMs) : Date.now(),
      };
    })
    .filter(Boolean)
    .slice(0, 90);
}

function normalizeTimelineActionDecisionCache(input) {
  if (!Array.isArray(input)) return [];

  return input
    .map((raw) => {
      const dayNumber = Number(raw?.dayNumber);
      if (!Number.isFinite(dayNumber) || dayNumber <= 0) return null;

      const actionType = String(raw?.actionType || "").trim().toUpperCase();
      const state = String(raw?.state || "").trim().toUpperCase();
      if (!actionType || !state) return null;

      const updatedAtEpochMs = Number(raw?.updatedAtEpochMs);
      const confidence = Number(raw?.confidence);

      return {
        dayNumber: Math.trunc(dayNumber),
        actionType,
        state,
        updatedAtEpochMs: Number.isFinite(updatedAtEpochMs) ? Math.trunc(updatedAtEpochMs) : Date.now(),
        nextBestAction: String(raw?.nextBestAction || "").trim(),
        followUpQuestion: String(raw?.followUpQuestion || "").trim(),
        confidence: Number.isFinite(confidence) ? confidence : 0,
        riskLevel: String(raw?.riskLevel || "unknown").trim() || "unknown",
        provider: String(raw?.provider || "agent-action-tracker-v1").trim() || "agent-action-tracker-v1",
      };
    })
    .filter(Boolean)
    .slice(0, 90);
}

function normalizeTimelineInsightCache(input) {
  if (!Array.isArray(input)) return [];
  const allowedStatuses = new Set(["NORMAL", "WARNING", "ACTION_TAKEN", "UPDATED"]);

  return input
    .map((raw) => {
      const dayNumber = Number(raw?.dayNumber);
      if (!Number.isFinite(dayNumber) || dayNumber <= 0) return null;

      const sourceDayNumber = Number(raw?.sourceDayNumber);
      const etaDaysMin = Number(raw?.etaDaysMin);
      const etaDaysMax = Number(raw?.etaDaysMax);
      const confidencePercent = Number(raw?.confidencePercent);
      const updatedAtEpochMs = Number(raw?.updatedAtEpochMs);

      const timelineStatusRaw = String(raw?.timelineStatus || "").trim().toUpperCase();

      return {
        dayNumber: Math.trunc(dayNumber),
        recommendedActionText: String(raw?.recommendedActionText || "").trim(),
        timelineStatus: allowedStatuses.has(timelineStatusRaw) ? timelineStatusRaw : null,
        sourceDayNumber: Number.isFinite(sourceDayNumber) ? Math.trunc(sourceDayNumber) : Math.trunc(dayNumber),
        trend: String(raw?.trend || "UNKNOWN").trim().toUpperCase() || "UNKNOWN",
        etaDaysMin: Number.isFinite(etaDaysMin) ? Math.max(1, Math.trunc(etaDaysMin)) : 1,
        etaDaysMax: Number.isFinite(etaDaysMax) ? Math.max(1, Math.trunc(etaDaysMax)) : 1,
        confidencePercent: Number.isFinite(confidencePercent) ? Math.max(0, Math.min(100, Math.trunc(confidencePercent))) : 0,
        confidenceTier: String(raw?.confidenceTier || "LOW").trim().toUpperCase() || "LOW",
        isUrgent: Boolean(raw?.isUrgent),
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
  if (EARTH_ENGINE_MODE === "mock") {
    throw new Error("EARTH_ENGINE_MODE=mock is disabled. Set EARTH_ENGINE_MODE=live.");
  }

  const eeStatus = await resolveEarthEngineStatus();
  if (!eeStatus.linked) {
    throw new Error(`Earth Engine live mode unavailable (${eeStatus.reason}).`);
  }

  try {
    const sampled = await sampleEarthEngineSummary(polygon, centroid);
    const sparse = isSparseEarthSummary(sampled);
    return {
      ...sampled,
      source: "earth-engine-live",
      sourceVerified: true,
      notes: sparse
        ? "Real Earth Engine datasets sampled, but metrics are sparse for this polygon/time window (Sentinel-2 NDVI, SMAP soil moisture, CHIRPS rainfall, ERA5 temperature)."
        : "Real Earth Engine datasets sampled (Sentinel-2 NDVI, SMAP soil moisture, CHIRPS rainfall, ERA5 temperature).",
    };
  } catch (error) {
    throw new Error(`Earth Engine sampling failed (${error instanceof Error ? error.message : String(error)}).`);
  }
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
    "Generate a photorealistic close-up crop growth photo for stage monitoring.",
    `Crop: ${cropName}`,
    `Expected stage: ${expectedStage}`,
    `Day number: ${dayNumber}`,
    "Focus on realistic morphology: leaf count, leaf shape, vein structure, stem thickness, and stage-specific traits.",
    "Image style: natural daylight farm photo, close-up framing of the plant, soft background blur.",
    "Photorealistic image only. No illustration, no icon, no cartoon, no vector, no CGI, no drawing, no painting.",
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
        `[timeline-stage-visual] Vertex photorealistic generation failed: ${
          error instanceof Error ? error.message : String(error)
        }`
      );
      const svg = fallbackStageSvg({ cropName, expectedStage, dayNumber });
      return {
        dayNumber,
        expectedStage,
        cropName,
        title,
        description,
        imageDataUrl: svgToDataUrl(svg),
        prompt,
        provider: "vertex-imagen-fallback-svg",
      };
    }
  }

  const svg = fallbackStageSvg({ cropName, expectedStage, dayNumber });
  return {
    dayNumber,
    expectedStage,
    cropName,
    title,
    description,
    imageDataUrl: svgToDataUrl(svg),
    prompt,
    provider: "timeline-fallback-svg",
  };
}

function svgToDataUrl(svgText) {
  return `data:image/svg+xml;base64,${Buffer.from(String(svgText || ""), "utf8").toString("base64")}`;
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
    return { ...fallback, provider: "fallback (no connection)" };
  }

  const prompt = [
    "You are an agronomy visual inspector.",
    `Crop: ${cropName}`,
    `Expected stage/day: ${expectedStage} (Day ${dayNumber})`,
    userMarkedSimilar == null ? "User similarity feedback: not provided" : `User similarity feedback: ${userMarkedSimilar ? "similar" : "not similar"}`,
    "Analyze the uploaded crop photo and return STRICT JSON with keys:",
    "similarityScore (0-100 integer), isSimilar (boolean), observedStage (string), recommendation (string), rationale (string)",
    "Keep recommendation and rationale extremely short (max 1 sentence each) and direct.",
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
      return { ...fallback, provider: "fallback (parse error)" };
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
  } catch (error) {
    console.error("Gemini assessment error:", error);
    return { ...fallback, provider: "fallback (api error)" };
  }
}

async function generateAiChatReply({ message, history, context }) {
  if (!ai) {
    return {
      reply: [
        "Gemini is temporarily unavailable.",
        "Immediate next step:",
        "1) Inspect leaves and stem base for new spots, wilting, or pest signs.",
        "2) Check soil moisture at root depth before watering.",
        "3) Upload a new photo in Timeline after 24 hours for re-check.",
      ].join("\n"),
      provider: "gemini-fallback-unavailable",
    };
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

  const modelCandidates = [
    ...new Set([
      GEMINI_MODEL,
      ...GEMINI_MODEL_CANDIDATES,
      "gemini-2.5-flash",
      "gemini-1.5-flash",
    ]),
  ];

  let text = "";
  let lastError = null;
  const errorMessages = [];

  for (const modelName of modelCandidates) {
    for (let attempt = 0; attempt < 2; attempt += 1) {
      try {
        const result = await ai.models.generateContent({
          model: modelName,
          contents: prompt,
        });
        text = String(result.text || "").trim();
        if (text) {
          break;
        }
      } catch (error) {
        lastError = error;
        const raw = String(error instanceof Error ? error.message : error || "");
        errorMessages.push(raw);
        const isTransient = /429|500|502|503|504|timed out|deadline|unavailable/i.test(raw);
        const isModelNotFound = /404|not found|model/i.test(raw);
        if (isModelNotFound) {
          break;
        }
        if (!isTransient) {
          break;
        }
      }
    }

    if (text) {
      break;
    }
  }

  if (!text) {
    const raw = String(lastError instanceof Error ? lastError.message : lastError || "");
    const combinedErrors = errorMessages.length ? `${errorMessages.join(" | ")} | ${raw}` : raw;

    // Check for various error patterns (SDK wraps HTTP errors differently)
    const isQuotaExhausted = /429|RESOURCE_EXHAUSTED|quota|rate limit|too many requests|exceeded your current quota/i.test(combinedErrors);
    const isApiKeyIssue = /API_KEY_INVALID|API key expired|401|authentication/i.test(combinedErrors);
    const isIpBlocked = combinedErrors.includes("API_KEY_IP_ADDRESS_BLOCKED");
    const isPermissionIssue = /403|forbidden|permission|access denied/i.test(combinedErrors);
    const isServiceUnavailable = /503|service unavailable|temporarily unavailable|high demand/i.test(combinedErrors);

    let fallbackTitle = "Gemini is not reachable right now.";
    let extraGuidance = [];

    if (isApiKeyIssue) {
      fallbackTitle = "Gemini API configuration issue.";
      extraGuidance = ["API key validation failed. Contact system administrator."];
    } else if (isIpBlocked) {
      fallbackTitle = "Gemini access restricted.";
      extraGuidance = ["API key IP restriction. Contact system administrator."];
    } else if (isQuotaExhausted) {
      fallbackTitle = "Gemini daily quota reached.";
      extraGuidance = [
        "Free tier: 20 requests/day for gemini-2.5-flash",
        "Try again after midnight UTC (quota resets daily)",
        "Or upgrade to Gemini API paid tier for unlimited requests",
      ];
    } else if (isServiceUnavailable) {
      fallbackTitle = "Gemini service temporarily overloaded.";
      extraGuidance = ["High demand detected. Try again in a few minutes."];
    } else if (isPermissionIssue) {
      fallbackTitle = "Gemini permission denied.";
      extraGuidance = ["Access restriction. Contact system administrator."];
    }

    return {
      reply: [
        fallbackTitle,
        ...extraGuidance,
        "",
        "Quick fallback guidance:",
        "1) Follow the latest recommended action from Timeline.",
        "2) Re-check crop symptoms in 24 hours.",
        "3) If symptoms worsen, open Knowledge Base for crop-specific control steps.",
      ].join("\n"),
      provider: "gemini-fallback-runtime",
    };
  }

  if (!text) {
    throw new Error("Gemini returned an empty response.");
  }

  return {
    reply: text,
    provider: "gemini-live",
  };
}

function isVertexSearchConfigured() {
  return VERTEX_SEARCH_ENABLED && Boolean(VERTEX_PROJECT_ID && VERTEX_SEARCH_DATA_STORE);
}

async function queryKnowledgeBase({ query, pageSize, expandQuery = VERTEX_SEARCH_QUERY_EXPANSION_ENABLED }) {
  const cleanQuery = String(query || "").trim();
  const normalizedPageSize = Math.min(Math.max(Math.trunc(Number(pageSize) || 5), 1), 10);
  const candidates = buildKnowledgeQueryCandidates(cleanQuery, {
    enabled: expandQuery,
    maxVariants: VERTEX_SEARCH_QUERY_EXPANSION_VARIANTS,
  });
  const useExpansion = candidates.length > 1;
  const perQueryPageSize = useExpansion
    ? Math.min(normalizedPageSize, VERTEX_SEARCH_EXPANSION_PAGE_SIZE)
    : normalizedPageSize;

  const settled = await Promise.allSettled(
    candidates.map((candidate) => queryKnowledgeBaseSingle({
      query: candidate,
      pageSize: perQueryPageSize,
    }))
  );

  const fulfilled = settled
    .filter((item) => item.status === "fulfilled")
    .map((item) => item.value);

  if (fulfilled.length === 0) {
    const firstRejected = settled.find((item) => item.status === "rejected");
    throw firstRejected?.reason || new Error("Knowledge base query failed.");
  }

  const merged = mergeKnowledgeResults(fulfilled, normalizedPageSize);
  const scored = applyFallbackRankScores(merged);
  const totalResults = Math.max(
    scored.length,
    ...fulfilled.map((item) => Number(item.totalResults || 0))
  );

  return {
    results: scored,
    totalResults,
  };
}

async function queryKnowledgeBaseSingle({ query, pageSize }) {
  const accessToken = await getVertexAccessToken();
  const endpoint = [
    "https://discoveryengine.googleapis.com/v1",
    `projects/${VERTEX_PROJECT_ID}`,
    `locations/${VERTEX_SEARCH_LOCATION}`,
    `collections/${VERTEX_SEARCH_COLLECTION}`,
    `dataStores/${VERTEX_SEARCH_DATA_STORE}`,
    `servingConfigs/${VERTEX_SEARCH_SERVING_CONFIG}:search`,
  ].join("/");

  const response = await fetch(endpoint, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({
      query,
      pageSize,
      contentSearchSpec: {
        snippetSpec: {
          returnSnippet: true,
        },
      },
      spellCorrectionSpec: {
        mode: "AUTO",
      },
      queryExpansionSpec: {
        condition: "AUTO",
      },
    }),
  });

  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    throw new Error(`Vertex AI Search error ${response.status}: ${detail.slice(0, 300)}`);
  }

  const payload = await response.json();
  const rawResults = Array.isArray(payload?.results) ? payload.results : [];
  return {
    results: rawResults.map(mapKnowledgeSearchResult).filter((item) => item.title || item.snippet),
    totalResults: Number(payload?.totalSize || rawResults.length || 0),
  };
}

function buildKnowledgeQueryCandidates(query, { enabled, maxVariants }) {
  const cleanQuery = String(query || "").trim();
  if (!cleanQuery) return [];

  const normalized = cleanQuery.toLowerCase();
  const candidates = [cleanQuery];
  const addCandidate = (value) => {
    const next = String(value || "").trim();
    if (!next) return;
    if (candidates.some((item) => item.toLowerCase() === next.toLowerCase())) return;
    candidates.push(next);
  };

  if (!enabled || maxVariants <= 0) {
    return candidates;
  }

  if (/\bpest|insect|borer|worm\b/.test(normalized)) {
    addCandidate(`${cleanQuery} integrated pest management`);
    addCandidate(`${cleanQuery} IPM`);
    addCandidate("field scouting pest management");
  }

  if (/\bdisease|blast|blight|fung|mildew|rot\b/.test(normalized)) {
    addCandidate(`${cleanQuery} disease management`);
    addCandidate("fungal disease prevention in crops");
  }

  if (/\bfertilizer|nutrient|npk|nitrogen|phosphorus|potassium\b/.test(normalized)) {
    addCandidate(`${cleanQuery} nutrient management`);
    addCandidate("fertilizer schedule by crop stage");
  }

  if (/\birrigation|water|drip|sprinkler\b/.test(normalized)) {
    addCandidate(`${cleanQuery} water management`);
    addCandidate("irrigation scheduling for smallholder farms");
  }

  if (/\brice|paddy\b/.test(normalized)) {
    addCandidate("rice disease and pest control");
  }

  if (/\bcorn|maize\b/.test(normalized)) {
    addCandidate("corn pest scouting and control");
  }

  if (/\bchili|chilli|pepper\b/.test(normalized)) {
    addCandidate("chili crop nutrition and pest management");
  }

  addCandidate(`${cleanQuery} best practices`);

  return candidates.slice(0, maxVariants + 1);
}

function mergeKnowledgeResults(searchReplies, pageSize) {
  const normalizedPageSize = Math.min(Math.max(Math.trunc(Number(pageSize) || 5), 1), 10);
  const mergedMap = new Map();

  for (const reply of searchReplies) {
    const items = Array.isArray(reply?.results) ? reply.results : [];
    for (const item of items) {
      const key = toKnowledgeResultKey(item);
      const score = Number(item?.score || 0);
      const existing = mergedMap.get(key);

      if (!existing) {
        mergedMap.set(key, {
          title: String(item?.title || "Knowledge Result").trim(),
          snippet: String(item?.snippet || "").trim(),
          uri: item?.uri || null,
          sourceId: String(item?.sourceId || "knowledge-doc"),
          score: Number.isFinite(score) ? score : 0,
          hitCount: 1,
        });
        continue;
      }

      existing.hitCount += 1;
      if (Number.isFinite(score) && score > existing.score) {
        existing.score = score;
      }
      if ((!existing.snippet || existing.snippet.length < 40) && item?.snippet) {
        existing.snippet = String(item.snippet).trim();
      }
      if (!existing.uri && item?.uri) {
        existing.uri = item.uri;
      }
    }
  }

  return Array.from(mergedMap.values())
    .sort((a, b) => {
      if (b.hitCount !== a.hitCount) return b.hitCount - a.hitCount;
      return b.score - a.score;
    })
    .slice(0, normalizedPageSize)
    .map((item) => ({
      title: item.title,
      snippet: item.snippet,
      uri: item.uri,
      sourceId: item.sourceId,
      score: item.score,
    }));
}

function applyFallbackRankScores(results) {
  const items = Array.isArray(results) ? results : [];
  if (items.length === 0) {
    return [];
  }

  const hasRealScores = items.some((item) => Number(item?.score) > 0);
  if (hasRealScores) {
    return items;
  }

  const total = items.length;
  return items.map((item, index) => {
    const rank = index + 1;
    const score = total === 1
      ? 1
      : 1 - ((rank - 1) / (total - 1)) * 0.6;

    return {
      ...item,
      score: Number(score.toFixed(3)),
    };
  });
}

function toKnowledgeResultKey(item) {
  const sourceId = String(item?.sourceId || "").trim().toLowerCase();
  const uri = String(item?.uri || "").trim().toLowerCase();
  const title = String(item?.title || "").trim().toLowerCase();
  const snippet = String(item?.snippet || "").trim().toLowerCase().slice(0, 80);

  if (sourceId) return `source:${sourceId}`;
  if (uri) return `uri:${uri}`;
  if (title && snippet) return `title-snippet:${title}:${snippet}`;
  if (title) return `title:${title}`;
  return `snippet:${snippet}`;
}

async function buildKnowledgeRagAnswer({ query, results }) {
  const contexts = Array.isArray(results)
    ? results
        .filter((item) => item && (item.snippet || item.title))
        .slice(0, 5)
        .map((item, index) => {
          const title = String(item.title || "Untitled").trim();
          const snippet = String(item.snippet || "").trim();
          const source = String(item.sourceId || "knowledge-doc").trim();
          const url = item.uri ? ` | uri: ${item.uri}` : "";
          return `[${index + 1}] title: ${title} | source: ${source}${url}\n${snippet}`;
        })
    : [];
  const hasContext = contexts.length > 0;
  if (!hasContext) {
    return null;
  }

  if (!ai) {
    return null;
  }

  const prompt = [
    "You are a practical agronomy assistant.",
    "Respond in 2-4 concise sentences with actionable guidance.",
    "Use only the retrieved context below and cite source indices like [1], [2].",
    "If the context does not support a claim, do not invent it.",
    `User question: ${query}`,
    "Retrieved context:",
    contexts.join("\n\n"),
  ].join("\n");

  try {
    const result = await ai.models.generateContent({
      model: GEMINI_MODEL,
      contents: prompt,
    });
    const text = String(result.text || "").trim();
    if (!text) {
      return null;
    }

    const normalizedText = text.replace(/\s+/g, " ").trim().slice(0, 700);
    return {
      title: "RAG answer",
      snippet: normalizedText,
      uri: null,
      sourceId: "rag-grounded",
      score: 1,
    };
  } catch (error) {
    return null;
  }
}

function mapKnowledgeSearchResult(result) {
  const document = result?.document && typeof result.document === "object" ? result.document : {};
  const derived = document?.derivedStructData && typeof document.derivedStructData === "object"
    ? document.derivedStructData
    : {};
  const structured = document?.structData && typeof document.structData === "object"
    ? document.structData
    : {};

  const snippets = Array.isArray(derived?.snippets) ? derived.snippets : [];
  const firstSnippet = snippets.find((snippet) => typeof snippet?.snippet === "string")?.snippet || "";
  const snippetFallback = [
    structured?.description,
    structured?.content,
    structured?.summary,
  ].find((value) => typeof value === "string" && value.trim().length > 0) || "";

  const title = [
    document?.title,
    derived?.title,
    structured?.title,
    structured?.name,
  ].find((value) => typeof value === "string" && value.trim().length > 0) || "Knowledge Result";

  const uri = [
    document?.uri,
    derived?.link,
    structured?.uri,
    structured?.link,
  ].find((value) => typeof value === "string" && value.trim().length > 0) || null;

  const score = Number(result?.modelScores?.relevanceScore || result?.chunk?.relevanceScore || 0);
  const normalizedPrimarySnippet = normalizeSearchSnippet(firstSnippet);
  const normalizedFallbackSnippet = normalizeSearchSnippet(snippetFallback);
  return {
    title: String(title).trim(),
    snippet: normalizedPrimarySnippet || normalizedFallbackSnippet,
    uri,
    sourceId: String(document?.id || document?.name || "knowledge-doc"),
    score: Number.isFinite(score) ? score : 0,
  };
}

function normalizeSearchSnippet(input) {
  const text = String(input || "")
    .replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, " ")
    .trim();
  if (text.toLowerCase() === "no snippet is available for this page.") {
    return "";
  }
  return text.slice(0, 320);
}

async function planScoutingToolSequence({
  cropName,
  expectedStage,
  dayNumber,
  recommendation,
  rationale,
  location,
  hasCoordinates,
  hasPolygon,
}) {
  const fallback = {
    steps: ["photo_assessment", "knowledge_base", "weather_now", "final_recommendation"],
    knowledgeQuery: `${cropName} ${expectedStage} ${recommendation}`.trim(),
    reasoning: "Default tool sequence applied.",
  };

  if (!ai) return fallback;

  const prompt = [
    "You are an agent planner for crop scouting.",
    "Choose the best tool sequence from: photo_assessment, knowledge_base, weather_now, final_recommendation.",
    "Return strict JSON object with keys: steps (array), knowledgeQuery (string), reasoning (string).",
    "Keep steps minimal but complete.",
    JSON.stringify({
      cropName,
      expectedStage,
      dayNumber,
      recommendation,
      rationale,
      location,
      hasCoordinates,
      hasPolygon,
    }),
  ].join("\n");

  try {
    const result = await ai.models.generateContent({ model: GEMINI_MODEL, contents: prompt });
    const parsed = safeParseJsonObject(result.text || "");
    if (!parsed) return fallback;

    const allowed = new Set(["photo_assessment", "knowledge_base", "weather_now", "final_recommendation"]);
    const steps = Array.isArray(parsed.steps)
      ? parsed.steps.map((s) => String(s || "").trim()).filter((s) => allowed.has(s))
      : fallback.steps;

    const normalizedSteps = steps.length ? steps : fallback.steps;
    if (!normalizedSteps.includes("photo_assessment")) normalizedSteps.unshift("photo_assessment");
    if (!normalizedSteps.includes("final_recommendation")) normalizedSteps.push("final_recommendation");

    return {
      steps: Array.from(new Set(normalizedSteps)),
      knowledgeQuery: String(parsed.knowledgeQuery || fallback.knowledgeQuery).trim() || fallback.knowledgeQuery,
      reasoning: String(parsed.reasoning || "").trim() || fallback.reasoning,
    };
  } catch (_error) {
    return fallback;
  }
}

async function resolveWeatherForAgent({ location, latitude, longitude, centroid = null }) {
  const hasCoordinates = Number.isFinite(latitude) && Number.isFinite(longitude);
  if (hasCoordinates) {
    const weather = await fetchCurrentWeather(latitude, longitude);
    return {
      location: location || `Lat ${round(latitude, 5)}, Lng ${round(longitude, 5)}`,
      resolvedAddress: location || `Lat ${round(latitude, 5)}, Lng ${round(longitude, 5)}`,
      latitude,
      longitude,
      ...weather,
      provider: "open-meteo-coordinates",
    };
  }

  const cleanLocation = String(location || "").trim();
  if (cleanLocation && GOOGLE_MAPS_API_KEY) {
    const geocoded = await geocodeWithGoogleMaps(cleanLocation);
    const weather = await fetchCurrentWeather(geocoded.lat, geocoded.lng);
    return {
      location: cleanLocation,
      resolvedAddress: geocoded.resolvedAddress,
      latitude: geocoded.lat,
      longitude: geocoded.lng,
      ...weather,
      provider: "google-geocode+open-meteo",
    };
  }

  if (centroid && Number.isFinite(centroid.lat) && Number.isFinite(centroid.lng)) {
    const weather = await fetchCurrentWeather(Number(centroid.lat), Number(centroid.lng));
    return {
      location: `Centroid ${round(Number(centroid.lat), 5)}, ${round(Number(centroid.lng), 5)}`,
      resolvedAddress: `Centroid ${round(Number(centroid.lat), 5)}, ${round(Number(centroid.lng), 5)}`,
      latitude: Number(centroid.lat),
      longitude: Number(centroid.lng),
      ...weather,
      provider: "centroid+open-meteo",
    };
  }

  return null;
}

async function synthesizeScoutingAction({ cropName, expectedStage, dayNumber, assessment, weather, knowledgeResults }) {
  const fallback = {
    issueSummary: `${cropName} appears ${assessment.isSimilar ? "near expected stage" : "off expected stage"}.`,
    riskLevel: assessment.isSimilar ? "low" : "medium",
    primaryAction: assessment.recommendation,
    followUpCheck: "Recheck field condition in 24 hours and capture another photo.",
    confidence: assessment.isSimilar ? 0.78 : 0.64,
    provider: "heuristic-fallback",
  };

  if (!ai) return fallback;

  const prompt = [
    "You are a farm scouting action agent.",
    "Produce strict JSON object with keys: issueSummary, riskLevel, primaryAction, followUpCheck, confidence.",
    "riskLevel must be one of: low, medium, high.",
    "confidence must be number between 0 and 1.",
    JSON.stringify({ cropName, expectedStage, dayNumber, assessment, weather, knowledgeResults: knowledgeResults.slice(0, 3) }),
  ].join("\n");

  try {
    const result = await ai.models.generateContent({ model: GEMINI_MODEL, contents: prompt });
    const parsed = safeParseJsonObject(result.text || "");
    if (!parsed) return fallback;

    const riskLevel = ["low", "medium", "high"].includes(String(parsed.riskLevel || "").toLowerCase())
      ? String(parsed.riskLevel).toLowerCase()
      : fallback.riskLevel;

    return {
      issueSummary: String(parsed.issueSummary || fallback.issueSummary).trim(),
      riskLevel,
      primaryAction: String(parsed.primaryAction || fallback.primaryAction).trim(),
      followUpCheck: String(parsed.followUpCheck || fallback.followUpCheck).trim(),
      confidence: clamp(Number(parsed.confidence ?? fallback.confidence), 0, 1),
      provider: "gemini-live",
    };
  } catch (_error) {
    return fallback;
  }
}

function buildInsightsKnowledgeQuery({ recommendations, summary, targetCrops }) {
  const firstCrop = recommendations?.[0]?.cropName || targetCrops?.[0] || "crop";
  const moisture = Number(summary?.soilMoistureMean || 0);
  const rainfall = Number(summary?.rainfallMm7d || 0);
  const ndvi = Number(summary?.ndviMean || 0);
  return `${firstCrop} management NDVI ${round(ndvi, 2)} soil moisture ${round(moisture, 2)} rainfall ${round(rainfall, 1)}mm`;
}

async function synthesizeFieldInsightsActionBrief({ summary, recommendations, weather, knowledgeResults, targetCrops }) {
  const topCrop = recommendations?.[0]?.cropName || targetCrops?.[0] || "crop";
  const fallback = {
    overview: `${topCrop} is currently the most suitable option based on field metrics and current conditions.`,
    topAction: "Proceed with staged monitoring and verify moisture before next irrigation.",
    watchouts: ["Monitor rainfall changes", "Watch early pest signs", "Re-assess in 48 hours"],
    provider: "heuristic-fallback",
  };

  if (!ai) return fallback;

  const prompt = [
    "You are a field insights orchestrator agent.",
    "Return strict JSON object with keys: overview, topAction, watchouts.",
    "watchouts must be an array of 2-4 short strings.",
    JSON.stringify({ summary, recommendations, weather, knowledgeResults: knowledgeResults.slice(0, 3), targetCrops }),
  ].join("\n");

  try {
    const result = await ai.models.generateContent({ model: GEMINI_MODEL, contents: prompt });
    const parsed = safeParseJsonObject(result.text || "");
    if (!parsed) return fallback;

    const watchouts = Array.isArray(parsed.watchouts)
      ? parsed.watchouts.map((item) => String(item || "").trim()).filter(Boolean).slice(0, 4)
      : fallback.watchouts;

    return {
      overview: String(parsed.overview || fallback.overview).trim(),
      topAction: String(parsed.topAction || fallback.topAction).trim(),
      watchouts: watchouts.length ? watchouts : fallback.watchouts,
      provider: "gemini-live",
    };
  } catch (_error) {
    return fallback;
  }
}

async function readRecentHistoryEvents(userId, limit = 20) {
  const db = getFirestoreDb();
  if (!db) return [];

  try {
    const size = Math.min(Math.max(Math.trunc(Number(limit) || 20), 1), 100);
    const snap = await db
      .collection(FIREBASE_HISTORY_COLLECTION)
      .where("userId", "==", userId)
      .orderBy("createdAt", "desc")
      .limit(size)
      .get();

    return snap.docs.map((doc) => {
      const data = doc.data() || {};
      return {
        id: doc.id,
        category: String(data.category || "").trim(),
        title: String(data.title || "").trim(),
        summary: String(data.summary || "").trim(),
        recommendation: String(data.recommendation || "").trim(),
      };
    });
  } catch (_error) {
    return [];
  }
}

async function synthesizeActionTrackerFollowUp({
  dayNumber,
  cropName,
  issueType,
  actionTaken,
  note,
  recentEvents,
}) {
  const fallback = {
    nextBestAction: actionTaken
      ? `Validate outcome of \"${actionTaken}\" within 24 hours and compare with Day ${dayNumber + 1} evidence.`
      : "Log the action taken, then capture a new photo for comparison within 24 hours.",
    followUpQuestion: "Did symptoms improve after your last action?",
    confidence: 0.62,
    riskLevel: "medium",
    provider: "heuristic-fallback",
  };

  if (!ai) return fallback;

  const compactEvents = Array.isArray(recentEvents)
    ? recentEvents.slice(0, 12).map((item) => ({
        category: item.category,
        title: item.title,
        summary: item.summary,
        recommendation: item.recommendation,
      }))
    : [];

  const prompt = [
    "You are an action tracker agent for farm follow-up.",
    "Return strict JSON object with keys: nextBestAction, followUpQuestion, confidence, riskLevel.",
    "riskLevel must be one of: low, medium, high.",
    JSON.stringify({ dayNumber, cropName, issueType, actionTaken, note, recentEvents: compactEvents }),
  ].join("\n");

  try {
    const result = await ai.models.generateContent({ model: GEMINI_MODEL, contents: prompt });
    const parsed = safeParseJsonObject(result.text || "");
    if (!parsed) return fallback;

    const riskLevel = ["low", "medium", "high"].includes(String(parsed.riskLevel || "").toLowerCase())
      ? String(parsed.riskLevel).toLowerCase()
      : fallback.riskLevel;

    return {
      nextBestAction: String(parsed.nextBestAction || fallback.nextBestAction).trim(),
      followUpQuestion: String(parsed.followUpQuestion || fallback.followUpQuestion).trim(),
      confidence: clamp(Number(parsed.confidence ?? fallback.confidence), 0, 1),
      riskLevel,
      provider: "gemini-live",
    };
  } catch (_error) {
    return fallback;
  }
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
      ? "Maintain current irrigation and monitor."
      : "Adjust watering and inspect leaf health.",
    rationale: isSimilar
      ? "Plant looks on track."
      : "Canopy differs from expected.",
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
