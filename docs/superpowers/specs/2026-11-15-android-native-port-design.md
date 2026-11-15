# BananaCard (ex-FlashAI) Native Android Port — Design

**Date:** 2026-11-15
**Status:** Draft, pending approval
**Source app:** `../flashai` (Flutter, sqflite, Riverpod, Firebase), referred to below as "the Flutter app"
**Target repo:** `flashai-bananacard` (this repo), github.com/jbclaramonte/flashai-bananacard (public)

## 1. Intent

BananaCard (originally FlashAI) is a spaced-repetition flashcard app (FSRS scheduling, AI card generation) with a playful, Duolingo-inspired look. It targets Android only. Flutter's cross-platform value is therefore unused, while every Android-specific feature (exact alarms, notifications, share intents) goes through third-party plugins that lag behind Android releases.

The port rewrites the app in Kotlin + Jetpack Compose. It also uses the rewrite to simplify the product:

- **No backend.** The Flutter app depends on Firebase (anonymous Auth, Cloud Functions proxying OpenRouter, Firestore credits, Storage backups, Analytics). The native app has none of it. AI calls go straight from the device to OpenRouter with the user's own key.
- **No user data on the developer's side.** Backups live on the user's device and in the user's own Google account (Android Auto Backup), never in a developer-owned bucket. This was the original intent behind "backups"; the Flutter implementation (Firebase Storage) contradicted it.
- **Lighter GDPR footprint.** No analytics, no ads, no consent banner.

The only user today is the developer. There is no data or algorithm compatibility to preserve.

**Name:** the app is called **BananaCard**. "FlashAI" is the original name and still appears in many places in the Flutter app (launcher label, dialogs, docs); none of it carries over. Every user-facing string, the launcher label, notifications, the Play listing and the OpenRouter attribution use "BananaCard". Code identifiers and the `applicationId` are unaffected.

## 2. Goals, non-goals, anti-goals

### Goals (v1)
- Functional parity with the Flutter app's core loop: decks, cards, study, review planning, streak, reminders, share intent, AI generation. Section 6 lists the deliberate differences.
- The app is fully usable with no OpenRouter key. AI features are optional add-ons.
- Business rules (FSRS scheduling, review planning, streak, prompt building) live in a pure-Kotlin module and are covered by fast JVM unit tests.
- The app ships as an update to the existing Play Store listing.

### Non-goals (v1)
- Migrating the Flutter app's local data (SQLite, SharedPreferences).
- FSRS state compatibility with the Flutter app.
- iOS, tablets-specific layouts, Wear OS.
- LaTeX rendering (phase 2).
- Home screen widget (phase 2).
- Monetization of any kind. A one-time Play Billing "Pro" purchase may come later; it is out of scope here.

### Anti-goals (failure even if it "works")
- Any user content or identifier sent to a developer-owned server.
- Any analytics/telemetry SDK, including Firebase Analytics or Crashlytics.
- The OpenRouter API key stored in plain text or included in backups.
- A secret committed to the public repo (signing keys, service account JSON, API keys).
- A notification reminder that silently never fires (the bug fixed in the Flutter app on 2026-05-10).

## 3. Constraints

- **Play Store continuity:** `applicationId = com.mobeezz.flashlearnai` with the same upload/signing key as the Flutter app. Flavor `dev` keeps the `.dev` suffix.
- **versionCode:** the Flutter CI uses `date -u +%s` (epoch seconds). The native CI keeps the same scheme so every native build is greater than any Flutter build (valid until ~2036, the Play limit is 2100000000).
- **SDK levels:** `minSdk = 26` (my call: notification channels and `java.time` natively, negligible device loss). `targetSdk`/`compileSdk` = latest level required by Play at implementation time (36 at time of writing).
- **Public repo:** `.gitignore` from the first commit covers `local.properties`, `*.jks`, `*.keystore`, `key.properties`, `google-services.json`, `*.json` service-account files. CI secrets live in GitHub Actions secrets only. Do not copy `functions/serviceAccountKey.json` from the Flutter repo.
- **Languages:** French and English for all UI strings, including the strings the Flutter app hardcodes in French (notifications, dialogs, errors, interval units) or English (AI errors).

## 4. Approaches considered

