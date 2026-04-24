import { genkit, z } from "genkit";
import { googleAI } from "@genkit-ai/google-genai";
import {
  assessTimelinePhotoTool,
  queryKnowledgeBaseTool,
  resolveWeatherTool,
  synthesizeScoutingActionTool,
  planScoutingSequenceTool,
  persistHistoryEventTool,
  LatLngPointSchema,
} from "../tools.js";

const ai = genkit({
  plugins: [googleAI()],
});

// ---------------------------------------------------------------------------
// Input/Output schemas
// ---------------------------------------------------------------------------

export const ScoutingLoopInputSchema = z.object({
  userId: z.string().nullable().optional(),
  dayNumber: z.number().int().positive(),
  expectedStage: z.string(),
  cropName: z.string(),
  photoMimeType: z.string().default("image/jpeg"),
  photoBase64: z.string(),
  location: z.string().optional(),
  latitude: z.number().optional(),
  longitude: z.number().optional(),
  polygon: z.array(LatLngPointSchema).optional(),
  userMarkedSimilar: z.boolean().optional(),
});

export const ScoutingLoopOutputSchema = z.object({
  dayNumber: z.number(),
  cropName: z.string(),
  expectedStage: z.string(),
  assessment: z.object({
    similarityScore: z.number(),
    isSimilar: z.boolean(),
    observedStage: z.string(),
    recommendation: z.string(),
    rationale: z.string(),
    provider: z.string(),
  }),
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
      query: z.string().nullable(),
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
  recommendation: z.object({
    issueSummary: z.string(),
    riskLevel: z.enum(["low", "medium", "high"]),
    primaryAction: z.string(),
    followUpCheck: z.string(),
    confidence: z.number(),
    provider: z.string(),
  }),
  toolPlan: z.object({
    steps: z.array(z.string()),
    knowledgeQuery: z.string(),
    reasoning: z.string(),
  }),
  toolTrace: z.array(
    z.object({
      step: z.string(),
      provider: z.string().optional(),
      used: z.boolean().optional(),
      query: z.string().nullable().optional(),
      totalResults: z.number().optional(),
      condition: z.string().nullable().optional(),
    })
  ),
  provider: z.string(),
});

// ---------------------------------------------------------------------------
// Scouting Loop Flow
// ---------------------------------------------------------------------------

export const scoutingLoopFlow = ai.defineFlow(
  {
    name: "scoutingLoop",
    inputSchema: ScoutingLoopInputSchema,
    outputSchema: ScoutingLoopOutputSchema,
  },
  async (input) => {
    // Step 1: Assess the photo
    const assessment = await assessTimelinePhotoTool({
      dayNumber: input.dayNumber,
      expectedStage: input.expectedStage,
      cropName: input.cropName,
      photoMimeType: input.photoMimeType,
      photoBase64: input.photoBase64,
      userMarkedSimilar: input.userMarkedSimilar,
    });

    // Step 2: Plan which additional tools to call
    const toolPlan = await planScoutingSequenceTool({
      cropName: input.cropName,
      expectedStage: input.expectedStage,
      dayNumber: input.dayNumber,
      recommendation: assessment.recommendation,
      rationale: assessment.rationale,
      location: input.location,
      hasCoordinates:
        input.latitude !== undefined && input.longitude !== undefined,
      hasPolygon: input.polygon !== undefined && input.polygon.length >= 3,
    });

    const knowledgeQuery =
      toolPlan.knowledgeQuery ||
      `${input.cropName} ${assessment.observedStage} ${assessment.recommendation}`.trim();
    const shouldUseKnowledge = toolPlan.steps.includes("knowledge_base");
    const shouldUseWeather = toolPlan.steps.includes("weather_now");

    // Step 3: Query knowledge base (optional)
    const kbReply = shouldUseKnowledge
      ? await queryKnowledgeBaseTool({
          query: knowledgeQuery,
          pageSize: 4,
          expandQuery: true,
        })
      : { results: [], totalResults: 0 };

    // Step 4: Get weather (optional)
    const weather = shouldUseWeather
      ? await resolveWeatherTool({
          location: input.location,
          latitude: input.latitude,
          longitude: input.longitude,
          polygon: input.polygon,
        })
      : null;

    // Step 5: Synthesize final recommendation
    const recommendation = await synthesizeScoutingActionTool({
      cropName: input.cropName,
      expectedStage: input.expectedStage,
      dayNumber: input.dayNumber,
      assessment,
      weather: weather ?? undefined,
      knowledgeResults: kbReply.results,
    });

    // Step 6: Persist history event
    if (input.userId) {
      const toolTrace = [
        {
          step: "photo_assessment",
          provider: assessment.provider,
          used: true,
        },
        {
          step: "knowledge_base",
          used: shouldUseKnowledge,
          query: shouldUseKnowledge ? knowledgeQuery : null,
          totalResults: kbReply.totalResults,
        },
        {
          step: "weather_now",
          used: shouldUseWeather,
          condition: weather?.condition ?? null,
        },
        {
          step: "final_recommendation",
          provider: recommendation.provider,
        },
      ];

      await persistHistoryEventTool({
        userId: input.userId,
        category: "action_log",
        title: `Scouting loop - Day ${input.dayNumber}`,
        summary: recommendation.issueSummary,
        recommendation: recommendation.primaryAction,
        payload: {
          cropName: input.cropName,
          expectedStage: input.expectedStage,
          dayNumber: input.dayNumber,
          similarityScore: assessment.similarityScore,
          observedStage: assessment.observedStage,
          weatherCondition: weather?.condition ?? null,
          toolTrace,
        },
      });
    }

    return {
      dayNumber: input.dayNumber,
      cropName: input.cropName,
      expectedStage: input.expectedStage,
      assessment,
      weather: weather ?? null,
      knowledge: {
        query: shouldUseKnowledge ? knowledgeQuery : null,
        results: kbReply.results,
        totalResults: kbReply.totalResults,
      },
      recommendation,
      toolPlan,
      toolTrace: [
        {
          step: "photo_assessment",
          provider: assessment.provider,
          used: true,
        },
        {
          step: "knowledge_base",
          used: shouldUseKnowledge,
          query: shouldUseKnowledge ? knowledgeQuery : null,
          totalResults: kbReply.totalResults,
        },
        {
          step: "weather_now",
          used: shouldUseWeather,
          condition: weather?.condition ?? null,
        },
        {
          step: "final_recommendation",
          provider: recommendation.provider,
        },
      ],
      provider: "agent-scouting-loop-v1-genkit",
    };
  }
);
