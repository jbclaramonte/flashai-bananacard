# BananaCard Plan 1 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the two-module Gradle project with the full pure-Kotlin domain core (FSRS-6 port, study planning, deck stats, streak, validation, review recording), the Room/DataStore data layer behind domain interfaces, and an empty three-tab Compose shell. CI runs on every PR.

**Architecture:** `:domain` is a Kotlin/JVM module with no Android dependency. It holds models, the FSRS scheduler port, business rules and repository interfaces, and is tested with JUnit on the JVM. `:app` is the Android application: Room + DataStore implementations of the domain interfaces, Hilt wiring, and the Compose UI shell.

**Tech Stack:** Kotlin, Gradle Kotlin DSL + version catalog, Jetpack Compose (Material 3), Navigation Compose, Hilt, KSP, Room, DataStore Preferences, kotlinx.coroutines, kotlinx.serialization (test fixtures), JUnit 4, Robolectric, Turbine, core-splashscreen.

**Spec:** `docs/superpowers/specs/2026-11-15-android-native-port-design.md`

**Plan series:** this is plan 1 of 5.
1. Foundation (this plan).
2. Core screens: decks, cards, study, review, streak, study settings.
3. Android integration: reminders, share intent, shortcuts, Auto Backup, export/import, Flutter cleanup, language.
4. AI with OpenRouter.
5. Play release CI.

## Global Constraints

- Repo root: `/Users/jean-baptiste.claram/dev/perso/flashai-bananacard`. Reference Flutter app (read-only): `/Users/jean-baptiste.claram/dev/perso/flashai`.
- `applicationId = "com.mobeezz.flashlearnai"`. Kotlin package and Android `namespace`: `com.mobeezz.bananacard`.
- Flavor dimension `env`:
  - `dev`: `applicationIdSuffix = ".dev"`, launcher label "BananaCard Dev".
  - `prod`: launcher label "BananaCard".
- `minSdk = 26`. `compileSdk = 36`, `targetSdk = 36`.
- JVM target 17. Local JDK: `~/.sdkman/candidates/java/17.0.5-amzn` or 21. Android SDK: `~/Library/Android/sdk` (platform 36 installed).
- `versionCode` comes from the Gradle property `versionCode` (default `1`). `versionName = "1.0.0"`.
- Name: every user-facing string says "BananaCard", never "FlashAI".
- All UI strings live in `values/strings.xml` (English, default) and `values-fr/strings.xml` (French).
- No analytics, crash-reporting or ad SDK. No Firebase dependency.
- Public repo: never commit keystores, `google-services.json`, service-account JSON or API keys (`.gitignore` already covers them).
- `:domain` must not depend on any `android.*` or `androidx.*` artifact.
- Time in `:domain` comes from an injected `java.time.Clock` (instants) and a `() -> ZoneId` provider (local dates). Never call `Instant.now()` or `LocalDate.now()` without a clock.
- Dates for daily tracking are local `LocalDate` stored as ISO `YYYY-MM-DD`. Instants are stored as epoch milliseconds.
- Database file name: `bananacard.db`.

## Review Focus

1. **Local day boundary and DST.** A review at 23:59 and one at 00:01 local land on different days. A streak spanning a DST change is not broken. Tests: `StreakCalculatorTest.dstChangeDoesNotBreakStreak` and `ReviewRecorderTest.reviewJustAfterMidnightCountsForNewDay` (Tasks 6 and 7).
2. **Lowering the daily new-card cap below what was already started today** yields 0 new cards, never a negative or a crash. Test: `StudyPlannerTest.capLoweredBelowStartedGivesZeroNew` (Task 5).
3. **Empty deck.** Stats on a deck with 0 cards give progress 0, not NaN or a crash. Test: `DeckStatsTest.emptyDeckHasZeroProgress` (Task 5).
4. **Reviewing a card early or late.** A card reviewed before its due date, or months late, follows FSRS. Covered by fixture scenarios `early_review` and `late_review` (Tasks 2 and 3).
5. **Deleting a deck removes its cards and its daily status rows.** No orphan makes a deleted deck reappear in counts. Test: `RoomRepositoriesTest.deleteDeckCascades` (Task 8).

---

## File Structure

```
settings.gradle.kts                     modules + repositories
build.gradle.kts                        root plugins (apply false)
gradle/libs.versions.toml               single source of versions
gradle/wrapper/…                        Gradle wrapper
domain/build.gradle.kts                 kotlin("jvm")
domain/src/main/kotlin/com/mobeezz/bananacard/domain/
  fsrs/FsrsCard.kt                      CardState, Rating, FsrsCard
  fsrs/FsrsScheduler.kt                 port of Dart fsrs 2.0.1 Scheduler
  model/Deck.kt                         Deck, DeckType
  model/Flashcard.kt                    Flashcard, CardSchedule
  model/DeckDailyStatus.kt              DeckDailyStatus
  srs/ReviewScheduler.kt                app FSRS settings, review + preview
  srs/IntervalLabel.kt                  IntervalUnit, IntervalLabel, intervalLabel()
  validation/Validation.kt              ValidationResult, ValidationError, validators
  planning/StudyPlanner.kt              DeckPlan, DeckSection, planDeck(), sectionOf()
  planning/DeckStats.kt                 DeckStats, deckStats()
  streak/StreakCalculator.kt            currentStreak()
  streak/StreakService.kt               StreakSnapshot, refresh()
  repository/Repositories.kt            DeckRepository, FlashcardRepository, ActivityRepository, ReviewRepository, SettingsRepository
  review/ReviewRecorder.kt              record() use case
domain/src/test/kotlin/…                JVM tests (one file per class above)
domain/src/test/resources/fsrs/fixtures.json   generated by tools/fsrs-fixtures
tools/fsrs-fixtures/pubspec.yaml        Dart project depending on fsrs 2.0.1
tools/fsrs-fixtures/bin/generate.dart   writes fixtures.json
app/build.gradle.kts
app/schemas/                            Room exported schemas (committed)
app/src/main/AndroidManifest.xml
app/src/main/kotlin/com/mobeezz/bananacard/
  BananaCardApp.kt                      @HiltAndroidApp
  MainActivity.kt                       splash, edge-to-edge, setContent
  data/db/BananaCardDatabase.kt         @Database v1
  data/db/Entities.kt                   DeckEntity, FlashcardEntity, DailyActivityEntity, DeckDailyStatusEntity
  data/db/Daos.kt                       DeckDao, FlashcardDao, ActivityDao
  data/db/Mappers.kt                    entity <-> domain
  data/repository/RoomRepositories.kt   Room implementations of the domain interfaces
  data/settings/DataStoreSettingsRepository.kt
  di/DataModule.kt                      Hilt bindings, Clock, ZoneId
  ui/theme/Color.kt, Theme.kt, Type.kt
  ui/navigation/AppNavHost.kt           tabs + NavigationBar
  ui/placeholder/PlaceholderScreen.kt   temporary tab content
app/src/main/res/                       strings (en/fr), icons, splash theme
app/src/test/kotlin/…                   Robolectric tests
.github/workflows/ci.yml
```