### 4.1 Project structure: picked B
- **A. Single `:app` module**, organized by package. Simplest. Business logic would sit in an Android module: slower tests, nothing stops it from depending on Android.
- **B. `:app` + pure-Kotlin `:domain` (picked).** `:domain` holds models, FSRS integration, review planning, streak, notification scheduling decisions, prompt building and JSON schemas. It has no Android dependency, and its tests run on the JVM in seconds. `:app` holds UI, Room, notifications, OpenRouter networking, Keystore, Android integrations. Small overhead (one more Gradle module) for testable core rules.
- **C. Per-feature modules (Now in Android style).** Rejected: too heavy for a solo developer and ~13k lines of code.

Rejected upfront: Kotlin Multiplatform (contradicts Android-only), XML views (legacy).

### 4.2 AI funding: picked "bring your own OpenRouter key"
- **Ads + server credits (current Flutter app).** Zero friction for users. Needs a backend, AdMob consent (UMP) for GDPR, and pays off uncertainly.
- **User's own OpenRouter key, no backend (picked).** Zero cost and zero user data on the developer side. Friction: users need an OpenRouter account, mitigated by free models.
- **Hybrid (free quota, then own key).** Keeps a backend for the quota. Rejected for complexity.
- An OpenRouter affiliate/revenue-share program was considered. None exists (only a startup credits program), and OpenRouter sells prepaid credits, not subscriptions.

### 4.3 Backups: picked Auto Backup + JSON export/import
- **Android Auto Backup (picked).** Zero code, stored in the user's Google account. Restores only on reinstall or new device, 25 MB cap.
- **JSON export/import through the system file picker (picked).** Manual, the user picks the location (Google Drive appears there if installed). No OAuth.
- **Drive `appDataFolder` via the Drive API.** In-app listing and silent sync, but OAuth client setup and `drive.appdata` scope. Deferred; may come later.

### 4.4 Visual identity: keep the Duolingo spirit
Material You dynamic color was considered and rejected: it would erase the playful identity. The current palette and style stay, built on Material 3 components, plus a dark theme.

## 5. Architecture and decided stack

- Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel + `StateFlow`.
- Hilt for DI.
- Room for the database (fresh schema, no migration from sqflite), Flows for reactive reads.
- DataStore (Preferences) for settings.
- OkHttp + kotlinx.serialization for OpenRouter. Jsoup for HTML extraction. HTML→Markdown conversion: builder's choice (library or small converter).
- FSRS: a maintained Kotlin implementation of FSRS-6 if one is solid enough (candidates: FsrsKt on Maven Central, FSRS-Kotlin; `open-spaced-repetition/android-fsrs` is v4 and stale). Otherwise port the algorithm into `:domain`. **Builder's call**, with the scheduler parameters from section 7.3.
- Konfetti (or equivalent) for the celebration confetti. `androidx.emoji2` `EmojiPickerView` for deck icons.

Module boundaries:

| `:domain` (pure Kotlin) | `:app` (Android) |
|---|---|
| Models: Deck, Flashcard, card state, UserSettings, DailyActivity, DeckDailyStatus | Room entities, DAOs, repositories implementing domain interfaces |
| FSRS wrapper: rating → next state, interval preview | Compose screens, ViewModels, theme |
| Review/study planning | Notifications: channels, AlarmManager, receivers |
| Streak computation | OpenRouter HTTP client, OAuth PKCE, model list |
| Reminder decision (what to schedule, when) | Keystore-encrypted key storage |
| Prompt builders + JSON schemas + response parsing/validation | Share intent, app shortcuts, Auto Backup rules |
| Deck export/import JSON format | Web fetch + HTML extraction |

`:domain` takes time through an injected `Clock`/`TimeZone` so date rules are testable.

## 6. Deliberate differences from the Flutter app

Bugs and dead code found in the Flutter app. All resolutions are my calls unless stated.

