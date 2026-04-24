import { genkit, z } from "genkit";
import { googleAI } from "@genkit-ai/google-genai";
import {
  assessTimelinePhoto,
  queryKnowledgeBase,
  resolveWeatherForAgent,
  getEarthSummary,
  getCropRecommendations,
  readRecentHistoryEvents,
  synthesizeFieldInsightsActionBrief,
  synthesizeActionTrackerFollowUp,
  persistHistoryEvent,
} from "../../server.js";

// Initialize Genkit with Google AI plugin
const ai = genkit({
  plugins: [googleAI()],
});

// ---------------------------------------------------------------------------
// Shared input/output Zod schemas
// ---------------------------------------------------------------------------

export const LatLngPointSchema = z.object({
  x: z.number().describe("Normalized X coordinate (0-1)"),
  y: z.number().describe("Normalized Y coordinate (0-1)"),
});

export const PhotoAssessmentSchema = z.object({
  dayNumber: z.number(),
  expectedStage: z.string(),
  cropName: z.string(),
  similarityScore: z.number().describe("0-100"),
  isSimilar: z.boolean(),
  observedStage: z.string(),
  recommendation: z.string(),
  rationale: z.string(),
  provider: z.string(),
});

export const WeatherSchema = z.object({
  location: z.string(),
  resolvedAddress: z.string(),
  latitude: z.number(),
  longitude: z.number(),
  condition: z.string(),
  temperatureC: z.number(),
  rainfallMm: z.number().optional(),
  windKmh: z.number().optional(),
  provider: z.string(),
});

export const KnowledgeResultSchema = z.object({
  title: z.string(),
  snippet: z.string(),
  uri: z.string().nullable(),
  sourceId: z.string(),
  score: z.number(),
});

export const CropRecommendationSchema = z.object({
  cropName: z.string(),
  suitability: z.string(),
  rationale: z.string(),
});

export const HistoryEventSchema = z.object({
  id: z.string(),
  category: z.string(),
  title: z.string(),
  summary: z.string(),
  recommendation: z.string(),
});

export const EarthSummarySchema = z.object({
  centroidLat: z.number(),
  centroidLng: z.number(),
  ndviMean: z.number(),
  soilMoistureMean: z.number(),
  rainfallMm7d: z.number(),
  averageTempC: z.number(),
  notes: z.string(),
  source: z.string(),
  sourceVerified: z.boolean(),
});

const ScoutingPlanSchema = z.object({
  steps: z.array(z.string()),
  knowledgeQuery: z.string(),
  reasoning: z.string(),
});

const ScoutingActionSchema = z.object({
  issueSummary: z.string(),
  riskLevel: z.enum(["low", "medium", "high"]),
  primaryAction: z.string(),
  followUpCheck: z.string(),
  confidence: z.number(),
  provider: z.string(),
});

const SCOUTING_ALLOWED_STEPS = new Set([
  "photo_assessment",
  "knowledge_base",
  "weather_now",
  "final_recommendation",
]);

// ---------------------------------------------------------------------------
// Tool: assessTimelinePhoto
// ---------------------------------------------------------------------------

export const assessTimelinePhotoTool = ai.defineTool(
  {
    name: "assessTimelinePhoto",
    description:
      "Assess a crop timeline photo using AI vision. Returns similarity score, observed stage, and a recommendation.",
    inputSchema: z.object({
      dayNumber: z.number(),
      expectedStage: z.string(),
      cropName: z.string(),
      photoMimeType: z.string().default("image/jpeg"),
      photoBase64: z.string(),
      userMarkedSimilar: z.boolean().optional(),
    }),
    outputSchema: PhotoAssessmentSchema,
  },
  async ({
    dayNumber,
    expectedStage,
    cropName,
    photoMimeType,
    photoBase64,
    userMarkedSimilar,
  }) => {
    return assessTimelinePhoto({
      dayNumber,
      expectedStage,
      cropName,
      photoMimeType,
      photoBase64,
      userMarkedSimilar,
    });
  }
);

