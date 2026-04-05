package com.afds.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.afds.app.AFDSApplication
import com.afds.app.data.model.AppUpdateInfo
import com.afds.app.data.remote.ApiClient
import com.afds.app.data.remote.ApiException
import com.afds.app.ui.components.AFDSTopBar
import com.afds.app.util.UpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    val telegramSetupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* result handled inside TelegramSetupActivity via sessionManager */ }
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val apiClient = AFDSApplication.instance.apiClient
    val sessionManager = AFDSApplication.instance.sessionManager

    // Profile data
    var email by remember { mutableStateOf("Loading...") }
    var memberSince by remember { mutableStateOf("Loading...") }
    var telegramId by remember { mutableStateOf<String?>(null) }
    var isProfileLoading by remember { mutableStateOf(true) }
    var dailyLinksUsed by remember { mutableIntStateOf(0) }
    var dailyLinksLimit by remember { mutableIntStateOf(100) }
    var dailySendsUsed by remember { mutableIntStateOf(0) }
    var dailySendsLimit by remember { mutableIntStateOf(50) }

    // App update
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateChecked by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }

    // Change password
    var currentPasswordPw by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isChangingPassword by remember { mutableStateOf(false) }

    // Change email
    var newEmail by remember { mutableStateOf("") }
    var currentPasswordEmail by remember { mutableStateOf("") }
    var isChangingEmail by remember { mutableStateOf(false) }

    // Telegram
    var telegramInput by remember { mutableStateOf("") }
    var isSettingTelegram by remember { mutableStateOf(false) }

    // Channel
    var channelId by remember { mutableStateOf<String?>(null) }
    var channelInput by remember { mutableStateOf("") }
    var isSettingChannel by remember { mutableStateOf(false) }

    // Preferences
    val nsfwEnabled by sessionManager.nsfwEnabled.collectAsState(initial = false)
    val mixMediaEnabled by sessionManager.mixMediaEnabled.collectAsState(initial = false)
    val showMyFiles by sessionManager.showMyFiles.collectAsState(initial = false)

    // Local cached values — shown immediately before API responds
    val localChannelId by sessionManager.channelId.collectAsState(initial = null)
    val localUserId by sessionManager.userId.collectAsState(initial = null)

    // Initialize display from local cache while API loads
    LaunchedEffect(localChannelId) {
        if (channelId == null && localChannelId != null) channelId = localChannelId
    }
    LaunchedEffect(localUserId) {
        if (telegramId == null && localUserId != null) telegramId = localUserId
    }

    // Load profile
    LaunchedEffect(Unit) {
        try {
            val token = sessionManager.getToken() ?: run {
                onLogout()
                return@LaunchedEffect
            }
            val profile = apiClient.getProfile(token)
            email = profile.email ?: "Unknown"
            memberSince = profile.memberSince ?: "Unknown"
            telegramId = profile.userId
            channelId = profile.channelId
            dailyLinksUsed = profile.dailyLinksUsed ?: 0
            dailyLinksLimit = profile.dailyLinksLimit ?: 100
            dailySendsUsed = profile.dailySendsUsed ?: 0
            dailySendsLimit = profile.dailySendsLimit ?: 50
            // Save both locally for offline/cache use
            if (profile.channelId != null) {
                sessionManager.setChannelId(profile.channelId)
            }
            if (profile.userId != null) {
                sessionManager.setUserId(profile.userId)
            }
        } catch (e: ApiException) {
            if (e.statusCode == 401) {
                sessionManager.clearSession()
                onLogout()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "Failed to load profile", Toast.LENGTH_SHORT).show()
        } finally {
            isProfileLoading = false
        }
    }

    Scaffold(
        topBar = {
            AFDSTopBar(
                title = "Your Profile",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            sessionManager.clearSession()
                            onLogout()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Account Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isProfileLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        Text("Email", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(email, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Member Since", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(memberSince, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Today's Usage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Downloads", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LinearProgressIndicator(
                                    progress = { if (dailyLinksLimit > 0) dailyLinksUsed.toFloat() / dailyLinksLimit.toFloat() else 0f },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                )
                                Text("$dailyLinksUsed / $dailyLinksLimit", style = MaterialTheme.typography.bodySmall)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Channel Sends", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LinearProgressIndicator(
                                    progress = { if (dailySendsLimit > 0) dailySendsUsed.toFloat() / dailySendsLimit.toFloat() else 0f },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                )
                                Text("$dailySendsUsed / $dailySendsLimit", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Change Password (hidden for now)
            /* Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = currentPasswordPw,
                        onValueChange = { currentPasswordPw = it },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isChangingPassword = true
                                try {
                                    val token = sessionManager.getToken() ?: return@launch
                                    apiClient.changePassword(token, currentPasswordPw, newPassword)
                                    Toast.makeText(context, "Password updated!", Toast.LENGTH_SHORT).show()
                                    currentPasswordPw = ""
                                    newPassword = ""
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isChangingPassword = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isChangingPassword && currentPasswordPw.isNotBlank() && newPassword.isNotBlank()
                    ) {
                        if (isChangingPassword) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Update Password")
                        }
                    }
                }
            }

            */ // End Change Password

            // Change Email (hidden for now)
            /* Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Email", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("New Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = currentPasswordEmail,
                        onValueChange = { currentPasswordEmail = it },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isChangingEmail = true
                                try {
                                    val token = sessionManager.getToken() ?: return@launch
                                    apiClient.changeEmail(token, newEmail, currentPasswordEmail)
                                    Toast.makeText(context, "Email updated!", Toast.LENGTH_SHORT).show()
                                    email = newEmail
                                    newEmail = ""
                                    currentPasswordEmail = ""
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isChangingEmail = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isChangingEmail && newEmail.isNotBlank() && currentPasswordEmail.isNotBlank()
                    ) {
                        if (isChangingEmail) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Update Email")
                        }
                    }
                }
            }

            */ // End Change Email

            // Telegram Integration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Telegram Integration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    val currentTelegramId = telegramId
                    if (currentTelegramId != null) {
                        Text("Current Telegram ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(currentTelegramId, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = telegramInput,
                            onValueChange = { telegramInput = it },
                            label = { Text("New Telegram User ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isSettingTelegram = true
                                        try {
                                            val token = sessionManager.getToken() ?: return@launch
                                            apiClient.updateTelegramId(token, telegramInput)
                                            telegramId = telegramInput
                                            sessionManager.setUserId(telegramInput)
                                            telegramInput = ""
                                            Toast.makeText(context, "Telegram ID updated!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSettingTelegram = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSettingTelegram && telegramInput.isNotBlank()
                            ) {
                                if (isSettingTelegram) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Update")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isSettingTelegram = true
                                        try {
                                            val token = sessionManager.getToken() ?: return@launch
                                            apiClient.removeTelegramId(token)
                                            telegramId = null
                                            sessionManager.setUserId(null)
                                            Toast.makeText(context, "Telegram ID removed!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSettingTelegram = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSettingTelegram,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Remove")
                            }
                        }
                    } else {
                        Text(
                            "Link your Telegram to receive login codes and access My Files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = telegramInput,
                            onValueChange = { telegramInput = it },
                            label = { Text("Telegram User ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isSettingTelegram = true
                                    try {
                                        val token = sessionManager.getToken() ?: return@launch
                                        apiClient.setTelegramId(token, telegramInput)
                                        telegramId = telegramInput
                                        telegramInput = ""
                                        Toast.makeText(context, "Telegram ID connected!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSettingTelegram = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSettingTelegram && telegramInput.isNotBlank()
                        ) {
                            if (isSettingTelegram) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Connect Telegram ID")
                            }
                        }
                    }
                }
            }

            // Telegram Channel (Direct File Delivery)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Podcasts, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Telegram Channel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Direct File Delivery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto Setup button (always visible)
                    Button(
                        onClick = {
                            telegramSetupLauncher.launch(
                                Intent(context, TelegramSetupActivity::class.java)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (channelId != null) "Change Channel (Auto Setup)" else "Auto Setup Channel")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "— or enter channel ID manually below —",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Bot admin warning
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Required: add all bots as channel admins",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "• @TGID1OO1Bot\n• @LinkerXHelperbot (then run /setup)\n• All bots listed in the LiquidXProjects channel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "The API will fail if any bot is missing admin permissions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (channelId != null) {
                        Text("Current Channel ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(channelId!!, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "✅ Files will be sent directly to this channel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = channelInput,
                            onValueChange = { channelInput = it },
                            label = { Text("New Channel ID") },
                            placeholder = { Text("-1001234567890") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isSettingChannel = true
                                        try {
                                            val token = sessionManager.getToken() ?: return@launch
                                            apiClient.setChannelId(token, channelInput)
                                            channelId = channelInput
                                            sessionManager.setChannelId(channelInput)
                                            channelInput = ""
                                            Toast.makeText(context, "Channel ID updated!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSettingChannel = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSettingChannel && channelInput.isNotBlank()
                            ) {
                                if (isSettingChannel) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Update")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isSettingChannel = true
                                        try {
                                            val token = sessionManager.getToken() ?: return@launch
                                            apiClient.removeChannelId(token)
                                            channelId = null
                                            sessionManager.setChannelId(null)
                                            Toast.makeText(context, "Channel ID removed!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isSettingChannel = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSettingChannel,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Remove")
                            }
                        }
                    } else {
                        Text(
                            "Set your Telegram Channel ID to receive files directly in your channel.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = channelInput,
                            onValueChange = { channelInput = it },
                            label = { Text("Channel ID") },
                            placeholder = { Text("-1001234567890") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isSettingChannel = true
                                    try {
                                        val token = sessionManager.getToken() ?: return@launch
                                        apiClient.setChannelId(token, channelInput)
                                        channelId = channelInput
                                        sessionManager.setChannelId(channelInput)
                                        channelInput = ""
                                        Toast.makeText(context, "Channel ID saved!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSettingChannel = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSettingChannel && channelInput.isNotBlank()
                        ) {
                            if (isSettingChannel) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("Save Channel ID")
                        }
                    }
                }
            }

            // Downloader App Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Choose which app to use for downloading files. Stored locally per device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val currentDownloader by sessionManager.downloaderApp.collectAsState(initial = "default")
                    val downloaderOptions = listOf(
                        "default" to "Built-in (Default)",
                        "1dm" to "1DM",
                        "1dm_plus" to "1DM+",
                        "1dm_lite" to "1DM Lite"
                    )

                    downloaderOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentDownloader == key,
                                onClick = {
                                    scope.launch { sessionManager.setDownloaderApp(key) }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (currentDownloader != "default") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "⚠️ Make sure the selected app is installed on your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // App Update
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("App Update", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val currentVersion = UpdateManager.getVersionName(context)
                    Text(
                        "Installed: v$currentVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    when {
                        updateInfo != null -> {
                            val update = updateInfo!!
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "v${update.version} available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (!update.changelog.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            update.changelog,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 6,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        isDownloadingUpdate = true
                                        val downloadUrl = update.downloadUrl
                                            ?: apiClient.getApkDownloadUrl(update.version)
                                        UpdateManager.downloadAndInstallUpdate(context, downloadUrl, update.version)
                                    },
                                    enabled = !isDownloadingUpdate,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isDownloadingUpdate) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Downloading…")
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Install Update")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ApiClient.GITHUB_RELEASES_PAGE))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("GitHub")
                                }
                            }
                        }
                        updateChecked -> {
                            Text(
                                "✓ You're on the latest version.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isCheckingUpdate = true
                                        updateChecked = false
                                        try {
                                            val remote = apiClient.checkForUpdate()
                                            if (UpdateManager.isNewerVersion(remote.version, currentVersion)) {
                                                updateInfo = remote
                                            } else {
                                                updateChecked = true
                                            }
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Could not check for updates", Toast.LENGTH_SHORT).show()
                                            updateChecked = true
                                        } finally {
                                            isCheckingUpdate = false
                                        }
                                    }
                                },
                                enabled = !isCheckingUpdate,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Check Again")
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isCheckingUpdate = true
                                        try {
                                            val remote = apiClient.checkForUpdate()
                                            if (UpdateManager.isNewerVersion(remote.version, currentVersion)) {
                                                updateInfo = remote
                                            } else {
                                                updateChecked = true
                                            }
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Could not check for updates", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isCheckingUpdate = false
                                        }
                                    }
                                },
                                enabled = !isCheckingUpdate,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Checking…")
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Check for Updates")
                                }
                            }
                        }
                    }
                }
            }

            // Content Preferences
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Content Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // NSFW Toggle (disabled for now)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show NSFW Content", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Coming soon", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = false,
                            onCheckedChange = { /* Disabled for now */ },
                            enabled = false
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // My Files Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show My Files", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Display My Files on homepage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = showMyFiles,
                            onCheckedChange = { scope.launch { sessionManager.setShowMyFiles(it) } }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Mix Media Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show MIX Files", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Enable MIX Files category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = mixMediaEnabled,
                            onCheckedChange = { scope.launch { sessionManager.setMixMediaEnabled(it) } }
                        )
                    }
                }
            }

            // Logout button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        sessionManager.clearSession()
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}