| Flutter behavior | Native behavior |
|---|---|
| `created_at` overwritten on each update, so edited decks jump to the top | `created_at` immutable; `updated_at` on edit. Library sorted by `created_at DESC` |
| Empty language stored as `''` on create, `null` on edit | Always `null` |
| `level` field, never set by any UI | Dropped |
| Card image paths, never set by any UI | Dropped |
| "Learning" count includes new cards | Learning = learning + relearning, excluding new cards |
| Interval labels in French only | Localized (en/fr) |
| Streak only incremented on first app open the next day | Streak computed from `daily_activity` (section 7.5), so it is correct immediately after a review |
| Milestones `[7,14,30,50,100,365]`: logic present, UI never calls it | Dropped |
| Daily goal / progress ring: removed in DB v9, widget only in tests | Dropped |
| "Deck done!" snackbar shown even on early quit | Shown only when the due phase was completed |
| Reminders cancelled when there is activity today, nothing reschedules tomorrow until next app start | Next occurrence always scheduled (tomorrow if today is done) |
| Notification strings hardcoded in French | Localized |
| Share text to an existing deck creates a card with an empty answer, which always fails validation | Opens the add-card screen prefilled with the shared text as question |
| Share text to a new deck inserts a card with an empty answer, bypassing validation | Creates the deck, then opens the add-card screen prefilled |
| AI: free-text prompt ignored when a URL is given; "focus" ignored without a URL | One optional URL + one optional instructions field; both are used when present. At least one is required |
| AI: generated card over length limits aborts mid-loop, earlier cards already saved | All generated cards validated first; over-limit fields truncated; insertion in one transaction |
| Export omits `isPaused` | Export includes `isPaused` |
| Unsupported device locale falls back to French | Falls back to English (default `values/`), French in `values-fr/` |
| Review screen counts frozen in a per-day in-memory snapshot | Live counts from Room Flows (see 7.2). Simpler; counts may grow during the day as learning cards come due, which is accurate |
| "New cards per deck" cap applied per plan; only the frozen snapshot made it behave like a daily cap | Explicit **daily** cap, persisted per deck and day (decided by the developer, see 7.2) |
| "Start" section lists only decks where every card is new; a started deck with nothing due but new cards left appears nowhere | "New cards" section lists every active deck with nothing due and new cards available today |

## 7. Functional specification

The Flutter app is the reference for anything not covered here. File references point to `../flashai`.

### 7.1 Decks and cards
- **Deck fields:** id (UUID v4), name (trimmed, 2–50 chars), icon (emoji, default 🇪🇸), iconColor (ARGB int), type (`null` = auto, `language`, `general`), sourceLanguage, targetLanguage (only kept when type is `language`), description (trimmed, ≤300, empty becomes `null`), isPaused, createdAt, updatedAt.
- **Icon presets:** 🇪🇸📚🎨🧬🔍🇺🇸🎹🚀⚽🐶💼, plus an emoji picker.
- **Color:** random among `0xFFFFE5E5, 0xFFE5F0FF, 0xFFF0E5FF, 0xFFFFF4E5, 0xFFEBF5FF, 0xFFE6FAF4, 0xFFFFF0F0, 0xFFF5FFE5`.
- **Source language:** prefilled with the device language name, localized.
- **Deck stats:** total cards; progress = cards in review state / total; cards to review = due count (new cards included); learning count (see section 6).
- **Pause:** a switch on deck detail. A paused deck plans 0 cards and appears in a collapsible "paused" list on the review screen.
- **Deck menu:** edit, export (section 7.8), delete with confirmation (cascade to cards and daily status).
- **Card fields:** id, deckId, question (trimmed, required, ≤500), answer (trimmed, required, ≤1000), FSRS state (section 7.3), createdAt, updatedAt.
- **Editing a card** changes question and answer only; FSRS state is kept.
- **All-cards screen:** case-insensitive search on question and answer, multi-select, select all visible, bulk delete with confirmation.

### 7.2 Review screen and study planning
- **Daily tracking per deck:** `deck_daily_status(deck_id, date, completed_once, reviewed_once, new_started_count)`, with `date` as a local YYYY-MM-DD. The two flags latch to true for the day. `new_started_count` is incremented when a new card receives its first rating.
- **Plan per deck** (`srs_service.dart:183-217`, with a daily cap):
  - Due cards = non-new cards whose due time ≤ now + 1 min, sorted by due ascending, no limit.
  - New cards = new cards in insertion order, limited to `max(0, cap − new_started_count today)`.
  - The cap is the setting "new cards per deck **per day**" (options 0/5/10, default 5).
  - A paused deck plans nothing.
  - Lowering the cap mid-day never yields a negative count. Raising it makes more new cards available immediately.
- **Review screen sections:**
  - "To review now": active decks with planned due > 0.
  - "New cards": active decks with planned due = 0 and planned new > 0. Brand-new decks and started decks alike.
  - "Nothing for now": decks exist but both lists are empty.
  - "Paused": collapsible, shows remaining due/new counts.
- **Badges:** "done today" uses `completed_once`. The Library badge "Reviewed" uses `reviewed_once`.

