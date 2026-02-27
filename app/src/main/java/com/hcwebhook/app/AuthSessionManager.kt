package com.hcwebhook.app

import android.content.Context
import android.util.Log
import com.amplifyframework.AmplifyException
import com.amplifyframework.auth.AuthUser
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin
import com.amplifyframework.auth.cognito.AWSCognitoAuthSession
import com.amplifyframework.auth.result.AuthSessionResult
import com.amplifyframework.core.Amplify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class AuthUiState(
    val isConfigured: Boolean = false,
    val isSignedIn: Boolean = false,
    val username: String? = null,
    val statusMessage: String? = null
)

object AuthSessionManager {
    private const val TAG = "AuthSessionManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()
    private var isAmplifyInitialized = false

    fun initialize(context: Context) {
        if (isAmplifyInitialized) return
        try {
            Amplify.addPlugin(AWSCognitoAuthPlugin())
            Amplify.configure(context.applicationContext)
            isAmplifyInitialized = true
            _authUiState.value = _authUiState.value.copy(isConfigured = true, statusMessage = null)
        } catch (error: AmplifyException) {
            Log.w(TAG, "Amplify auth initialization failed", error)
            _authUiState.value = _authUiState.value.copy(
                isConfigured = false,
                statusMessage = "Auth not configured. Add amplifyconfiguration.json."
            )
        }
    }

    fun refreshSessionInBackground() {
        scope.launch {
            refreshSession()
        }
    }

    suspend fun refreshSession(): Boolean = suspendCancellableCoroutine { continuation ->
        if (!isAmplifyInitialized) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        Amplify.Auth.fetchAuthSession(
            { session ->
                val isSignedIn = session.isSignedIn
                val user = if (isSignedIn) runCatching { Amplify.Auth.currentUser }.getOrNull() else null
                _authUiState.value = _authUiState.value.copy(
                    isConfigured = true,
                    isSignedIn = isSignedIn,
                    username = user?.username,
                    statusMessage = if (isSignedIn) null else "Please sign in to sync"
                )
                continuation.resume(isSignedIn)
            },
            {
                _authUiState.value = _authUiState.value.copy(
                    isSignedIn = false,
                    username = null,
                    statusMessage = "Session unavailable. Please sign in again."
                )
                continuation.resume(false)
            }
        )
    }

    suspend fun signIn(username: String, password: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        if (!isAmplifyInitialized) {
            continuation.resume(Result.failure(IllegalStateException("Auth is not configured")))
            return@suspendCancellableCoroutine
        }
        Amplify.Auth.signIn(
            username,
            password,
            { result ->
                if (result.isSignedIn) {
                    _authUiState.value = _authUiState.value.copy(
                        isSignedIn = true,
                        username = username,
                        statusMessage = null
                    )
                    continuation.resume(Result.success(Unit))
                } else {
                    continuation.resume(Result.failure(IllegalStateException("Additional auth step required")))
                }
            },
            { error ->
                _authUiState.value = _authUiState.value.copy(
                    isSignedIn = false,
                    username = null,
                    statusMessage = error.localizedMessage
                )
                continuation.resume(Result.failure(error))
            }
        )
    }

    fun signOutInBackground() {
        scope.launch {
            signOut()
        }
    }

    suspend fun signOut() = suspendCancellableCoroutine { continuation ->
        if (!isAmplifyInitialized) {
            _authUiState.value = _authUiState.value.copy(isSignedIn = false, username = null)
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        Amplify.Auth.signOut(
            {
                _authUiState.value = _authUiState.value.copy(
                    isSignedIn = false,
                    username = null,
                    statusMessage = "Signed out"
                )
                continuation.resume(Unit)
            },
            {
                _authUiState.value = _authUiState.value.copy(
                    isSignedIn = false,
                    username = null,
                    statusMessage = "Signed out"
                )
                continuation.resume(Unit)
            }
        )
    }

    suspend fun getAccessTokenOrSignOut(): Result<String> = suspendCancellableCoroutine { continuation ->
        if (!isAmplifyInitialized) {
            continuation.resume(Result.failure(IllegalStateException("Auth is not configured")))
            return@suspendCancellableCoroutine
        }
        Amplify.Auth.fetchAuthSession(
            { session ->
                val cognitoSession = session as? AWSCognitoAuthSession
                val tokenResult = cognitoSession?.userPoolTokensResult
                if (session.isSignedIn && tokenResult?.type == AuthSessionResult.Type.SUCCESS) {
                    val token = tokenResult.value.accessToken
                    continuation.resume(Result.success(token))
                } else {
                    signOutInBackground()
                    continuation.resume(Result.failure(IllegalStateException("Session expired. Please sign in again.")))
                }
            },
            { error ->
                signOutInBackground()
                continuation.resume(Result.failure(error))
            }
        )
    }
}
