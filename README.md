# FitHub — Your Fitness. Your Hub.

FitHub is an Android fitness tracker built with Kotlin and Jetpack Compose. It counts steps, tracks runs, walks and rides with GPS, awards points and streaks, and includes an exercise library. It works offline and syncs to the cloud when you're back online.

## Features

| Requirement | How FitHub does it |
|---|---|
| Register & log in, encrypted password | Firebase Authentication (passwords hashed by Firebase with scrypt). A salted **PBKDF2-HMAC-SHA256** hash is kept on the device for offline login. The password is never stored in plain text or in Firestore. |
| Single sign-on (SSO) | **Continue with Google** via Android Credential Manager + Firebase Auth. |
| Change settings | Settings tab: name, age, fitness level, daily step goal, notifications, dark mode, language, change password, log out. Saved locally and mirrored to Firestore. |
| REST API + database | **wger Workout Manager API** (`https://wger.de/api/v2/exerciseinfo/`) for the exercise library, **Open-Meteo** for weather, and **Cloud Firestore** as the online database. |
| Offline mode with sync | Workouts are written to **SQLite** first. **WorkManager** uploads unsynced workouts to Firestore as soon as there's a connection. The exercise library is cached in SQLite. Offline login uses the stored hash. |
| Real-time notifications | **Firebase Cloud Messaging** push notifications (topic `fithub_updates` + per-device token), plus instant local alerts for workout saved, sync complete and daily goal reached. |
| Multi-language | **English, isiZulu and Afrikaans**, switchable in Settings (per-app language). |
| Part 1 features | Step counter, live GPS activity tracking, distance, active time, points, streaks, weather, workout history. |
| App icon & assets | Custom adaptive launcher icon (with themed/monochrome version), in-app logo and notification icon. |
| Automated testing | 24 JUnit tests (validation, password hashing, fitness maths, API parsing) run by **GitHub Actions** on every push. |

## Project structure

```
app/src/main/java/com/example/fithub/
├── FitHubApp.kt              Application: notification channels, sync on reconnect
├── MainActivity.kt           Navigation + bottom tabs
├── ServiceLocator.kt         Shared repositories
├── data/
│   ├── local/FitHubDatabase.kt     SQLite (workouts, exercise cache, local password hashes)
│   ├── remote/                     Retrofit clients + wger models/mapper
│   ├── repository/                 Workouts, exercises, settings, session, network monitor
│   ├── LocationManager.kt / StepCounterManager.kt / WeatherService.kt
├── notifications/            FCM service, push registration, local notifications
├── security/PasswordHasher.kt
├── sync/SyncWorker.kt        Offline → online sync
├── ui/                       auth, dashboard, exercises, history, settings, theme
└── util/                     Validators, FitnessCalculator, HtmlText
```

## Setup

1. Open the project in Android Studio and let Gradle sync.
2. **Firebase console** (project `fithub-7ee18`):
   - Authentication → Sign-in method → enable **Email/Password** and **Google**.
   - Project settings → your Android app → **Add fingerprint**: paste the SHA-1 from Android Studio (Gradle panel → `app › Tasks › android › signingReport`).
   - Download the new `google-services.json` and replace `app/google-services.json`. (This adds the web client ID Google sign-in needs.)
   - Firestore Database → Rules → paste `firestore.rules`.
3. Run on an emulator **with Google Play** (or a real phone) signed into a Google account.

### Sending a test push notification
Firebase console → Messaging → **Create your first campaign** → Firebase Notification messages → enter a title and text → **Send test message** (paste the device's FCM token from Firestore `users/{uid}.fcmToken`) or target the topic `fithub_updates`.

## Where the data lives (for the demo video)

| Place | What's stored |
|---|---|
| Firebase Authentication | Email, display name, provider (password/Google), UID. Password hash is managed by Firebase and never visible. |
| Cloud Firestore `users/{uid}` | fullName, email, age, fitnessLevel, dailyStepGoal, preferredLanguage, notificationsEnabled, darkMode, points, currentStreak, totalWorkouts, fcmToken, timestamps |
| Cloud Firestore `users/{uid}/workouts/{id}` | type, startTime, durationSec, distanceMeters, steps, points, syncedAt |
| wger REST API | Exercise names, categories, muscles, equipment, images, descriptions |
| SQLite on device (`fithub.db`) | `workouts` (with `synced` flag), `exercises` cache, `local_users` (salted PBKDF2 hash) — view with Android Studio → App Inspection → Database Inspector |

## Testing & CI

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # build APK
```
`.github/workflows/build.yml` runs both on GitHub Actions for every push and uploads the APK and test reports.

## References list

- Firebase Cloud Messaging. Available at: https://firebase.google.com/docs/cloud-messaging [Accessed 3 November 2026].
- Firebase Authentication. Available at: https://firebase.google.com/docs/auth/android/start [Accessed 3 November 2026].
- Android Developers. Authenticate users with Sign in with Google. Available at: https://developer.android.com/identity/sign-in/credential-manager-siwg [Accessed 3 November 2026].
- Android Developers. WorkManager. Available at: https://developer.android.com/develop/background-work/background-tasks/persistent [Accessed 3 November 2026].
- Android Developers. Per-app language preferences. Available at: https://developer.android.com/guide/topics/resources/app-languages [Accessed 3 November 2026].
- wger Workout Manager REST API. Available at: https://wger.de/en/software/api [Accessed 3 November 2026].
- Open-Meteo Weather API. Available at: https://open-meteo.com/en/docs [Accessed 3 November 2026].
- GitHub Marketplace. Automated build Android app with GitHub Action. Available at: https://github.com/marketplace/actions/automated-build-android-app-with-github-action [Accessed 3 November 2026].
- IMAD5112. GitHub Actions build.yml. Available at: https://github.com/IMAD5112/Github-actions/blob/main/.github/workflows/build.yml [Accessed 3 November 2026].