// ---------------------------------------------------------------------------
// Tool: queryKnowledgeBase
// ---------------------------------------------------------------------------

export const queryKnowledgeBaseTool = ai.defineTool(
  {
    name: "queryKnowledgeBase",
    description:
      "Query the agronomy knowledge base (Vertex AI Search) for relevant documents and best practices.",
    inputSchema: z.object({
      query: z.string(),
      pageSize: z.number().default(4),
      expandQuery: z.boolean().default(true),
    }),
    outputSchema: z.object({
      results: z.array(KnowledgeResultSchema),
      totalResults: z.number(),
    }),
  },
  async ({ query, pageSize, expandQuery }) => {
    return queryKnowledgeBase({ query, pageSize, expandQuery });
  }
);

// ---------------------------------------------------------------------------
// Tool: resolveWeather
// ---------------------------------------------------------------------------

export const resolveWeatherTool = ai.defineTool(
  {
    name: "resolveWeather",
    description:
      "Get the current weather for a location using coordinates or location name. Returns temperature, condition, rainfall, and wind.",
    inputSchema: z.object({
      location: z.string().optional(),
      latitude: z.number().optional(),
      longitude: z.number().optional(),
      polygon: z.array(LatLngPointSchema).optional(),
    }),
    outputSchema: WeatherSchema.nullable(),
  },
  async ({ location, latitude, longitude, polygon }) => {
    return resolveWeatherForAgent({
      location: location ?? "",
      latitude,
      longitude,
      centroid: null,
    });
  }
);

// ---------------------------------------------------------------------------
// Tool: getEarthSummary
// ---------------------------------------------------------------------------

export const getEarthSummaryTool = ai.defineTool(
  {
    name: "getEarthSummary",
    description:
      "Get satellite-based environmental summary for a farm polygon using Google Earth Engine. Returns NDVI, soil moisture, rainfall, and temperature.",
    inputSchema: z.object({
      polygon: z.array(LatLngPointSchema),
      centroid: z
        .object({ lat: z.number(), lng: z.number() })
        .optional(),
    }),
    outputSchema: EarthSummarySchema,
  },
  async ({ polygon, centroid }) => {
    return getEarthSummary({ polygon, centroid });
  }
);

// ---------------------------------------------------------------------------
// Tool: getCropRecommendations
// ---------------------------------------------------------------------------

export const getCropRecommendationsTool = ai.defineTool(
  {
    name: "getCropRecommendations",
    description:
      "Get AI crop suitability recommendations based on Earth Engine environmental summary.",
    inputSchema: z.object({
      summary: EarthSummarySchema,
      targetCrops: z.array(z.string()).default([]),
      totalFarmAreaHectares: z.number().optional(),
      lotAreaHectares: z.number().optional(),
    }),
    outputSchema: z.array(CropRecommendationSchema),
  },
  async ({ summary, targetCrops, totalFarmAreaHectares, lotAreaHectares }) => {
    return getCropRecommendations(summary, targetCrops, {
      totalFarmAreaHectares,
      lotAreaHectares,
    });
  }
);

// ---------------------------------------------------------------------------
// Tool: readRecentHistoryEvents
// ---------------------------------------------------------------------------

export const readRecentHistoryEventsTool = ai.defineTool(
  {
    name: "readRecentHistoryEvents",
    description:
      "Read recent history events from Firestore for a given user. Used by the action tracker to build context.",
    inputSchema: z.object({
      userId: z.string(),
      limit: z.number().default(25),
    }),
    outputSchema: z.array(HistoryEventSchema),
  },
  async ({ userId, limit }) => {
    return readRecentHistoryEvents(userId, limit);
  }
);

// ---------------------------------------------------------------------------
// Tool: planScoutingSequence
// ---------------------------------------------------------------------------

