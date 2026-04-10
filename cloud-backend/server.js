import cors from "cors";
import dotenv from "dotenv";
import express from "express";
import { GoogleGenAI } from "@google/genai";
import { GoogleAuth } from "google-auth-library";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const ee = require("@google/earthengine");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Root .env is the single source of truth for all app/backend settings.
dotenv.config({ path: path.resolve(__dirname, "../.env") });

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

app.get("/health", async (_req, res) => {
  const eeStatus = await resolveEarthEngineStatus();
  res.json({
    ok: true,
    service: "field-insights",
    geminiEnabled: Boolean(ai),
    earthEngineMode: EARTH_ENGINE_MODE,
    earthEngineLinked: eeStatus.linked,
    earthEngineReason: eeStatus.reason,
  });
});

app.post("/api/field-insights", async (req, res) => {
  try {
    const polygon = normalizePolygon(req.body?.polygon);
    if (polygon.length < 3) {
      return res.status(400).json({ error: "Polygon must have at least 3 points." });
    }
    const targetCrops = normalizeTargetCrops(req.body?.targetCrops);

    const centroid = req.body?.centroid
      ? toLatLngPoint(req.body.centroid)
      : computeCentroid(polygon);

    const earthSummary = await getEarthSummary({ polygon, centroid });
    const recommendations = await getCropRecommendations(earthSummary, targetCrops);

    res.json({
      summary: earthSummary,
      recommendations,
      provider: ai ? "gemini-live" : "gemini-mock",
    });
  } catch (error) {
    res.status(500).json({
      error: "Unable to produce field insights.",
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
    const credentials = EARTH_ENGINE_SERVICE_ACCOUNT_JSON.trim()
      ? JSON.parse(EARTH_ENGINE_SERVICE_ACCOUNT_JSON)
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
      if (!EARTH_ENGINE_SERVICE_ACCOUNT_JSON.trim()) {
        return reject(new Error("missing_service_account_json"));
      }

      const credentials = JSON.parse(EARTH_ENGINE_SERVICE_ACCOUNT_JSON);
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

async function getCropRecommendations(summary, targetCrops = []) {
  if (!ai) {
    return fallbackRecommendations(summary, targetCrops);
  }

  const cropHint = targetCrops.length
    ? `Prioritize and explicitly evaluate these target crops first: ${targetCrops.join(", ")}.`
    : "No explicit target crops were provided; suggest generally suitable crops.";

  const prompt = [
    "You are an agronomy assistant for tropical smallholder farms.",
    cropHint,
    "Given this environment summary, recommend 3 crops.",
    "Return strict JSON array only with keys: cropName, suitability, rationale.",
    "Suitability must be one of: High, Moderate, Low.",
    "Environment summary:",
    JSON.stringify(summary),
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

    return parsed.slice(0, 3).map((item) => ({
      cropName: String(item.cropName || "Unknown"),
      suitability: normalizeSuitability(item.suitability),
      rationale: String(item.rationale || "No rationale returned."),
    }));
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
    .slice(0, 3)
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
