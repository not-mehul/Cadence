# Cadence — Product & Implementation Plan

Cadence is a simple, private period-tracking companion app. This document covers:

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
| Estimated ovulation day and fertile window | P1 | Clearly labelled as an estimate. Hidden when hormonal birth control is on. |
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
| Discharge / cervical mucus | P2 | Dry, sticky, creamy, watery, egg-white. |
| Medications and contraception taken | P2 | Pill check-off, painkillers, supplements. |
| Basal body temperature (BBT) | P2 | Charted. Used to confirm ovulation after the fact. |
| Ovulation test (LH) and pregnancy test results | P2 | |
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
| BBT chart with cover line | P2 | |

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
| Encrypted database at rest | P0 | Much cheaper to do from day one than to retrofit later. |
| App lock (PIN plus biometrics) | P1 | |
| Delete all data | P0 | |
| No third-party analytics or ads | P0 | |
| Plain-language privacy screen | P1 | Explains exactly what is stored and where. |
| Discreet app icon and name option | P3 | |

### 2.8 Data portability
| Feature | Priority | Notes |
|---|---|---|
| Export JSON (full backup) | P1 | |
| Import / restore from backup | P1 | Because data is local, losing a phone without a backup means losing everything. |
| Export CSV | P2 | |
| Clinician report (PDF): cycle table, averages, symptom summary, flags | P2 | |
| Encrypted backup file (password-protected) | P2 | |
| Optional end-to-end-encrypted sync across devices | P3 | Needs a server, so it is deliberately deferred. |

### 2.9 Goals / life-stage modes
| Feature | Priority | Notes |
|---|---|---|
| Track my cycle (default) | P0 | |
| Hormonal birth control mode | P1 | Turns off ovulation and fertile predictions, which don't apply, and adds pill reminders. |
| Trying to conceive (TTC) mode | P2 | Puts the fertile window, LH tests, BBT and mucus first. |
| Pregnancy mode | P3 | Pauses predictions and shows week count. |
| Perimenopause mode | P3 | Tolerates irregular cycles and tracks hot flashes and night sweats. |
| ~~Avoid pregnancy / contraception mode~~ | **Not offered** | Using an app as contraception requires regulatory clearance and clinical validation. We show a clear disclaimer instead. |

### 2.10 Experience & platform
| Feature | Priority | Notes |
|---|---|---|
| Onboarding (last period, typical lengths, goal) | P0 | Every question is skippable, with sensible defaults. |
| Light and dark theme | P1 | |
| Accessibility: screen-reader labels, dynamic type, 44pt touch targets, WCAG AA contrast | P1 | |
| Educational cards per phase | P2 | Short, factual, sourced. |
| Localization (dates, first day of week, translations) | P2 | |
| Home-screen widget (cycle day / next period) | P3 | |
| Apple Health / Android Health Connect sync | P3 | |
| Partner sharing (read-only) | P3 | Needs a backend, so it is deferred. |

### 2.11 Explicit non-goals
- Social feeds, community forums, gamification, ads, or selling data.
- Contraceptive claims or diagnosis.
- Mandatory accounts or cloud storage.

---

## 3. Recommended architecture

**Recommendation: React Native with Expo (TypeScript), local-first, no backend.**

| Concern | Choice | Why |
|---|---|---|
| App framework | Expo (managed workflow, Expo Router) | One TypeScript codebase for iOS and Android, with a web build for quick demos. |
| Storage | `expo-sqlite` with SQLCipher enabled | Relational queries for stats, encrypted at rest. |
| ORM / migrations | Drizzle ORM (`drizzle-orm/expo-sqlite`) | Typed schema and versioned migrations from day one. |
| Encryption key | Random 256-bit key in `expo-secure-store` (Keychain / Keystore) | The key never leaves secure hardware-backed storage. |
| State | Zustand for UI state; queries read from SQLite | Small and simple. |
| Notifications | `expo-notifications`, local scheduled notifications only | No push server, so nothing leaves the device. |
| App lock | `expo-local-authentication` plus a PIN fallback | |
| Charts | `victory-native` or `react-native-svg` hand-rolled bars | |
| Dates | `date-fns`; days stored as `YYYY-MM-DD` local strings | Avoids timezone and DST bugs. A "day" is a calendar date, not an instant. |
| Testing | Jest for the domain logic, React Native Testing Library for components, Maestro for E2E flows | |
| CI | GitHub Actions: typecheck, lint, unit tests | |

