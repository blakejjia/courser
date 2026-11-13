package com.theveloper.pixelplay.presentation.gdrive.auth

import android.accounts.Account
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.theveloper.pixelplay.data.gdrive.GDriveConstants
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import timber.log.Timber

@AndroidEntryPoint
class GDriveLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayTheme {
                GDriveLoginScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GDriveLoginScreen(
    viewModel: GDriveLoginViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val loginState by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp, cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )

    var pendingEmail by remember { mutableStateOf<String?>(null) }
    var pendingDisplayName by remember { mutableStateOf<String?>(null) }
    var pendingIdToken by remember { mutableStateOf<String?>(null) }
    var pendingAvatar by remember { mutableStateOf<String?>(null) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == android.app.Activity.RESULT_OK && activityResult.data != null) {
            try {
                val authResult = Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(activityResult.data)
                val accessToken = authResult.accessToken
                val serverAuthCode = authResult.serverAuthCode
                if (!serverAuthCode.isNullOrBlank()) {
                    viewModel.processCredential(
                        idToken = pendingIdToken ?: "",
                        serverAuthCode = serverAuthCode,
                        email = pendingEmail,
                        displayName = pendingDisplayName,
                        profilePictureUri = pendingAvatar
                    )
                } else if (!accessToken.isNullOrBlank()) {
                    viewModel.loginWithAccessToken(
                        accessToken = accessToken,
                        email = pendingEmail,
                        displayName = pendingDisplayName
                    )
                } else {
                    viewModel.setError("Failed to retrieve Google Drive authorization")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error extracting authorization result")
                viewModel.setError(e.message ?: "Authorization failed")
            }
        } else {
            Timber.w("Authorization cancelled or failed: resultCode=${activityResult.resultCode}")
            viewModel.setError("Google Drive authorization cancelled")
        }
    }

    // Handle state transitions
    LaunchedEffect(loginState) {
        when (loginState) {
            is GDriveLoginState.Success -> {
                Toast.makeText(context, context.getString(R.string.auth_gdrive_connected_toast), Toast.LENGTH_SHORT).show()
                (context as? android.app.Activity)?.setResult(android.app.Activity.RESULT_OK)
                onClose()
            }
            is GDriveLoginState.Error -> {
                Toast.makeText(
                    context,
                    (loginState as GDriveLoginState.Error).message,
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.auth_gdrive_title),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = loginState) {
                is GDriveLoginState.Idle, is GDriveLoginState.Error -> {
                    // Sign-in screen
                    SignInContent(
                        cardShape = cardShape,
                        isError = state is GDriveLoginState.Error,
                        errorMessage = (state as? GDriveLoginState.Error)?.message,
                        onSignIn = {
                            scope.launch {
                                signInWithGoogle(
                                    context = context,
                                    viewModel = viewModel,
                                    authLauncher = authLauncher,
                                    onPendingAccount = { idToken, email, displayName, avatar ->
                                        pendingIdToken = idToken
                                        pendingEmail = email
                                        pendingDisplayName = displayName
                                        pendingAvatar = avatar
                                    }
                                )
                            }
                        }
                    )
                }

                is GDriveLoginState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.auth_gdrive_setup_msg),
                                fontFamily = GoogleSansRounded,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                is GDriveLoginState.Success -> {
                    // Handled by LaunchedEffect
                }
            }
        }
    }
}

@Composable
private fun SignInContent(
    cardShape: AbsoluteSmoothCornerShape,
    isError: Boolean,
    errorMessage: String?,
    onSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Google Drive icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.CloudQueue,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            stringResource(R.string.auth_gdrive_connect_title),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.auth_gdrive_connect_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onSignIn,
            shape = cardShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                stringResource(R.string.auth_gdrive_signin_action),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (isError && errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

private suspend fun signInWithGoogle(
    context: android.content.Context,
    viewModel: GDriveLoginViewModel,
    authLauncher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    onPendingAccount: (idToken: String?, email: String?, displayName: String?, avatar: String?) -> Unit
) {
    if (!GDriveConstants.isConfigured()) {
        val configMsg = "Google Client ID is not configured. Add GDRIVE_WEB_CLIENT_ID to local.properties or GDriveConstants.kt."
        Toast.makeText(context, configMsg, Toast.LENGTH_LONG).show()
        viewModel.setError(configMsg)
        return
    }

    try {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(GDriveConstants.WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(null)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(
            request = request,
            context = context as android.app.Activity
        )

        val credential = result.credential
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val idToken = googleIdTokenCredential.idToken
        val email = googleIdTokenCredential.id
        val displayName = googleIdTokenCredential.displayName
        val avatar = googleIdTokenCredential.profilePictureUri?.toString()

        onPendingAccount(idToken, email, displayName, avatar)

        // Request Google Drive OAuth scope using Google Identity AuthorizationClient
        val requestedScopes = listOf(Scope(GDriveConstants.SCOPE_DRIVE_READONLY))
        val authRequestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)

        if (email.isNotBlank()) {
            authRequestBuilder.setAccount(Account(email, "com.google"))
        }

        try {
            authRequestBuilder.requestOfflineAccess(GDriveConstants.WEB_CLIENT_ID)
        } catch (e: Exception) {
            Timber.w(e, "requestOfflineAccess builder warning")
        }

        Identity.getAuthorizationClient(context)
            .authorize(authRequestBuilder.build())
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val pendingIntent = authResult.pendingIntent
                    if (pendingIntent != null) {
                        authLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        viewModel.setError("Authorization resolution missing")
                    }
                } else {
                    val accessToken = authResult.accessToken
                    val serverAuthCode = authResult.serverAuthCode
                    if (!serverAuthCode.isNullOrBlank()) {
                        viewModel.processCredential(
                            idToken = idToken,
                            serverAuthCode = serverAuthCode,
                            email = email,
                            displayName = displayName,
                            profilePictureUri = avatar
                        )
                    } else if (!accessToken.isNullOrBlank()) {
                        viewModel.loginWithAccessToken(
                            accessToken = accessToken,
                            email = email,
                            displayName = displayName
                        )
                    } else {
                        viewModel.setError("Drive access token not returned")
                    }
                }
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Identity authorize failed")
                viewModel.setError("Google Drive authorization failed: ${e.message}")
            }
    } catch (e: GetCredentialException) {
        Timber.e(e, "Google Sign-In credential exception")
        viewModel.setError(e.message ?: "Google Sign-In failed")
    } catch (e: Exception) {
        Timber.e(e, "Google Sign-In failed")
        viewModel.setError(e.message ?: "Google Sign-In failed")
    }
}