---

### Task 1: Gradle scaffold with both modules and flavors

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`
- Create: `domain/build.gradle.kts`, `domain/src/test/kotlin/com/mobeezz/bananacard/domain/ModuleSmokeTest.kt`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/com/mobeezz/bananacard/BananaCardApp.kt`, `app/src/main/kotlin/com/mobeezz/bananacard/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`

**Interfaces:**
- Produces:
  - Module names `:domain` and `:app`.
  - Variants `devDebug`, `devRelease`, `prodDebug`, `prodRelease`.
  - Catalog aliases used by later tasks: `libs.room.*`, `libs.hilt.*`, `libs.datastore.preferences`, `libs.compose.*`, `libs.navigation.compose`, `libs.splashscreen`, `libs.robolectric`, `libs.turbine`, `libs.kotlinx.serialization.json`, `libs.kotlinx.coroutines.*`, `libs.junit`.

- [ ] **Step 1: Resolve versions.** Look up the latest **stable** versions on Maven Central / Google Maven, and write them into `gradle/libs.versions.toml`, for:
  - Android Gradle Plugin, Kotlin (with the matching KSP), Compose BOM, activity-compose, lifecycle, navigation-compose.
  - Hilt (and `hilt-navigation-compose`), Room, DataStore, core-splashscreen.
  - kotlinx-coroutines, kotlinx-serialization, Robolectric, Turbine, JUnit 4.

  Use the Compose compiler Gradle plugin `org.jetbrains.kotlin.plugin.compose`. Generate the wrapper with the latest stable Gradle supported by that AGP: `gradle wrapper --gradle-version <x>`.
- [ ] **Step 2: Write `domain/build.gradle.kts`.**
  - Plugins `kotlin("jvm")` and `kotlin("plugin.serialization")`. `jvmToolchain(17)`.
  - Dependencies: `kotlinx-coroutines-core`. Test dependencies: `junit`, `kotlinx-coroutines-test`, `kotlinx-serialization-json`, `turbine`.
- [ ] **Step 3: Write `app/build.gradle.kts`.**
  - Plugins: android application, kotlin android, compose compiler, ksp, hilt.
  - `namespace = "com.mobeezz.bananacard"` and `applicationId = "com.mobeezz.flashlearnai"`.
  - SDK levels and `versionCode`/`versionName` from Global Constraints.
  - Flavors `dev`/`prod` with `resValue("string", "app_name", …)`.
  - Room schema export through `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`.
  - `testOptions.unitTests.isIncludeAndroidResources = true`.
  - Dependencies: `implementation(project(":domain"))` plus the catalog libraries.
- [ ] **Step 4: Write the app entry points.**
  - `BananaCardApp` is annotated `@HiltAndroidApp`.
  - `MainActivity` is `@AndroidEntryPoint` and calls `setContent { Text("BananaCard") }`.
  - The manifest declares the application with `android:label="@string/app_name"` and the launcher activity.
  - `strings.xml` (en/fr) starts empty apart from a comment. `app_name` comes from the flavor `resValue`.
- [ ] **Step 5: Write the smoke test** `ModuleSmokeTest.domainModuleRuns`: `assertEquals(4, 2 + 2)`. It proves the JVM test task is wired.
- [ ] **Step 6: Verify.** Run `./gradlew :domain:test :app:assembleDevDebug :app:assembleProdDebug`. Expected: `BUILD SUCCESSFUL`. Then run `./gradlew :domain:dependencies --configuration runtimeClasspath | grep -i android`. Expected: no output.
- [ ] **Step 7: Commit**
```bash
git add -A && git commit -m "build: scaffold domain and app modules with dev/prod flavors"
```

---

### Task 2: FSRS reference fixtures from the Dart package

**Files:**
- Create: `tools/fsrs-fixtures/pubspec.yaml`, `tools/fsrs-fixtures/bin/generate.dart`, `tools/fsrs-fixtures/README.md`
- Create (generated, committed): `domain/src/test/resources/fsrs/fixtures.json`

**Interfaces:**
- Produces the JSON shape `{ "scenarios": [ { "name": String, "steps": [ { "rating": 1..4, "reviewAt": ISO-8601 UTC, "expected": { "state": 1..3, "step": Int|null, "stability": Double|null, "difficulty": Double|null, "due": ISO-8601 UTC, "lastReview": ISO-8601 UTC|null } } ] } ] }`. Every scenario starts from `Card(cardId: 1, state: State.learning, step: 0, due: <first reviewAt>)`. Task 3 consumes it.

