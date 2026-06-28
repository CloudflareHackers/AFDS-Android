# AFDS-Android — Living Plan

## Active Work

See [C:\Users\Bhadoo\.claude\plans\toasty-juggling-cookie.md] for the current approved plan.

---

## Planned Features (Implementation Order)

### Step 1: CLAUDE.md + plan.md (Done)
Context files for all related projects.

### Step 2: Login Check on All Pages
**Files:** `ui/navigation/Navigation.kt`, `ui/screens/HomeScreen.kt`, `SearchScreen.kt`, `BrowseScreen.kt`, `MyFilesScreen.kt`

**Changes:**
- Add `LaunchedEffect(isLoggedIn) { if (!isLoggedIn) navigate to LOGIN }` in `AFDSNavHost`
- Each screen's initial `LaunchedEffect`: call `sessionManager.getToken()` → if null, call `onLogout()`
- Confirm 401 → logout pattern exists in all bearer-auth screens

### Step 3: Remove Forced Setup
**Files:** `ui/navigation/Navigation.kt`, `ui/screens/HomeScreen.kt`

**Changes:**
- Remove `isSetupComplete` check from `AFDSNavHost` start destination
- Add dismissable setup banner in HomeScreen when `!isSetupComplete`

### Step 4: Telegram Bot Clickable Links
**Files:** `ui/screens/SetupScreen.kt`, `ui/screens/ProfileScreen.kt`

**Bots to link:**
- `@userinfobot` → `https://t.me/userinfobot`
- `@LinkerXHelperbot` → `https://t.me/LinkerXHelperbot`
- `@TGID1OO1Bot` → `https://t.me/TGID1OO1Bot`

**Method:** `LocalUriHandler.current.openUri(url)` in Compose clickable text

### Step 5: Coroutine Fixes
**Files:** All screens

**Changes:**
- Add `withTimeout(30_000L)` around API calls
- Show "Taking longer than expected..." after 10s
- Audit all `launch {}` blocks for `finally { isLoading = false }`
- Use `supervisorScope` in SearchScreen/BrowseScreen for multi-call isolation

### Step 6: Google Sign-In
**Files:** `ui/screens/LoginScreen.kt`

**Changes:**
- Add "Continue with Google" button with Google SVG logo
- Use `CredentialManager.getCredential()` with `GetGoogleIdOption`
- Extract `idToken` from `GoogleIdTokenCredential`
- Call `apiClient.googleAuth(idToken)` → same post-login flow as OTP
- Handle 404 (no account) with message

**Client ID:** Already in `ApiClient.GOOGLE_CLIENT_ID`
**No new deps needed** — all in build.gradle.kts already

### Step 7: Automated Telegram Setup Page
**Files:** `ui/navigation/Navigation.kt`, new `ui/screens/TelegramSetupScreen.kt`, `ui/screens/SetupScreen.kt`, `app/src/main/assets/tg-webapp/`

**Flow:**
1. SetupScreen shows "Auto Setup (Recommended)" + "Set up manually" options
2. Auto Setup → `TELEGRAM_SETUP` route → WebView loading `file:///android_asset/tg-webapp/index.html`
3. User logs in with Telegram phone/code in WebView
4. AFDS automation script: creates "AFDS Files" channel, adds 20 bots as admins
5. JS calls `AndroidBridge.onChannelCreated(channelId)` → Android saves via API
6. Prompts user to logout from Telegram session
7. Navigate to HOME on success

**Bots to add (20):**
```
@LinkerXTest1Bot @LinkerXTestBot @DLCDNHelper1bot @DLCDNHelper2bot
@Warp1GBot @HHDay3Bot @HHDay4Bot @HHDay5Bot @BhadooJarvis_Bot
@TGArchiveB1Bot @TGArchiveB2Bot @TGArchiveC3Bot @TGArchiveB4Bot
@TGArchiveB5Bot @TGArchiveB6Bot @TGArchiveB7Bot @TGArchiveB8Bot
@TGArchiveB9Bot @TGArchiveB10Bot @TGArchiveB11Bot @TGArchiveB12Bot
@TGArchiveB13Bot @TGArchiveB14Bot @TGArchiveB15Bot @TGArchiveB16Bot
@TGArchiveB17Bot @TGArchiveB18Bot @TGArchiveB19Bot @TGArchiveB20Bot
```

**Source:** Bundle tg-webapp-proxy from `d:\GitLab_Projects\TG-WebApp-Proxy\` into APK assets.

---

## Completed

- [x] CLAUDE.md + plan.md created (2026-03-29)
- [x] Login check fixed on all pages — reactive `isLoggedIn` in Navigation.kt + token check in HomeScreen (2026-03-29)
- [x] Forced setup removed — banner shown on HomeScreen instead (2026-03-29)
- [x] Telegram bot links made clickable in SetupScreen + ProfileScreen (2026-03-29)
- [x] Coroutine fixes — `withTimeout(30s)`, slow request warnings, `CancellationException` rethrow in all screens (2026-03-29)
- [x] Google Sign-In — CredentialManager + `OutlinedButton` in LoginScreen, calls `apiClient.googleAuth()` (2026-03-29)
- [x] Automated TG setup — TelegramSetupScreen.kt, WebViewAssetLoader, tg-webapp-proxy bundled in assets, `afds-android-setup.js` creates "AFDS Files" channel + adds 20 bots (2026-03-29)

## Notes

- `app/src/main/assets/tg-webapp/` — built from TG-WebApp-Proxy dist. Rebuild with `cd d:\GitLab_Projects\TG-WebApp-Proxy && npm run build && cp -r dist/. d:\GitLab_Projects\AFDS-Android\app\src\main\assets\tg-webapp\`
- Google Sign-In `GOOGLE_CLIENT_ID` is in `ApiClient.GOOGLE_CLIENT_ID`
- LoginScreen no longer routes to SETUP after login (forced setup removed) — always routes to HOME
