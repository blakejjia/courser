@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.gdrive.GDriveRepository
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.DriveFolderEntry
import com.theveloper.pixelplay.data.repository.LocalFolderEntry
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.gdrive.auth.GDriveLoginActivity
import com.theveloper.pixelplay.presentation.viewmodel.CourseFolderSelectionViewModel
import com.theveloper.pixelplay.presentation.viewmodel.CourseSourceTab
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import java.io.File

@Composable
fun CourseFolderSelectionScreen(
    onBack: () -> Unit,
    viewModel: CourseFolderSelectionViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    isSetupFlow: Boolean = false,
    onContinueSetup: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isMiniPlayerVisible = !isSetupFlow && stablePlayerState.currentSong != null && stablePlayerState.currentSong != Song.emptySong()
    val fabBottomPadding by animateDpAsState(
        targetValue = if (isMiniPlayerVisible) MiniPlayerHeight + 12.dp else 0.dp,
        label = "fabBottomPadding"
    )

    val gdriveLoginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.refreshDriveLoginState()
        }
        viewModel.selectTab(CourseSourceTab.GDRIVE)
    }

    // Intercept back button to navigate up folders first if inside a subfolder
    BackHandler {
        val handled = if (uiState.currentTab == CourseSourceTab.LOCAL) {
            viewModel.navigateLocalUp()
        } else {
            viewModel.navigateDriveUp()
        }
        if (!handled) {
            if (uiState.hasChanges) {
                viewModel.applyChanges { onBack() }
            } else {
                onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when {
                        isSetupFlow -> "Select Course Folders"
                        uiState.currentTab == CourseSourceTab.LOCAL -> "Local Course Folders"
                        else -> "Google Drive Folders"
                    }
                    val selectedCount = if (uiState.currentTab == CourseSourceTab.LOCAL) {
                        uiState.checkedLocalPaths.size
                    } else {
                        uiState.checkedDriveFolderIds.size
                    }
                    Column {
                        Text(
                            text = titleText,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "$selectedCount course ${if (selectedCount == 1) "folder" else "folders"} selected",
                            fontFamily = GoogleSansRounded,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (!isSetupFlow) {
                        IconButton(onClick = {
                            val handled = if (uiState.currentTab == CourseSourceTab.LOCAL) {
                                viewModel.navigateLocalUp()
                            } else {
                                viewModel.navigateDriveUp()
                            }
                            if (!handled) {
                                if (uiState.hasChanges) {
                                    viewModel.applyChanges { onBack() }
                                } else {
                                    onBack()
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.applyChanges {
                        if (isSetupFlow && onContinueSetup != null) {
                            onContinueSetup()
                        } else {
                            onBack()
                        }
                    }
                },
                modifier = Modifier.padding(bottom = fabBottomPadding),
                expanded = true,
                icon = {
                    if (uiState.isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Apply courses"
                        )
                    }
                },
                text = {
                    val label = when {
                        uiState.isApplying -> "Syncing..."
                        isSetupFlow -> "Continue (${uiState.totalSelectedCoursesCount})"
                        uiState.hasChanges -> "Apply Changes"
                        else -> "Done"
                    }
                    Text(
                        text = label,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.currentTab) {
                CourseSourceTab.LOCAL -> {
                    LocalFolderExplorerContent(
                        breadcrumbs = uiState.localBreadcrumbs,
                        folders = uiState.localFolders,
                        isLoading = uiState.isLocalLoading,
                        bottomPadding = fabBottomPadding + 88.dp,
                        onBreadcrumbClick = { index -> viewModel.navigateLocalBreadcrumb(index) },
                        onFolderClick = { folder -> viewModel.navigateLocalInto(folder) },
                        onToggleChecked = { folder -> viewModel.toggleLocalFolderChecked(folder) }
                    )
                }
                CourseSourceTab.GDRIVE -> {
                    if (!uiState.isGDriveLoggedIn) {
                        GDriveSignInPrompt(
                            onSignIn = {
                                gdriveLoginLauncher.launch(Intent(context, GDriveLoginActivity::class.java))
                            }
                        )
                    } else {
                        GDriveFolderExplorerContent(
                            userName = uiState.gdriveUserName,
                            breadcrumbs = uiState.currentDriveBreadcrumb,
                            folders = uiState.driveFolders,
                            isLoading = uiState.isDriveLoading,
                            bottomPadding = fabBottomPadding + 88.dp,
                            onBreadcrumbClick = { index -> viewModel.navigateDriveBreadcrumb(index) },
                            onFolderClick = { folder -> viewModel.navigateDriveInto(folder) },
                            onToggleChecked = { folder -> viewModel.toggleDriveFolderChecked(folder) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalFolderExplorerContent(
    breadcrumbs: List<File>,
    folders: List<LocalFolderEntry>,
    isLoading: Boolean,
    bottomPadding: Dp = 96.dp,
    onBreadcrumbClick: (Int) -> Unit,
    onFolderClick: (File) -> Unit,
    onToggleChecked: (File) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BreadcrumbsBar(
            items = breadcrumbs.map { if (it.name.isEmpty()) "Storage" else it.name },
            onItemClick = onBreadcrumbClick
        )

        Spacer(Modifier.height(4.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (folders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No subfolders found in this directory.\nCheck parent folder or navigate to another path.",
                    fontFamily = GoogleSansRounded,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                items(folders, key = { it.file.absolutePath }) { entry ->
                    FolderRowItem(
                        title = entry.name,
                        subtitle = "${entry.audioCount} audio lesson${if (entry.audioCount == 1) "" else "s"}",
                        isChecked = entry.isChecked,
                        onClick = { onFolderClick(entry.file) },
                        onCheckedChange = { onToggleChecked(entry.file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GDriveFolderExplorerContent(
    userName: String?,
    breadcrumbs: List<GDriveRepository.DriveFolder>,
    folders: List<DriveFolderEntry>,
    isLoading: Boolean,
    bottomPadding: Dp = 96.dp,
    onBreadcrumbClick: (Int) -> Unit,
    onFolderClick: (GDriveRepository.DriveFolder) -> Unit,
    onToggleChecked: (DriveFolderEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!userName.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Connected as $userName",
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        BreadcrumbsBar(
            items = breadcrumbs.map { it.name },
            onItemClick = onBreadcrumbClick
        )

        Spacer(Modifier.height(4.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (folders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No subfolders found in this Drive folder.",
                    fontFamily = GoogleSansRounded,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                items(folders, key = { it.id }) { entry ->
                    FolderRowItem(
                        title = entry.name,
                        subtitle = "Google Drive folder",
                        isChecked = entry.isChecked,
                        onClick = { onFolderClick(GDriveRepository.DriveFolder(entry.id, entry.name)) },
                        onCheckedChange = { onToggleChecked(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbsBar(
    items: List<String>,
    onItemClick: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, crumb ->
            val isLast = index == items.lastIndex
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isLast) { onItemClick(index) },
                color = if (isLast) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = crumb,
                    fontFamily = GoogleSansRounded,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLast) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderRowItem(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isChecked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "FolderRowBackground"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        tonalElevation = if (isChecked) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isChecked) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontFamily = GoogleSansRounded,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
private fun GDriveSignInPrompt(onSignIn: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = "Connect Google Drive",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Sign in to access and select course folders directly from your Google Drive. Once connected, your session will stay active.",
                    fontFamily = GoogleSansRounded,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Button(
                    onClick = onSignIn,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Sign in with Google",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