- [ ] **Step 1: Write `pubspec.yaml`** with `name: fsrs_fixtures`, `environment: sdk: ^3.0.0`, `dependencies: fsrs: 2.0.1` (exact pin).
- [ ] **Step 2: Write `bin/generate.dart`.**
  - Scheduler: `Scheduler(enableFuzzing: false)` with the package defaults (desiredRetention 0.9, steps 1m/10m, relearning 10m, maximumInterval 36500).
  - For each scenario, chain `scheduler.reviewCard(card, rating, reviewDateTime: t)` and record the resulting card after each step.
  - Write pretty-printed JSON to the path given as the first CLI argument.
  - Scenarios. `t0 = 2026-01-05T08:00:00Z`. "+X" means relative to the previous review.

| name | ratings and review times |
|---|---|
| `good_chain` | Good@t0, Good@+10m, Good@+1d, Good@+3d, Good@+8d, Good@+21d |
| `again_then_good` | Again@t0, Again@+1m, Good@+1m, Good@+10m, Good@+1d |
| `easy_first` | Easy@t0, Easy@+15d, Good@+60d |
| `hard_path` | Hard@t0, Hard@+6m, Hard@+10m, Hard@+1d, Hard@+2d |
| `lapse_in_review` | Good@t0, Good@+10m, Good@+1d, Again@+4d, Good@+10m, Good@+1d |
| `early_review` | Good@t0, Good@+10m, Good@+1d, Good@+1h, Easy@+2h |
| `late_review` | Good@t0, Good@+10m, Good@+1d, Good@+200d, Hard@+400d |
| `max_interval` | Easy@t0, then Easy@+due each time, 12 times (review exactly at the previous due) |

- [ ] **Step 3: Generate.**
```bash
cd tools/fsrs-fixtures && ~/dev/perso/flutter-sdk/flutter/bin/dart pub get && \
~/dev/perso/flutter-sdk/flutter/bin/dart run bin/generate.dart ../../domain/src/test/resources/fsrs/fixtures.json
```
Expected: the file exists with 8 scenarios, and every `expected` has a non-null `due`.
- [ ] **Step 4: Write `README.md`.** Explain how to regenerate the fixtures, and that they pin the Kotlin port to Dart `fsrs` 2.0.1 (FSRS-6, MIT, © Open Spaced Repetition).
- [ ] **Step 5: Commit**
```bash
git add tools/fsrs-fixtures domain/src/test/resources/fsrs/fixtures.json && git commit -m "test: add FSRS reference fixtures generated from Dart fsrs 2.0.1"
```
Do not commit `.dart_tool/` or `pubspec.lock`; add `tools/fsrs-fixtures/.dart_tool/` to `.gitignore`.

---

### Task 3: FSRS-6 scheduler port

**Files:**
- Create: `domain/src/main/kotlin/com/mobeezz/bananacard/domain/fsrs/FsrsCard.kt`
- Create: `domain/src/main/kotlin/com/mobeezz/bananacard/domain/fsrs/FsrsScheduler.kt`
- Test: `domain/src/test/kotlin/com/mobeezz/bananacard/domain/fsrs/FsrsSchedulerFixtureTest.kt`, `FsrsSchedulerFuzzTest.kt`
- Reference: `~/.pub-cache/hosted/pub.dev/fsrs-2.0.1/lib/fsrs.dart` (class `Scheduler`, lines 404–1062; constants lines 15–111)

**Interfaces:**
- Produces:
```kotlin
enum class CardState(val value: Int) { LEARNING(1), REVIEW(2), RELEARNING(3) }
enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }
data class FsrsCard(
    val state: CardState,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val due: Instant,
    val lastReview: Instant?,
) { companion object { fun new(now: Instant): FsrsCard /* LEARNING, step 0, nulls, due = now */ } }
class FsrsScheduler(
    val parameters: List<Double> = DEFAULT_PARAMETERS,
    val desiredRetention: Double = 0.9,
    val learningSteps: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    val relearningSteps: List<Duration> = listOf(Duration.ofMinutes(10)),
    val maximumInterval: Int = 36500,
    val enableFuzzing: Boolean = true,
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
) {
    fun reviewCard(card: FsrsCard, rating: Rating, reviewAt: Instant): FsrsCard
    fun retrievability(card: FsrsCard, at: Instant): Double
    companion object { val DEFAULT_PARAMETERS: List<Double> /* the 21 values of fsrs.dart:15-37 */ }
}
```
`java.time.Instant` and `java.time.Duration`.

- [ ] **Step 1: Write the failing fixture test** `FsrsSchedulerFixtureTest`.
  - Load `/fsrs/fixtures.json` with kotlinx.serialization.
  - For each scenario, start from `FsrsCard.new(firstReviewAt)` and apply each step with `FsrsScheduler(enableFuzzing = false)`.
  - Assertions per step:
    - `state` and `step` are equal.
    - `stability` and `difficulty` are within `1e-9`, or both null.
    - `due` and `lastReview` are equal to the millisecond.
  - Name each case `"${scenario.name}[$index]"` in failure messages.
- [ ] **Step 2: Run** `./gradlew :domain:test --tests '*FsrsSchedulerFixtureTest*'`. Expected: FAIL (unresolved `FsrsScheduler`).
- [ ] **Step 3: Port `Scheduler` into `FsrsScheduler.kt`.**
  - Port line by line: parameter validation, `_decay = -parameters[20]`, `_factor`, `reviewCard` with its three state branches, the private stability/difficulty helpers, `_nextInterval`, and fuzzing with `fuzzRanges`.
  - Mirror Dart numeric semantics exactly:
    - `Duration.inDays` truncates toward zero: use `Duration.toDays()`.
    - Dart `num.round()` rounds half away from zero: use `Math.round` (or `roundToLong()`) on non-negative values, never `kotlin.math.round` (half-even).
    - `math.pow` maps to `Math.pow`.
  - Skip `ReviewLog`, `cardId` and `reviewDuration`: nothing needs them.
