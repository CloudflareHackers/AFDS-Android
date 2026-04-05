package com.afds.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.afds.app.AFDSApplication
import com.afds.app.data.remote.ApiClient
import com.afds.app.data.remote.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = AFDSApplication.instance.sessionManager
    val apiClient = AFDSApplication.instance.apiClient

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun doSignIn() {
        isLoading = true
        errorMessage = null
        try {
            val idToken = getGoogleIdToken(context) ?: run {
                // user cancelled
                isLoading = false
                onBack()
                return
            }
            val authResponse = apiClient.googleAuth(idToken)
            val token = authResponse.token ?: throw Exception("No token in response")
            sessionManager.saveToken(token)
            try {
                val profile = apiClient.getProfile(token)
                sessionManager.saveProfileData(profile.email, profile.userId, profile.channelId)
            } catch (_: Exception) { }
            onLoginSuccess()
        } catch (e: NoCredentialException) {
            isLoading = false
            errorMessage = "No Google account found on this device. Please add one in Settings."
        } catch (e: ApiException) {
            isLoading = false
            errorMessage = e.message ?: "Sign-in failed"
        } catch (e: Exception) {
            isLoading = false
            errorMessage = e.message ?: "Sign-in failed"
        }
    }

    // Auto-trigger on open
    LaunchedEffect(Unit) { doSignIn() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in with Google") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isLoading) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Signing in with Google...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    errorMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Button(
                        onClick = { scope.launch { doSignIn() } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue with Google")
                    }
                    TextButton(onClick = onBack) { Text("Cancel") }
                }
            }
        }
    }
}

/** Returns the Google ID token, or null if the user cancelled. Throws on all other errors. */
private suspend fun getGoogleIdToken(context: android.content.Context): String? {
    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(ApiClient.GOOGLE_CLIENT_ID)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    return try {
        val result = credentialManager.getCredential(context = context, request = request)
        val credential = result.credential
        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            throw Exception("Unexpected credential type: ${credential.type}")
        }
    } catch (e: GetCredentialCancellationException) {
        null
    }
}