**Alternative considered: an installable PWA (React + IndexedDB).** It is simpler to ship, but it can't reliably schedule *local* notifications (period reminders would need a push server, which breaks the no-server rule), and it gets no hardware-backed key storage or biometrics. If phone-native features matter, Expo is the better fit.

### Code layout
```
app/                 # Expo Router screens (today, calendar, insights, settings, onboarding)
src/
  domain/            # PURE TypeScript, no React/Expo imports — fully unit-tested
    cycles.ts        #   derive periods & cycles from day logs
    predict.ts       #   next period, ovulation, fertile window, confidence
    flags.ts         #   health-pattern flags
    phases.ts        #   cycle-day → phase
  data/              # Drizzle schema, migrations, repositories
  features/          # UI feature modules (logging, calendar, insights, reminders, lock)
  ui/                # design system: tokens, buttons, chips, sheets
  lib/               # date utils, notifications, secure-store wrapper
```
The key rule: **all cycle math lives in `src/domain` as pure functions.** That keeps it easy to test exhaustively and independent of the UI and storage.

---

## 4. Data model

The **source of truth is the per-day log.** Periods and cycles are *derived*, not stored, so editing any day keeps everything consistent automatically.

```ts
// A calendar date in the user's local timezone, e.g. "2026-11-08"
type LocalDate = string;

type Flow = 'spotting' | 'light' | 'medium' | 'heavy';

interface DayLog {
  date: LocalDate;              // primary key
  flow?: Flow;                  // presence of light/medium/heavy = period day
  painLevel?: number;           // 0–10
  energy?: 1 | 2 | 3 | 4 | 5;
  sleep?: 1 | 2 | 3 | 4 | 5;
  stress?: 1 | 2 | 3 | 4 | 5;
  mucus?: 'dry' | 'sticky' | 'creamy' | 'watery' | 'eggwhite';
  sex?: 'none' | 'protected' | 'unprotected';
  bbtCelsius?: number;
  weightKg?: number;
  lhTest?: 'negative' | 'positive';
  pregnancyTest?: 'negative' | 'positive';
  note?: string;
  updatedAt: string;            // ISO timestamp, for future sync/merge
}

interface TagDefinition {        // symptoms, moods, custom tags
  id: string;
  kind: 'symptom' | 'mood' | 'custom';
  label: string;
  builtIn: boolean;
  hidden: boolean;
}

interface DayTag {               // many-to-many: which tags on which day
  date: LocalDate;
  tagId: string;
  severity?: 1 | 2 | 3;          // mild / moderate / severe
}

interface Medication {
  id: string;
  name: string;
  kind: 'pill' | 'patch' | 'ring' | 'other';
  schedule: { type: 'daily' | 'weekly' | 'monthly'; time: string /* "21:00" */ };
  active: boolean;
}

interface MedicationDose { medicationId: string; date: LocalDate; taken: boolean; }

interface Settings {
  goal: 'track' | 'birth_control' | 'ttc' | 'pregnant' | 'perimenopause';
  defaultCycleLength: number;   // 28 if unknown
  defaultPeriodLength: number;  // 5 if unknown
  lutealPhaseLength: number;    // 14 default; user-adjustable (or learned from BBT)
  firstDayOfWeek: 0 | 1;
  hiddenCategories: string[];
  reminders: ReminderSettings;
  lock: { enabled: boolean; biometrics: boolean };
  theme: 'system' | 'light' | 'dark';
  onboardingComplete: boolean;
  schemaVersion: number;
}

// Optional: user marks a cycle as unusual (illness, travel, postpartum…) so
// it's excluded from predictions.
interface CycleAnnotation { startDate: LocalDate; excludeFromPredictions: boolean; reason?: string; }
```

**Deriving periods:** consecutive days with flow `light | medium | heavy` form a period. A gap of ≤ 1 day without flow is bridged. Spotting alone never *starts* a period.
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
- Skipped entirely when goal is `birth_control` or `pregnant`.
- In a later phase, a positive LH test or a sustained BBT rise (3 readings ≥ 0.2 °C above the previous 6) *confirms* ovulation for that cycle and feeds back into a learned luteal length.

### 5.4 Phases (for the Today screen)
| Phase | Days |
|---|---|
| Menstrual | Period days |
| Follicular | After the period until the fertile window starts |
| Ovulatory | Fertile window |
| Luteal | After ovulation until the next period |

### 5.5 Late period
If today is past `nextStart + rangeHigh` with no flow logged, the state becomes **Late by N days**. Predictions for later cycles shift off today rather than staying in the past.