- [ ] **Step 4: Run the fixture test.** Expected: PASS for all 8 scenarios.
- [ ] **Step 5: Write the fuzz test** `FsrsSchedulerFuzzTest`.
  - `fuzzedIntervalStaysWithinBounds`: with `enableFuzzing = true` and `Random(42)`, reviewing the `good_chain` card at step 4 gives a due within the Dart bounds formula (`fsrs.dart:1011-1046`) around the unfuzzed interval.
  - `intervalsBelowTwoAndHalfDaysAreNotFuzzed`: the first review's due is identical with and without fuzzing.
- [ ] **Step 6: Run** `./gradlew :domain:test --tests '*FsrsScheduler*'`. Expected: PASS.
- [ ] **Step 7: Commit**
```bash
git add domain && git commit -m "feat(domain): port FSRS-6 scheduler from Dart fsrs 2.0.1"
```

---

### Task 4: Domain models, review scheduler, interval labels, validation

**Files:**
- Create: `domain/…/model/Deck.kt`, `model/Flashcard.kt`, `model/DeckDailyStatus.kt`
- Create: `domain/…/srs/ReviewScheduler.kt`, `srs/IntervalLabel.kt`
- Create: `domain/…/validation/Validation.kt`
- Test: `domain/…/srs/ReviewSchedulerTest.kt`, `srs/IntervalLabelTest.kt`, `validation/ValidationTest.kt`

**Interfaces:**
- Consumes: `FsrsScheduler`, `FsrsCard`, `CardState`, `Rating` (Task 3).
- Produces:
```kotlin
enum class DeckType { LANGUAGE, GENERAL }               // null on Deck = auto
data class Deck(
    val id: String, val name: String, val icon: String, val iconColor: Long,
    val type: DeckType?, val sourceLanguage: String?, val targetLanguage: String?,
    val description: String?, val isPaused: Boolean,
    val createdAt: Instant, val updatedAt: Instant,
) { companion object { val DEFAULT_ICON = "🇪🇸"; val ICON_PRESETS: List<String>; val PASTEL_COLORS: List<Long> } }
data class CardSchedule(
    val state: CardState, val step: Int?, val stability: Double, val difficulty: Double,
    val due: Instant, val lastReview: Instant?,
)
data class Flashcard(
    val id: String, val deckId: String, val question: String, val answer: String,
    val schedule: CardSchedule?,                         // null = new card
    val createdAt: Instant, val updatedAt: Instant,
) { val isNew: Boolean get() = schedule == null }
data class DeckDailyStatus(
    val deckId: String, val date: LocalDate,
    val completedOnce: Boolean, val reviewedOnce: Boolean, val newStartedCount: Int,
)
class ReviewScheduler(private val fsrs: FsrsScheduler, private val previewFsrs: FsrsScheduler) {
    fun review(card: Flashcard, rating: Rating, now: Instant): Flashcard   // updatedAt = now
    fun preview(card: Flashcard, now: Instant): Map<Rating, Duration>     // unfuzzed (previewFsrs)
    companion object { fun default(random: Random = Random.Default): ReviewScheduler }
}
enum class IntervalUnit { LESS_THAN_MINUTE, MINUTES, HOURS, DAYS, MONTHS, YEARS }
data class IntervalLabel(val value: Long, val unit: IntervalUnit)
fun intervalLabel(interval: Duration): IntervalLabel
enum class ValidationError { REQUIRED, TOO_SHORT, TOO_LONG }
sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>
    data class Invalid(val error: ValidationError) : ValidationResult<Nothing>
}
fun validateDeckName(raw: String): ValidationResult<String>          // trim, 2..50
fun normalizeDeckDescription(raw: String?): ValidationResult<String?> // trim, empty -> null, max 300
fun validateQuestion(raw: String): ValidationResult<String>          // trim, required, max 500
fun validateAnswer(raw: String): ValidationResult<String>            // trim, required, max 1000
```
- Constant values:
  - `ICON_PRESETS = listOf("🇪🇸","📚","🎨","🧬","🔍","🇺🇸","🎹","🚀","⚽","🐶","💼")`.
  - `PASTEL_COLORS = listOf(0xFFFFE5E5, 0xFFE5F0FF, 0xFFF0E5FF, 0xFFFFF4E5, 0xFFEBF5FF, 0xFFE6FAF4, 0xFFFFF0F0, 0xFFF5FFE5)`.
- `ReviewScheduler.default` builds both schedulers with desiredRetention 0.9, steps 1m/10m, relearning 10m, maximumInterval 36500. `fsrs` has fuzzing on; `previewFsrs` has fuzzing off.
- A new card maps to `FsrsCard.new(now)`.

- [ ] **Step 1: Write failing tests.**
  - `ReviewSchedulerTest`:
    - `newCardRatedGoodGetsSchedule`: `schedule` is non-null, `state == LEARNING`, `lastReview == now`.
    - `previewHasFourRatingsAndIsDeterministic`: two calls give equal maps; `AGAIN < HARD <= GOOD < EASY` for a review-state card.
    - `reviewKeepsQuestionAndAnswer`.
  - `IntervalLabelTest`. Thresholds from `../flashai/lib/services/srs_service.dart:141-157`:
    - `30s → (0, LESS_THAN_MINUTE)`, `59m → (59, MINUTES)`, `60m → (1, HOURS)`, `23h → (23, HOURS)`, `24h → (1, DAYS)`.
    - `29d → (29, DAYS)`, `30d → (1, MONTHS)`, `45d → (2, MONTHS)` (45/30 = 1.5 rounds half up), `364d → (12, MONTHS)`, `365d → (1, YEARS)`, `548d → (2, YEARS)`.
  - `ValidationTest`:
    - Deck names: `" a "` → `TOO_SHORT`, `""` → `REQUIRED`, 51 chars → `TOO_LONG`, `"  Spanish  "` → `Valid("Spanish")`.
    - Description: `"   "` → `Valid(null)`, 301 chars → `TOO_LONG`.
    - Question: 501 chars → `TOO_LONG`. Answer: `" "` → `REQUIRED`, 1001 chars → `TOO_LONG`.
