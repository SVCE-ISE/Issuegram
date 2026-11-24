package com.svce.issuegram.data.datasource.remote

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.svce.issuegram.data.mapper.UserMapper
import com.svce.issuegram.domain.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRemoteDataSource(
    private val auth: FirebaseAuth = Firebase.auth,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRemoteDataSource {

    override fun observeAuthState(): Flow<User?> = callbackFlow {
        var firestoreListener: ListenerRegistration? = null
        var currentIsAdmin = false

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            firestoreListener?.remove()
            firestoreListener = null

            if (user == null) {
                currentIsAdmin = false
                trySend(null)
            } else {
                currentIsAdmin = false
                firestoreListener = firestore.collection("users").document(user.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        currentIsAdmin = snapshot?.getBoolean("isAdmin") == true
                        trySend(UserMapper.toDomain(auth.currentUser, isAdmin = currentIsAdmin))
                    }
                trySend(UserMapper.toDomain(user, isAdmin = false))
            }
        }
        auth.addAuthStateListener(authListener)
        awaitClose {
            auth.removeAuthStateListener(authListener)
            firestoreListener?.remove()
        }
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
