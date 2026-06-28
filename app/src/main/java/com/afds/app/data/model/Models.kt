package com.afds.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class SearchResponse(
    val files: List<FileItem> = emptyList(),
    @SerialName("total_pages") val totalPages: JsonElement? = null,
    @SerialName("current_page") val currentPage: JsonElement? = null,
    @SerialName("total_files") val totalFiles: JsonElement? = null
) {
    val totalPagesInt: Int get() = totalPages.toIntSafe() ?: 1
    val currentPageInt: Int get() = currentPage.toIntSafe() ?: 1
    val totalFilesInt: Int get() = totalFiles.toIntSafe() ?: 0
}

@Serializable
data class FileItem(
    val id: JsonElement? = null,
    @SerialName("file_id") val fileId: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_size") val fileSize: JsonElement? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    val caption: String? = null,
    val category: String? = null,
    @SerialName("source_table") val sourceTable: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("saved_at") val savedAt: String? = null
) {
    val displayName: String get() = fileName ?: caption ?: "Unnamed File"
    // Prefer file_id over id because:
    // - Search results: id = telegram file ID (string), file_id = null
    // - My Files results: id = DB row ID (number), file_id = telegram file ID (string)
    val idString: String? get() {
        if (id == null) return null
        return when (id) {
            is JsonPrimitive -> id.content
            else -> null
        }
    }
    val effectiveId: String get() = fileId ?: idString ?: ""
    val fileSizeLong: Long get() = fileSize.toLongSafe() ?: 0L
    val effectiveCategory: String get() {
        val raw = category ?: sourceTable ?: return ""
        return TABLE_TO_SHORT_MAP[raw] ?: raw
    }

    companion object {
        val TABLE_TO_SHORT_MAP = mapOf(
            "files" to "files",
            "nsfw_files" to "nsfw",
            "music_files" to "music",
            "mix_media_files" to "mix_media"
        )
    }
}

@Serializable
data class FileDetails(
    val id: String? = null,
    @SerialName("unique_id") val uniqueId: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_size") val fileSize: JsonElement? = null,
    @SerialName("file_size_bytes") val fileSizeBytes: JsonElement? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_type") val fileType: String? = null,
    val caption: String? = null
) {
    val fileSizeLong: Long get() = fileSizeBytes.toLongSafe() ?: fileSize.toLongSafe() ?: 0L
    val effectiveMimeType: String? get() = mimeType ?: fileType
}

@Serializable
data class GenLinkResponse(
    val success: Boolean = false,
    val url: String? = null,
    val error: String? = null
)

@Serializable
data class LoginRequest(
    val email: String
)

@Serializable
data class OtpRequest(
    val email: String,
    val code: String
)

@Serializable
data class OtpResponse(
    val token: String? = null,
    val error: String? = null,
    @SerialName("login_type") val loginType: String? = null,
    @SerialName("bot_id") val botId: String? = null,
    val message: String? = null
)

@Serializable
data class GoogleAuthRequest(
    val credential: String
)

@Serializable
data class AuthResponse(
    val token: String? = null,
    val error: String? = null
)

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

@Serializable
data class ProfileResponse(
    val id: Int? = null,
    val email: String? = null,
    @SerialName("memberSince") val memberSince: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_id_updated_at") val userIdUpdatedAt: String? = null,
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("daily_links_used") val dailyLinksUsed: Int? = null,
    @SerialName("daily_links_limit") val dailyLinksLimit: Int? = null,
    @SerialName("daily_sends_used") val dailySendsUsed: Int? = null,
    @SerialName("daily_sends_limit") val dailySendsLimit: Int? = null,
    val error: String? = null
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class ChangeEmailRequest(
    val newEmail: String,
    val currentPassword: String
)

@Serializable
data class SetUserIdRequest(
    @SerialName("user_id") val userId: String
)

@Serializable
data class SaveFileRequest(
    @SerialName("file_id") val fileId: String,
    val category: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_size") val fileSize: Long
)

@Serializable
data class RemoveFileRequest(
    @SerialName("file_id") val fileId: String,
    val category: String
)

@Serializable
data class MessageResponse(
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class SetChannelIdRequest(
    @SerialName("channel_id") val channelId: String
)

@Serializable
data class SendToChannelRequest(
    @SerialName("unique_id") val uniqueId: String,
    @SerialName("channel_id") val channelId: String
)

@Serializable
data class SendToChannelResponse(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

// Extension functions to safely parse JsonElement to Int/Long
fun JsonElement?.toIntSafe(): Int? {
    if (this == null) return null
    return try {
        when (this) {
            is JsonPrimitive -> intOrNull ?: content.toIntOrNull()
            else -> null
        }
    } catch (_: Exception) { null }
}

fun JsonElement?.toLongSafe(): Long? {
    if (this == null) return null
    return try {
        when (this) {
            is JsonPrimitive -> longOrNull ?: content.toLongOrNull()
            else -> null
        }
    } catch (_: Exception) { null }
}

enum class FileCategory(
    val apiName: String,
    val shortName: String,
    val displayName: String
) {
    MEDIA("files", "files", "Media"),
    MUSIC("music_files", "music", "Music"),
    NSFW("nsfw_files", "nsfw", "NSFW"),
    MIX_MEDIA("mix_media_files", "mix_media", "Mix Media");

    companion object {
        fun fromApiName(name: String): FileCategory =
            entries.find { it.apiName == name } ?: MEDIA

        fun fromShortName(name: String): FileCategory =
            entries.find { it.shortName == name } ?: MEDIA
    }

    fun getTelegramPrefix(fileId: String): String = when (this) {
        MEDIA -> "files-$fileId"
        MUSIC -> "music-$fileId"
        NSFW -> "nsfw-$fileId"
        MIX_MEDIA -> "mix-media-files-$fileId"
    }

    fun getTelegramBotUrl(fileId: String): String =
        "https://telegram.dog/TGID1OO1Bot?start=${getTelegramPrefix(fileId)}"

    fun getTableName(): String = when (this) {
        MEDIA -> "files"
        MUSIC -> "music_files"
        NSFW -> "nsfw_files"
        MIX_MEDIA -> "mix_media_files"
    }
}