- [ ] **Step 2: Run** `./gradlew :domain:test`. Expected: FAIL (unresolved symbols).
- [ ] **Step 3: Implement** the models and functions with the signatures above. For `intervalLabel`, a value of 0 is used for `LESS_THAN_MINUTE`, and months/years use `roundToLong()` on `days / 30.0` and `days / 365.0`.
- [ ] **Step 4: Run** `./gradlew :domain:test`. Expected: PASS.
- [ ] **Step 5: Commit**
```bash
git add domain && git commit -m "feat(domain): add models, review scheduler, interval labels, validation"
```

---

### Task 5: Study planner and deck stats

**Files:**
- Create: `domain/…/planning/StudyPlanner.kt`, `planning/DeckStats.kt`
- Test: `domain/…/planning/StudyPlannerTest.kt`, `planning/DeckStatsTest.kt`

**Interfaces:**
- Consumes: `Deck`, `Flashcard`, `CardSchedule`, `CardState` (Tasks 3–4).
- Produces:
```kotlin
data class DeckPlan(val dueCards: List<Flashcard>, val newCards: List<Flashcard>) {
    val plannedDue: Int get() = dueCards.size
    val plannedNew: Int get() = newCards.size
}
enum class DeckSection { TO_REVIEW, NEW_CARDS, NONE, PAUSED }
object StudyPlanner {
    val DUE_TOLERANCE: Duration = Duration.ofMinutes(1)
    val ALLOWED_CAPS = listOf(0, 5, 10); const val DEFAULT_CAP = 5
    fun isDue(card: Flashcard, now: Instant): Boolean          // non-new && due <= now + 1 min
    fun planDeck(deck: Deck, cards: List<Flashcard>, newCap: Int, newStartedToday: Int, now: Instant): DeckPlan
    fun sectionOf(deck: Deck, plan: DeckPlan): DeckSection
}
data class DeckStats(val total: Int, val progress: Double, val cardsToReview: Int, val learningCount: Int)
fun deckStats(cards: List<Flashcard>, now: Instant): DeckStats
```
- `planDeck` rules (spec 7.2):
  - Paused deck: empty plan.
  - Due cards = cards where `isDue`, sorted by `schedule.due` ascending, no limit.
  - New cards = new cards in the input order (callers pass cards in insertion order), limited to `max(0, newCap - newStartedToday)`.
- `sectionOf` rules: paused → `PAUSED`; `plannedDue > 0` → `TO_REVIEW`; `plannedNew > 0` → `NEW_CARDS`; otherwise `NONE`.
- `deckStats` rules:
  - `progress` = review-state cards / total, or 0.0 when total is 0.
  - `cardsToReview` = due non-new cards + new cards.
  - `learningCount` = state LEARNING or RELEARNING, new cards excluded.

- [ ] **Step 1: Write failing tests.**
  - `StudyPlannerTest`:
    - `pausedDeckPlansNothing`.
    - `dueCardsSortedByDueAscending`.
    - `cardDueWithinOneMinuteIsDue`: due = now + 59 s is due; due = now + 61 s is not.
    - `newCardsKeepInputOrderAndRespectRemainingCap`: cap 5, started 2, 10 new cards → the first 3.
    - `capLoweredBelowStartedGivesZeroNew`: cap 5, started 7 → 0.
    - `capZeroGivesNoNew`.
    - `sectionToReviewWinsOverNew`.
    - `startedDeckWithOnlyNewLeftIsNewCardsSection`: some review cards not due, plus new cards → `NEW_CARDS`.
    - `nothingLeftIsNone`.
  - `DeckStatsTest`:
    - `emptyDeckHasZeroProgress`: `DeckStats(0, 0.0, 0, 0)`.
    - `learningCountExcludesNewCards`.
    - `progressCountsReviewStateOnly`: 2 review out of 4 → 0.5.
- [ ] **Step 2: Run** `./gradlew :domain:test --tests '*planning*'`. Expected: FAIL.
- [ ] **Step 3: Implement** per the rules above.
- [ ] **Step 4: Run** `./gradlew :domain:test`. Expected: PASS.
- [ ] **Step 5: Commit**
```bash
git add domain && git commit -m "feat(domain): add study planner with daily new-card cap and deck stats"
```

---

### Task 6: Repository interfaces and streak

**Files:**
- Create: `domain/…/repository/Repositories.kt`
- Create: `domain/…/streak/StreakCalculator.kt`, `streak/StreakService.kt`
- Create: `domain/src/test/kotlin/com/mobeezz/bananacard/domain/fakes/Fakes.kt` (in-memory fakes reused by Task 7)
- Test: `domain/…/streak/StreakCalculatorTest.kt`, `streak/StreakServiceTest.kt`