export const planScoutingSequenceTool = ai.defineTool(
  {
    name: "planScoutingSequence",
    description:
      "Decide which tools to call for a scouting task. Returns the ordered list of steps and a knowledge query string.",
    inputSchema: z.object({
      cropName: z.string(),
      expectedStage: z.string(),
      dayNumber: z.number(),
      recommendation: z.string(),
      rationale: z.string(),
      location: z.string().optional(),
      hasCoordinates: z.boolean().optional(),
      hasPolygon: z.boolean().optional(),
    }),
    outputSchema: ScoutingPlanSchema,
  },
  async ({
    cropName,
    expectedStage,
    dayNumber,
    recommendation,
    rationale,
    location,
    hasCoordinates,
    hasPolygon,
  }) => {
    const fallback = {
      steps: ["photo_assessment", "knowledge_base", "weather_now", "final_recommendation"],
      knowledgeQuery: `${cropName} ${expectedStage} ${recommendation}`.trim(),
      reasoning: "Default tool sequence applied.",
    };

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
      const response = await ai.generate({
        model: "googleai/gemini-2.0-flash",
        prompt,
        output: {
          schema: ScoutingPlanSchema,
        },
      });

      const parsed = response?.output || safeParseJsonObject(response?.text || "");
      if (!parsed) {
        return fallback;
      }

      const steps = Array.isArray(parsed.steps)
        ? parsed.steps
            .map((s) => String(s || "").trim())
            .filter((s) => SCOUTING_ALLOWED_STEPS.has(s))
        : fallback.steps;

      const normalizedSteps = steps.length ? [...steps] : [...fallback.steps];
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
);

function safeParseJsonObject(input) {
  const text = String(input || "").trim();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch (_error) {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Tool: synthesizeScoutingAction
// ---------------------------------------------------------------------------

export const synthesizeScoutingActionTool = ai.defineTool(
  {
    name: "synthesizeScoutingAction",
    description:
      "Synthesize a final scouting action recommendation from photo assessment, weather, and knowledge results.",
    inputSchema: z.object({
      cropName: z.string(),
      expectedStage: z.string(),
      dayNumber: z.number(),
      assessment: PhotoAssessmentSchema,
      weather: WeatherSchema.nullable().optional(),
      knowledgeResults: z.array(KnowledgeResultSchema).default([]),
    }),
    outputSchema: ScoutingActionSchema,
  },
  async ({ cropName, expectedStage, dayNumber, assessment, weather, knowledgeResults }) => {
    const fallback = {
      issueSummary: `${cropName} appears ${assessment.isSimilar ? "near expected stage" : "off expected stage"}.`,
      riskLevel: assessment.isSimilar ? "low" : "medium",
      primaryAction: assessment.recommendation,
      followUpCheck: "Recheck field condition in 24 hours and capture another photo.",
      confidence: assessment.isSimilar ? 0.78 : 0.64,
      provider: "heuristic-fallback",
    };

    const prompt = [
      "You are a farm scouting action agent.",
      "Produce strict JSON object with keys: issueSummary, riskLevel, primaryAction, followUpCheck, confidence, provider.",
      "riskLevel must be one of: low, medium, high.",
      "confidence must be number between 0 and 1.",
      "provider should be a short model identifier string.",
      JSON.stringify({
        cropName,
        expectedStage,
        dayNumber,
        assessment,
        weather,
        knowledgeResults: knowledgeResults.slice(0, 3),
      }),
    ].join("\n");

    try {
      const response = await ai.generate({
        model: "googleai/gemini-2.0-flash",
        prompt,
        output: {
          schema: ScoutingActionSchema,
        },
      });

      const parsed = response?.output || safeParseJsonObject(response?.text || "");
      if (!parsed) {
        return fallback;
      }

      const risk = String(parsed.riskLevel || "").toLowerCase();
      const riskLevel = ["low", "medium", "high"].includes(risk) ? risk : fallback.riskLevel;

      return {
        issueSummary: String(parsed.issueSummary || fallback.issueSummary).trim() || fallback.issueSummary,
        riskLevel,
        primaryAction: String(parsed.primaryAction || fallback.primaryAction).trim() || fallback.primaryAction,
        followUpCheck: String(parsed.followUpCheck || fallback.followUpCheck).trim() || fallback.followUpCheck,
        confidence: clampNumber(Number(parsed.confidence ?? fallback.confidence), 0, 1),
        provider: String(parsed.provider || "genkit-googleai").trim() || "genkit-googleai",
      };
    } catch (_error) {
      return fallback;
    }
  }
);

function clampNumber(value, min, max) {
  if (!Number.isFinite(value)) return min;
  return Math.min(max, Math.max(min, value));
}

// ---------------------------------------------------------------------------
// Tool: synthesizeFieldInsightsBrief
// ---------------------------------------------------------------------------

export const synthesizeFieldInsightsBriefTool = ai.defineTool(
  {
    name: "synthesizeFieldInsightsBrief",
    description:
      "Synthesize an action brief from field insights orchestrator results — Earth summary, crop recommendations, weather, and knowledge.",
    inputSchema: z.object({
      summary: EarthSummarySchema,
      recommendations: z.array(CropRecommendationSchema),
      weather: WeatherSchema.nullable().optional(),
      knowledgeResults: z.array(KnowledgeResultSchema).default([]),
      targetCrops: z.array(z.string()).default([]),
    }),
    outputSchema: z.object({
      overview: z.string(),
      topAction: z.string(),
      watchouts: z.array(z.string()),
      provider: z.string(),
    }),
  },
  async ({ summary, recommendations, weather, knowledgeResults, targetCrops }) => {
    return synthesizeFieldInsightsActionBrief({
      summary,
      recommendations,
      weather,
      knowledgeResults,
      targetCrops,
    });
  }
);

// ---------------------------------------------------------------------------
// Tool: synthesizeActionTrackerFollowUp
// ---------------------------------------------------------------------------

export const synthesizeActionTrackerFollowUpTool = ai.defineTool(
  {
    name: "synthesizeActionTrackerFollowUp",
    description:
      "Generate a follow-up recommendation based on recent history events and the current action taken.",
    inputSchema: z.object({
      dayNumber: z.number(),
      cropName: z.string(),
      issueType: z.string(),
      actionTaken: z.string().optional(),
      note: z.string().optional(),
      recentEvents: z.array(HistoryEventSchema).default([]),
    }),
    outputSchema: z.object({
      nextBestAction: z.string(),
      followUpQuestion: z.string(),
      confidence: z.number(),
      riskLevel: z.enum(["low", "medium", "high"]),
      provider: z.string(),
    }),
  },
  async ({ dayNumber, cropName, issueType, actionTaken, note, recentEvents }) => {
    return synthesizeActionTrackerFollowUp({
      dayNumber,
      cropName,
      issueType,
      actionTaken,
      note,
      recentEvents,
    });
  }
);

// ---------------------------------------------------------------------------
// Tool: persistHistoryEvent
// ---------------------------------------------------------------------------

export const persistHistoryEventTool = ai.defineTool(
  {
    name: "persistHistoryEvent",
    description: "Persist a history event to Firestore for audit and follow-up tracking.",
    inputSchema: z.object({
      userId: z.string().nullable().optional(),
      category: z.string(),
      title: z.string(),
      summary: z.string(),
      recommendation: z.string(),
      payload: z.record(z.unknown()).default({}),
    }),
    outputSchema: z.object({ ok: z.boolean() }),
  },
  async ({ userId, category, title, summary, recommendation, payload }) => {
    await persistHistoryEvent({ userId, category, title, summary, recommendation, payload });
    return { ok: true };
  }
);
