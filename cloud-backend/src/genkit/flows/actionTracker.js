import { genkit, z } from "genkit";
import { googleAI } from "@genkit-ai/google-genai";
import {
  readRecentHistoryEventsTool,
  synthesizeActionTrackerFollowUpTool,
  persistHistoryEventTool,
} from "../tools.js";

const ai = genkit({
  plugins: [googleAI()],
});

// ---------------------------------------------------------------------------
// Input/Output schemas
// ---------------------------------------------------------------------------

export const ActionTrackerInputSchema = z.object({
  userId: z.string(),
  dayNumber: z.number().int().positive(),
  cropName: z.string(),
  issueType: z.string().default("crop health issue"),
  actionTaken: z.string().optional(),
  note: z.string().optional(),
});

export const ActionTrackerOutputSchema = z.object({
  dayNumber: z.number(),
  cropName: z.string(),
  issueType: z.string(),
  actionTaken: z.string().nullable(),
  note: z.string().nullable(),
  recentEventsUsed: z.number(),
  followUp: z.object({
    nextBestAction: z.string(),
    followUpQuestion: z.string(),
    confidence: z.number(),
    riskLevel: z.enum(["low", "medium", "high"]),
    provider: z.string(),
  }),
  provider: z.string(),
});

// ---------------------------------------------------------------------------
// Action Tracker Flow
// ---------------------------------------------------------------------------

export const actionTrackerFlow = ai.defineFlow(
  {
    name: "actionTracker",
    inputSchema: ActionTrackerInputSchema,
    outputSchema: ActionTrackerOutputSchema,
  },
  async (input) => {
    // Step 1: Read recent history events from Firestore
    const recentEvents = await readRecentHistoryEventsTool({
      userId: input.userId,
      limit: 25,
    });

    // Step 2: Synthesize follow-up recommendation
    const followUp = await synthesizeActionTrackerFollowUpTool({
      dayNumber: input.dayNumber,
      cropName: input.cropName,
      issueType: input.issueType,
      actionTaken: input.actionTaken,
      note: input.note,
      recentEvents: recentEvents,
    });

    // Step 3: Persist this action event
    await persistHistoryEventTool({
      userId: input.userId,
      category: "action_log",
      title: `Action tracker follow-up - Day ${input.dayNumber}`,
      summary: `${input.issueType} (${input.cropName})`,
      recommendation: followUp.nextBestAction,
      payload: {
        dayNumber: input.dayNumber,
        cropName: input.cropName,
        issueType: input.issueType,
        actionTaken: input.actionTaken,
        note: input.note,
        confidence: followUp.confidence,
      },
    });

    return {
      dayNumber: input.dayNumber,
      cropName: input.cropName,
      issueType: input.issueType,
      actionTaken: input.actionTaken ?? null,
      note: input.note ?? null,
      recentEventsUsed: recentEvents.length,
      followUp,
      provider: "agent-action-tracker-v1-genkit",
    };
  }
);
