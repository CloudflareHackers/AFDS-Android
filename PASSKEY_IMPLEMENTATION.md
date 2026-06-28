# Add Passkey (WebAuthn) Support to the AFDS Android App

**For the coding agent:** Implement passkey sign-in + management in this app. The **backend is already done and deployed** — you only write Android code. Match the existing conventions in this repo (Ktor `HttpClient`, kotlinx.serialization, `ApiClient`, `SessionManager`, Jetpack Compose). Read `app/src/main/java/com/afds/app/data/remote/ApiClient.kt`, `data/local/SessionManager.kt`, `data/model/Models.kt`, and `ui/screens/LoginScreen.kt` + `ProfileScreen.kt` before starting, and mirror their style.

---

## 1. What already exists (do NOT rebuild)

- **Backend**: same API as the rest of the app — `BASE_URL = "https://tga-hd.api.hashhackers.com"`. New passkey routes are live (see §3).
- **JWT**: passkey login returns the **same JWT** the app already gets from OTP/Google (`{ token }`). Store it exactly like today via `SessionManager.saveToken(token)`. Nothing else in the app changes.
- **RP ID**: `afds.hashhackers.com`. Digital Asset Links is **already published** at `https://afds.hashhackers.com/.well-known/assetlinks.json` associating package `com.afds.app` with this signing cert:
  ```
  SHA-256: 90:28:75:F0:B9:98:59:41:60:4C:78:09:D5:64:F6:FC:9B:5A:B2:2B:6E:03:2D:60:03:89:30:46:3C:BF:6D:8A
  ```
  > ⚠️ **Play App Signing:** if this app is distributed through Google Play, Play re-signs it with a *different* key. The fingerprint above is the upload/local key. If production builds use Play App Signing, the **Play "App signing key" SHA-256** (Play Console → Test and release → App integrity) must ALSO be added to that `assetlinks.json` (it lives in the `gods-eye` web repo, not here) or passkeys will fail in production. Flag this to the human if you can't confirm.

---

## 2. Dependencies

Add to `app/build.gradle(.kts)`:

```kotlin
implementation("androidx.credentials:credentials:1.3.0")
implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
```

**Min SDK note:** platform passkeys require **Android 9 (API 28)+** with Google Play services. If `minSdk < 28`, gate the passkey UI behind `Build.VERSION.SDK_INT >= 28` and handle `UnsupportedException`/`NoCredentialException` gracefully (hide or disable the buttons, show a message).

---

## 3. Backend API contract (already implemented)

All bodies are JSON. **Do not model the WebAuthn internals** — pass the options/response objects through as raw JSON. Android's Credential Manager produces and consumes the standard WebAuthn JSON directly.

### Registration (auth required — `Authorization: Bearer <jwt>`)

**`POST /auth/passkey/register/options`** — body `{}`
→ `200`: returns a `PublicKeyCredentialCreationOptions` JSON object (pass this **whole object** to Credential Manager as a string).

**`POST /auth/passkey/register/verify`** (Bearer) — body:
```json
{ "attestationResponse": <the registrationResponseJson object from Credential Manager>, "deviceName": "Pixel 8" }
```
→ `200`: `{ "success": true, "message": "Passkey registered." }`

### Login (NO auth header)

**`POST /auth/passkey/login/options`** — body `{}` or `{ "email": "user@example.com" }` (email optional; narrows credentials)
→ `200`:
```json
{ "options": { ...PublicKeyCredentialRequestOptions... }, "handle": "login:uuid" }
```
You must keep `handle` and send it back in verify.

**`POST /auth/passkey/login/verify`** — body:
```json
{ "assertionResponse": <the authenticationResponseJson object from Credential Manager>, "handle": "login:uuid" }
```
→ `200`: `{ "success": true, "token": "<JWT>", "email": "..." }` — **same JWT shape as OTP/Google.**

### Management (auth required)