**Interfaces:**
- Consumes: models (Task 4).
- Produces:
```kotlin
interface DeckRepository {
    fun observeDecks(): Flow<List<Deck>>                      // createdAt DESC
    fun observeDeck(id: String): Flow<Deck?>
    suspend fun insert(deck: Deck)
    suspend fun update(deck: Deck)
    suspend fun delete(id: String)                            // cascades cards + daily status
}
interface FlashcardRepository {
    fun observeCards(deckId: String): Flow<List<Flashcard>>  // insertion order
    fun observeAllCards(): Flow<List<Flashcard>>             // insertion order
    suspend fun get(id: String): Flashcard?
    suspend fun insert(cards: List<Flashcard>)               // one transaction
    suspend fun update(card: Flashcard)
    suspend fun delete(ids: List<String>)                    // one transaction
}
interface ActivityRepository {
    fun observeActiveDays(from: LocalDate): Flow<Set<LocalDate>>          // cards_reviewed > 0
    fun observeCardsReviewed(date: LocalDate): Flow<Int>
    fun observeDailyStatuses(date: LocalDate): Flow<Map<String, DeckDailyStatus>>  // key = deckId
    suspend fun activeDaysSince(from: LocalDate): Set<LocalDate>
    suspend fun markCompleted(deckId: String, date: LocalDate)
}
interface ReviewRepository {
    /** One transaction: update card, daily_activity +1, reviewed_once = true, new_started_count += 1 if wasNew. */
    suspend fun saveReview(card: Flashcard, date: LocalDate, wasNew: Boolean)
}
interface SettingsRepository {
    val newCardsPerDay: Flow<Int>          // default StudyPlanner.DEFAULT_CAP
    suspend fun setNewCardsPerDay(value: Int)  // require(value in StudyPlanner.ALLOWED_CAPS)
    val bestStreak: Flow<Int>              // default 0
    suspend fun setBestStreak(value: Int)
}
fun currentStreak(activeDays: Set<LocalDate>, today: LocalDate): Int
data class StreakSnapshot(val current: Int, val best: Int)
class StreakService(
    private val activity: ActivityRepository, private val settings: SettingsRepository,
    private val clock: Clock, private val zone: () -> ZoneId,
) {
    /** Computes current from activity, persists best = max(stored, current), returns both. */
    suspend fun refresh(): StreakSnapshot
}
```
- `currentStreak` rule: count consecutive days with activity going backwards, starting from `today` if it is active, otherwise from `today - 1`. Return 0 if neither is active.
- `StreakService.refresh` reads `activeDaysSince(today.minusDays(400))`; the 400-day window is my call, since the longest meaningful milestone was 365.

- [ ] **Step 1: Write failing tests.**
  - `StreakCalculatorTest`:
    - `noActivityIsZero`.
    - `todayOnlyIsOne`.
    - `yesterdayOnlyIsOne`: today not yet studied, so the streak is still alive.
    - `gapBreaksStreak`: active on days −3, −2 and today → 1.
    - `fiveConsecutiveEndingYesterdayIsFive`.
    - `dstChangeDoesNotBreakStreak`: `Europe/Paris`, active on 2026-03-28, 03-29 and 03-30, today = 2026-03-30 → 3.
  - `StreakServiceTest` (fakes from `Fakes.kt`):
    - `bestIsRaisedWhenCurrentExceedsIt`: stored best 2, current 3 → best 3 persisted.
    - `bestIsKeptWhenCurrentIsLower`: stored 10, current 1 → `StreakSnapshot(1, 10)`, no write.
