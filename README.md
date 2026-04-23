# AI Field Scouting and Decision Support System

Kotlin Multiplatform farm management app (Android + iOS) with a Cloud Run backend for AI-assisted field scouting, daily crop monitoring, and farm decision support.

## What This Project Does

This project helps farmers move from static records to daily action loops:

- map farm context (address, boundary, lots)
- monitor growth progress day by day
- compare real crop photos with expected stage
- suggest next action and track intervention outcomes
- persist farm data and timeline caches in the cloud

## Feature Highlights

### Latest Updates (Apr 2026)

- Farm creation timestamp is now immutable per farm (`createdAtEpochMs`) and preserved across syncs.
- Timeline progression is calendar-aware from planting date, so future days cannot be unlocked early by repeated uploads.
- Timeline day cards show expected calendar date (for example, Day 5 from 2026-04-23 -> 2026-04-27).
- Dashboard `Current day` now follows actual farm progression and no longer follows whichever timeline day was last clicked.
- Day calculation now uses local device timezone instead of UTC-only date math to avoid midnight off-by-one behavior.
- AI consultation fallback now detects Gemini quota exhaustion and shows explicit reset guidance.

### Authentication

- Email/password sign-up and sign-in
- Google sign-in via browser OAuth code flow
- Backend token exchange and Firebase user provisioning/merge

### Farm Setup and Multi-Farm Management

- Step-based setup: address -> boundary -> lot sections
- Lot-level crop plan, soil, and water metadata
- Multi-farm switching with cloud sync
- Per-farm location persistence (address + map query stored independently)

### Dashboard

- Farm lot map preview with lot overlays
- Lot summary cards (crop, soil, water)
- Current day and latest health score shortcuts
- `Current day` opens Timeline at the actual progression day (not last selected timeline page)
- Weather-now summary by active farm location

### Timeline and AI Comparison

- AI stage visual generation for selected timeline day
- Photo upload (camera/gallery)
- AI similarity scoring and observed stage output
- Recommendation and rationale for follow-up action
- Calendar-date-based day unlock from planting date
- Expected date display per timeline day

### Action and Recovery Tracking

- Action confirmation and intervention logging
- Risk-aware suggestions and progressive follow-up
- Recovery forecast visibility when relevant

### Cloud and Data Layer

- Node.js backend on Cloud Run
- Firestore persistence for farm config, timeline photo cache, stage visual cache, and assessment cache
- History/event endpoints for action logs, knowledge lookups, and timeline comparisons

## End-to-End User Flow

### 1. Authentication Flow

1. User opens Auth screen.
2. User signs in using Email/Password or Google.
3. For Google sign-in, app opens Google OAuth in browser.
4. OAuth callback is handled by backend callback endpoint, then relayed into app deep link.
5. App sends authorization code to backend `/api/auth/google-signin`.
6. Backend exchanges code, verifies ID token, creates/merges Firebase user, returns app auth session.

### 2. First-Time Farm Setup Flow

1. User enters farm address or uses current location.
2. User confirms boundary area on map.
3. User divides farm into lots and assigns lot details.
4. User completes recommendation/persistence step.
5. App syncs active farm and stored farms to cloud.

### 3. Daily Monitoring Flow

1. User opens Dashboard and picks active farm.
2. Dashboard shows current progression day derived from planting date and local timezone.
3. User opens Timeline for the current progression day.
4. Timeline only allows days that are already reached by calendar date.
5. App loads expected stage visual and expected calendar date for selected day.
6. User uploads a real crop photo and runs comparison.
7. AI returns similarity, observed stage, and recommendation.
8. User logs action decision and continues to next day loop.

## Data and Sync Model

Farm config sync stores:

- active farm ID
- farms array (each farm has independent address/map query/boundary/lots)
- active farm legacy fields for backward compatibility
- timeline caches (photo uploads, stage visuals, assessments)
- per-farm immutable creation timestamp (`createdAtEpochMs`)

This design keeps active farm UX simple while preserving multi-farm history and compatibility with older payloads.

## Technical Architecture

### Mobile App

- Kotlin Multiplatform shared domain/state/UI logic in `composeApp`
- Platform implementations for maps, image picking, auth/session bridges, and storage

### Backend

- Express service in `cloud-backend`
- Firebase Admin for auth and Firestore
- Google OAuth and AI integrations

### Platforms

- Android: Compose + WebView-based map integration
- iOS: Compose Multiplatform host with native platform bridges

## Current Product Intent

The product is optimized for practical field usage:

- fast setup and clear map workflows
- concise, actionable AI output
- explicit controls over hidden complexity
- incremental decision support instead of one-time reports