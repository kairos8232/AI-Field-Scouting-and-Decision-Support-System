# AI Field Scouting and Decision Support System

Kotlin Multiplatform farm management app (Android + iOS) with an AI-assisted cloud backend for:

- farm setup and lot mapping
- field insights and crop recommendation
- daily timeline stage generation and photo comparison
- action planning and recovery tracking
- weather-now by farm location

## Product Overview

This app helps farmers move from passive record-keeping to active daily decisions.

Instead of only showing static farm data, it continuously combines:

- mapped farm context (boundary, lots, crop plan)
- daily plant evidence (camera/gallery photos)
- AI interpretation (growth-stage similarity and action suggestions)
- lightweight operational tracking (actions taken and follow-up)

The core goal is simple: keep daily monitoring practical, readable, and actionable.

## Core Functions

### 1) Farm Setup and Lot Management

- capture farm name/location
- draw farm boundary and split lots
- assign crop plan, soil, and water availability per lot
- support multi-farm switching and cloud persistence

### 2) Dashboard Monitoring

- shows lot map and lot-level summary cards
- weather-now panel from backend geocode + weather endpoint
- timeline and health entry points

### 3) Daily Timeline (AI)

- generate expected stage image for the selected day
- upload farmer photo (camera or gallery)
- compare with AI to get similarity and observed stage
- show concise next-action suggestion
- open action plan flow for intervention logging

### 4) Action and Recovery Flow

- action confirmation with recommended and alternative actions
- pesticide/fungicide safety warning gate
- recovery forecast shown only when risk warrants it
- progressive day unlock behavior (next day unlocks after check-in)

### 5) Backend and Cloud

- Cloud Run Node service for:
- `field insights`
- `weather-now by location`
- `auth endpoints`
- `timeline image/photo endpoints`
- Firestore-based persistence for farm config + media cache metadata

## App Flow (End-to-End)

### Flow A: Initial Setup

1. User signs in.
2. User defines farm location and boundary.
3. User creates lots and assigns crop plans.
4. App saves farm config to cloud.

### Flow B: Daily Monitoring Loop

1. User opens Timeline for current day.
2. App shows expected stage visual for that day.
3. User uploads a real plant photo.
4. User taps Compare.
5. AI returns similarity, observed stage, and next action.
6. User optionally confirms action taken.
7. Next day unlocks after check-in.

### Flow C: Action and Recovery

1. If condition looks risky, app suggests action options.
2. User selects what was actually done.
3. Recovery trend/forecast is shown only when needed.
4. User repeats check-in daily until condition stabilizes.

## Screen-Level Intent

- Dashboard: quick farm health and navigation hub.
- Timeline: primary daily check-in workspace.
- Action Confirmation: operational logging + safety-aware decisions.
- Me/Profile: farm switching and account controls.

## Design Direction

The UI aims to stay farmer-friendly by prioritizing:

- fewer high-signal actions per screen
- short and direct AI wording
- explicit controls (avoid hidden long-press behavior)
- progressive disclosure (show detail only when needed)