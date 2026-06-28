# AFDS-Android

## Project Overview
Android app for browsing/searching files served via Telegram bots. Users can search, browse, and download files across categories. Files are delivered to users' personal Telegram channels via a bot helper system.

- **Language:** Kotlin (100% Jetpack Compose UI)
- **Min SDK:** 26 | **Target SDK:** 35 | **Current version:** 1.0.4 (versionCode 5)
- **Package:** `com.afds.app`
- **Architecture:** Single Activity + Compose Navigation

## Key File Map

```
app/src/main/java/com/afds/app/
├── AFDSApplication.kt              # App singleton — initializes ApiClient + SessionManager
├── MainActivity.kt                 # Entry point — network monitor, update check, NavHost
├── data/
│   ├── local/
│   │   ├── SessionManager.kt       # DataStore-backed auth token, prefs, session expiry (28d)
│   │   └── CacheManager.kt         # In-memory 60s TTL cache for search/browse results
│   ├── model/
│   │   ├── Models.kt               # All data classes (FileItem, ProfileResponse, etc.) + FileCategory enum
│   │   └── UpdateModels.kt         # AppUpdateInfo model
│   └── remote/
│       └── ApiClient.kt            # Ktor OkHttp client — all API calls, GOOGLE_CLIENT_ID const
├── ui/
│   ├── navigation/
│   │   └── Navigation.kt           # Routes object + AFDSNavHost composable
│   ├── screens/
│   │   ├── LoginScreen.kt          # 2-step OTP login (email → OTP code)
│   │   ├── SetupScreen.kt          # 4-step wizard: TG user ID → instructions → channel ID → sync wait
│   │   ├── HomeScreen.kt           # Search hub + category browse cards
│   │   ├── SearchScreen.kt         # Paginated search results
│   │   ├── BrowseScreen.kt         # Paginated category browse
│   │   ├── ProfileScreen.kt        # Profile, Telegram settings, preferences, logout
│   │   └── MyFilesScreen.kt        # Saved files list
│   ├── components/
│   │   └── SharedComponents.kt     # FileCard, FileDetailDialog, FileListContent, pagination
│   └── theme/
│       └── Theme.kt                # Material3 dark theme
└── util/
    ├── DownloadHelper.kt           # Android DownloadManager + 1DM app integration
    ├── NetworkObserver.kt          # Real-time connectivity Flow
    ├── UpdateManager.kt            # APK download + install flow
    └── Utils.kt                    # normalizeEmail() (Gmail dot removal)
```

## Authentication Flow

1. User enters email → `POST /request-login-otp` → returns `loginType` (email/telegram) + optional `botId`
2. User enters 6-digit OTP → `POST /verify-login-otp` → returns `token`
3. Token saved to DataStore with timestamp; expires after **28 days**
4. Google Sign-In: `POST /auth/google` with credential idToken (backend + deps ready, UI wiring needed)

**Session expiry:** `SessionManager.getToken()` returns null + clears session if token age > 28d.
**Logout:** `sessionManager.clearSession()` + navigate to LOGIN clearing back stack.

## Navigation Routes

```kotlin
Routes.LOGIN       // Unauthenticated entry
Routes.SETUP       // Account setup wizard (optional after Part 6 fix)
Routes.HOME        // Main hub
Routes.SEARCH      // search?query={query}&category={category}
Routes.BROWSE      // browse/{category}
Routes.PROFILE     // User profile & settings
Routes.MY_FILES    // Saved files
// Planned:
Routes.TELEGRAM_SETUP  // Automated TG channel setup via WebView
```

Start destination logic in `AFDSNavHost`: `!isLoggedIn → LOGIN`, else `HOME`.

## API Endpoints

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `POST /request-login-otp` | No | Request OTP (email or Telegram) |
| `POST /verify-login-otp` | No | Verify OTP → get token |
| `POST /auth/google` | No | Google Sign-In (backend ready) |
| `GET /profile` | Bearer | Get user profile |
| `POST /profile/set-user-id` | Bearer | Set Telegram user ID (first time) |
| `PUT /profile/update-user-id` | Bearer | Update Telegram user ID |
| `POST /profile/set-channel-id` | Bearer | Set Telegram channel ID |
| `DELETE /profile/remove-channel-id` | Bearer | Remove channel ID |
| `GET /{category}/search?q=&page=` | Bearer | Search files |
| `GET /{category}/index?page=` | Bearer | Browse category |
| `GET /{category}/id?id=` | No | File details |
| `GET /genLink?type=&id=` | No | Generate download link |
| `POST /user/save-file` | Bearer | Save file to My Files |
| `GET /user/my-files?page=` | Bearer | Get saved files |
| `POST /sendToChannel` (external CDN) | No | Send file to Telegram channel |

**Base URL:** `https://tga-hd.api.hashhackers.com`
**File Delivery URL:** `https://tgarchiveapifilecopyandlinkgen.hashhackersapi.workers.dev`
**APK Updates URL:** `https://afds.apks.zindex.eu.org/com.afds.app`

## File Categories

```kotlin
enum class FileCategory(val tableName, val prefix, val displayName)
  MEDIA       → "files"           prefix: "files"
  MUSIC       → "music_files"     prefix: "music"
  NSFW        → "nsfw_files"      prefix: "nsfw"
  MIX_MEDIA   → "mix_media_files" prefix: "mix-media-files"
```

Prefixes used to construct `uniqueId` for Telegram bot URLs and `sendToChannel` requests.

## Telegram Integration

- **File delivery bot:** `@TGID1OO1Bot` — `https://t.me/TGID1OO1Bot?start={prefix}-{fileId}`
- **Channel helper bot:** `@LinkerXHelperbot` — must be added as admin, run `/setup`
- **User ID helper:** `@userinfobot` — users get their numeric Telegram ID from this
- **Channel ID format:** `-1001234567890`

## Google Sign-In

Dependencies declared, backend endpoint ready. Only UI + CredentialManager wiring needed:
- `ApiClient.GOOGLE_CLIENT_ID` = `"58094879805-2k4u6f17pfn7fm68kg31fcr4ah7slm0d.apps.googleusercontent.com"`
- `ApiClient.googleAuth(credential: String)` — throws `ApiException(404)` if no account

## Build & Signing

```bash
./gradlew assembleRelease
```

Signing requires `app/keystore.jks` + env vars: `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
CI: `.github/workflows/build.yml`

## Planned Features (see plan.md)

1. Login check on all pages (reactive session expiry)
2. Coroutine timeout + long-request feedback
3. Google Sign-In UI
4. Telegram bot clickable links
5. Automated TG setup page (WebView + tg-webapp-proxy assets)
6. Remove forced setup for new users
