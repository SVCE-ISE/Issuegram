@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.svce.issuegram

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.DisposableEffect
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
import androidx.credentials.exceptions.NoCredentialException
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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
                var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
                DisposableEffect(Unit) {
                    val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
                        currentUser = auth.currentUser
                    }
                    Firebase.auth.addAuthStateListener(listener)
                    onDispose {
                        Firebase.auth.removeAuthStateListener(listener)
                    }
                }
                var isSigningIn by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                // Firebase profile synchronization helper
                fun syncUserProfile(user: com.google.firebase.auth.FirebaseUser) {
                    val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore('@') ?: "Citizen Reporter"
                    val username = user.email?.substringBefore('@')?.replace('.', '_')?.replace('-', '_') ?: "user_${user.uid.take(8)}"
                    val avatarUrl = user.photoUrl?.toString()

                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            val profileData = hashMapOf(
                                "id" to user.uid,
                                "displayName" to displayName,
                                "username" to username,
                                "avatarUrl" to avatarUrl,
                                "updatedAt" to java.time.Instant.now().toString()
                            )
                            firestore.collection("users").document(user.uid)
                                .set(profileData, com.google.firebase.firestore.SetOptions.merge())
                        }
                    }
                }

                // Legacy Google Sign-In Intent Fallback launcher
                val legacyGoogleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { activityResult ->
                    isSigningIn = true
                    scope.launch {
                        try {
                            val task = GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
                            val account = task.getResult(ApiException::class.java)
                            val idToken = account.idToken
                            if (idToken != null) {
                                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                                val authResult = Firebase.auth.signInWithCredential(authCredential).awaitResult()
                                authResult.user?.let { syncUserProfile(it) }
                            } else {
                                Toast.makeText(context, "Google authentication did not provide an ID token", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: ApiException) {
                            Log.w("IssuegramAuth", "Legacy Google Sign-In failed or cancelled: statusCode=${e.statusCode}")
                            if (e.statusCode != 12501) { // 12501 = SIGN_IN_CANCELLED
                                Toast.makeText(context, "Sign-in error: ${e.statusCode}", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("IssuegramAuth", "Legacy Sign-In error: ${e.message}", e)
                            Toast.makeText(context, "Sign-in failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSigningIn = false
                        }
                    }
                }

                fun launchLegacyGoogleSignIn() {
                    val webClientId = context.getString(R.string.default_web_client_id)
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(webClientId)
                        .requestEmail()
                        .build()
                    val client = GoogleSignIn.getClient(this@MainActivity, gso)
                    legacyGoogleSignInLauncher.launch(client.signInIntent)
                }

                if (currentUser == null) {
                    LoginScreen(
                        isSigningIn = isSigningIn,
                        onGoogleSignIn = {
                            scope.launch {
                                isSigningIn = true
                                try {
                                    val webClientId = context.getString(R.string.default_web_client_id)

                                    // Modern Credential Manager Bottom Sheet option:
                                    // setFilterByAuthorizedAccounts(false) surfaces the native bottom sheet with all Google accounts on device
                                    val googleIdOption = GetGoogleIdOption.Builder()
                                        .setServerClientId(webClientId)
                                        .setFilterByAuthorizedAccounts(false)
                                        .setAutoSelectEnabled(false)
                                        .build()

                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()

                                    Log.d("IssuegramAuth", "Triggering modern Credential Manager bottom sheet (GetGoogleIdOption)...")
                                    val result = credentialManager.getCredential(request = request, context = context)
                                    val credential = result.credential
                                    val idToken = when {
                                        credential is CustomCredential &&
                                            (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                                             credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL) -> {
                                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                            googleIdTokenCredential.idToken
                                        }
                                        else -> throw IllegalStateException("Unexpected credential: ${credential.type}")
                                    }

                                    val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                                    val authResult = Firebase.auth.signInWithCredential(authCredential).awaitResult()
                                    authResult.user?.let { syncUserProfile(it) }
                                } catch (e: GetCredentialCancellationException) {
                                    Log.w("IssuegramAuth", "User dismissed Credential Manager bottom sheet")
                                } catch (e: NoCredentialException) {
                                    Log.i("IssuegramAuth", "No Credential Manager accounts; falling back to GoogleSignInClient...")
                                    launchLegacyGoogleSignIn()
                                } catch (e: Exception) {
                                    Log.w("IssuegramAuth", "Credential Manager encountered ${e::class.java.simpleName}: ${e.message}. Triggering fallback...")
                                    launchLegacyGoogleSignIn()
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
