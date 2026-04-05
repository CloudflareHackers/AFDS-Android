package com.afds.app.util

import java.text.DecimalFormat
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

fun formatBytes(bytes: Long?, decimals: Int = 2): String {
    if (bytes == null || bytes == 0L) return "0 Bytes"
    if (bytes < 0L) return "Unknown"
    val k = 1024.0
    val sizes = arrayOf("Bytes", "KB", "MB", "GB", "TB")
    val i = floor(log10(bytes.toDouble()) / log10(k)).toInt()
    val value = bytes / k.pow(i)
    val df = DecimalFormat("#.${"#".repeat(decimals.coerceAtLeast(0))}")
    return "${df.format(value)} ${sizes[i]}"
}

fun normalizeEmail(email: String): String {
    val emailLower = email.lowercase().trim()
    if (emailLower.endsWith("@gmail.com")) {
        val parts = emailLower.split("@")
        val normalizedLocal = parts[0].replace(".", "")
        return "$normalizedLocal@${parts[1]}"
    }
    return emailLower
}