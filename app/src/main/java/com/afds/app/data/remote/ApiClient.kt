package com.afds.app.data.remote

import com.afds.app.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import android.util.Log
import com.afds.app.data.local.CacheManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

class ApiClient {

    companion object {
        const val BASE_URL = "https://tga-hd.api.hashhackers.com"
        const val FILE_DELIVERY_URL = "https://tgarchiveapifilecopyandlinkgen.hashhackersapi.workers.dev"
        const val GOOGLE_CLIENT_ID = "58094879805-2k4u6f17pfn7fm68kg31fcr4ah7slm0d.apps.googleusercontent.com"
        const val APK_BASE_URL = "https://afds.apks.zindex.eu.org/com.afds.app"
        const val GITHUB_RELEASES_API = "https://api.github.com/repos/CloudflareHackers/AFDS-Android/releases/latest"
        const val GITHUB_RELEASES_PAGE = "https://github.com/CloudflareHackers/AFDS-Android/releases/latest"
    }

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(60, TimeUnit.SECONDS)
                readTimeout(60, TimeUnit.SECONDS)
                writeTimeout(60, TimeUnit.SECONDS)
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("AFDS_HTTP", message)
                }
            }
        }
    }

    // ---- Auth ----

    suspend fun requestLoginOtp(email: String): OtpResponse {
        val response = client.post("$BASE_URL/request-login-otp") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email))
        }
        return response.body()
    }

    suspend fun verifyLoginOtp(email: String, code: String): OtpResponse {
        val response = client.post("$BASE_URL/verify-login-otp") {
            contentType(ContentType.Application.Json)
            setBody(OtpRequest(email, code))
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        val error: OtpResponse = response.body()
        throw ApiException(error.error ?: "Verification failed", response.status.value)
    }

    suspend fun requestLoginOtpEmail(email: String): OtpResponse {
        val response = client.post("$BASE_URL/request-login-otp-email") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email))
        }
        return response.body()
    }

    suspend fun googleAuth(credential: String): AuthResponse {
        val response = client.post("$BASE_URL/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(GoogleAuthRequest(credential))
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        if (response.status == HttpStatusCode.NotFound) {
            throw ApiException("No account exists with this Google account. Please sign up first.", 404)
        }
        val error: AuthResponse = response.body()
        throw ApiException(error.error ?: "Google sign-in failed", response.status.value)
    }

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

    // ---- Search & Browse ----

    suspend fun searchFiles(token: String, category: String, query: String, page: Int = 1): SearchResponse {
        val cacheKey = CacheManager.searchKey(category, query, page)
        CacheManager.get<SearchResponse>(cacheKey)?.let { return it }
        Log.d("AFDS_API", "searchFiles: category=$category, query=$query, page=$page")
        val response = client.get("$BASE_URL/$category/search") {
            header("Authorization", "Bearer $token")
            parameter("q", query)
            parameter("page", page)
        }
        Log.d("AFDS_API", "searchFiles response status: ${response.status}")
        if (response.status == HttpStatusCode.Unauthorized) throw ApiException("Session expired", 401)
        if (response.status.value == 429) throw ApiException("Daily limit reached. Try again tomorrow.", 429)
        val bodyText = response.bodyAsText()
        Log.d("AFDS_API", "searchFiles raw response (first 500): ${bodyText.take(500)}")
        val result = json.decodeFromString<SearchResponse>(bodyText)
        if (result.files.isNotEmpty()) {
            val f = result.files[0]
            Log.d("AFDS_API", "First file: id=${f.id}, fileId=${f.fileId}, effectiveId=${f.effectiveId}, name=${f.fileName}")
        }
        CacheManager.put(cacheKey, result)
        return result
    }

    suspend fun browseFiles(token: String, category: String, page: Int = 1): SearchResponse {
        val cacheKey = CacheManager.browseKey(category, page)
        CacheManager.get<SearchResponse>(cacheKey)?.let { return it }
        Log.d("AFDS_API", "browseFiles: category=$category, page=$page")
        val response = client.get("$BASE_URL/$category/index") {
            header("Authorization", "Bearer $token")
            parameter("page", page)
        }
        Log.d("AFDS_API", "browseFiles response status: ${response.status}")
        if (response.status == HttpStatusCode.Unauthorized) throw ApiException("Session expired", 401)
        if (response.status.value == 429) throw ApiException("Daily limit reached. Try again tomorrow.", 429)
        val bodyText = response.bodyAsText()
        Log.d("AFDS_API", "browseFiles raw response (first 500): ${bodyText.take(500)}")
        val browseResult = json.decodeFromString<SearchResponse>(bodyText)
        CacheManager.put(cacheKey, browseResult)
        return browseResult
    }

    suspend fun getFileDetails(token: String, category: String, fileId: String): FileDetails {
        Log.d("AFDS_API", "getFileDetails: category=$category, fileId=$fileId")
        val response = client.get("$BASE_URL/$category/id") {
            header("Authorization", "Bearer $token")
            parameter("id", fileId)
        }
        if (response.status == HttpStatusCode.Unauthorized) throw ApiException("Session expired", 401)
        if (!response.status.isSuccess()) throw ApiException("Failed to fetch details", response.status.value)
        val bodyText = response.bodyAsText()
        Log.d("AFDS_API", "getFileDetails raw response (first 500): ${bodyText.take(500)}")
        return json.decodeFromString<FileDetails>(bodyText)
    }

    suspend fun generateDownloadLink(token: String, tableName: String, fileId: String): GenLinkResponse {
        val response = client.get("$BASE_URL/genLink") {
            header("Authorization", "Bearer $token")
            parameter("type", tableName)
            parameter("id", fileId)
        }
        if (response.status == HttpStatusCode.Unauthorized) throw ApiException("Session expired", 401)
        if (response.status.value == 429) throw ApiException("Daily link limit reached. Try again tomorrow.", 429)
        return response.body()
    }

    // ---- Profile ----

    suspend fun getProfile(token: String): ProfileResponse {
        val response = client.get("$BASE_URL/profile") {
            header("Authorization", "Bearer $token")
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw ApiException("Session expired", 401)
        }
        return response.body()
    }

    suspend fun changePassword(token: String, currentPassword: String, newPassword: String): MessageResponse {
        val response = client.post("$BASE_URL/profile/change-password") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(ChangePasswordRequest(currentPassword, newPassword))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw ApiException("Session expired", 401)
        }
        if (!response.status.isSuccess()) {
            val error: MessageResponse = response.body()
            throw ApiException(error.error ?: "Failed to change password", response.status.value)
        }
        return response.body()
    }

    suspend fun changeEmail(token: String, newEmail: String, currentPassword: String): MessageResponse {
        val response = client.post("$BASE_URL/profile/change-email") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(ChangeEmailRequest(newEmail, currentPassword))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw ApiException("Session expired", 401)
        }
        if (!response.status.isSuccess()) {
            val error: MessageResponse = response.body()
            throw ApiException(error.error ?: "Failed to change email", response.status.value)
        }
        return response.body()
    }

    suspend fun setTelegramId(token: String, userId: String): MessageResponse {
        val response = client.post("$BASE_URL/profile/set-user-id") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(SetUserIdRequest(userId))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw ApiException("Session expired", 401)
        }
        if (!response.status.isSuccess()) {
            val error: MessageResponse = response.body()
            throw ApiException(error.error ?: "Failed to set Telegram ID", response.status.value)
        }
        return response.body()
    }

    suspend fun updateTelegramId(token: String, userId: String): MessageResponse {
        val response = client.put("$BASE_URL/profile/update-user-id") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(SetUserIdRequest(userId))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw ApiException("Session expired", 401)
        }
        if (!response.status.isSuccess()) {
            val error: MessageResponse = response.body()
            throw ApiException(error.error ?: "Failed to update Telegram ID", response.status.value)
        }
        return response.body()
    }

    suspend fun removeTelegramId(token: String): MessageResponse {
        val response = client.delete("$BASE_URL/profile/remove-user-id") {
            header("Authorization", "Bearer $token")
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw ApiException("Session expired", 401)
        }
        if (!response.status.isSuccess()) {
            val error: MessageResponse = response.body()
            throw ApiException(error.error ?: "Failed to remove Telegram ID", response.status.value)
        }
        return response.body()
    }

    // ---- My Files ----

    suspend fun saveFile(token: String, fileId: String, category: String, fileName: String, fileSize: Long): MessageResponse {
        val response = client.post("$BASE_URL/user/save-file") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(SaveFileRequest(fileId, category, fileName, fileSize))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw ApiException("Session expired", 401)
        }
        if (!response.status.isSuccess()) {
            val error: MessageResponse = response.body()
            throw ApiException(error.error ?: "Failed to save file", response.status.value)
        }
        return response.body()
    }

    suspend fun getMyFiles(token: String, page: Int = 1): SearchResponse {
        val cacheKey = CacheManager.myFilesKey(page)
        CacheManager.get<SearchResponse>(cacheKey)?.let { return it }
        Log.d("AFDS_API", "getMyFiles: page=$page")
        val response = client.get("$BASE_URL/user/my-files") {
            header("Authorization", "Bearer $token")
            parameter("page", page)
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw ApiException("Session expired", 401)
        }
        val bodyText = response.bodyAsText()
        Log.d("AFDS_API", "getMyFiles raw response (first 500): ${bodyText.take(500)}")
        val myFilesResult = json.decodeFromString<SearchResponse>(bodyText)
        CacheManager.put(cacheKey, myFilesResult)
        return myFilesResult
    }

    suspend fun removeFile(token: String, fileId: String, category: String): MessageResponse {
        val response = client.delete("$BASE_URL/user/remove-file") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(RemoveFileRequest(fileId, category))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw ApiException("Session expired", 401)
        }
        if (!response.status.isSuccess()) {
            val error: MessageResponse = response.body()
            throw ApiException(error.error ?: "Failed to remove file", response.status.value)
        }
        CacheManager.invalidateAll()
        return response.body()
    }

    // ---- Channel ----

    suspend fun setChannelId(token: String, channelId: String): MessageResponse {
        val response = client.post("$BASE_URL/profile/set-channel-id") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(SetChannelIdRequest(channelId))
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw ApiException("Session expired", 401)
        }
        if (!response.status.isSuccess()) {
            val error: MessageResponse = response.body()
            throw ApiException(error.error ?: "Failed to set channel ID", response.status.value)
        }
        return response.body()
    }

    suspend fun removeChannelId(token: String): MessageResponse {
        val response = client.delete("$BASE_URL/profile/remove-channel-id") {
            header("Authorization", "Bearer $token")
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw ApiException("Session expired", 401)
        }
        if (!response.status.isSuccess()) {
            val error: MessageResponse = response.body()
            throw ApiException(error.error ?: "Failed to remove channel ID", response.status.value)
        }
        return response.body()
    }

    suspend fun sendToChannel(token: String, uniqueId: String, channelId: String): SendToChannelResponse {
        Log.d("AFDS_API", "sendToChannel: uniqueId=$uniqueId, channelId=$channelId")
        val response = client.post("$BASE_URL/sendToChannel") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(SendToChannelRequest(uniqueId, channelId))
        }
        if (response.status.value == 429) throw ApiException("Daily send limit reached. Try again tomorrow.", 429)
        val bodyText = response.bodyAsText()
        Log.d("AFDS_API", "sendToChannel response: $bodyText")
        return json.decodeFromString<SendToChannelResponse>(bodyText)
    }

    // ---- Update ----

    suspend fun checkForUpdate(): AppUpdateInfo {
        Log.d("AFDS_UPDATE", "Checking for update from GitHub releases")
        val response = client.get(GITHUB_RELEASES_API) {
            header("Accept", "application/vnd.github.v3+json")
        }
        val bodyText = response.bodyAsText()
        Log.d("AFDS_UPDATE", "GitHub release response (first 500): ${bodyText.take(500)}")
        val release = json.decodeFromString<GitHubRelease>(bodyText)
        val version = release.tagName.trimStart('v')
        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
        return AppUpdateInfo(
            version = version,
            changelog = release.body?.trim(),
            forceUpdate = false,
            downloadUrl = apkAsset?.downloadUrl
        )
    }

    fun getApkDownloadUrl(version: String): String {
        return "$APK_BASE_URL/$version.apk"
    }

    fun close() {
        client.close()
    }
}

class ApiException(message: String, val statusCode: Int) : Exception(message)