- [ ] **Step 2: Run** `./gradlew :domain:test --tests '*streak*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the interfaces and `currentStreak`/`StreakService`. The fakes in `Fakes.kt` are `MutableStateFlow`-backed in-memory implementations of all five interfaces. `FakeReviewRepository` applies the same three effects as the real one.
- [ ] **Step 4: Run** `./gradlew :domain:test`. Expected: PASS.
- [ ] **Step 5: Commit**
```bash
git add domain && git commit -m "feat(domain): add repository interfaces and streak computation"
```

---

### Task 7: Review recorder use case

**Files:**
- Create: `domain/…/review/ReviewRecorder.kt`
- Test: `domain/…/review/ReviewRecorderTest.kt`

**Interfaces:**
- Consumes: `ReviewScheduler` (Task 4), `ReviewRepository`, `ActivityRepository` and fakes (Task 6).
- Produces:
```kotlin
class ReviewRecorder(
    private val scheduler: ReviewScheduler, private val reviews: ReviewRepository,
    private val clock: Clock, private val zone: () -> ZoneId,
) {
    /** Applies FSRS, persists card + daily tracking in one transaction, returns the updated card. */
    suspend fun record(card: Flashcard, rating: Rating): Flashcard
}
```
Plan 3 adds reminder re-sync as a separate listener. Do not add hooks for it here.

- [ ] **Step 1: Write failing tests** `ReviewRecorderTest`, with a fixed `Clock` and zone `Europe/Paris`.
  - `recordsActivityAndReviewedOnce`: after one review, `observeCardsReviewed(today)` emits 1 and today's status has `reviewedOnce = true`.
  - `newCardIncrementsNewStarted`: rating a new card gives `newStartedCount = 1`. Rating the returned (now scheduled) card again keeps it at 1.
  - `reviewCardDoesNotIncrementNewStarted`.
  - `reviewJustAfterMidnightCountsForNewDay`:
    - A review at 2026-01-05T22:59:00Z (23:59 Paris) is recorded on `2026-01-05`.
    - A review at 2026-01-05T23:01:00Z (00:01 Paris) is recorded on `2026-01-06`.
- [ ] **Step 2: Run** `./gradlew :domain:test --tests '*ReviewRecorder*'`. Expected: FAIL.
- [ ] **Step 3: Implement** `record` as `now = clock.instant()`, `date = LocalDate.ofInstant(now, zone())`, then `scheduler.review` and `reviews.saveReview(updated, date, card.isNew)`.
- [ ] **Step 4: Run** `./gradlew :domain:test`. Expected: PASS.
- [ ] **Step 5: Commit**
```bash
git add domain && git commit -m "feat(domain): add review recorder use case"
```

---

### Task 8: Room database and repository implementations

**Files:**
- Create: `app/src/main/kotlin/com/mobeezz/bananacard/data/db/Entities.kt`, `Daos.kt`, `BananaCardDatabase.kt`, `Mappers.kt`
- Create: `app/src/main/kotlin/com/mobeezz/bananacard/data/repository/RoomRepositories.kt`
- Create (generated): `app/schemas/com.mobeezz.bananacard.data.db.BananaCardDatabase/1.json`
- Test: `app/src/test/kotlin/com/mobeezz/bananacard/data/RoomRepositoriesTest.kt`

**Interfaces:**
- Consumes: `DeckRepository`, `FlashcardRepository`, `ActivityRepository`, `ReviewRepository` (Task 6); models (Task 4).
- Produces: `class RoomDeckRepository(db) : DeckRepository`, `RoomFlashcardRepository`, `RoomActivityRepository`, `RoomReviewRepository`, and `BananaCardDatabase.create(context: Context): BananaCardDatabase` (file `bananacard.db`).
- Schema, version 1, `exportSchema = true`, `PRAGMA foreign_keys` on (Room enables FK enforcement):

| table | columns |
|---|---|
| `decks` | `id TEXT PK`, `name TEXT`, `icon TEXT`, `icon_color INTEGER`, `type TEXT NULL` (`language`/`general`), `source_language TEXT NULL`, `target_language TEXT NULL`, `description TEXT NULL`, `is_paused INTEGER`, `created_at INTEGER`, `updated_at INTEGER` |
| `flashcards` | `id TEXT PK`, `deck_id TEXT` (FK → decks.id ON DELETE CASCADE, indexed), `question TEXT`, `answer TEXT`, `state TEXT NULL` (`learning`/`review`/`relearning`), `step INTEGER NULL`, `stability REAL NULL`, `difficulty REAL NULL`, `due INTEGER NULL`, `last_review INTEGER NULL`, `created_at INTEGER`, `updated_at INTEGER` |
| `daily_activity` | `date TEXT PK` (YYYY-MM-DD), `cards_reviewed INTEGER` |
| `deck_daily_status` | `deck_id TEXT` + `date TEXT` (composite PK, FK deck_id → decks.id ON DELETE CASCADE), `completed_once INTEGER`, `reviewed_once INTEGER`, `new_started_count INTEGER` |

- Behavior:
  - A new card has all six schedule columns null.
  - Insertion order: `ORDER BY created_at ASC, rowid ASC`. Decks: `ORDER BY created_at DESC`.
  - `saveReview` and `markCompleted` use `INSERT … ON CONFLICT DO UPDATE` upserts inside `@Transaction`, so flags only ever go from 0 to 1.
  - `observeActiveDays` emits from a `SELECT date FROM daily_activity WHERE date >= :from AND cards_reviewed > 0` query.

- [ ] **Step 1: Write failing tests** `RoomRepositoriesTest`.
  - Setup: Robolectric, `Room.inMemoryDatabaseBuilder`, `runTest`, Turbine.
  - `deckRoundTripKeepsAllFields`, including null type/languages and `isPaused = true`.
  - `decksOrderedByCreatedAtDesc`.
  - `cardsKeepInsertionOrderWithEqualCreatedAt`: insert 3 cards with the same `createdAt` in one call; `observeCards` returns them in that order.
  - `newCardRoundTripsWithNullSchedule`. `scheduledCardRoundTrips`: all `CardSchedule` fields equal.
  - `saveReviewUpdatesCardActivityAndStatus`: two reviews (one new, one not) on the same day give cards_reviewed 2, reviewedOnce true, newStartedCount 1.
  - `markCompletedLatches`.
  - `deleteDeckCascades`: after delete, `observeCards(deckId)` emits empty and `observeDailyStatuses(date)` has no key for the deck.
  - `activeDaysExcludesZeroAndOlderDates`.
- [ ] **Step 2: Run** `./gradlew :app:testDevDebugUnitTest --tests '*RoomRepositoriesTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** entities, DAOs (Flow queries plus suspend writes), mappers (`Mappers.kt`, enum ↔ lowercase string) and the four repositories. A deck's `type` and a card's `state` serialize to lowercase strings.
- [ ] **Step 4: Run** the same command. Expected: PASS. Confirm `app/schemas/…/1.json` was generated.
- [ ] **Step 5: Commit**
```bash
git add app && git commit -m "feat(data): add Room database and repository implementations"
```

---

### Task 9: DataStore settings and Hilt wiring

**Files:**
- Create: `app/src/main/kotlin/com/mobeezz/bananacard/data/settings/DataStoreSettingsRepository.kt`
- Create: `app/src/main/kotlin/com/mobeezz/bananacard/di/DataModule.kt`
- Test: `app/src/test/kotlin/com/mobeezz/bananacard/data/DataStoreSettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository`, `StudyPlanner.DEFAULT_CAP`/`ALLOWED_CAPS` (Tasks 5–6); Room repositories (Task 8); `ReviewScheduler.default()`, `ReviewRecorder`, `StreakService` (Tasks 4–7).
- Produces:
  - `class DataStoreSettingsRepository(dataStore: DataStore<Preferences>) : SettingsRepository`. Keys `new_cards_per_day` (Int) and `best_streak` (Int). DataStore file name `settings`.
  - Hilt `@Singleton` bindings for: `Clock` (`Clock.systemUTC()`; only `instant()` is used), `() -> ZoneId` (`{ ZoneId.systemDefault() }`, re-read on every call so a time-zone change is picked up), `BananaCardDatabase`, the five repository interfaces, `ReviewScheduler`, `ReviewRecorder`, `StreakService`.

- [ ] **Step 1: Write failing tests** `DataStoreSettingsRepositoryTest`, using `PreferenceDataStoreFactory.create` on a temp file inside `runTest`.
  - `defaultsAreFiveAndZero`.
  - `newCardsPerDayPersists`: set 10, read 10.
  - `invalidCapIsRejected`: `setNewCardsPerDay(7)` throws `IllegalArgumentException`.
  - `bestStreakPersists`.