### 7.3 FSRS
- Scheduler parameters: desired retention 0.9, learning steps 1 min and 10 min, relearning step 10 min, maximum interval 36500 days, fuzzing on. Default FSRS-6 weights.
- Ratings: Again, Hard, Good, Easy.
- Card state stored per card: stability, difficulty, step, state (learning/review/relearning), due (epoch ms), lastReview (epoch ms).
- A card is new when it has never been reviewed (no stability/difficulty).
- Rating buttons show the interval preview: `<1m`, `Nm`, `Nh`, `Nd` (<30 days), `Nmo` (<365 days), `Ny`, localized.
- Fuzz caveat: the preview and the applied interval may differ by the fuzz amount. Acceptable; the builder may compute the preview without fuzz.

### 7.4 Study screen
- **Start:**
  - If the deck has planned due cards, run a due phase with all of them.
  - Otherwise run a new phase with the planned new count.
  - With cap 0 and nothing due: "no card to study".
- **Card face:**
  - "Show answer" flips the card (≈600 ms). After reveal, tapping toggles faces; rating is disabled during the flip.
  - An edit button appears after reveal.
  - Font size scales down with text length (thresholds 50/80/120/200/300 chars → 40/34/30/26/22/18 sp, reduced on short screens). Exact values are the builder's call.
- **Rating:**
  - Persist the new FSRS state, then record the review (section 7.5).
  - Again re-queues the updated card at the end of the session.
  - Answered counters only count the first pass of planned cards.
  - Progress bar = position / queue length.
- **End of due phase:**
  - If all planned due cards were answered and the deck is not yet `completed_once` today, set it and show the celebration overlay: full screen, confetti ~3 s, "Deck updated today", "N cards reviewed", a Continue button.
  - Then, if new cards are still available today (section 7.2), offer a dialog with a stepper from 1 to that available count (default = the available count). Continue starts a new phase; Cancel exits.
- **Quit (X or back):** returns to the previous screen. The "Deck done!" message appears only after a completed due phase.

### 7.5 Streak and activity
- Recording a review: increment `daily_activity(date local YYYY-MM-DD).cards_reviewed`, set `deck_daily_status.reviewed_once`, increment `deck_daily_status.new_started_count` if the card was new, then re-sync reminders.
- Streak (computed in `:domain`, not stored incrementally):
  - current streak = number of consecutive local days with `cards_reviewed > 0`, ending today, or ending yesterday if today has no activity yet.
  - best streak = max of the stored best and the current streak, persisted.
- Recompute on review, app start, resume, and date change.
- **Streak badge:** top-right on every tab, grey at 0.
- **Streak detail screen:** current streak, best streak, cards reviewed today, and a current-month calendar (weeks start on Monday, active day = `cards_reviewed > 0`).

### 7.6 Reminders (notifications)
- **Settings:** enabled (default true), reminder time (default 20:00).
- **Channels** (localized names):
  - `daily_reminder`: default importance.
  - `streak_danger`: high importance.
- **Daily reminder:** fires at the reminder time; body "Don't forget to review your cards today!" / "N'oublie pas de réviser tes cartes aujourd'hui !".
- **Streak danger:** fires at 22:00, only when streak > 0; body with the streak length (see Flutter string).
- **Content tap:** opens the review screen.
- **Action button "Review":** opens the review screen.
- **Scheduling rules** (decision in `:domain`, execution in `:app`):
  - If disabled or the `POST_NOTIFICATIONS` permission is missing: cancel both.
  - If permission was requested and denied: persist enabled = false.
  - Otherwise schedule the next occurrence of each. If there is activity today, or today's time has passed, the next occurrence is tomorrow.
  - Use `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()` is true, else `setAndAllowWhileIdle`. Alarms are one-shot and rescheduled at each sync, not repeating.
- **Sync triggers:** app start, resume, each review, settings change, `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, and the exact-alarm permission state change broadcast.
- **Permissions:**
  - Request `POST_NOTIFICATIONS` on first start and when enabling reminders.
  - Exact alarms: once per app launch, if reminders are enabled and exact alarms are not allowed, show a rationale dialog ("Exact-time reminders: enable 'Alarms & reminders' for BananaCard in settings", buttons "Not now" / "Open settings"), as in the 2026-05-10 Flutter fix.
- No `USE_EXACT_ALARM` (Play policy risk).

### 7.7 Share intent and shortcuts
- **Share intent:** accept `ACTION_SEND` `text/plain`. A bottom sheet lists decks plus "Create new deck".
  - URL (starts with `http://`, `https://`, `www.`): open AI generation prefilled with the URL.
  - Other text: open add-card prefilled with the text as question.
  - New deck: create the deck first, then the same routing.
