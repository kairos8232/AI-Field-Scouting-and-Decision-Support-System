import { genkit, z } from "genkit";
import { googleAI } from "@genkit-ai/google-genai";
import {
  scoutingLoopFlow,
  ScoutingLoopInputSchema,
  ScoutingLoopOutputSchema,
} from "./scouting.js";
import {
  actionTrackerFlow,
  ActionTrackerOutputSchema,
} from "./actionTracker.js";

const ai = genkit({
  plugins: [googleAI()],
});

export const DailyDecisionLoopInputSchema = ScoutingLoopInputSchema.extend({
  issueType: z.string().default("crop health issue"),
  actionTaken: z.string().optional(),
  note: z.string().optional(),
  autoTrackThreshold: z.enum(["low", "medium", "high"]).default("medium"),
});

export const DailyDecisionLoopOutputSchema = z.object({
  scouting: ScoutingLoopOutputSchema,
  actionTracker: ActionTrackerOutputSchema.nullable(),
  autoTracked: z.boolean(),
  provider: z.string(),
});

function riskToScore(riskLevel) {
  if (riskLevel === "high") return 3;
  if (riskLevel === "medium") return 2;
  return 1;
}

export const dailyDecisionLoopFlow = ai.defineFlow(
  {
    name: "dailyDecisionLoop",
    inputSchema: DailyDecisionLoopInputSchema,
    outputSchema: DailyDecisionLoopOutputSchema,
  },
  async (input) => {
    const scouting = await scoutingLoopFlow({
      userId: input.userId,
      dayNumber: input.dayNumber,
      expectedStage: input.expectedStage,
      cropName: input.cropName,
      photoMimeType: input.photoMimeType,
      photoBase64: input.photoBase64,
      location: input.location,
      latitude: input.latitude,
      longitude: input.longitude,
      polygon: input.polygon,
      userMarkedSimilar: input.userMarkedSimilar,
    });

    const shouldAutoTrack =
      Boolean(input.userId) &&
      riskToScore(scouting.recommendation.riskLevel) >= riskToScore(input.autoTrackThreshold);

    let actionTracker = null;
    if (shouldAutoTrack && input.userId) {
      actionTracker = await actionTrackerFlow({
        userId: input.userId,
        dayNumber: input.dayNumber,
        cropName: input.cropName,
        issueType: input.issueType,
        actionTaken: input.actionTaken || scouting.recommendation.primaryAction,
        note: input.note,
      });
    }

    return {
      scouting,
      actionTracker,
      autoTracked: shouldAutoTrack,
      provider: "agent-daily-decision-loop-v1-genkit",
    };
  }
);
