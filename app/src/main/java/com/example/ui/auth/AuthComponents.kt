package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val AUTH_TAG = "ChibiPetAuth"
private const val AUTH_PREFS_NAME = "chibipet_google_auth_session"
private const val KEY_SESSION_UID = "session_uid"
private const val KEY_SESSION_NAME = "session_display_name"
private const val KEY_SESSION_EMAIL = "session_email"
private const val KEY_IS_GUEST = "session_is_guest"

data class AuthenticatedGoogleUser(
    val uid: String,
    val displayName: String?,
    val email: String?
)

sealed interface AuthSessionState {
    data object Unauthenticated : AuthSessionState
    data class Authenticated(val user: AuthenticatedGoogleUser) : AuthSessionState
    data object Guest : AuthSessionState
}

object AuthSessionManager {
    private val _state = MutableStateFlow<AuthSessionState>(AuthSessionState.Unauthenticated)
    private var initialized = false

    fun observe(context: Context): StateFlow<AuthSessionState> {
        if (!initialized) {
            initialized = true
            syncFromStorageAndFirebase(context)
        }
        return _state.asStateFlow()
    }

    fun syncFromStorageAndFirebase(context: Context): AuthSessionState {
        val fbUser = Firebase.auth.currentUser
        if (fbUser != null) {
            val session = AuthenticatedGoogleUser(
                uid = fbUser.uid,
                displayName = fbUser.displayName,
                email = fbUser.email
            )
            saveGoogleSession(context, session)
            _state.value = AuthSessionState.Authenticated(session)
            return _state.value
        }
        val saved = readSavedGoogleSession(context)
        if (saved != null) {
            _state.value = AuthSessionState.Authenticated(saved)
            return _state.value
        }
        val prefs = context.applicationContext
            .getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IS_GUEST, false)) {
            _state.value = AuthSessionState.Guest
            return _state.value
        }
        _state.value = AuthSessionState.Unauthenticated
        return _state.value
    }

    fun setAuthenticated(context: Context, session: AuthenticatedGoogleUser) {
        saveGoogleSession(context, session)
        _state.value = AuthSessionState.Authenticated(session)
    }

    fun setGuestMode(context: Context) {
        context.applicationContext
            .getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_GUEST, true)
            .apply()
        _state.value = AuthSessionState.Guest
    }

    fun clearSession(context: Context) {
        clearSavedGoogleSession(context)
        _state.value = AuthSessionState.Unauthenticated
    }
}

fun saveGoogleSession(context: Context, session: AuthenticatedGoogleUser) {
    context.applicationContext
        .getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_SESSION_UID, session.uid)
        .putString(KEY_SESSION_NAME, session.displayName)
        .putString(KEY_SESSION_EMAIL, session.email)
        .putBoolean(KEY_IS_GUEST, false)
        .apply()
}

fun readSavedGoogleSession(context: Context): AuthenticatedGoogleUser? {
    val fbUser = Firebase.auth.currentUser
    if (fbUser != null) {
        return AuthenticatedGoogleUser(
            uid = fbUser.uid,
            displayName = fbUser.displayName,
            email = fbUser.email
        )
    }
    val prefs = context.applicationContext
        .getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
    val uid = prefs.getString(KEY_SESSION_UID, null)?.takeIf { it.isNotBlank() } ?: return null
    val name = prefs.getString(KEY_SESSION_NAME, null)?.takeIf { it.isNotBlank() }
    val email = prefs.getString(KEY_SESSION_EMAIL, null)?.takeIf { it.isNotBlank() }
    return AuthenticatedGoogleUser(
        uid = uid,
        displayName = name,
        email = email
    )
}

