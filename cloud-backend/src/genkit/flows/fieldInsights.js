import { genkit, z } from "genkit";
import { googleAI } from "@genkit-ai/google-genai";
import {
  getEarthSummaryTool,
  getCropRecommendationsTool,
  queryKnowledgeBaseTool,
  resolveWeatherTool,
  synthesizeFieldInsightsBriefTool,
  persistHistoryEventTool,
  LatLngPointSchema,
} from "../tools.js";

const ai = genkit({
  plugins: [googleAI()],
});

// ---------------------------------------------------------------------------
// Input/Output schemas
// ---------------------------------------------------------------------------

export const FieldInsightsOrchestratorInputSchema = z.object({
  userId: z.string().nullable().optional(),
  polygon: z.array(LatLngPointSchema).min(3),
  centroid: z
    .object({ lat: z.number(), lng: z.number() })
    .optional(),
  targetCrops: z.array(z.string()).default([]),
  totalFarmAreaHectares: z.number().optional(),
  lotAreaHectares: z.number().optional(),
  location: z.string().optional(),
});

export const FieldInsightsOrchestratorOutputSchema = z.object({
  summary: z.object({
    centroidLat: z.number(),
    centroidLng: z.number(),
    ndviMean: z.number(),
    soilMoistureMean: z.number(),
    rainfallMm7d: z.number(),
    averageTempC: z.number(),
    notes: z.string(),
    source: z.string(),
    sourceVerified: z.boolean(),
  }),
  recommendations: z.array(
    z.object({
      cropName: z.string(),
      suitability: z.string(),
      rationale: z.string(),
    })
  ),
  weather: z
    .object({
      location: z.string(),
      resolvedAddress: z.string(),
      latitude: z.number(),
      longitude: z.number(),
      condition: z.string(),
      temperatureC: z.number(),
      rainfallMm: z.number().optional(),
      windKmh: z.number().optional(),
      provider: z.string(),
    })
    .nullable()
    .optional(),
  knowledge: z
    .object({
      query: z.string(),
      results: z.array(
        z.object({
          title: z.string(),
          snippet: z.string(),
          uri: z.string().nullable(),
          sourceId: z.string(),
          score: z.number(),
        })
      ),
      totalResults: z.number(),
    })
    .nullable()
    .optional(),
  actionBrief: z.object({
    overview: z.string(),
    topAction: z.string(),
    watchouts: z.array(z.string()),
    provider: z.string(),
  }),
  toolTrace: z.array(z.string()),
  provider: z.string(),
});

// ---------------------------------------------------------------------------
// Field Insights Orchestrator Flow
// ---------------------------------------------------------------------------

export const fieldInsightsOrchestratorFlow = ai.defineFlow(
  {
    name: "fieldInsightsOrchestrator",
    inputSchema: FieldInsightsOrchestratorInputSchema,
    outputSchema: FieldInsightsOrchestratorOutputSchema,
  },
  async (input) => {
    // Step 1: Get Earth Engine summary
    const summary = await getEarthSummaryTool({
      polygon: input.polygon,
      centroid: input.centroid,
    });

    // Step 2: Get crop recommendations from Gemini
    const recommendations = await getCropRecommendationsTool({
      summary,
      targetCrops: input.targetCrops,
      totalFarmAreaHectares: input.totalFarmAreaHectares,
      lotAreaHectares: input.lotAreaHectares,
    });

    // Step 3: Build knowledge query from recommendations
    const firstCrop = recommendations[0]?.cropName || input.targetCrops[0] || "crop";
    const moisture = summary.soilMoistureMean;
    const rainfall = summary.rainfallMm7d;
    const ndvi = summary.ndviMean;
    const kbQuery = `${firstCrop} management NDVI ${ndvi.toFixed(2)} soil moisture ${moisture.toFixed(2)} rainfall ${rainfall.toFixed(1)}mm`;

    // Step 4: Query knowledge base
    const kbReply = await queryKnowledgeBaseTool({
      query: kbQuery,
      pageSize: 4,
      expandQuery: true,
    });

    // Step 5: Get weather
    const weather = await resolveWeatherTool({
      location: input.location,
      latitude: input.centroid?.lat,
      longitude: input.centroid?.lng,
      polygon: input.polygon,
    });

    // Step 6: Synthesize action brief
    const actionBrief = await synthesizeFieldInsightsBriefTool({
      summary,
      recommendations,
      weather: weather ?? undefined,
      knowledgeResults: kbReply.results,
      targetCrops: input.targetCrops,
    });

    // Step 7: Persist history event
    if (input.userId) {
      await persistHistoryEventTool({
        userId: input.userId,
        category: "action_log",
        title: "Field insights orchestrator run",
        summary: actionBrief.overview,
        recommendation: actionBrief.topAction,
        payload: {
          centroid: input.centroid,
          targetCrops: input.targetCrops,
          kbQuery,
          weatherCondition: weather?.condition ?? null,
        },
      });
    }

    return {
      summary,
      recommendations,
      weather: weather ?? null,
      knowledge: {
        query: kbQuery,
        results: kbReply.results,
        totalResults: kbReply.totalResults,
      },
      actionBrief,
      toolTrace: [
        "earth_engine",
        "gemini_recommendation",
        "vertex_search",
        "weather_now",
        "final_brief",
      ],
      provider: "agent-field-insights-orchestrator-v1-genkit",
    };
  }
);
