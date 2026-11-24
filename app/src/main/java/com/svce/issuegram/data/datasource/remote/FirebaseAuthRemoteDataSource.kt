package com.svce.issuegram.data.datasource.remote

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.svce.issuegram.data.mapper.UserMapper
import com.svce.issuegram.domain.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            cont.resume(task.result, onCancellation = null)
        } else {
            cont.resumeWith(Result.failure(task.exception ?: RuntimeException("Task failed")))
        }
    }
}

class FirebaseAuthRemoteDataSource(private val auth: FirebaseAuth = Firebase.auth) : AuthRemoteDataSource {

    override fun observeAuthState(): Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(UserMapper.toDomain(firebaseAuth.currentUser))
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun getCurrentUser(): User? = UserMapper.toDomain(auth.currentUser)

    override suspend fun updateDisplayName(name: String) {
        val user = auth.currentUser ?: return
        val profileUpdates = userProfileChangeRequest {
            displayName = name
        }
        user.updateProfile(profileUpdates).await()
    }

    override suspend fun updatePhotoUri(uri: String?) {
        val user = auth.currentUser ?: return
        val profileUpdates = userProfileChangeRequest {
            photoUri = uri?.let { Uri.parse(it) }
        }
        user.updateProfile(profileUpdates).await()
    }

    override fun signOut() {
        auth.signOut()
    }
}