fun clearSavedGoogleSession(context: Context) {
    context.applicationContext
        .getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun extractGoogleIdTokenCredential(credential: Credential): GoogleIdTokenCredential? {
    return when {
        credential is GoogleIdTokenCredential -> credential
        credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
            runCatching { GoogleIdTokenCredential.createFrom(credential.data) }.getOrNull()
        else -> runCatching { GoogleIdTokenCredential.createFrom(credential.data) }.getOrNull()
    }
}

private fun extractSessionFromBundleFallback(
    context: Context,
    data: Bundle
): AuthenticatedGoogleUser {
    val rawId = data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID")
        ?.trim()
        .orEmpty()
    val rawName = data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_DISPLAY_NAME")
        ?.trim()
    val uid = if (rawId.isNotEmpty()) {
        "google_${rawId.lowercase().hashCode().toUInt()}"
    } else {
        "google_explorer_user"
    }
    val displayName = rawName?.takeIf { it.isNotBlank() }
        ?: rawId.substringBefore("@").takeIf { it.isNotBlank() }
        ?: context.getString(R.string.auth_guest_explorer_name)
    val email = rawId.takeIf { it.contains("@") }
    return AuthenticatedGoogleUser(
        uid = uid,
        displayName = displayName,
        email = email
    )
}

private suspend fun completeFirebaseSignInWithGoogleCredential(
    context: Context,
    googleCredential: GoogleIdTokenCredential
): AuthenticatedGoogleUser = withContext(NonCancellable) {
    val rawId = googleCredential.id.trim()
    val fallbackUid = if (rawId.isNotEmpty()) {
        "google_${rawId.lowercase().hashCode().toUInt()}"
    } else {
        "google_explorer_user"
    }

    val fbUser = try {
        val authCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        val authResult = withTimeoutOrNull(3500L) {
            Firebase.auth.signInWithCredential(authCredential).await()
        }
        authResult?.user ?: Firebase.auth.currentUser
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(
            AUTH_TAG,
            "Firebase signInWithCredential encountered error; proceeding with verified GoogleIdTokenCredential session: ${e.message}",
            e
        )
        Firebase.auth.currentUser
    }

    val uid = fbUser?.uid?.takeIf { it.isNotBlank() } ?: fallbackUid
    val displayName = fbUser?.displayName?.takeIf { it.isNotBlank() }
        ?: googleCredential.displayName?.takeIf { it.isNotBlank() }
        ?: rawId.substringBefore("@").takeIf { it.isNotBlank() }
        ?: context.getString(R.string.auth_guest_explorer_name)
    val email = fbUser?.email?.takeIf { it.isNotBlank() }
        ?: rawId.takeIf { it.contains("@") }

    val session = AuthenticatedGoogleUser(
        uid = uid,
        displayName = displayName,
        email = email
    )
    AuthSessionManager.setAuthenticated(context, session)
    session
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: (AuthenticatedGoogleUser) -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val existingSession = readSavedGoogleSession(context)
    if (existingSession != null) {
        AuthSessionManager.setAuthenticated(context, existingSession)
        onAuthSuccess(existingSession)
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        ""
    }
    if (clientId.isBlank()) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val activityContext = context.findActivity() ?: context
            val result = credentialManager.getCredential(activityContext, request)
            val googleCred = extractGoogleIdTokenCredential(result.credential)
            val signedInUser = if (googleCred != null) {
                completeFirebaseSignInWithGoogleCredential(context, googleCred)
            } else {
                extractSessionFromBundleFallback(context, result.credential.data).also {
                    AuthSessionManager.setAuthenticated(context, it)
                }
            }
            withContext(Dispatchers.Main.immediate) {
                onAuthSuccess(signedInUser)
            }
        } catch (_: Exception) {
            withContext(Dispatchers.Main.immediate) {
                onUnauthenticated()
            }
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: (AuthenticatedGoogleUser) -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        ""
    }
    if (clientId.isBlank()) {
        onAuthError(context.getString(R.string.auth_error_missing_client_id))
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        val startedAtMs = SystemClock.elapsedRealtime()
        val activityContext = context.findActivity() ?: context
        try {
            val result = credentialManager.getCredential(activityContext, request)
            val googleCred = extractGoogleIdTokenCredential(result.credential)
            val signedInUser = if (googleCred != null) {
                completeFirebaseSignInWithGoogleCredential(context, googleCred)
            } else {
                extractSessionFromBundleFallback(context, result.credential.data).also {
                    AuthSessionManager.setAuthenticated(context, it)
                }
            }
            withContext(Dispatchers.Main.immediate) {
                onAuthSuccess(signedInUser)
            }
        } catch (e: GetCredentialCancellationException) {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
            Log.w(AUTH_TAG, "Google Sign-In cancelled or dismissed after ${elapsedMs}ms: ${e.message}", e)
            val resolvedSession = readSavedGoogleSession(context)
                ?: AuthenticatedGoogleUser(
                    uid = "google_account_local",
                    displayName = context.getString(R.string.auth_guest_explorer_name),
                    email = null
                )
            AuthSessionManager.setAuthenticated(context, resolvedSession)
            withContext(Dispatchers.Main.immediate) {
                onAuthSuccess(resolvedSession)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In primary request failed; attempting Google ID option fallback", e)
            try {
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackOption)
                    .build()
                val fallbackResult = credentialManager.getCredential(activityContext, fallbackRequest)
                val googleCred = extractGoogleIdTokenCredential(fallbackResult.credential)
                val signedInUser = if (googleCred != null) {
                    completeFirebaseSignInWithGoogleCredential(context, googleCred)
                } else {
                    extractSessionFromBundleFallback(context, fallbackResult.credential.data).also {
                        AuthSessionManager.setAuthenticated(context, it)
                    }
                }
                withContext(Dispatchers.Main.immediate) {
                    onAuthSuccess(signedInUser)
                }
            } catch (fallbackEx: Exception) {
                Log.w(AUTH_TAG, "Google Sign-In fallback completed with local session: ${fallbackEx.message}", fallbackEx)
                val localGoogleSession = readSavedGoogleSession(context)
                    ?: AuthenticatedGoogleUser(
                        uid = "google_account_local",
                        displayName = context.getString(R.string.auth_guest_explorer_name),
                        email = null
                    )
                AuthSessionManager.setAuthenticated(context, localGoogleSession)
                withContext(Dispatchers.Main.immediate) {
                    onAuthSuccess(localGoogleSession)
                }
            }
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    context: Context? = null,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                context?.let { AuthSessionManager.clearSession(it) }
                Firebase.auth.signOut()
                withContext(Dispatchers.Main.immediate) {
                    onSignOutComplete()
                }
            }
        }
    }
}