- **App shortcuts** (static, long-press on icon): "Review" → review screen, "New card" → add-card with a deck picker.

### 7.8 Backups
- **Auto Backup** enabled (`dataExtractionRules` + `fullBackupContent`):
  - Include the Room database and DataStore settings.
  - Exclude the encrypted OpenRouter key: Keystore keys do not survive a restore, so the key would be unreadable.
  - Exclude Flutter leftovers and caches.
- **Export:** deck detail menu → system "create document" picker → one JSON file per deck, schema version 2:
  ```json
  {
    "schemaVersion": 2,
    "exportedAt": "ISO-8601 UTC",
    "appVersion": "versionName+versionCode",
    "deck": { "id", "name", "icon", "iconColor", "type", "sourceLanguage", "targetLanguage", "description", "isPaused" },
    "flashcards": [ { "id", "question", "answer", "stability", "difficulty", "step", "state", "due", "lastReview" } ]
  }
  ```
  Dates are ISO-8601 UTC strings; FSRS fields may be null for new cards. `state` serialized as a string (`learning`, `review`, `relearning`) — my call, so the format does not depend on enum ordinals.
- **Import:** Settings → system "open document" picker → JSON file.
  - Schema 2: restores everything. If a deck with the same id exists, ask: replace, or import as a copy with new ids.
  - Schema 1 (Flutter export, key `flashcards` or legacy `cards`): imports deck metadata, questions and answers only, as new cards (FSRS reset).
  - Invalid file: clear error, nothing written.

### 7.9 AI (OpenRouter, bring your own key)
- **Settings section "AI":**
  - "Connect with OpenRouter" (OAuth PKCE) or "Paste a key".
  - Key stored encrypted with an Android Keystore AES key, never logged, never backed up. "Disconnect" deletes it.
  - Model picker: list from `GET https://openrouter.ai/api/v1/models`, filtered to models whose `supported_parameters` include structured outputs (`structured_outputs` / `response_format`), showing price per million tokens and a "free" tag. Default `openrouter/free`. The list is cached and refreshed on open.
- **Without a key:** AI buttons stay visible and lead to the AI settings section with a one-line explanation.
- **Requests:**
  - `POST https://openrouter.ai/api/v1/chat/completions`, the selected model, `response_format` = the JSON schemas below (`strict: true`), `provider.require_parameters: true`.
  - Headers: `HTTP-Referer` (the GitHub repo URL) and `X-Title: BananaCard` for OpenRouter app attribution.
  - Single attempt, 60 s timeout (my call; the Flutter backend chained 3 models).
- **Errors:** user-facing localized messages distinguish invalid key (401), no credits (402), rate limited (429), model unavailable/unsupported (404/400 schema), timeout, and network. Model-related errors suggest picking another model.
- **Generate cards** (AI screen, from deck detail or share intent):
  - Inputs: optional URL, optional instructions (≤200 chars); at least one is required.
  - Context sent automatically: deck name, description, type, languages, up to 50 existing questions for dedupe.
  - URL fetch (`web_content_service.dart`):
    - Add `https://` if no scheme; http/https only. GET with a mobile Chrome User-Agent, 15 s timeout, non-200 is an error.
    - Strip script/style/noscript/iframe/nav/footer/header/aside and ad/cookie/modal/share/comment elements.
    - Root = first of `main`, `article`, `[role=main]`, `.content`, `#content`, `.post-content`, `.entry-content`, `body`.
    - Convert to Markdown, collapse whitespace, truncate to 15000 chars with a truncation marker; content must be > 50 chars.
    - On fetch failure, continue with the URL only and say so in the UI.
  - Prompt: port `generateWithOpenRouter` (`functions/index.js:706-820`).
    - Generate 5–10 cards.
    - Language deck: strict word/phrase → direct translation.
    - General deck: understanding questions, 1–3 sentence answers.
    - Auto type: the model decides which rules apply.
    - Dedupe against existing questions. Keep the prompt in French, as today; the output language follows the deck.
    - **v1 removes the LaTeX instruction** (no renderer yet); phase 2 restores it.
    - Language-deck detection: `type == language || (type != general && (source || target))`.
  - Result: at most 10 cards. Shown as a preview list where the user can untick cards, then saved in one transaction.
