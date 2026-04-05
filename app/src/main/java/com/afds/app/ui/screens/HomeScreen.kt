package com.afds.app.ui.screens

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.afds.app.AFDSApplication
import com.afds.app.R
import android.widget.Toast
import com.afds.app.data.local.CacheManager
import com.afds.app.data.model.FileCategory
import com.afds.app.util.UpdateManager
import kotlinx.coroutines.launch
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearch: (String, String) -> Unit,
    onBrowse: (String) -> Unit,
    onProfile: () -> Unit,
    onMyFiles: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sessionManager = AFDSApplication.instance.sessionManager
    val apiClient = AFDSApplication.instance.apiClient
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(FileCategory.MEDIA) }
    var totalFilesCount by remember { mutableStateOf<String?>(null) }
    // NSFW disabled for now - force false regardless of stored preference
    val nsfwEnabled = false
    val mixMediaEnabled by sessionManager.mixMediaEnabled.collectAsState(initial = false)
    val showMyFiles by sessionManager.showMyFiles.collectAsState(initial = false)
    val context = LocalContext.current

    // Check session validity and fetch total files count
    LaunchedEffect(Unit) {
        if (sessionManager.getToken() == null) {
            onLogout()
            return@LaunchedEffect
        }
        try {
            val token = sessionManager.getToken() ?: return@LaunchedEffect
            val response = apiClient.browseFiles(token, "mix_media_files", 1)
            totalFilesCount = NumberFormat.getInstance().format(response.totalFilesInt)
        } catch (_: Exception) {
            totalFilesCount = "millions of"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AFDS") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                actions = {
                    // Refresh profile
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                val token = sessionManager.getToken() ?: return@launch
                                val profile = apiClient.getProfile(token)
                                sessionManager.saveProfileData(profile.email, profile.userId, profile.channelId)
                                CacheManager.invalidateAll()
                                Toast.makeText(context, "Profile refreshed!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Refresh failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Profile")
                    }
                    IconButton(onClick = onProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Logo
            Icon(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "AFDS Logo",
                modifier = Modifier.size(100.dp),
                tint = Color.Unspecified
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "AFDS Search",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Category Selection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = selectedCategory == FileCategory.MEDIA,
                        onClick = { selectedCategory = FileCategory.MEDIA },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = getVisibleCategoryCount(nsfwEnabled, mixMediaEnabled)),
                        icon = { Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("Media", style = MaterialTheme.typography.labelSmall)
                    }
                    SegmentedButton(
                        selected = selectedCategory == FileCategory.MUSIC,
                        onClick = { selectedCategory = FileCategory.MUSIC },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = getVisibleCategoryCount(nsfwEnabled, mixMediaEnabled)),
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("Music", style = MaterialTheme.typography.labelSmall)
                    }
                    if (nsfwEnabled) {
                        SegmentedButton(
                            selected = selectedCategory == FileCategory.NSFW,
                            onClick = { selectedCategory = FileCategory.NSFW },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = 2,
                                count = getVisibleCategoryCount(nsfwEnabled, mixMediaEnabled)
                            ),
                            icon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text("NSFW", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (mixMediaEnabled) {
                        SegmentedButton(
                            selected = selectedCategory == FileCategory.MIX_MEDIA,
                            onClick = { selectedCategory = FileCategory.MIX_MEDIA },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = getVisibleCategoryCount(nsfwEnabled, mixMediaEnabled) - 1,
                                count = getVisibleCategoryCount(nsfwEnabled, mixMediaEnabled)
                            ),
                            icon = { Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text("MIX", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search AFDS...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            if (searchQuery.isNotBlank()) {
                                onSearch(searchQuery.trim(), selectedCategory.apiName)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Search button
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (searchQuery.isNotBlank()) {
                            onSearch(searchQuery.trim(), selectedCategory.apiName)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = searchQuery.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search")
                }

                // Files count
                totalFilesCount?.let { count ->
                    Text(
                        text = "Search from $count files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Browse Section
            Text(
                text = "Browse Files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onBrowse(FileCategory.MEDIA.apiName) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Media")
                    }
                    FilledTonalButton(
                        onClick = { onBrowse(FileCategory.MUSIC.apiName) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Music")
                    }
                }

                if (nsfwEnabled || mixMediaEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (nsfwEnabled) {
                            FilledTonalButton(
                                onClick = { onBrowse(FileCategory.NSFW.apiName) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("NSFW")
                            }
                        }
                        if (mixMediaEnabled) {
                            FilledTonalButton(
                                onClick = { onBrowse(FileCategory.MIX_MEDIA.apiName) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("MIX")
                            }
                        }
                    }
                }
            }

            // My Files
            if (showMyFiles) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onMyFiles,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("My Files")
                }
            }

            // Version info
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "v${UpdateManager.getVersionName(context)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

}

private fun getVisibleCategoryCount(nsfwEnabled: Boolean, mixMediaEnabled: Boolean): Int {
    var count = 2 // Media + Music always visible
    if (nsfwEnabled) count++
    if (mixMediaEnabled) count++
    return count
}