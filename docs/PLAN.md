# Cadence — Product & Implementation Plan

Cadence is a simple, private period-tracking companion app **for Android only**. This document covers:

1. [Product principles](#1-product-principles)
2. [Feature inventory](#2-feature-inventory): what a good period tracker offers, and which ones Cadence will build
3. [Recommended architecture](#3-recommended-architecture)
4. [Data model](#4-data-model)
5. [Prediction engine](#5-prediction-engine)
6. [Screens & UX](#6-screens--ux)
7. [Phased delivery plan](#7-phased-delivery-plan)
8. [Risks & mitigations](#8-risks--mitigations)
9. [Open decisions](#9-open-decisions)

---

## 1. Product principles

| Principle | What it means in practice |
|---|---|
| **Private by default** | Data lives on the device. No account, no server, no ads, no third-party analytics SDKs. Encrypted at rest. Nothing leaves the phone unless the user exports it. |
| **Fast to log** | Logging today takes 1–2 taps from the home screen. Detailed logging is optional. |
| **Honest predictions** | Show predictions as ranges with a confidence level, never as certainties. Learn from the user's own history. |
| **Not a medical device** | Cadence is not contraception and does not diagnose. It points out patterns worth raising with a clinician and says so clearly. |
| **Inclusive** | Neutral language ("people who menstruate", no assumed gender or relationship). Works for irregular cycles, hormonal birth control, PCOS, perimenopause, and more. |
| **The user owns the data** | Full export, full delete, and a backup/restore path, all one tap away. |

---

## 2. Feature inventory

Priority key: **P0** = MVP, must have · **P1** = core, expected by users · **P2** = valuable, comes later · **P3** = nice to have or future.

### 2.1 Cycle & period logging
| Feature | Priority | Notes |
|---|---|---|
| Mark period start / end | P0 | One-tap "Period started" and "Period ended" on the Today screen. |
| Edit past periods from the calendar | P0 | Tap any day to toggle a period day. Backfilling history matters for good predictions. |
| Flow intensity per day (spotting / light / medium / heavy) | P1 | Spotting does not count toward period length. |
| Bulk-add past periods during onboarding | P1 | "When did your last 3 periods start?" improves predictions from day one. |

### 2.2 Predictions & cycle awareness
| Feature | Priority | Notes |
|---|---|---|
| Next period date and countdown | P0 | "Period in 5 days" or "Period is 2 days late". |
| Current cycle day and phase | P0 | Menstrual, follicular, ovulatory or luteal, each with a short explanation. |
| Predicted period length | P0 | Used to pre-fill predicted days on the calendar. |
| Estimated ovulation day and fertile window | P1 | Shown only as general cycle awareness and clearly labelled as an estimate. Hidden when hormonal birth control is on. **No trying-to-conceive features** (see §2.9). |
| PMS window estimate | P2 | Learned from symptoms the user logs in the luteal phase. |
| Confidence ranges ("likely Mar 3–6") | P1 | Driven by how variable the user's cycles are. |
| Late or missed period detection | P1 | Prompts the user to log, take a test, or note a skipped cycle. |

### 2.3 Daily logging (symptoms & body)
| Feature | Priority | Notes |
|---|---|---|
| Symptoms with severity (cramps, headache, bloating, acne, breast tenderness, back pain, nausea, fatigue, cravings, etc.) | P1 | Built-in catalogue of about 20 items. |
| Mood (happy, calm, irritable, anxious, sad, sensitive, etc.) | P1 | |
| Energy, sleep quality, stress (1–5 scale) | P1 | |
| Pain level (0–10) | P1 | Useful for spotting possible endometriosis or dysmenorrhea patterns. |
| Sexual activity (protected / unprotected), libido | P1 | Optional. Can be hidden in settings. |
| Discharge | P2 | Dry, sticky, creamy, watery, egg-white. Logged as a general health symptom. |
| Medications and contraception taken | P2 | Pill check-off, painkillers, supplements. |
| Pregnancy test result | P2 | Useful when a period is late. |
| Weight | P3 | Opt-in only. |
| Free-text notes | P1 | |
| Custom tags and symptoms | P2 | |
| Hide categories the user doesn't care about | P1 | Keeps the log sheet short. |

### 2.4 Calendar & history
| Feature | Priority | Notes |
|---|---|---|
| Month calendar: logged period, predicted period, fertile window, ovulation, "has log" dot | P0 | Needs a colour-blind-safe palette plus non-colour cues such as patterns and icons. |
| Cycle history list (each cycle's start, length, period length) | P1 | |
| Day detail view | P1 | Everything logged on a given day. |

### 2.5 Insights & statistics
| Feature | Priority | Notes |
|---|---|---|
| Average cycle length, average period length, variation | P1 | |
| Cycle length chart over time | P1 | Bar chart per cycle, with a band for the normal range. |
| Symptom patterns by cycle day ("you often log headaches 2 days before your period") | P2 | |
| Health flags: cycles < 24 or > 38 days, variation > 9 days, period > 8 days, no period in 90 days | P1 | Based on FIGO normal ranges. Gentle wording plus "consider talking to a clinician". |

### 2.6 Reminders & notifications
| Feature | Priority | Notes |
|---|---|---|
| Upcoming period (N days before) | P1 | |
| Period is late | P1 | |
| Daily log reminder | P1 | |
| Pill / contraception reminder (daily pill, weekly patch, monthly ring) | P2 | |
| Fertile window start | P2 | Only in modes where it makes sense. |
| **Discreet notification text** | P1 | Default to neutral text, e.g. "Time to check in", so nothing sensitive shows on the lock screen. |

### 2.7 Privacy & security
| Feature | Priority | Notes |
|---|---|---|
| Local-only storage, no account | P0 | |
| **No `INTERNET` permission** | P0 | The app physically cannot send data anywhere. The manifest explicitly removes the permission, even if a library tries to add it, and CI checks the built APK. |
| Encrypted database at rest | P0 | Much cheaper to do from day one than to retrofit later. |
| App lock (PIN plus biometrics) | P1 | |
| Delete all data | P0 | |
| No third-party analytics or ads | P0 | |
| Plain-language privacy screen | P1 | Explains exactly what is stored and where. |
| Block screenshots and the recents preview (`FLAG_SECURE`) | P1 | |
| Keep data out of Android cloud backup | P0 | The app turns Android Auto Backup off. The encryption key can't be restored on another device anyway, so a cloud copy would be useless and would also be a leak. |
| Discreet app icon and name option | P3 | Done with launcher `activity-alias` entries. |

### 2.8 Data portability
| Feature | Priority | Notes |
|---|---|---|
| Export JSON (full backup) | P1 | |
| Import / restore from backup | P1 | Because data is local, losing a phone without a backup means losing everything. |
| Export CSV | P2 | |
| Clinician report (PDF): cycle table, averages, symptom summary, flags | P2 | |
| Encrypted backup file (password-protected) | P2 | |
| ~~Sync across devices~~ | **Not offered** | Device only, by decision. Moving to a new phone means export then import. |

### 2.9 Goals / life-stage modes
| Feature | Priority | Notes |
|---|---|---|
Cadence has **exactly two modes**.

| Feature | Priority | Notes |
|---|---|---|
| Track my cycle (default) | P0 | |
| Hormonal birth control mode | P1 | Turns off ovulation and fertile predictions, which don't apply, and adds pill / patch / ring reminders. |
| ~~Trying to conceive (TTC)~~ | **Not offered** | Out of scope by decision: no TTC mode, no BBT, no LH tests, no ovulation confirmation. |
| ~~Pregnancy / perimenopause modes~~ | **Not offered** | Out of scope by decision. |
| ~~Avoid pregnancy / contraception mode~~ | **Not offered** | Using an app as contraception requires regulatory clearance and clinical validation. We show a clear disclaimer instead. |

### 2.10 Experience & platform
| Feature | Priority | Notes |
|---|---|---|
| Onboarding (last period, typical lengths, goal) | P0 | Every question is skippable, with sensible defaults. |
| Light and dark theme (Material 3) using Cadence's own fixed palette (§6.1) | P0 | No Material You dynamic colour, so the cycle colours always mean the same thing. |
| Accessibility: TalkBack labels, font scaling up to 200%, 48dp touch targets, WCAG AA contrast | P1 | |
| Educational cards per phase | P2 | Short, factual, sourced. |
| Localization (dates, first day of week, translations) | P2 | |
| Home-screen widget (cycle day / next period) | P3 | A plain `AppWidgetProvider` (see Phase 7). |
| Health Connect sync (menstruation periods and flow) | P3 | Opt-in. Health Connect is on-device storage, so this stays within the device-only rule. |
| ~~Partner sharing~~ | **Not offered** | Would need a server. |

### 2.11 Explicit non-goals
- Social feeds, community forums, gamification, ads, or selling data.
- Contraceptive claims or diagnosis.
- Mandatory accounts or cloud storage.

---

## 3. Recommended architecture

**Recommendation: a native Android app in Kotlin with Jetpack Compose, local-first, no backend.**

Since the app only targets Android, a cross-platform framework adds nothing and costs us direct access to the platform. Going native gives first-class access to the Android Keystore, BiometricPrompt, notification scheduling, home-screen widgets and Health Connect, all without bridging layers.

| Concern | Choice | Why |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 | The current standard Android toolkit. |
| SDK levels | `minSdk 26` (Android 8.0), `targetSdk 36` (Android 16), `compileSdk 37` | API 26 covers about 97% of active devices and gives `java.time` without desugaring. compileSdk 37 is required by Compose 1.12 and only affects compilation; runtime behaviour follows targetSdk. |
| Build | Gradle 9 wrapper, AGP 9 (built-in Kotlin), Kotlin 2.4, KSP, version catalog, convention plugins in `build-logic/` | |
| App structure | Single activity, Navigation Compose, MVVM (`ViewModel` + `StateFlow`) | Simple, idiomatic, and easy to test. |
| Dependency injection | Hilt | |
| Async | Kotlin Coroutines + Flow | Room queries come back as `Flow`, so the UI updates live. |
| Storage | Room on SQLCipher (`net.zetetic:sqlcipher-android` through `SupportOpenHelperFactory`) | Typed queries, versioned migrations, encrypted at rest. |
| Encryption key | A random 256-bit database passphrase, wrapped by an AES-GCM key held in the **Android Keystore** and stored in DataStore | The Keystore key never leaves secure hardware. `EncryptedSharedPreferences` is deprecated, so we don't use it. |
| Settings | A single-row `settings` table in the encrypted Room database | Not plain DataStore: "uses hormonal birth control" is sensitive, so it gets the same encryption as everything else. |
| Notifications | `AlarmManager` (`setAndAllowWhileIdle`) for timed reminders, plus `WorkManager` for the daily rescheduling pass | Everything is local, with no push server. The app re-arms alarms on boot, on time or timezone changes, and after app updates. |
| App lock | `androidx.biometric` (`BiometricPrompt`, with device credential as the fallback) plus an optional app PIN | |
| Calendar UI | `com.kizitonwose.calendar:compose`, or a custom `LazyVerticalGrid` | |
| Charts | Vico (Compose charts) or a hand-drawn Compose `Canvas` | |
| PDF report | `android.graphics.pdf.PdfDocument`, shared through the share sheet | No third-party library needed. |
| Export / import | Storage Access Framework (`CreateDocument` / `OpenDocument`) | The user picks the location, so no storage permission is needed. |
| Dates | `java.time.LocalDate`, stored in Room as `epochDay: Long` | Avoids timezone and DST bugs. A "day" is a calendar date, not an instant. |
| Testing | JUnit 5 + kotlin.test for the domain module; Turbine for Flows; in-memory Room tests; Compose UI tests; Robolectric | |
| Quality | ktlint (with Compose rules), Android Lint (warnings are errors), Kover coverage gate on `:core:domain` | detekt was dropped: its stable release doesn't support Kotlin 2.4 yet. |
| CI | GitHub Actions: `./gradlew ktlintCheck lint test koverVerify assembleDebug`, an APK permission check, and instrumented tests on API 26 and API 36 emulators | |

### Module layout
```
:app                  # Android application: Activity, navigation, Hilt setup, theme
:core:domain          # PURE Kotlin/JVM module (no Android dependency) — fully unit-tested
    cycles/           #   derive periods & cycles from day logs
    predict/          #   next period, ovulation, fertile window, confidence
    flags/            #   health-pattern flags
    phases/           #   cycle-day → phase
:core:data            # Room entities, DAOs, migrations, SQLCipher, Keystore, DataStore, repositories
:core:ui              # design system: theme, tokens, shared composables
:core:testing         # fakes and test rules shared by feature and app tests
:feature:onboarding
:feature:today
:feature:calendar
:feature:insights
:feature:settings     # incl. lock, privacy explainer, reminders config
# added in the phase that needs them:
:feature:log          # Phase 2 — the daily log bottom sheet
:feature:reminders    # Phase 4 — alarm scheduling, receivers, notification channels
:feature:backup       # Phase 5 — JSON backup/restore, CSV export, clinician PDF
```
The key rule: **all cycle maths lives in `:core:domain`, a plain Kotlin JVM module.** Gradle itself stops it from importing Android code, so the logic stays pure, fast to test, and independent of the UI and storage.

---

## 4. Data model

The **source of truth is the per-day log.** Periods and cycles are *derived*, not stored, so editing any day keeps everything consistent automatically.

```kotlin
// Domain types (in :core:domain). Days are java.time.LocalDate; Room stores them as epochDay.

enum class Flow { SPOTTING, LIGHT, MEDIUM, HEAVY }
enum class Discharge { DRY, STICKY, CREAMY, WATERY, EGG_WHITE }
enum class SexActivity { NONE, PROTECTED, UNPROTECTED }
enum class TestResult { NEGATIVE, POSITIVE }

data class DayLog(
    val date: LocalDate,                // primary key
    val flow: Flow? = null,             // LIGHT/MEDIUM/HEAVY = period day
    val painLevel: Int? = null,         // 0–10
    val energy: Int? = null,            // 1–5
    val sleep: Int? = null,             // 1–5
    val stress: Int? = null,            // 1–5
    val discharge: Discharge? = null,
    val sex: SexActivity? = null,
    val weightKg: Double? = null,
    val pregnancyTest: TestResult? = null,
    val note: String? = null,
    val updatedAt: Instant,             // when the row last changed
)

enum class TagKind { SYMPTOM, MOOD, CUSTOM }

data class TagDefinition(               // symptoms, moods, custom tags
    val id: String,
    val kind: TagKind,
    val label: String,                  // string-resource key for built-ins (localizable)
    val builtIn: Boolean,
    val hidden: Boolean,
)

data class DayTag(                      // many-to-many: which tags on which day
    val date: LocalDate,
    val tagId: String,
    val severity: Int? = null,          // 1 mild, 2 moderate, 3 severe
)

enum class MedKind { PILL, PATCH, RING, OTHER }
enum class Recurrence { DAILY, WEEKLY, MONTHLY }

data class Medication(
    val id: String,
    val name: String,
    val kind: MedKind,
    val recurrence: Recurrence,
    val time: LocalTime,                // e.g. 21:00
    val active: Boolean,
)

data class MedicationDose(val medicationId: String, val date: LocalDate, val taken: Boolean)

enum class Goal { TRACK, BIRTH_CONTROL }

data class Settings(                    // single-row table in the encrypted database
    val goal: Goal = Goal.TRACK,
    val defaultCycleLength: Int = 28,
    val defaultPeriodLength: Int = 5,
    val lutealPhaseLength: Int = 14,    // user-adjustable in advanced settings
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY, // default from locale
    val hiddenCategories: Set<String> = emptySet(),
    val reminders: ReminderSettings = ReminderSettings(),
    val lockEnabled: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val onboardingComplete: Boolean = false,
)

// Optional: the user marks a cycle as unusual (illness, travel, postpartum…)
// so it's left out of predictions.
data class CycleAnnotation(val startDate: LocalDate, val excludeFromPredictions: Boolean, val reason: String?)
```

**As built (schema v3):** symptoms and moods live in a `day_tag` table (`date`, `tag_id`, `kind`, `severity`) keyed by ids from a built-in catalogue in `:core:domain` (`LogCatalog`). **Schema v7 (Phase 6):** custom tags are rows in `custom_tag`, logged in `day_tag` with kind `CUSTOM` and tag id `tag:<id>`. Medications are in `medication` and `medication_dose`. Birth control doses are in `contraceptive_dose`, one per day (`TAKEN` or `MISSED`). The pill, patch or ring schedule is a set of settings columns rather than the generic `Medication` sketched above, because it drives predictions. Discharge and pregnancy tests are not built yet.

**Deriving periods:** consecutive days with flow `LIGHT | MEDIUM | HEAVY` form a period. A gap of ≤ 1 day without flow is bridged. Spotting alone never *starts* a period.
**Deriving cycles:** a cycle runs from one period's start to the day before the next period's start. The current cycle is open-ended.

---

## 5. Prediction engine

All functions are pure: `(dayLogs, settings, annotations, today) → result`.

### 5.1 Next period
1. Take completed cycles, excluding user-excluded cycles and outliers (< 15 or > 60 days, which are likely logging gaps).
2. Use the **last 6** remaining cycles. Compute a **weighted average** that favours recent cycles (weights 1…6) → `predictedCycleLength`.
3. If there are fewer than 2 completed cycles, fall back to `settings.defaultCycleLength`.
4. `nextStart = currentPeriodStart + round(predictedCycleLength)`.
5. **Confidence range** = ± `max(1, round(stdDev))` days, capped at ± 7. Labels:
   - **High**: ≥ 3 cycles and SD ≤ 2
   - **Medium**: SD ≤ 4
   - **Low**: otherwise, or when running on defaults

### 5.2 Period length
Weighted average of the last 6 period lengths, falling back to `defaultPeriodLength`.

### 5.3 Ovulation & fertile window
- `ovulation ≈ nextStart − lutealPhaseLength` (default 14 days)
- `fertileWindow = [ovulation − 5, ovulation + 1]`
- Skipped entirely in birth control mode.
- This is an estimate for cycle awareness only. Ovulation is never confirmed from BBT or LH tests, because trying-to-conceive features are out of scope.

### 5.4 Phases (for the Today screen)
| Phase | Days |
|---|---|
| Menstrual | Period days |
| Follicular | After the period until the fertile window starts |
| Ovulatory | Fertile window |
| Luteal | After ovulation until the next period |

### 5.5 Late period
If today is past `nextStart + rangeHigh` with no flow logged, the state becomes **Late by N days**. Between the expected day and the end of the range it is **Due** ("Period due any day now"). Once the expected day has passed, predictions for later cycles shift off today rather than staying in the past, and the current cycle's fertile window and phase are no longer shown.

### 5.6 Health flags (FIGO-based, informational only)
- Average cycle < 24 or > 38 days
- Cycle-to-cycle variation (max − min over the last 6) > 9 days
- Any period lasting > 8 days
- No period for ≥ 90 days (when not on birth control)
- Pain level ≥ 7 on multiple period days across 3+ cycles

### 5.7 Testing strategy
Table-driven unit tests with fixture histories: perfectly regular 28-day cycles, irregular cycles, a single cycle, no data, outliers, an excluded cycle, DST boundaries, leap years, a late period, an ongoing period, and spotting-only days. These run as plain JVM tests (no emulator) in milliseconds. Target **100% branch coverage of `:core:domain`**, measured with Kover.

---

## 6. Screens & UX

```
┌─ Material 3 NavigationBar ──────────────────────────┐
│  Today   │   Calendar   │   Insights   │   Settings │
└─────────────────────────────────────────────────────┘
```

The app supports edge-to-edge display and predictive back, and adapts to tablets and foldables with a `NavigationRail` on wide screens.

1. **Onboarding**: welcome and privacy promise → last period start → typical cycle and period length ("I don't know" allowed) → goal → optional app lock → done.
2. **Today**: a large cycle ring (segments in the phase colours from §6.1) showing cycle day N of ~M with the current phase highlighted; the headline ("Period in 4 days", "Day 2 of period", "3 days late"); a primary action button that changes with context ("Period started" / "Period ended" / "Log today"); chips for what's already logged today; and a short phase card.
   **One-tap logging (as built in Phase 1):** "Period started" logs today plus the predicted remaining days, so a period is complete even if the user never comes back, and offers Undo. While a period is on, the button becomes "Period ended" (today or yesterday), which trims the extra days. For two days after a period's last logged day, "Still on your period? Log today" extends it.
3. **Log sheet** (Material 3 `ModalBottomSheet`, opened from Today or any calendar day): sections for Flow, Symptoms, Mood, Body (pain, energy, sleep, stress), Sex, Discharge, Tests, Meds, and Notes. Sections are collapsible, the user can hide them, and changes save automatically.
4. **Calendar**: month grid, swipe between months. Legend:
   - Solid fill = logged period
   - Dashed outline = predicted period
   - Tinted = fertile window
   - Dot = ovulation estimate
   - Small dot = has log

   Tapping a day opens the log sheet for that date. An "Edit period days" mode switches taps back to toggling period days, for quick backfilling.
5. **Insights**: summary cards (average cycle, average period, variation, confidence), a cycle length chart, the cycle history list, symptom patterns, and health flags.
6. **Settings**: goal, cycle defaults, reminders, categories shown, app lock, theme, export / import / delete all, privacy explainer, about and disclaimer.

### 6.1 Colour theme

The look is calm and warm, not stereotypically pink. Every role is a fixed Material 3 colour scheme defined in `:core:ui`, with a light and a dark variant. Unit tests check every text/background pair for WCAG AA contrast (4.5:1 for text, 3:1 for non-text UI).

| Role | Light | Dark | Used for |
|---|---|---|---|
| Primary: **Rosewood** | `#9A3B53` | `#FFB1C1` | Period days, primary actions, menstrual phase |
| Primary container | `#FFD9E0` | `#7C263D` | Period-day fills, selected chips |
| Secondary: **Dusty mauve** | `#6E5A5E` | `#DAC1C5` | Secondary actions, less prominent UI |
| Tertiary: **Deep teal** | `#2E6A67` | `#98D3CE` | Ovulation estimate, ovulatory phase |
| Tertiary container | `#B4EFEA` | `#10504E` | Fertile window tint |
| Surface / background | `#FFF8F7` | `#191113` | Warm off-white / warm near-black |
| On-surface text | `#22191B` | `#EFDFE1` | |
| Error | `#BA1A1A` | `#FFB4AB` | |

**Cycle phase colours** are four clearly different hues, used on the cycle ring and in Insights:

| Phase | Light | Dark |
|---|---|---|
| Menstrual: rosewood | `#9A3B53` | `#FFB1C1` |
| Follicular: amber | `#8A5100` | `#FFB870` |
| Ovulatory: teal | `#2E6A67` | `#98D3CE` |
| Luteal: plum | `#6D4C8C` | `#D7BAF5` |

Every state also has a non-colour cue (a fill pattern, icon or label) for colour-blind users. Predicted periods use a dashed outline, while logged ones use a solid fill.

### 6.2 Visual identity (as built after Phase 7)

The first seven phases left the app correct but plain: stock Material components on a tinted background. The redesign gives it one idea to hang on, **cadence as rhythm**, and applies it everywhere.

- **Typeface:** headings, the cycle day and the big numbers are set in **Lora**, a warm serif with Cyrillic, bundled as one variable font (SIL Open Font License; the licence ships in the APK and About credits it). Body text stays in the system font, so long text and 200% scaling behave as before.
- **Each phase has a colour, a soft tint and a glyph:** a drop (menstrual), a sprout (follicular), a sun (ovulatory) and a moon (luteal). Birth control uses a pill. Unit tests check text and the phase colour against every tint for WCAG AA.
- **Today leads with a hero card** in the current phase's tint, with faint concentric ripples behind the ring. It holds a phase chip, the ring, the headline and the prediction. The screen opens with a greeting and the date. The ring's marker glides to today's position.
- **Cards** are white with a hairline border and an icon header (log, fertile window, phase, birth control, medications, patterns, history). The fertile window has its own card with its "not contraception" caveat, instead of sharing the hero.
- **Settings** is grouped into cards: Your cycle, Daily logging, Medications, Reminders, Appearance, Privacy, Health Connect, Your data, About.
- **The buddy** (see §6.3) appears on the welcome screen, the lock screen and Today before anything is logged.
- **Calendar** sits in a card with a serif month title, and day cells are at least 48dp wide on a 360dp phone.

### 6.3 Material 3 Expressive refresh (as built)

A second pass to make the app cute and friendly while staying clean:

- **Material 3 Expressive:** Material 3 1.5 (`MaterialExpressiveTheme`, expressive spring motion, buttons whose corners squish when pressed, rounder shapes). 1.5 is a release candidate; the Compose BOM still pins 1.4, which has none of this. The version is set in `libs.versions.toml` and the experimental API is opted into in the Compose convention plugin.
- **Icons instead of text where they help:** Material Symbols Rounded throughout (Apache 2.0, credited in About): every symptom, mood, flow level, log section, settings row, Insights tile and main button has one, and the navigation bar fills the selected tab's icon. Icons sit beside their labels rather than replacing them, so nothing depends on guessing what an icon means. Flow is a drop that fills up from spotting to heavy.
- **Shapes with a meaning:** each phase has a Material shape (heart, flower, sun, cloud; a pill on birth control), worn by the buddy, and card icons sit on cookie-shaped badges. The calendar stays with plain circles (see §6.4).
- **The buddy:** a small mascot made of the phase's shape with a face. It sits beside the greeting on Today in the current phase's colours, stands in for the ring before anything is logged, greets new users as a family of four phase buddies, dozes on the lock screen, and keeps empty calendar days company. Sparkles decorate the hero.
- **Small, playful touches:** the buddy bounces in, breathes gently and blinks every few seconds (buddies on one screen blink out of step), hops with a happy face when tapped, and beams on Today once something is logged or today's pill, patch or ring is done. On the lock screen it dozes with little z's drifting up. Hero sparkles twinkle, a soft ripple spreads from today's marker on the ring, chip icons pop when picked, the chosen number on a scale pops, the calendar's month title slides the way the months move, and the selected day gives a springy pop.
- **Motion stays calm and testable:** loops use infinite transitions, which Compose's test clock treats as idle, so screens settle in tests. All decorative motion is skipped when the system's animations are off.
- **Brighter ring:** in light mode the cycle ring uses lively versions of the phase colours (rose, orange, teal, lavender). The phase colours used for text and icons are unchanged, so their contrast still holds; dark mode was already bright.

### 6.4 Calendar (as built)

- **Plain circles, one line at most:** logged period days are a solid pinkish peach (3:1 against the card and the page, with dark numbers at 5.7:1). Predicted days are soft pink with a dashed edge, fertile days light teal, and the ovulation estimate a solid deeper teal. The only lines are the predicted dash, a thin ring for today, and a ring for the selected day, and a day never shows two: the selection ring replaces today's ring and the predicted dash.
- **Tap a day to see it:** the day is selected and a card below the month shows its date, where it sits ("Period · Cycle day 3", "Predicted period · Cycle day 2 (estimate)", or the pack day on birth control), and everything logged as icon chips plus the note. From there: add or edit details (opens the log sheet), and, for days up to today, period actions.
- **Periods in the past:** "Period started" on any past day logs the expected number of days from there, the same way the button on Today does: the average of logged periods once there are some, otherwise the typical length from settings. Predictions, fertile windows and cycle days are recomputed from the new history, and a snackbar offers undo for exactly the days that were added. On a period day, "Period ended" clears the days after it and "Not a period day" removes that one day. "Edit period days" still toggles single days for quick backfilling.

---

## 7. Phased delivery plan

Each phase ends with a **working, shippable app**. Phases are sized for one developer at a few days to two weeks each.

### Phase 0: Foundation ✅
**Goal:** the skeleton everything else builds on.
- Gradle multi-module project (Kotlin DSL, version catalog `libs.versions.toml`) with the modules from §3; Hilt, Compose and Navigation wired up.
- ktlint (with Compose rules) and Android Lint configured; Kover coverage gate on `:core:domain`.
- `INTERNET` permission explicitly removed from the merged manifest, with a CI check on the APK.
- Material 3 theme in `:core:ui`: the §6.1 colour scheme (light and dark) with contrast tests, cycle phase colours, type scale, spacing tokens, plus base composables (primary button, chip, card, section header).
- Room on SQLCipher; passphrase generated on first launch and wrapped by an Android Keystore key; schema export on, with a migration test harness.
- `android:allowBackup="false"` and data-extraction rules that exclude the database and preferences.
- `:core:domain` scaffold with JUnit 5.
- GitHub Actions CI: `lint detekt test assembleDebug`, uploading the debug APK as an artifact.
- Bottom navigation with placeholder screens.

**Done when:** the debug APK installs and runs on an API 26 emulator and a current API emulator, CI is green, the database file is unreadable without the key, and a migration test passes.

### Phase 1: MVP (log periods, see predictions) ✅
**Goal:** the smallest app that is actually useful.
- Onboarding: last period date, typical lengths, goal (track / birth control).
- Today screen: cycle day, phase, next-period countdown, and a contextual "Period started / ended" button.
- Calendar: month view showing logged and predicted period days; tap a day to toggle a period day.
- Domain: derive periods and cycles; basic prediction (average plus defaults); phases.
- Settings: cycle defaults, goal, theme, **delete all data**, and the disclaimer.

**Done when:** a new user can onboard in under 60 seconds, log a period in 1 tap, see the next prediction, and edit history on the calendar. The domain has full unit tests.

### Phase 2: Daily logging ✅
**Goal:** a rich but fast daily check-in.
- Log sheet with flow intensity, symptoms with severity, mood, pain, energy, sleep, stress, sex, and notes.
- Built-in tag catalogue; hide or show categories.
- Calendar "has log" dots; day detail view.
- Chips on the Today screen summarising today's log.

**Done when:** a typical day can be logged in under 10 seconds, every field persists and is editable, and categories can be hidden.

### Phase 3: Smarter predictions & insights ✅
**Goal:** predictions people can trust, plus useful statistics.
- Weighted-average prediction, outlier filtering, confidence ranges and labels.
- Ovulation and fertile window on Today and Calendar (hidden in birth control mode).
- Late period state; "exclude this cycle" annotation.
- Insights tab: summary cards, cycle length chart, cycle history, and FIGO-based health flags.

**Done when:** prediction fixtures cover all edge cases in §5.7, Insights renders correctly with 0, 1 and many cycles, and flag wording has been reviewed.

### Phase 4: Reminders & notifications ✅
**Goal:** the app reaches out at the right moments without giving anything away.
- Local notifications for upcoming period, late period, and a daily log reminder at a chosen time.
- Discreet notification text on by default.
- Notifications rescheduled automatically whenever predictions change.
- Notification channels (Period, Daily check-in, Medication) so users can tune each one in system settings.
- `POST_NOTIFICATIONS` runtime permission (Android 13+), asked in context when the user turns on the first reminder, not at launch.
- Inexact alarms (`setAndAllowWhileIdle`) by default, so the restricted `SCHEDULE_EXACT_ALARM` permission isn't needed. A reminder arriving a few minutes late is fine.
- Alarms re-armed on `BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_SET` and `MY_PACKAGE_REPLACED`.
- Notification visibility set to `VISIBILITY_PRIVATE`, with a neutral public version for the lock screen.
- An in-app hint for OEMs with aggressive battery management (Xiaomi, Samsung, etc.), linking to the battery-optimisation setting.

**Done when:** reminders fire correctly after a reboot, a timezone change, an app update, Doze, and edits to period history, and no sensitive words appear on the lock screen by default.

**As built:** WorkManager wasn't needed. Each alarm re-arms the next one when it fires, a data/settings observer re-plans on every change, and a receiver re-arms after boot, app update, and time or timezone changes. Alarms are inexact (`setAndAllowWhileIdle`), so a reminder can arrive a few minutes late under Doze. Reminder planning is a pure function in `:core:domain` (`ReminderPlanner`).

### Phase 5: Privacy hardening & data portability ✅
**Goal:** the user fully controls and can move their data.
- App lock: `BiometricPrompt` (with device-credential fallback) or an app PIN; auto-lock after N minutes in the background, handled with a `ProcessLifecycleOwner` observer.
- `FLAG_SECURE` on the activity, so there are no screenshots, screen recordings or recents previews (on by default, can be turned off).
- JSON export and import through the Storage Access Framework (versioned format, validated on import, merge or replace).
- CSV export.
- Clinician PDF report (`PdfDocument`): date range, cycle table, averages, top symptoms, flags; shared through the share sheet.
- Optional password-encrypted backup file.
- Privacy explainer screen.

**Done when:** an export → delete all → import round trip restores identical data, the lock can't be bypassed via deep links, notifications or the recents screen, and the PDF renders correctly.

**As built:**
- **App lock** uses the phone's own fingerprint, face or screen lock (`BIOMETRIC_WEAK or DEVICE_CREDENTIAL`) instead of a separate app PIN. A second PIN would be one more secret to forget, with no way to reset it on a device-only app. Turning the lock on or off needs the same check. If the phone has no screen lock, Settings says to set one up first. The lock is off by default. It re-locks as soon as the app leaves the foreground, or after 1 or 5 minutes away. While locked, `MainActivity` composes only the lock screen, so a notification tap, or any other way into the activity, shows the lock screen first. `FLAG_SECURE` is set before the first frame and is on by default.
- **Backups** are versioned JSON (`cadence-backup`, version 1), saved and opened through the Storage Access Framework. Import is validated before anything changes, then runs in one transaction, as either *replace* or *merge* (backup days win). A password (8+ characters) encrypts the file with AES-256-GCM, using a key derived by PBKDF2-HMAC-SHA256 with 600,000 iterations. App lock and screenshot settings belong to the phone, so they are never exported or overwritten. Onboarding offers "Restore from a backup" for moving to a new phone.
- **CSV** export has one row per logged day (RFC 4180, symptoms as `id:severity`).
- **The clinician PDF** is drawn with `PdfDocument` on A4: summary, the last 12 cycles, the top 8 symptoms over 6 months, average pain on period days, and FIGO flags. It is saved through the Storage Access Framework instead of the share sheet, because sharing would need a `FileProvider` and a cache copy of health data, and the user can share the saved file from their file manager anyway.
- **Recovery:** if the database can't be opened (for example, the Keystore key was wiped), Cadence offers "Start over" instead of crashing. That erases the unreadable data and restarts the app, after which the user can restore a backup.
- **Permissions:** `androidx.biometric` adds `USE_BIOMETRIC` and `USE_FINGERPRINT` (Android 8.x). Both are normal permissions that only allow showing the system prompt, and they are now on the APK allowlist. There is still no network permission.
- **Tests:** an export → erase → import round trip gives an identical snapshot. The PDF is rendered page by page on a device with `PdfRenderer`, in the emulator CI job. An E2E test shows that while locked, nothing but the lock screen is composed.

### Phase 6: Birth control, medications & patterns ✅
**Goal:** first-class support for hormonal birth control, plus deeper insights.
- Birth control mode in full: contraceptive schedule (daily pill including placebo or break weeks, weekly patch, monthly ring), dose check-off on Today, missed-dose prompts, and reminders.
- Other medications (painkillers, supplements) with optional reminders.
- Symptom pattern insights ("you often log X around cycle day Y").
- Custom tags.

**Done when:** birth control mode changes predictions, the Today screen and reminders as specified, missed doses are surfaced, and switching between the two modes never loses data.

**As built:**
- **The schedule** is a repeating pack: active days, then 0 to 7 break days, with placebo pills as an option for pills. Settings offers pill, patch (changed weekly, with or without a patch-free week) and ring, plus "Something else" for an IUD, implant or injection. The user says which day of the pack today is, rather than picking a start date. The schedule is stored with the settings and kept when switching to cycle tracking, so switching back restores it. Doses live in their own table, so switching modes never touches them.
- **Predictions on a schedule:** the pack, not cycle history, says when a bleed is expected. The break is the expected withdrawal bleed (on the calendar too), and "late" doesn't apply. On continuous use (no break), Cadence doesn't predict bleeding and says so. Without a pill, patch or ring schedule (an IUD, implant or injection), birth control mode keeps history-based predictions as before, still without ovulation estimates.
- **Today** shows the pack as a ring (active days, then the break), "Break in N days", and today's task ("Take today's pill", "Take out your ring") with a one-tap Done. Missed doses are required doses (not placebo pills) from the last 7 days with nothing recorded, never before the schedule was set up. Each is asked about with "I did" or "I missed it", plus a pointer to the pack leaflet; Cadence gives no dosing advice of its own. The log sheet records the same dose for past days.
- **Reminders:** in birth control mode with a schedule, period and late reminders are replaced by a birth control reminder at a chosen time, on days with something to do (placebo pills included, for the habit). Daily medications can each have a reminder. Both use a new "Birth control and medications" channel, are discreet by default, and have a "Done" action that records the dose without opening the app. A dose already recorded isn't reminded.
- **Medications** are daily or as-needed, and are ticked off on Today and in the log. A stopped medication keeps its history; deleting one removes it and its days.
- **Custom tags** are created from the log sheet or Settings, and can be renamed or deleted (which removes them from every day). They're stored with symptoms and moods in `day_tag`.
- **Symptom patterns** (`SymptomPatterns`, pure and fully tested) look at the last 12 completed, non-excluded cycles that have anything logged. A symptom, mood or tag counts as a pattern when it shows up in at least 3 cycles and at least half of them: during the period, in the 5 days before it (with the typical day), or around the same cycle day (±1). Insights lists them as observations, not diagnoses.
- **Data:** schema v7 adds the schedule columns to settings, plus `contraceptive_dose`, `medication`, `medication_dose` and `custom_tag`, all by auto-migration with a test. Backups are format version 2 and still read version 1. Merging a backup matches tags and medications by name. The CSV adds tags, birth control and medication columns. The clinician report adds the birth control layout, missed doses in the last 90 days, medications, and patterns.
- **Tests:** end to end through the real app (pill schedule, check-offs, switching to tracking and back with nothing lost), domain logic at 100% coverage, repositories, backup round trip with all Phase 6 data, reminders, and UI.

### Phase 7: Polish & ecosystem ✅
**Goal:** release quality and nice-to-haves.
- Accessibility audit (TalkBack, Switch Access, font scaling up to 200%, contrast, Accessibility Scanner) and fixes.
- Localization: string resources for everything, per-app language (Android 13+), RTL layout check, locale-based first day of week and units.
- Educational phase cards.
- Home-screen widget, discreet by default.
- Health Connect read and write, opt-in (`MenstruationPeriodRecord`, `MenstruationFlowRecord`).
- Optional discreet launcher icon (`activity-alias`).
- Baseline Profiles for fast startup; R8 minification and shrinking.
- **Sideload release pipeline:**
  - A release keystore created once and stored offline in two places, with the CI signing secrets kept in GitHub encrypted secrets. Losing this key would mean users can never update in place.
  - Tag-triggered GitHub Actions workflow builds a signed, minified release APK and publishes it to GitHub Releases along with its SHA-256 checksum and the signing certificate fingerprint.
  - `versionCode` / `versionName` derived from the git tag.
  - Install guide in the README: allowing "Install unknown apps", verifying the checksum, and updating by installing a newer APK over the old one, which keeps the data.
  - The app has no internet access, so it can't check for updates. Settings → About shows the installed version and where to look for new ones.

**As built:**
- **Release pipeline:**
  - Pushing a tag such as `v1.2.3` runs `.github/workflows/release.yml`. It runs lint and the unit tests, then builds a minified, resource-shrunk APK signed with the release key from repository secrets. It checks the APK's permissions and publishes it to GitHub Releases with its SHA-256 checksum and the signing certificate's fingerprint.
  - The version comes from the tag (`versionCode` = 10203 for 1.2.3).
  - Creating and backing up the key is a one-time manual step, covered in [`docs/RELEASING.md`](RELEASING.md). The key is never created in CI.
  - Settings → About shows the version, the signing certificate fingerprint (to compare with the release notes), and where updates are published.
  - CI's emulator jobs install the minified release APK (signed with the debug key) and check that it reaches its first screen, which needs Hilt, Room, SQLCipher's native code and the Keystore all to survive R8.
- **Startup profiles:** `profileinstaller` (pulled in by Compose) installs the libraries' Baseline Profiles on sideloaded installs. An app-specific profile needs a Macrobenchmark run on a device and is left for later.
- **Accessibility:**
  - Accessibility Test Framework checks (touch targets, labels, text contrast, duplicate clickables) run on every main screen and every onboarding step, in light and dark themes, as instrumented tests on the CI emulators. Under Robolectric the checks silently pass (a deliberately broken button wasn't caught), so they can't run as unit tests.
  - The audit found rows built from a checkbox, radio button or switch that were only about 40dp tall. They now have a 48dp minimum, and calendar days are tappable across the whole cell.
  - At 200% text on a 360dp phone, Today, Calendar, Insights, Settings and onboarding were checked by eye and are covered by layout tests.
  - TalkBack, Switch Access and Accessibility Scanner still need a pass on a real phone. That's manual by nature and should happen before a 1.0 tag.
- **Localization:**
  - All text is in string resources.
  - The per-app language list (Android 13+) is generated from the translations that exist: English and Russian.
  - Debug builds include the pseudolocales en-XA and ar-XB.
  - Right-to-left layouts are covered by tests.
  - Weeks start on the locale's first day, and dates and times use the locale's formats.
  - **Russian** was added after Phase 7, covering every screen, notifications, the widget, Health Connect and the clinician report.
    - Plurals have all four Russian forms. Sentences are worded so that an inserted number, date or name stays grammatical, and dates that stand alone start with a capital.
    - Settings → Appearance → Language chooses between the phone's language, English and Русский on Android 13 and later. On Android 8–12 the app follows the phone's language, and the row opens the phone's language settings. (An in-app switch on those versions would need AppCompat or hand-rolled locale wrapping; not worth it for this.)
    - Tests cover the plural forms, the widget text, language switching, and the main screens in Russian on a 360dp phone at normal and 200% text. Lint fails the build on a missing translation or plural form.
    - The translation was not written by a native speaker. The health wording in particular should be reviewed by one before a 1.0 release.
- **Educational cards:** each phase card on Today, and the birth control card, has a "Learn more" with a few well-established facts and the NHS page they come from. There are no links, since the app has no internet access.
- **Widget:**
  - It's a plain `AppWidgetProvider` rather than Jetpack Glance. Glance runs on WorkManager, which would add wake-lock, foreground-service and other permissions for what is two lines of text.
  - It follows the "Discreet notifications and widget" setting. By default it shows only "Day N" and the date. With discreet mode off it shows "Period in 9 days" or "Break in 3 days". While app lock is on it shows nothing.
  - It refreshes when data changes and hourly, for the change of day.
- **Discreet icon:** two launcher activity-aliases. Settings → App icon switches between Cadence's icon and a plain notepad called "Planner". `MainActivity` itself is no longer exported.
- **Health Connect:**
  - Opt-in, from Settings. Only the menstruation read and write permissions are requested.
  - While permitted, Cadence mirrors its period days and flow there, using client record ids derived from the date, so edits and cleared days are mirrored too.
  - "Copy periods from other apps" fills only days with nothing logged in Cadence.
  - Spotting isn't shared, since it would need a third permission. Turning sharing off revokes the permissions.
  - Health Connect's required rationale screen explains that apps allowed there can read the data.
  - The mirroring logic is tested against an in-memory store. The real adapter is thin and still needs checking on a phone with Health Connect.

### Phase summary

| Phase | Theme | Key output |
|---|---|---|
| 0 | Foundation | Encrypted DB, design system, CI |
| 1 | MVP | Log periods → see predictions |
| 2 | Daily logging | Symptoms, mood, flow, notes |
| 3 | Insights | Confidence-ranged predictions, fertile window, stats, flags |
| 4 | Reminders | Discreet local notifications |
| 5 | Privacy & portability | App lock, export/import, clinician PDF |
| 6 | Birth control & patterns | Contraceptive schedules, medications, symptom patterns |
| 7 | Polish & release | a11y, i18n, home-screen widget, Health Connect, signed sideload APK on GitHub Releases |

---

## 8. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Users rely on fertile window estimates as contraception | A persistent disclaimer on the fertile window UI, no "safe days" wording, and no contraception mode. |
| Sensitive data exposure (legal requests, a lost device, a shoulder-surfer) | Local-only storage, SQLCipher, app lock, discreet notifications, `FLAG_SECURE`, Auto Backup off, and no server to subpoena. |
| Data loss because nothing is in the cloud (Auto Backup is off on purpose) | Prominent backup and export (Phase 5), plus a periodic "back up your data" nudge. |
| Reminders delayed or killed by OEM battery managers | Inexact alarms plus re-arming in WorkManager, and an in-app guide to battery-optimisation exemptions. |
| Keystore key lost (for example after some device resets), leaving the DB unreadable | Detect it on launch, explain it plainly, and offer to restore from an export file. This is another reason to nudge users to back up. |
| Timezone or DST bugs shifting days | `java.time.LocalDate` (stored as `epochDay`) everywhere, no `Instant` maths on stored days, and dedicated tests. |
| Poor predictions for irregular cycles | Confidence ranges, a Low-confidence label, the exclude-cycle feature, and never presenting a single date as certain. |
| Medical inaccuracy in flags or education | Base thresholds on FIGO/ACOG guidance, cite sources in-app, and use informational wording only. |
| Regulations (GDPR special-category data, FTC Health Breach Notification Rule) | Collect nothing, transmit nothing (no `INTERNET` permission), and publish a plain privacy statement. |
| Losing the release signing key | Back the keystore up offline in two places. Without it, users would have to uninstall (losing data unless exported) to move to a newly signed build. |
| Users installing a tampered APK from an unofficial source | Publish only on GitHub Releases, with a SHA-256 checksum and the signing certificate fingerprint documented in the README. |
| No automatic updates when sideloading | The About screen shows the installed version, the release page lists changes, and data survives in-place upgrades. |

---

## 9. Decisions

Confirmed by the product owner:

| # | Decision | Consequence |
|---|---|---|
| 1 | **Android only** | Native Kotlin + Jetpack Compose (§3). |
| 2 | **Device only** | No server, no account, no sync, no `INTERNET` permission. Moving phones means export then import. |
| 3 | **Two modes only: cycle tracking and hormonal birth control** | No trying-to-conceive features of any kind (no TTC mode, BBT, LH tests or ovulation confirmation). No pregnancy or perimenopause modes. |
| 4 | **A fixed Cadence colour theme** (§6.1) | No Material You dynamic colour. Cycle colours mean the same thing everywhere. |
| 5 | **Distributed as a sideloaded APK** | No Play Store, Play Services or Play-only APIs. A signed release APK goes out through GitHub Releases (Phase 7). |
| 6 | **minSdk 26 (Android 8.0)** | `java.time` without desugaring; Keystore AES-GCM is available. |