- [ ] **Step 2: Run** `./gradlew :app:testDevDebugUnitTest --tests '*DataStoreSettings*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the repository and `DataModule` (`@Module @InstallIn(SingletonComponent::class)`).
- [ ] **Step 4: Verify.** Run `./gradlew :app:testDevDebugUnitTest :app:assembleDevDebug`. Expected: PASS and `BUILD SUCCESSFUL`. The Hilt graph compiles.
- [ ] **Step 5: Commit**
```bash
git add app && git commit -m "feat(data): add DataStore settings and Hilt wiring"
```

---

### Task 10: Theme, three-tab shell, splash and icon

**Files:**
- Create: `app/src/main/kotlin/com/mobeezz/bananacard/ui/theme/Color.kt`, `Theme.kt`, `Type.kt`
- Create: `app/src/main/kotlin/com/mobeezz/bananacard/ui/navigation/AppNavHost.kt`, `ui/placeholder/PlaceholderScreen.kt`
- Modify: `MainActivity.kt`, `AndroidManifest.xml`, `res/values/strings.xml`, `res/values-fr/strings.xml`
- Create: `res/values/themes.xml` (splash theme `Theme.BananaCard.Starting`), `res/values-night/themes.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/values/ic_launcher_background.xml`
- Copy: `../flashai/android/app/src/main/res/{mipmap-*,drawable-*}/ic_launcher*.png` into the same folders
- Test: `app/src/test/kotlin/com/mobeezz/bananacard/ui/AppNavHostTest.kt`

**Interfaces:**
- Produces:
```kotlin
@Composable fun BananaCardTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)
enum class TopLevelTab(val route: String, @StringRes val label: Int) { LIBRARY("library", …), REVIEW("review", …), PROFILE("profile", …) }
@Composable fun AppNavHost(navController: NavHostController = rememberNavController())
```
Plan 2 replaces each placeholder destination with its real screen.
- Colors (spec section 8, light): primary `#1CB0F6`, secondary `#58CC02`, streak orange `#FF9600`, background `#F7F7F7`, surface `#FFFFFF`, onSurface `#DE000000`.
- Dark (my call, same hues): background `#131F24`, surface `#1F2B31`, primary `#1CB0F6`, secondary `#58CC02`, onSurface `#F2FFFFFF`.
- No dynamic color.
- Typography: `headlineMedium` 32 sp w900, `titleLarge` 24 sp bold, `bodyMedium` 14 sp, `labelMedium` 14 sp w600.
- Shapes: `medium` = 16 dp rounded corners.
- Strings (en / fr): `tab_library` LIBRARY / BIBLIOTHÈQUE, `tab_review` REVIEW / RÉVISION, `tab_profile` PROFILE / PROFIL.
- Adaptive icon: background `#FFFFFF`; foreground `@drawable/ic_launcher_foreground` with a 16 % inset, as in Flutter; monochrome = the same foreground drawable.
- Splash: `installSplashScreen()` before `super.onCreate`, with `windowSplashScreenBackground` `#FFFFFF` (night `#131F24`) and the launcher foreground as icon.
- Window: `enableEdgeToEdge()`, and a `Scaffold` that consumes insets.

- [ ] **Step 1: Write the failing test** `AppNavHostTest` (Robolectric + `createComposeRule`).
  - `startsOnLibraryTab`: the placeholder text for Library is displayed.
  - `tappingReviewShowsReviewPlaceholder`.
  - `backFromReviewReturnsToLibrary`: the start destination stays on the back stack (`popUpTo(startDestination) { saveState = true }`, `launchSingleTop`, `restoreState`).
- [ ] **Step 2: Run** `./gradlew :app:testDevDebugUnitTest --tests '*AppNavHostTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the theme, `AppNavHost` with a Material 3 `NavigationBar`, `PlaceholderScreen(title)`, the `MainActivity` wiring, the resources and the icons.
- [ ] **Step 4: Verify.**
  - Run `./gradlew :app:testDevDebugUnitTest :app:assembleDevDebug`. Expected: PASS.
  - Install on a device or emulator with `./gradlew :app:installDevDebug`.
  - Check: the launcher shows "BananaCard Dev" with the icon, the splash appears, the content draws edge-to-edge, the three tabs switch, and a dark system theme gives dark colors.
- [ ] **Step 5: Commit**
```bash
git add app && git commit -m "feat(ui): add theme, three-tab shell, splash and launcher icon"
```

---

### Task 11: PR continuous integration

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `README.md` (project purpose, module layout, `./gradlew` commands, link to spec and plans)

**Interfaces:**
- Produces: a required check `ci / build` (job id `build`) that plan 5 keeps.

- [ ] **Step 1: Write `ci.yml`.**
  - Triggers: `pull_request` and `push` on `main`.
  - Runs on `ubuntu-latest`.
  - Steps: `actions/checkout@v4`, `actions/setup-java@v4` (temurin 17), `gradle/actions/setup-gradle@v4`, then `./gradlew :domain:test :app:testDevDebugUnitTest :app:lintDevDebug :app:assembleDevDebug`.
  - `permissions: contents: read`. No secrets.
- [ ] **Step 2: Verify locally.** Run `./gradlew :domain:test :app:testDevDebugUnitTest :app:lintDevDebug :app:assembleDevDebug`. Expected: `BUILD SUCCESSFUL`, no lint errors (warnings allowed).
- [ ] **Step 3: Commit and push**
```bash
git add .github README.md && git commit -m "ci: run tests, lint and dev build on pull requests" && git push
```
- [ ] **Step 4: Verify on GitHub.** Run `gh run list --limit 1`. Expected: the `ci` run on `main` concludes `success`.
