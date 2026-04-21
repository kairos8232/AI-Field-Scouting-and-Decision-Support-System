# Field Insights Cloud Service

Node.js backend for polygon analysis prototype:
- Receives polygon from mobile app
- Produces Earth Engine environment summary
- Produces crop recommendations with Gemini (when API key is set)
- Produces timeline stage visuals with Vertex Imagen (preferred when configured), Gemini SVG fallback otherwise
- Supports farmer knowledge-base search with Vertex AI Search (Discovery Engine)
- Optionally stores each analysis result in Firebase Firestore

## 1) Deploy To Cloud Run

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

## 2) Request/Response Contract

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

## 2.1) Knowledge Base Query API (Vertex AI Search)

Endpoint:

```bash
POST /api/knowledge/query
```

Request body:

```json
{
  "query": "How to control rice blast in wet season?",
  "pageSize": 5
}
```

Response body:

```json
{
  "query": "How to control rice blast in wet season?",
  "results": [
    {
      "title": "Rice Blast Management",
      "snippet": "Use resistant varieties, balanced nitrogen, and fungicide timing...",
      "uri": "https://example.org/rice-blast",
      "sourceId": "doc-123",
      "score": 0.91
    }
  ],
  "totalResults": 24,
  "provider": "vertex-ai-search-live"
}
```

Required environment variables:

```bash
VERTEX_SEARCH_ENABLED=true
VERTEX_SEARCH_DATA_STORE=YOUR_DATA_STORE_ID
```

Optional (defaults shown):

```bash
VERTEX_PROJECT_ID=YOUR_PROJECT_ID
VERTEX_SEARCH_LOCATION=global
VERTEX_SEARCH_COLLECTION=default_collection
VERTEX_SEARCH_SERVING_CONFIG=default_search
VERTEX_SEARCH_QUERY_EXPANSION_ENABLED=true
VERTEX_SEARCH_QUERY_EXPANSION_VARIANTS=3
VERTEX_SEARCH_EXPANSION_PAGE_SIZE=4
```

Query expansion notes:

- `VERTEX_SEARCH_QUERY_EXPANSION_ENABLED=true` enables related-query retrieval and merges unique results.
- `VERTEX_SEARCH_QUERY_EXPANSION_VARIANTS=3` means up to 3 extra query variants beyond the original query.
- `VERTEX_SEARCH_EXPANSION_PAGE_SIZE=4` controls how many results each expanded variant requests.
- You can disable expansion per request by sending `"expandQuery": false` in request body.

Quick test:

```bash
curl -X POST "https://YOUR_CLOUD_RUN_URL/api/knowledge/query" \
  -H "Content-Type: application/json" \
  -d '{"query":"best practices for corn pest scouting","pageSize":5}'
```

## 2.2) Bulk Add Knowledge Documents (Vertex AI Search)

If your datastore has only a few documents, search results will stay small even with larger `pageSize`. Use this script to upsert many agronomy documents:

1. Prepare your docs JSON file (array of objects) with at least:

```json
[
  {
    "id": "rice-blast-management",
    "title": "Rice Blast Management",
    "snippet": "Use resistant varieties, balanced nitrogen, and fungicide timing.",
    "uri": "https://example.org/rice-blast",
    "crop": "rice",
    "topic": "disease",
    "tags": ["rice", "blast", "IPM"]
  }
]
```

2. You can start from the sample file:

```bash
cloud-backend/data/knowledge-docs.sample.json
```

3. Run bulk upsert from repository root:

```bash
npm --prefix cloud-backend run kb:upsert -- --file cloud-backend/data/knowledge-docs.sample.json
```

Required env vars for script:

```bash
VERTEX_PROJECT_ID=YOUR_PROJECT_ID
VERTEX_SEARCH_DATA_STORE=YOUR_DATA_STORE_ID
```

Optional env vars:

```bash
VERTEX_SEARCH_LOCATION=global
VERTEX_SEARCH_COLLECTION=default_collection
VERTEX_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'
```

The script first tries `PATCH .../branches/default_branch/documents/{id}` and, if missing, falls back to `POST .../branches/default_branch/documents?documentId={id}`.

## 3) Firebase Storage Endpoints

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

## 4) Authentication Endpoints

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

## 5) Earth Engine Upgrade Path

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
gcloud run deploy farmtwin-field-insights 
  --source ./cloud-backend 
  --region asia-southeast1 
  --platform managed 
  --allow-unauthenticated

gcloud run deploy farmtwin-field-insights --source ./cloud-backend --region asia-southeast1 --platform managed --allow-unauthenticated

Windows: 
(New-Object Net.WebClient).DownloadFile("https://dl.google.com/dl/cloudsdk/channels/rapid/GoogleCloudSDKInstaller.exe", "$env:Temp\GoogleCloudSDKInstaller.exe")

& $env:Temp\GoogleCloudSDKInstaller.exe

gcloud run deploy farmtwin-field-insights `
  --source .\cloud-backend `
  --region asia-southeast1 `
  --platform managed `
  --allow-unauthenticated