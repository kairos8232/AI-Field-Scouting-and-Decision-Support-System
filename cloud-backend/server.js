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

const ai = process.env.GEMINI_API_KEY
  ? new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY })
  : null;

const EARTH_ENGINE_SCOPE = "https://www.googleapis.com/auth/earthengine.readonly";

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

async function persistInsightRecord({ requestBody, normalized, response }) {
  const db = getFirestoreDb();
  if (!db) return;

  try {
    await db.collection(FIREBASE_COLLECTION).add({
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
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
