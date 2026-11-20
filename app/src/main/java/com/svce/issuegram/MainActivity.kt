@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.svce.issuegram

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.svce.issuegram.ui.navigation.IssuegramApp
import com.svce.issuegram.ui.screens.LoginScreen
import com.svce.issuegram.ui.theme.IssuegramTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result, onCancellation = null)
            } else {
                cont.resumeWith(Result.failure(task.exception ?: RuntimeException("Firebase task failed")))
            }
        }
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Calm utility product: no bounce on routine actions.
            // Dynamic colour on Android 12+; brand palette below that.
            IssuegramTheme(
                motionScheme = MotionScheme.standard(),
                dynamicColor = true,
            ) {
                val context = LocalContext.current
                val credentialManager = remember { CredentialManager.create(context) }
                val currentUser = remember { Firebase.auth.currentUser }
                var isLoggedIn by remember { mutableStateOf(currentUser != null) }
                var isSigningIn by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                if (!isLoggedIn) {
                    LoginScreen(
                        isSigningIn = isSigningIn,
                        onGoogleSignIn = {
                            scope.launch {
                                isSigningIn = true
                                try {
                                    val webClientId = context.getString(R.string.default_web_client_id)
                                    val googleIdOption = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId(webClientId)
                                        .setAutoSelectEnabled(false)
                                        .build()

                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()

                                    Log.d("IssuegramAuth", "Initiating Google Sign-In with Credential Manager...")
                                    val result = credentialManager.getCredential(request = request, context = context)
                                    val credential = result.credential
                                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

                                        Log.i("IssuegramAuth", "================ GOOGLE ACCOUNT RECEIVED ================")
                                        Log.i("IssuegramAuth", "Google ID (sub): ${googleIdTokenCredential.id}")
                                        Log.i("IssuegramAuth", "Email / Account: ${googleIdTokenCredential.id}")
                                        Log.i("IssuegramAuth", "Display Name:    ${googleIdTokenCredential.displayName}")
                                        Log.i("IssuegramAuth", "Given Name:      ${googleIdTokenCredential.givenName}")
                                        Log.i("IssuegramAuth", "Family Name:     ${googleIdTokenCredential.familyName}")
                                        Log.i("IssuegramAuth", "Profile Picture: ${googleIdTokenCredential.profilePictureUri}")
                                        Log.i("IssuegramAuth", "Phone Number:    ${googleIdTokenCredential.phoneNumber}")
                                        Log.i("IssuegramAuth", "ID Token Prefix: ${googleIdTokenCredential.idToken.take(30)}...")

                                        // Authenticate with Firebase Auth
                                        val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                                        val authResult = Firebase.auth.signInWithCredential(authCredential).awaitResult()
                                        val user = authResult.user

                                        Log.i("IssuegramAuth", "================ FIREBASE AUTH SUCCESS ================")
                                        Log.i("IssuegramAuth", "Firebase UID:         ${user?.uid}")
                                        Log.i("IssuegramAuth", "Firebase Email:       ${user?.email}")
                                        Log.i("IssuegramAuth", "Email Verified:       ${user?.isEmailVerified}")
                                        Log.i("IssuegramAuth", "Display Name:         ${user?.displayName}")
                                        Log.i("IssuegramAuth", "Photo URL:            ${user?.photoUrl}")
                                        Log.i("IssuegramAuth", "Is Anonymous:         ${user?.isAnonymous}")
                                        Log.i("IssuegramAuth", "Account Creation:     ${user?.metadata?.creationTimestamp}")
                                        Log.i("IssuegramAuth", "Last Sign-In:         ${user?.metadata?.lastSignInTimestamp}")
                                        user?.providerData?.forEachIndexed { index, profile ->
                                            Log.i("IssuegramAuth", "Provider [$index]: ${profile.providerId} | UID: ${profile.uid} | Email: ${profile.email} | Name: ${profile.displayName}")
                                        }
                                        Log.i("IssuegramAuth", "=======================================================")

                                        isLoggedIn = true
                                    } else {
                                        Log.w("IssuegramAuth", "Unexpected credential type: ${credential.type}")
                                    }
                                } catch (e: GetCredentialCancellationException) {
                                    Log.w("IssuegramAuth", "User cancelled Google Sign-In prompt")
                                } catch (e: Exception) {
                                    Log.e("IssuegramAuth", "Sign-In Error: ${e.message}", e)
                                } finally {
                                    isSigningIn = false
                                }
                            }
                        },
                    )
                } else {
                    IssuegramApp()
                }
            }
        }
    }
}
