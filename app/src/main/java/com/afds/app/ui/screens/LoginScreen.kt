package com.afds.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.afds.app.AFDSApplication
import com.afds.app.R
import com.afds.app.data.remote.ApiException
import com.afds.app.data.remote.PasskeyManager
import com.afds.app.util.normalizeEmail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onSetupNeeded: () -> Unit = onLoginSuccess,
    onGoogleLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val apiClient = AFDSApplication.instance.apiClient
    val sessionManager = AFDSApplication.instance.sessionManager
    val passkeyManager = remember { PasskeyManager(context) }
    val passkeySupported = Build.VERSION.SDK_INT >= 28

    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var isEmailStep by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var isSlowRequest by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loginType by remember { mutableStateOf("") }
    var botId by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Icon(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "AFDS Logo",
                modifier = Modifier.size(120.dp),
                tint = Color.Unspecified
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "AFDS",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Advanced File Discovery System",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Login Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Error message
                    AnimatedVisibility(visible = errorMessage != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    if (isEmailStep) {
                        // Email Step
                        Text(
                            text = "Sign In",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                errorMessage = null
                                if (email.isBlank() || !email.contains("@")) {
                                    errorMessage = "Please enter a valid email address"
                                    return@Button
                                }
                                scope.launch {
                                    isLoading = true
                                    isSlowRequest = false
                                    val slowJob = launch { delay(10_000); isSlowRequest = true }
                                    try {
                                        val normalizedEmail = normalizeEmail(email)
                                        val response = withTimeout(30_000L) { apiClient.requestLoginOtp(normalizedEmail) }
                                        if (response.error != null) {
                                            errorMessage = response.error + "\n\nVisit https://afds.pages.dev to create an account."
                                        } else {
                                            loginType = response.loginType ?: "email"
                                            botId = response.botId ?: ""
                                            isEmailStep = false
                                        }
                                    } catch (e: TimeoutCancellationException) {
                                        errorMessage = "Request timed out. Please check your connection and try again."
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        val msg = e.message ?: "Failed to send login code"
                                        errorMessage = if (msg.contains("not found", ignoreCase = true) || msg.contains("not exist", ignoreCase = true) || msg.contains("no account", ignoreCase = true)) {
                                            "Account not found.\n\nVisit https://afds.pages.dev to create an account."
                                        } else msg
                                    } finally {
                                        slowJob.cancel()
                                        isSlowRequest = false
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isSlowRequest) "Still sending..." else "Sending Code...")
                            } else {
                                Icon(Icons.Default.Email, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Get Login Code")
                            }
                        }
                    } else {
                        // OTP Step
                        Text(
                            text = "Verify Code",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Enter the 6-digit code sent to",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Delivery indicator
                        if (loginType == "telegram" && botId.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val botAnnotated = buildAnnotatedString {
                                        append("Code sent to Telegram ")
                                        addLink(
                                            LinkAnnotation.Url(
                                                url = "https://t.me/$botId",
                                                styles = TextLinkStyles(
                                                    style = SpanStyle(
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        textDecoration = TextDecoration.Underline
                                                    )
                                                )
                                            ),
                                            start = length,
                                            end = length + "@$botId".length
                                        )
                                        append("@$botId")
                                    }
                                    Text(
                                        text = botAnnotated,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.MarkEmailRead,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Code sent to your email!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = otp,
                            onValueChange = { if (it.length <= 6) otp = it.filter { c -> c.isDigit() } },
                            label = { Text("Verification Code") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                errorMessage = null
                                if (otp.length != 6) {
                                    errorMessage = "Please enter a 6-digit code"
                                    return@Button
                                }
                                scope.launch {
                                    isLoading = true
                                    isSlowRequest = false
                                    val slowJob = launch { delay(10_000); isSlowRequest = true }
                                    try {
                                        val normalizedEmail = normalizeEmail(email)
                                        val response = withTimeout(30_000L) { apiClient.verifyLoginOtp(normalizedEmail, otp) }
                                        if (response.token != null) {
                                            sessionManager.saveToken(response.token)
                                            try {
                                                val profile = apiClient.getProfile(response.token)
                                                sessionManager.saveProfileData(profile.email, profile.userId, profile.channelId)
                                            } catch (_: Exception) { }
                                            Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                            onLoginSuccess()
                                        } else {
                                            errorMessage = "Verification failed"
                                        }
                                    } catch (e: TimeoutCancellationException) {
                                        errorMessage = "Request timed out. Please check your connection and try again."
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Verification failed"
                                    } finally {
                                        slowJob.cancel()
                                        isSlowRequest = false
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isSlowRequest) "Still verifying..." else "Verifying...")
                            } else {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verify & Sign In")
                            }
                        }

                        // Request email code button (when Telegram delivery)
                        if (loginType == "telegram") {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val normalizedEmail = normalizeEmail(email)
                                            apiClient.requestLoginOtpEmail(normalizedEmail)
                                            loginType = "email"
                                            Toast.makeText(context, "Code sent to your email!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            errorMessage = e.message ?: "Failed to send email code"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Can't access Telegram? Send to Email")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                isEmailStep = true
                                otp = ""
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Back to Email")
                        }
                    }
                }
            }

            // Google Sign-In (email step only)
            if (isEmailStep) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "  OR  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onGoogleLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continue with Google")
                }

                // Passkey Sign-In (platform passkeys require API 28+)
                if (passkeySupported) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            errorMessage = null
                            scope.launch {
                                isLoading = true
                                isSlowRequest = false
                                val slowJob = launch { delay(10_000); isSlowRequest = true }
                                try {
                                    // If a valid email is already entered, narrow to that user's passkeys.
                                    val narrowEmail = email.takeIf { it.contains("@") }?.let { normalizeEmail(it) }
                                    val opt = apiClient.passkeyLoginOptions(narrowEmail)
                                    val assertionJson = passkeyManager.authenticate(opt.optionsJson)
                                    val token = apiClient.passkeyLoginVerify(assertionJson, opt.handle)
                                    sessionManager.saveToken(token)
                                    try {
                                        val profile = apiClient.getProfile(token)
                                        sessionManager.saveProfileData(profile.email, profile.userId, profile.channelId)
                                    } catch (_: Exception) { }
                                    Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } catch (e: GetCredentialCancellationException) {
                                    // User cancelled the system prompt — stay silent.
                                } catch (e: NoCredentialException) {
                                    errorMessage = "No passkey found on this device. Use email or Google to sign in."
                                } catch (e: ApiException) {
                                    errorMessage = e.message ?: "Passkey sign-in failed"
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: GetCredentialException) {
                                    errorMessage = e.message ?: "Passkey sign-in failed"
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Passkey sign-in failed"
                                } finally {
                                    slowJob.cancel()
                                    isSlowRequest = false
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with a passkey")
                    }
                }
            }
        }
    }
}