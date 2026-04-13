# Field Insights Cloud Service

Node.js backend for polygon analysis prototype:
- Receives polygon from mobile app
- Produces Earth-style environment summary (mock mode by default)
- Produces crop recommendations with Gemini (when API key is set)
- Optionally stores each analysis result in Firebase Firestore

## 1) Local Run

1. Copy root env example:

  cp ../.env.example ../.env

2. Set values in root .env:

   GEMINI_API_KEY=YOUR_KEY
   EARTH_ENGINE_MODE=mock
  EARTH_ENGINE_PROJECT_ID=your-gcp-project-id
  EARTH_ENGINE_SERVICE_ACCOUNT_JSON={"type":"service_account",...}
  FIREBASE_PROJECT_ID=your-gcp-project-id
  FIREBASE_SERVICE_ACCOUNT_JSON={"type":"service_account",...}
  FIREBASE_COLLECTION=fieldInsights
  FIREBASE_WEB_API_KEY=your-firebase-web-api-key

3. Install and run:

   npm install
   npm run dev

4. Test endpoint:

   curl -X POST http://localhost:8080/api/field-insights \
     -H "Content-Type: application/json" \
     -d '{"polygon":[{"x":0.2,"y":0.2},{"x":0.8,"y":0.2},{"x":0.8,"y":0.8},{"x":0.2,"y":0.8}]}'

## 2) Deploy To Cloud Run

### One Source of Truth (Recommended)

If you do not want to maintain both `.env` and YAML manually, keep only root `.env` updated, then generate Cloud Run YAML right before deploy:

```bash
bash cloud-backend/scripts/generate-cloudrun-env.sh
```

Then deploy:

```bash
gcloud run deploy farmtwin-field-insights \
  --source ./cloud-backend \
  --region asia-southeast1 \
  --platform managed \
  --allow-unauthenticated \
  --env-vars-file cloud-backend/env.cloudrun.yaml
```

This way `.env` is your only file to edit, and `env.cloudrun.yaml` is generated automatically.

If the generator reports `*_SERVICE_ACCOUNT_JSON looks incomplete`, it means your `.env` contains partial JSON (often from multiline paste).

If you want to keep JSON directly inside `.env`, normalize it to single-line JSON first:

```bash
node cloud-backend/scripts/normalize-env-json.mjs .env
```

Then regenerate YAML and deploy:

```bash
bash cloud-backend/scripts/generate-cloudrun-env.sh
gcloud run deploy farmtwin-field-insights \
  --source ./cloud-backend \
  --region asia-southeast1 \
  --platform managed \
  --allow-unauthenticated \
  --env-vars-file cloud-backend/env.cloudrun.yaml
```

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

### Deploy With Firebase Firestore Enabled

From repository root:

```bash
gcloud run deploy farmtwin-field-insights \
  --source ./cloud-backend \
  --region asia-southeast1 \
  --platform managed \
  --allow-unauthenticated \
  --set-env-vars GEMINI_API_KEY=YOUR_KEY,EARTH_ENGINE_MODE=live,EARTH_ENGINE_PROJECT_ID=YOUR_PROJECT_ID,EARTH_ENGINE_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}',FIREBASE_PROJECT_ID=YOUR_PROJECT_ID,FIREBASE_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}',FIREBASE_COLLECTION=fieldInsights,FIREBASE_WEB_API_KEY=YOUR_FIREBASE_WEB_API_KEY
```

Tip: For production on Cloud Run, you can avoid `FIREBASE_SERVICE_ACCOUNT_JSON` by attaching a service account with Firestore access and setting only `FIREBASE_PROJECT_ID`.

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

## 4) Firebase Storage Endpoints

When Firebase is configured, each `POST /api/field-insights` call is stored in Firestore.

Read recent records:

```bash
curl "https://YOUR_CLOUD_RUN_URL/api/field-insights/history?limit=20"
```

Response shape:

```json
{
  "items": [
    {
      "id": "abc123",
      "createdAt": {"_seconds": 1710000000, "_nanoseconds": 0},
      "request": {...},
      "response": {...}
    }
  ],
  "count": 1
}
```

## 5) Authentication Endpoints

Sign up (creates Firebase Auth user and upserts user profile in Firestore `users`):

```bash
curl -X POST "https://YOUR_CLOUD_RUN_URL/api/auth/signup" \
  -H "Content-Type: application/json" \
  -d '{"email":"farmer@example.com","password":"strongpass123","displayName":"Farmer Ali"}'
```

Sign in (validates credentials via Firebase Auth):

```bash
curl -X POST "https://YOUR_CLOUD_RUN_URL/api/auth/signin" \
  -H "Content-Type: application/json" \
  -d '{"email":"farmer@example.com","password":"strongpass123"}'
```

## 6) Earth Engine Upgrade Path

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

mac:
gcloud run deploy farmtwin-field-insights \
  --source ./cloud-backend \
  --region asia-southeast1 \
  --platform managed \
  --allow-unauthenticated

Windows: 
(New-Object Net.WebClient).DownloadFile("https://dl.google.com/dl/cloudsdk/channels/rapid/GoogleCloudSDKInstaller.exe", "$env:Temp\GoogleCloudSDKInstaller.exe")

& $env:Temp\GoogleCloudSDKInstaller.exe

gcloud run deploy farmtwin-field-insights `
  --source .\cloud-backend `
  --region asia-southeast1 `
  --platform managed `
  --allow-unauthenticated