**`GET /auth/passkey/list`** (Bearer)
→ `200`: `{ "passkeys": [ { "id": 1, "device_name": "Pixel 8", "created_at": "...", "last_used_at": "..." } ] }`

**`DELETE /auth/passkey/{id}`** (Bearer)
→ `200`: `{ "success": true, "deleted": 1 }`

Errors on all routes: non-2xx with `{ "error": "..." }`. Reuse the existing `ApiException(message, statusCode)` pattern.

---

## 4. Models — add to `data/model/Models.kt`

```kotlin
@Serializable
data class PasskeyInfo(
    val id: Int,
    val device_name: String? = null,
    val created_at: String? = null,
    val last_used_at: String? = null
)

@Serializable
data class PasskeyListResponse(
    val passkeys: List<PasskeyInfo> = emptyList(),
    val error: String? = null
)

// Returned to the UI from passkeyLoginOptions()
data class PasskeyLoginOptions(val optionsJson: String, val handle: String)
```

(The existing `AuthResponse { token, error }` and `MessageResponse { message, error }` are reused for verify responses.)

---

## 5. ApiClient — add to `data/remote/ApiClient.kt`

Add these methods inside the `ApiClient` class. They reuse the existing `client`, `json`, `BASE_URL`, and `ApiException`. Note the use of `kotlinx.serialization.json` helpers to pass WebAuthn JSON through untouched.

```kotlin
import kotlinx.serialization.json.*

// ---- Passkeys ----

// Registration: returns the raw creation-options JSON to hand to Credential Manager.
suspend fun passkeyRegisterOptions(token: String): String {
    val response = client.post("$BASE_URL/auth/passkey/register/options") {
        header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody("{}")
    }
    if (response.status == HttpStatusCode.Unauthorized) throw ApiException("Session expired", 401)
    if (!response.status.isSuccess()) {
        val err = runCatching { json.decodeFromString<MessageResponse>(response.bodyAsText()) }.getOrNull()
        throw ApiException(err?.error ?: "Could not start passkey registration", response.status.value)
    }
    return response.bodyAsText()
}

// registrationResponseJson comes from CreatePublicKeyCredentialResponse.registrationResponseJson
suspend fun passkeyRegisterVerify(token: String, registrationResponseJson: String, deviceName: String): Boolean {
    val payload = buildJsonObject {
        put("attestationResponse", json.parseToJsonElement(registrationResponseJson))
        put("deviceName", deviceName)
    }
    val response = client.post("$BASE_URL/auth/passkey/register/verify") {
        header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(payload)
    }
    if (response.status == HttpStatusCode.Unauthorized) throw ApiException("Session expired", 401)
    if (!response.status.isSuccess()) {
        val err = runCatching { json.decodeFromString<MessageResponse>(response.bodyAsText()) }.getOrNull()
        throw ApiException(err?.error ?: "Could not register passkey", response.status.value)
    }
    return true
}

// Login (no token). Returns options JSON (for Credential Manager) + handle (echo back on verify).
suspend fun passkeyLoginOptions(email: String?): PasskeyLoginOptions {
    val body = buildJsonObject { if (!email.isNullOrBlank()) put("email", email) }
    val response = client.post("$BASE_URL/auth/passkey/login/options") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    if (!response.status.isSuccess()) {
        val err = runCatching { json.decodeFromString<MessageResponse>(response.bodyAsText()) }.getOrNull()
        throw ApiException(err?.error ?: "Could not start passkey sign-in", response.status.value)
    }
    val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
    val optionsJson = root["options"]!!.toString()           // re-serialize the options object to a string
    val handle = root["handle"]!!.jsonPrimitive.content
    return PasskeyLoginOptions(optionsJson, handle)
}

// authenticationResponseJson comes from PublicKeyCredential.authenticationResponseJson. Returns JWT.
suspend fun passkeyLoginVerify(authenticationResponseJson: String, handle: String): String {
    val payload = buildJsonObject {
        put("assertionResponse", json.parseToJsonElement(authenticationResponseJson))
        put("handle", handle)
    }
    val response = client.post("$BASE_URL/auth/passkey/login/verify") {
        contentType(ContentType.Application.Json)
        setBody(payload)
    }
    val text = response.bodyAsText()
    if (!response.status.isSuccess()) {
        val err = runCatching { json.decodeFromString<AuthResponse>(text) }.getOrNull()
        throw ApiException(err?.error ?: "Passkey sign-in failed", response.status.value)
    }
    return json.decodeFromString<AuthResponse>(text).token
        ?: throw ApiException("No token returned", response.status.value)
}

suspend fun listPasskeys(token: String): List<PasskeyInfo> {
    val response = client.get("$BASE_URL/auth/passkey/list") {
        header("Authorization", "Bearer $token")
    }
    if (response.status == HttpStatusCode.Unauthorized) throw ApiException("Session expired", 401)
    if (!response.status.isSuccess()) return emptyList()
    return json.decodeFromString<PasskeyListResponse>(response.bodyAsText()).passkeys
}

suspend fun deletePasskey(token: String, id: Int): Boolean {
    val response = client.delete("$BASE_URL/auth/passkey/$id") {
        header("Authorization", "Bearer $token")
    }
    if (response.status == HttpStatusCode.Unauthorized) throw ApiException("Session expired", 401)
    if (!response.status.isSuccess()) {
        val err = runCatching { json.decodeFromString<MessageResponse>(response.bodyAsText()) }.getOrNull()
        throw ApiException(err?.error ?: "Failed to remove passkey", response.status.value)
    }
    return true
}
```