### 5.6 Health flags (FIGO-based, informational only)
- Average cycle < 24 or > 38 days
- Cycle-to-cycle variation (max − min over the last 6) > 9 days
- Any period lasting > 8 days
- No period for ≥ 90 days (when not pregnant or on birth control)
- Pain level ≥ 7 on multiple period days across 3+ cycles

### 5.7 Testing strategy
Table-driven unit tests with fixture histories: perfectly regular 28-day cycles, irregular cycles, a single cycle, no data, outliers, an excluded cycle, DST boundaries, leap years, a late period, an ongoing period, and spotting-only days. Target **100% branch coverage of `src/domain`**.

---

## 6. Screens & UX

```
┌─ Tab bar ───────────────────────────────────────────┐
│  Today   │   Calendar   │   Insights   │   Settings │
└─────────────────────────────────────────────────────┘
```

1. **Onboarding**: welcome and privacy promise → last period start → typical cycle and period length ("I don't know" allowed) → goal → optional app lock → done.
2. **Today**: a large cycle ring showing cycle day N of ~M with the current phase highlighted; the headline ("Period in 4 days", "Day 2 of period", "3 days late"); a primary action button that changes with context ("Period started" / "Period ended" / "Log today"); chips for what's already logged today; and a short phase card.
3. **Log sheet** (bottom sheet, opened from Today or any calendar day): sections for Flow, Symptoms, Mood, Body (pain, energy, sleep, stress), Sex, Discharge, Tests, Meds, and Notes. Sections are collapsible, the user can hide them, and changes save automatically.
4. **Calendar**: month grid, swipe between months. Legend:
   - Solid fill = logged period
   - Dashed outline = predicted period
   - Tinted = fertile window
   - Dot = ovulation estimate
   - Small dot = has log

   Tapping a day opens the log sheet for that date.
5. **Insights**: summary cards (average cycle, average period, variation, confidence), a cycle length chart, the cycle history list, symptom patterns, and health flags.
6. **Settings**: goal, cycle defaults, reminders, categories shown, app lock, theme, export / import / delete all, privacy explainer, about and disclaimer.

**Visual direction:** calm and warm, not stereotypically pink. Primary terracotta or rose, a teal accent for the fertile window, and neutral warm greys. Every state has a non-colour cue (pattern, icon or label) for colour-blind users.

---

## 7. Phased delivery plan

Each phase ends with a **working, shippable app**. Phases are sized for one developer at a few days to two weeks each.

### Phase 0: Foundation
**Goal:** the skeleton everything else builds on.
- Expo + TypeScript + Expo Router project; ESLint, Prettier and strict `tsconfig`.
- Design tokens (colours, spacing, type scale, light and dark), plus base components: Button, Chip, Card, BottomSheet, SegmentedControl.
- Encrypted SQLite (SQLCipher) with a key in SecureStore; Drizzle schema and migration runner.
- `src/domain` scaffold with Jest; date utilities using `LocalDate`.
- GitHub Actions CI: typecheck, lint, test.
- Tab navigation with placeholder screens.

**Done when:** the app launches on iOS and Android simulators, CI is green, the DB opens encrypted, and a migration test passes.

### Phase 1: MVP (log periods, see predictions)
**Goal:** the smallest app that is actually useful.
- Onboarding: last period date, typical lengths, goal (track / birth control).
- Today screen: cycle day, phase, next-period countdown, and a contextual "Period started / ended" button.
- Calendar: month view showing logged and predicted period days; tap a day to toggle a period day.
- Domain: derive periods and cycles; basic prediction (average plus defaults); phases.
- Settings: cycle defaults, goal, theme, **delete all data**, and the disclaimer.

**Done when:** a new user can onboard in under 60 seconds, log a period in 1 tap, see the next prediction, and edit history on the calendar. The domain has full unit tests.

### Phase 2: Daily logging
**Goal:** a rich but fast daily check-in.
- Log sheet with flow intensity, symptoms with severity, mood, pain, energy, sleep, stress, sex, and notes.
- Built-in tag catalogue; hide or show categories.
- Calendar "has log" dots; day detail view.
- Chips on the Today screen summarising today's log.

**Done when:** a typical day can be logged in under 10 seconds, every field persists and is editable, and categories can be hidden.

### Phase 3: Smarter predictions & insights
**Goal:** predictions people can trust, plus useful statistics.
- Weighted-average prediction, outlier filtering, confidence ranges and labels.
- Ovulation and fertile window on Today and Calendar (hidden in birth control mode).
- Late period state; "exclude this cycle" annotation.
- Insights tab: summary cards, cycle length chart, cycle history, and FIGO-based health flags.

