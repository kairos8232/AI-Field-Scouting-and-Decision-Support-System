# AI Field Scouting & Decision Support System

A farm management app for Android and iOS with an AI-powered cloud backend. Helps farmers monitor crop growth day-by-day, compare real photos with expected growth stages, and get actionable recommendations.

---

## What's In This Repo

| Folder | What It Is |
|--------|------------|
| `composeApp/` | Kotlin Multiplatform app — runs on Android and iOS |
| `cloud-backend/` | Node.js/Express backend — runs on Google Cloud Run |
| `iosApp/` | iOS-specific build files and assets |
| `gradle/`, `build.gradle.kts` | Android build configuration |

---

## Quick Start

### 1. Backend (Cloud Run)

```bash
cd cloud-backend

# Install dependencies
npm install

# Copy and fill in your secrets
cp ../.env.example .env
# Edit .env with your API keys

# Run locally
npm run dev
```

The backend needs these keys in `.env`:

- `GEMINI_API_KEY` — for AI recommendations
- `FIREBASE_*` — for data storage (optional for local dev)
- `GOOGLE_MAPS_API_KEY` — for map display in the app

### 2. Android App

```bash
# From repo root
./gradlew installDebug
```

Or open the `composeApp/` folder in Android Studio and run from there.

### 3. iOS App

1. Open `iosApp/iosApp.xcworkspace` in Xcode
2. Select your team for code signing
3. Run on simulator or device

---

## How It Works

### Daily Farming Loop

1. **Open the app** → see your farm dashboard
2. **Pick a day on the timeline** → see what the crop should look like (AI-generated picture)
3. **Take or upload a photo** of your actual crop
4. **Get AI comparison** → tells you if the crop is on track or needs attention
5. **Log your action** → app tracks what you did and follows up later

### Farm Setup

1. Enter your farm address
2. Draw the farm boundary on the map
3. Divide into lots (sections)
4. Assign a crop to each lot with planting date
5. Done — you're ready for daily monitoring

---

## Tech Stack

**Mobile App**
- Kotlin Multiplatform (shared code for Android + iOS)
- Jetpack Compose (UI)
- Firebase Auth (sign in)
- Google Maps (farm map)

**Backend**
- Node.js + Express
- Firebase Firestore (data storage)
- Google Gemini (AI comparison & recommendations)
- Google Earth Engine (environmental data)
- Vertex AI Search (farmer knowledge base)

**Infrastructure**
- Google Cloud Run (backend hosting)

---

## Key Files

| File | Purpose |
|------|---------|
| `cloud-backend/server.js` | All backend API endpoints |
| `composeApp/src/commonMain/kotlin/...` | Shared app logic and UI |
| `composeApp/src/androidMain/kotlin/...` | Android-specific code |
| `composeApp/src/iosMain/kotlin/...` | iOS-specific code |

---

## Common Tasks

**Redeploy backend to Cloud Run:**
```bash
cd cloud-backend
gcloud run deploy farmtwin-field-insights \
  --source . \
  --region asia-southeast1 \
  --platform managed \
  --allow-unauthenticated
```

**Update app config:**
Edit `.env` in the repo root — it feeds both local dev and Cloud Run deploys via `cloud-backend/scripts/generate-cloudrun-env.sh`.

**Run tests:**
```bash
./gradlew test        # unit tests
./gradlew check       # full verification
```

---

## Need Help?

- App auth flow → see "End-to-End User Flow" in the old README above
- Backend API details → see `cloud-backend/README.md`
- If something breaks → check `.env` first, then Cloud Run logs