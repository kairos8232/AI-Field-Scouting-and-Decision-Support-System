# Field Insights Cloud Service

Node.js backend for polygon analysis prototype:
- Receives polygon from mobile app
- Produces Earth-style environment summary (mock mode by default)
- Produces crop recommendations with Gemini (when API key is set)

## 1) Local Run

1. Copy root env example:

  cp ../.env.example ../.env

2. Set values in root .env:

   GEMINI_API_KEY=YOUR_KEY
   EARTH_ENGINE_MODE=mock
  EARTH_ENGINE_PROJECT_ID=your-gcp-project-id
  EARTH_ENGINE_SERVICE_ACCOUNT_JSON={"type":"service_account",...}

3. Install and run:

   npm install
   npm run dev

4. Test endpoint:

   curl -X POST http://localhost:8080/api/field-insights \
     -H "Content-Type: application/json" \
     -d '{"polygon":[{"x":0.2,"y":0.2},{"x":0.8,"y":0.2},{"x":0.8,"y":0.8},{"x":0.2,"y":0.8}]}'

## 2) Deploy To Cloud Run

From repository root:

1. Build and deploy:

   gcloud run deploy farmtwin-field-insights \
     --source ./cloud-backend \
     --region asia-southeast1 \
     --platform managed \
     --allow-unauthenticated \
     --set-env-vars GEMINI_API_KEY=YOUR_KEY,EARTH_ENGINE_MODE=mock

2. Copy service URL from output.

3. Put URL in app env file:

   FIELD_INSIGHTS_BASE_URL=https://YOUR_CLOUD_RUN_URL/api

Note: If your Cloud Run URL already includes no trailing slash, use exactly /api at the end.

### Quick Redeploy After `server.js` Changes

From repository root, run:

```bash
gcloud run deploy farmtwin-field-insights \
  --source ./cloud-backend \
  --region asia-southeast1 \
  --platform managed \
  --allow-unauthenticated
```

This creates a new revision using the latest code in `cloud-backend/` while keeping existing service settings.

If you also need to update environment variables in the same deploy, add:

```bash
--set-env-vars GEMINI_API_KEY=YOUR_KEY,EARTH_ENGINE_MODE=live,EARTH_ENGINE_PROJECT_ID=YOUR_PROJECT_ID,EARTH_ENGINE_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'
```

## 3) Request/Response Contract

Request:

{
  "polygon": [
    { "x": 0.2, "y": 0.2 },
    { "x": 0.8, "y": 0.2 },
    { "x": 0.8, "y": 0.8 }
  ],
  "centroid": { "x": 0.6, "y": 0.5 },
  "targetCrops": ["Tomato", "Chili"]
}

Response:

{
  "summary": {
    "centroidLat": 6.1,
    "centroidLng": 100.3,
    "ndviMean": 0.62,
    "soilMoistureMean": 0.44,
    "rainfallMm7d": 26.7,
    "averageTempC": 28.6,
    "notes": "..."
  },
  "recommendations": [
    {
      "cropName": "Chili",
      "suitability": "High",
      "rationale": "..."
    }
  ],
  "provider": "gemini-live"
}

## 4) Earth Engine Upgrade Path

Current implementation returns geometry fallback summary by default. Live-mode linking now validates Earth Engine auth and API access.

To enable Earth Engine link:
1. Enable Earth Engine API in your project.
2. Create a service account with Earth Engine access.
3. Deploy with these env vars:

  gcloud run deploy farmtwin-field-insights \
    --source ./cloud-backend \
    --region asia-southeast1 \
    --platform managed \
    --allow-unauthenticated \
    --set-env-vars EARTH_ENGINE_MODE=live,EARTH_ENGINE_PROJECT_ID=YOUR_PROJECT_ID,EARTH_ENGINE_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}',GEMINI_API_KEY=YOUR_KEY

4. Check health endpoint. You should see:
  - earthEngineLinked: true
  - earthEngineReason: ok

After link is verified, replace buildGeometrySummary in server.js with real dataset sampling while keeping response shape unchanged.