---

## 6. Credential Manager helper — new file `data/remote/PasskeyManager.kt`

```kotlin
package com.afds.app.data.remote

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential

class PasskeyManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    /** Pass the creation-options JSON string; returns registrationResponseJson. */
    suspend fun register(optionsJson: String): String {
        val request = CreatePublicKeyCredentialRequest(requestJson = optionsJson)
        val result = credentialManager.createCredential(context, request)
                as CreatePublicKeyCredentialResponse
        return result.registrationResponseJson
    }

    /** Pass the request-options JSON string; returns authenticationResponseJson. */
    suspend fun authenticate(optionsJson: String): String {
        val option = GetPublicKeyCredentialOption(requestJson = optionsJson)
        val request = GetCredentialRequest(listOf(option))
        val result = credentialManager.getCredential(context, request)
        val cred = result.credential as PublicKeyCredential
        return cred.authenticationResponseJson
    }
}
```

> **Context must be an Activity.** In Compose use `LocalContext.current` from within the screen (it's the `ComponentActivity`). Credential Manager shows system UI, so an application context will fail.

---

## 7. UI wiring

### Login screen (`ui/screens/LoginScreen.kt`)
Add a **"Sign in with a passkey"** button alongside the existing Google/OTP options. On tap (inside a coroutine, e.g. `rememberCoroutineScope().launch`):

```kotlin
val context = LocalContext.current
val api = remember { ApiClient() }            // or the shared instance the app already uses
val passkeys = remember { PasskeyManager(context) }
// ...
try {
    val opt = api.passkeyLoginOptions(email = null)          // null = let user pick any passkey
    val assertionJson = passkeys.authenticate(opt.optionsJson)
    val token = api.passkeyLoginVerify(assertionJson, opt.handle)
    sessionManager.saveToken(token)                          // SAME as existing login flow
    // navigate to Home exactly like OTP/Google success does
} catch (e: GetCredentialCancellationException) {
    // user cancelled — show nothing or a soft message
} catch (e: NoCredentialException) {
    // no passkey on this device — show "No passkey found. Use email or Google."
} catch (e: ApiException) {
    // show e.message
} catch (e: GetCredentialException) {
    // show e.message
}
```

Optional: if the email field already has a value, pass it to `passkeyLoginOptions(email)` to narrow the credential list.

### Profile screen (`ui/screens/ProfileScreen.kt`)
Add a **"Passkeys"** section that:
1. On load, calls `api.listPasskeys(token)` and renders each `PasskeyInfo` (`device_name`, `created_at`, `last_used_at`) with a delete (trash) button.
2. Has an **"Add a passkey"** button:
```kotlin
try {
    val token = sessionManager.getToken() ?: return@launch
    val optionsJson = api.passkeyRegisterOptions(token)
    val regResponseJson = passkeys.register(optionsJson)
    val deviceName = Build.MODEL ?: "Passkey"
    api.passkeyRegisterVerify(token, regResponseJson, deviceName)
    // reload list, show success
} catch (e: CreateCredentialCancellationException) {
    // cancelled
} catch (e: ApiException) { /* show e.message */ }
  catch (e: CreateCredentialException) { /* show e.message */ }
```
3. Delete button → `api.deletePasskey(token, info.id)` then reload.

Use the app's existing loading/snackbar/error patterns from the other Profile actions (change email, set Telegram ID, etc.) — don't invent new UI primitives.

Required imports for exception handling:
```kotlin
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialCancellationException
import android.os.Build
```

---

## 8. Gotchas (read before coding)

- **Pass JSON through, don't parse it.** The backend's options are standard WebAuthn JSON; Credential Manager parses them. You only: (a) feed the options string in, (b) take the response string out, (c) wrap it for the verify call. The `buildJsonObject { put(..., json.parseToJsonElement(x)) }` pattern in §5 is what keeps the nested object intact.
- **`handle` is mandatory for login** — it's how the server finds the challenge. Echo back the exact value from `login/options`.
- **No token on login endpoints**; Bearer token required on register/list/delete (use `SessionManager.getToken()`).
- **RP ID = `afds.hashhackers.com`** is baked into the server options. The app works only because `assetlinks.json` associates `com.afds.app` with the signing cert. If `createCredential`/`getCredential` throws a domain/`assetlinks` error, the fingerprint in the web repo doesn't match the build's signing key (see the Play App Signing note in §1).
- **API 28+** for platform passkeys. Gate UI on older devices.
- **`signCount`** is tracked server-side — nothing to do here.
- The existing app stores the token under DataStore key `auth_token` via `SessionManager`. Passkey login must use the **same** `saveToken()` so the rest of the app (browse, profile, etc.) just works.

---

## 9. Test plan

1. **Assetlinks reachable:** `curl https://afds.hashhackers.com/.well-known/assetlinks.json` returns the JSON with this app's SHA-256.
2. **Register:** log in via OTP → Profile → "Add a passkey" → device biometric/screen-lock prompt → success → passkey appears in the list.
3. **Login:** log out → Login → "Sign in with a passkey" → biometric prompt → lands on Home, authenticated (browse files works → token valid).
4. **Manage:** delete the passkey in Profile → it disappears and can no longer be used to sign in.
5. **Negative:** on a device with no passkey, "Sign in with a passkey" shows a clean "no passkey found" message (no crash).

---

## 11. Also required: 90-day session (no premature auto-logout)

Separate from passkeys, extend the local session to **90 days** to match the backend (the backend JWT now expires in 90 days, and the web client now uses 90 days too).

In `data/local/SessionManager.kt`, change:
```kotlin
private const val SESSION_EXPIRY_MS = 28L * 24 * 60 * 60 * 1000 // 28 days
```
to:
```kotlin
private const val SESSION_EXPIRY_MS = 90L * 24 * 60 * 60 * 1000 // 90 days
```
This affects `getToken()` and `isLoggedIn`. Do not change anything else about session handling — the only problem was the 28-day local cutoff being shorter than the new 90-day token lifetime.

---

## 10. Definition of done
- New `ApiClient` passkey methods + `PasskeyManager` + models added, matching repo style.
- Login screen: working "Sign in with a passkey" → stores JWT via `SessionManager.saveToken`.
- Profile screen: list / add / delete passkeys.
- Builds clean; manual test plan §9 passes on an API 28+ device.
- Existing OTP/Google login untouched and still working.