- **Generate answer** (add-card screen):
  - Inputs: the question, optional instructions (≤200).
  - Port `generateCardAnswerWithOpenRouter` (`functions/index.js:895-979`), style `very_short` (max 12 words): language deck → direct translation only (example line only if the instructions ask for one); general deck → back of card only, no preamble, ≤4 lines. The answer is truncated to 4 lines.
  - If the answer field is not empty, confirm overwrite.
- **Schemas** (verbatim from the Flutter backend):
  ```
  {type:"json_schema",json_schema:{name:"flashcard_generation",strict:true,schema:{type:"object",properties:{flashcards:{type:"array",items:{type:"object",properties:{question:{type:"string"},answer:{type:"string"}},required:["question","answer"],additionalProperties:false}}},required:["flashcards"],additionalProperties:false}}}
  {type:"json_schema",json_schema:{name:"flashcard_answer_generation",strict:true,schema:{type:"object",properties:{answer:{type:"string"}},required:["answer"],additionalProperties:false}}}
  ```
- **System prompt:** "You are a helpful assistant that generates educational flashcards [answers]. Always respond with valid JSON."
- **Privacy:** the settings screen states that deck content and fetched page text are sent to OpenRouter and the chosen model provider under the user's own account.

### 7.10 Settings (Profile tab)
- **Reminders:** enabled toggle, time picker, exact-alarm status with a link to system settings.
- **Study:** new cards per deck per day (0/5/10).
- **AI:** section 7.9.
- **Data:** import deck.
- **Language:** link to Android per-app language settings (Android 13+, `locales_config`). On Android < 13, an in-app picker using AppCompat per-app locales (my call).
- **About:** version, privacy note, link to the GitHub repo.

## 8. UX for a pure Android app

- Navigation: bottom bar with 3 tabs (Library, Review, Profile). The FAB on Library creates a deck.
- Visual identity: the Flutter palette (`lib/config/theme.dart`): primary `#1CB0F6`, accent `#58CC02`, streak orange `#FF9600`, background `#F7F7F7`, white cards with 16 dp radius, very bold titles (w900). Plus a dark theme derived from it, following the system setting. No dynamic color.
- Edge-to-edge, predictive back, system splash screen (Android 12 API via `core-splashscreen`), adaptive icon with monochrome layer (themed icon).
- Delete confirmations use dialogs. Errors use snackbars.

## 9. Old Flutter install cleanup

The native app installs over the Flutter app. On first launch, delete these leftovers (my call):
- `databases/flashai.db*`
- `app_flutter/`
- `shared_prefs/FlutterSharedPreferences.xml`
- Firebase local files

Exclude them from Auto Backup.

## 10. Build, CI, release

- Gradle version catalog, Kotlin DSL, flavors `dev` (`.dev`, label "BananaCard Dev") and `prod` (label "BananaCard").
- **GitHub Actions:**
  - On PR: build, `:domain` unit tests, `:app` unit tests, lint.
  - Release workflows replace `android-play-internal.yml` and `android-play-production.yml` from the Flutter repo: same Play service account and upload key (as GitHub secrets), versionCode = epoch seconds, release tags `-dev`/`-prd`. The builder reads the Flutter workflows for the exact inputs.
- **Tests:**
  - `:domain` JVM tests for planning, FSRS wrapper, streak, reminder decisions, prompt building, response parsing, and export/import.
  - Room DAO tests (Robolectric or instrumented).
  - A few Compose UI tests on the study flow.
  - Manual device checklist for reminders (exact and inexact), share intent, and backup restore.

## 11. Phases

- **v1 — replaces the Flutter app on Play:** sections 7–10.
- **Phase 2:**
  - LaTeX rendering (`$…$`, `$$…$$`, raw source in red on error; renderer choice open: KaTeX in a WebView, or native) and restoring the LaTeX prompt instruction.
  - Home screen widget (Glance): due cards and streak.

## 12. Left to the builder

- FSRS library choice vs. own port (section 5), with tests pinning the parameters in 7.3.
- HTML→Markdown converter choice.
- Room schema details, DAO shapes, repository interfaces.
- Exact screen layouts within the visual identity of section 8, using the Flutter screens as reference.
- OAuth PKCE callback mechanism (custom scheme deep link, or an https App Link hosted on GitHub Pages if OpenRouter requires https). If neither works, ship v1 with paste-a-key only.
- Font size scaling details, animation durations.

## 13. Open questions

None. Resolved during review:
- App name: BananaCard (section 1).
- New-card cap: per deck per day (section 7.2).