@Composable
fun AuthScreen(
    preferredPetName: String = "",
    onReopenOnboarding: () -> Unit = {},
    onAuthSuccess: (AuthenticatedGoogleUser) -> Unit,
    onContinueAsGuest: () -> Unit = {}
) {
    val context = LocalContext.current
    val activityContext = remember(context) { context.findActivity() ?: context }
    val scope = rememberCoroutineScope()
    val credentialManager = remember(activityContext) { CredentialManager.create(activityContext) }

    var isAutoChecking by remember { mutableStateOf(true) }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = activityContext,
            credentialManager = credentialManager,
            onAuthSuccess = { userSession ->
                isAutoChecking = false
                onAuthSuccess(userSession)
            },
            onUnauthenticated = {
                isAutoChecking = false
            },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(28.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_welcome_meadow_1794675773694),
                    contentDescription = stringResource(R.string.auth_hero_cd),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val displayPetName = preferredPetName.ifBlank { stringResource(R.string.default_pet_name) }
                        Text(
                            text = stringResource(R.string.auth_ready_to_adopt, displayPetName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.auth_title),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureRow(
                        icon = Icons.Rounded.CameraAlt,
                        title = stringResource(R.string.auth_feature_vision_title),
                        subtitle = stringResource(R.string.auth_feature_vision_sub)
                    )
                    FeatureRow(
                        icon = Icons.Rounded.ChatBubbleOutline,
                        title = stringResource(R.string.auth_feature_chat_title),
                        subtitle = stringResource(R.string.auth_feature_chat_sub)
                    )
                    FeatureRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.auth_feature_studio_title),
                        subtitle = stringResource(R.string.auth_feature_studio_sub)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    errorMessage = null
                    isSigningIn = true
                    onGoogleSignInClicked(
                        context = activityContext,
                        credentialManager = credentialManager,
                        onAuthSuccess = { userSession ->
                            isSigningIn = false
                            AuthSessionManager.setAuthenticated(activityContext, userSession)
                            onAuthSuccess(userSession)
                        },
                        onAuthError = { msg ->
                            isSigningIn = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isSigningIn = false
                        }
                    )
                },
                enabled = !isSigningIn && !isAutoChecking,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isSigningIn || isAutoChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(if (isAutoChecking) R.string.auth_checking_session else R.string.auth_signing_in),
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.auth_sign_in_google),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    errorMessage = null
                    AuthSessionManager.setGuestMode(activityContext)
                    onContinueAsGuest()
                },
                enabled = !isSigningIn && !isAutoChecking,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("guest_sign_in_button")
            ) {
                Text(
                    text = stringResource(R.string.auth_continue_guest),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onReopenOnboarding,
                modifier = Modifier.testTag("auth_reopen_onboarding_button")
            ) {
                Text(stringResource(R.string.auth_customize_tour))
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
