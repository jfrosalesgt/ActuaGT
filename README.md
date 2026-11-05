# ActuaGT

An AI-Verified Civic Action Platform connecting grassroots infrastructure reporting with accountable public leadership.

Citizens report urban problems (potholes, water leaks, broken streetlights, trash buildup) by speaking for up to 30 seconds. Meta AI structures the idea, verifies the photos against the text, and neighbors ratify the case before it reaches officials.

## How it works

1. **Voice dictation** — Android speech recognition captures up to 30 s of audio (chained segments, silence cutoff, Detener button), with automatic fallback to the system voice dialog on wedged ROMs.
2. **AI cleanup** — Muse Spark (`muse-spark-1.3`) neutralizes the transcript into a short title plus description: no profanity, no insults, no personal accusations. Crime reports divert to a dedicated security outcome.
3. **Verify before submit** — the same model judges whether the photos show the reported problem (`match`/`reason`). Mismatch blocks submission with an explanation. Photos upload to Storage only when the report is actually sent; a scheduled sweep deletes orphaned uploads.
4. **Triage** — authenticity (no screenshots), text–image coherence, and civic category (`VIALIDAD`, `AGUA_Y_DRENAJES`, `ALUMBRADO`, `DESECHOS_Y_AMBIENTE`, `ESPACIOS_PUBLICOS`, `OTROS`).
5. **Ratification** — neighbors confirm cases with their own located photos; confirmed cases escalate to officials, who respond with resolution proof.
6. **Map + list** — zone-scoped feed with counters (ratified / pending), nearest-first ordering, and a "Mi ubicación" pin.

## Stack

| Layer | Tech |
|---|---|
| App | Kotlin 2.3.0, Compose Multiplatform 1.10.0, Voyager navigation |
| Android | AGP 8.13, Credential Manager Google Sign-In, Maps Compose, Play Location |
| Data | GitLive SDK 2.5.0 (Auth, Firestore, Storage, Functions), Firebase BoM 35.1.0 |
| Backend | Cloud Functions (Node 22, TypeScript), Firestore rules + indexes, Storage rules, daily orphan sweep |
| AI | Muse Spark `muse-spark-1.3` via `https://api.meta.ai/v1/chat/completions` (strict-JSON contracts) + SAM 3.1 damage overlay (backend-auto, optional) |

Code and comments in English; user-facing UI copy in Spanish.

## Project structure

- `shared/` — Kotlin Multiplatform shared logic: screens, models, media bridges (`androidMain`), iOS stubs (`iosMain`)
- `androidApp/` — Android shell: `MainActivity`, Google Sign-In, file provider
- `functions/src/` — Cloud Functions: `transcription`, `moderation` (triage + coherence), `sam` (SAM 3.1 damage overlay, backend-auto), `escalation`, `official`, `notifications`, `orphan-sweep`
- `functions/test/` — backend unit tests (58 green)
- `firestore.rules`, `firestore.indexes.json`, `storage.rules` — security + indexes

## Setup

Prerequisites: JDK 17, Android SDK, Node 22, Firebase CLI logged into the project.

1. Android secrets (never commit): place `google-services.json` in `androidApp/` and define SDK paths in `local.properties`.
2. Muse API key for the backend:
   `firebase functions:secrets:set MUSE_API_KEY`
3. SAM 3.1 (optional, backend-auto): point to a hosted endpoint; when unset, moderation works unchanged without overlay:
   `firebase functions:secrets:set SAM_API_URL` (e.g. your FastAPI/HF/Replicate proxy)
   `firebase functions:secrets:set SAM_API_TOKEN` (optional bearer)
4. Install backend deps: `npm --prefix functions install`

## Commands

- App compile: `.\gradlew.bat :shared:compileKotlinAndroid :shared:assembleAndroidMain :androidApp:assembleDebug`
- Install on device: `.\gradlew.bat :androidApp:installDebug` (Xiaomi/Redmi: tap Install on the on-device USB prompt)
- Backend tests: `npm --prefix functions test`
- Deploy sweep: `firebase deploy --only functions:sweepOrphanPhotos --project actuagt-22679`

## Tests

- Backend: `functions/test/*.test.cjs` — transcription, coherence, moderation, escalation, official, orphan-sweep
- App: `shared` commonTest + `androidApp` instrumented tests (location submit, sign-in errors, geo utils)

## Git flow

`feature/*` branches off `develop`, merged with `--no-ff`. Never commit `google-services.json`, `.env`, `local.properties`, or build outputs.
