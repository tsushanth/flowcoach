# FlowCoach

FlowCoach is a native Android hydration coach. It tracks how much water you drink through the day, keeps you honest with streaks, and nudges you with reminders — all built with Jetpack Compose and Material 3.

## Features

- **Daily progress ring** — log water with one tap and watch a circular progress indicator fill toward your daily goal.
- **Quick add & custom amounts** — six preset pour sizes (100–750 ml) for one-tap logging, plus a custom-amount entry dialog for Premium users.
- **Streaks** — current and best-ever daily streaks, recomputed automatically whenever you log or delete an entry or change your goal.
- **History** — a day-by-day log of totals against your goal; free users see the last 7 days, Premium users see the last 30.
- **Hydration reminders** — configurable periodic notifications (WorkManager-based) with preset or custom intervals, gated behind the Android 13+ notification permission.
- **FlowCoach Premium** — a paywall (Google Play Billing) offering weekly/monthly/yearly/lifetime subscriptions and a one-time "remove ads" purchase, unlocking custom quick-add amounts, custom reminder intervals, full history, and advanced streak insights.
- **Accessibility & polish** — content descriptions on all interactive controls, Material You dynamic color with light/dark theme support, haptic feedback on key actions, IME-aware keyboard handling in dialogs, and dedicated empty/error states.

## Requirements

- Android Studio (Koala or newer recommended)
- JDK 17
- Android SDK with:
  - `compileSdk` / `targetSdk` 34
  - `minSdk` 24 (Android 7.0+)
- A device or emulator running Android 7.0+ (dynamic color theming requires Android 12+/API 31)

## Build instructions

1. Clone the repository and open it in Android Studio, **or** build from the command line:
   ```bash
   ./gradlew assembleDebug
   ```
2. To install on a connected device or emulator:
   ```bash
   ./gradlew installDebug
   ```
3. To run unit/instrumented tests:
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

Google Play Billing requires the app to be signed and installed via a Play-associated build (internal testing track or later) for real purchases to succeed; local debug builds fall back to displaying fallback prices and will report billing as disconnected.

## Project structure

```
app/src/main/java/com/factory/flowcoach/
├── MainActivity.kt              # Single-activity host, splash screen, edge-to-edge setup
├── FlowCoachApplication.kt      # App-level DI: repository, billing, and premium managers
├── billing/                     # Google Play Billing integration
│   ├── BillingManager.kt        #   Connection lifecycle, purchase flow, product details
│   ├── PremiumManager.kt        #   Derives entitlements from purchases + DataStore
│   └── PremiumSku.kt            #   Product catalog (weekly/monthly/yearly/lifetime/remove-ads)
├── data/
│   ├── local/                   # Room database (WaterEntry, DailyTotal, WaterDao, AppDatabase)
│   ├── prefs/                   # DataStore-backed user & premium preferences
│   └── repository/              # HydrationRepository — combines DB + prefs into app-facing flows
├── notification/                # WorkManager-based reminder scheduling and notification worker
└── ui/
    ├── navigation/               # NavHost + bottom navigation bar (Screen routes)
    ├── home/                     # Today screen: progress ring, quick add, entry log
    ├── history/                  # Daily history list with Premium-gated range
    ├── settings/                 # Goals, reminders, and Premium management
    ├── paywall/                  # Premium upsell + purchase/restore flow
    ├── common/                   # Shared composables (e.g. ProBadge)
    └── theme/                    # Material 3 color scheme, typography, dynamic theming
```