**Done when:** prediction fixtures cover all edge cases in §5.7, Insights renders correctly with 0, 1 and many cycles, and flag wording has been reviewed.

### Phase 4: Reminders & notifications
**Goal:** the app reaches out at the right moments without giving anything away.
- Local notifications for upcoming period, late period, and a daily log reminder at a chosen time.
- Discreet notification text on by default.
- Notifications rescheduled automatically whenever predictions change.
- Permission prompt in context (asked when the user turns on the first reminder, not at launch).

**Done when:** reminders fire correctly across app restarts, timezone changes and edits to period history, and no sensitive words appear by default.

### Phase 5: Privacy hardening & data portability
**Goal:** the user fully controls and can move their data.
- App lock: PIN plus biometrics, auto-lock after N minutes in the background, and the app-switcher snapshot blurred.
- JSON export and import (versioned format, validated on import, merge or replace).
- CSV export.
- Clinician PDF report (`expo-print`): date range, cycle table, averages, top symptoms, flags.
- Optional password-encrypted backup file.
- Privacy explainer screen.

**Done when:** an export → delete all → import round trip restores identical data, the lock can't be bypassed via deep links or the app switcher, and the PDF renders correctly.

### Phase 6: Goals, fertility signals & medications
**Goal:** support different life stages.
- TTC mode: BBT logging and chart, cervical mucus, LH tests, and confirmed ovulation feeding back into a learned luteal length.
- Birth control mode: medication or contraceptive schedule (pill / patch / ring), dose check-off, and reminders.
- Pregnancy mode (pauses predictions, shows week count) and perimenopause mode (relaxed irregularity flags, extra symptoms).
- Symptom pattern insights ("you often log X around cycle day Y").
- Custom tags.

**Done when:** each mode changes predictions, the Today screen and reminders as specified, and switching modes never loses data.

### Phase 7: Polish & ecosystem
**Goal:** release quality and nice-to-haves.
- Accessibility audit (VoiceOver / TalkBack, dynamic type, contrast) and fixes.
- Localization framework plus first translations; first-day-of-week and unit preferences.
- Educational phase cards.
- Home-screen widget.
- Apple Health / Health Connect read and write (opt-in).
- Store readiness: icons, screenshots, privacy nutrition labels, app review notes.
- *(Stretch, needs a backend)*: E2E-encrypted sync, partner sharing.

### Phase summary

| Phase | Theme | Key output |
|---|---|---|
| 0 | Foundation | Encrypted DB, design system, CI |
| 1 | MVP | Log periods → see predictions |
| 2 | Daily logging | Symptoms, mood, flow, notes |
| 3 | Insights | Confidence-ranged predictions, fertile window, stats, flags |
| 4 | Reminders | Discreet local notifications |
| 5 | Privacy & portability | App lock, export/import, clinician PDF |
| 6 | Modes | TTC, birth control, pregnancy, perimenopause |
| 7 | Polish | a11y, i18n, widgets, health integrations, store release |

---

## 8. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Users rely on fertile window predictions as contraception | A persistent disclaimer on the fertile window UI, no "safe days" wording, and no contraception mode. |
| Sensitive data exposure (legal requests, a lost device, a shoulder-surfer) | Local-only storage, SQLCipher, app lock, discreet notifications, a blurred app-switcher snapshot, and no server to subpoena. |
| Data loss because nothing is in the cloud | Prominent backup and export (Phase 5), plus a periodic "back up your data" nudge. |
| Timezone or DST bugs shifting days | `LocalDate` strings everywhere, no `Date` math on stored days, and dedicated tests. |
| Poor predictions for irregular cycles | Confidence ranges, a Low-confidence label, the exclude-cycle feature, and never presenting a single date as certain. |
| Medical inaccuracy in flags or education | Base thresholds on FIGO/ACOG guidance, cite sources in-app, and use informational wording only. |
| Regulations (GDPR special-category data, FTC Health Breach Notification Rule) | Collect nothing server-side and keep a clear privacy policy. Revisit if sync or a backend is ever added. |

---

## 9. Open decisions

These choices affect the plan. Defaults are in **bold**.

1. **Platforms:** **iOS + Android via Expo**, or web/PWA only?
2. **Sync:** **local-only for now**, or plan a backend for E2E-encrypted sync earlier?
3. **Initial modes:** **track + birth control in MVP**, or also TTC?
4. **Visual identity:** **warm terracotta / teal palette**, or something else?
