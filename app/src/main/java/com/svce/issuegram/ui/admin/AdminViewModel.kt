package com.svce.issuegram.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminViewModel(
    private val updateAdminFlag: suspend (String, Boolean) -> Unit = { uid, isAdmin ->
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(mapOf("isAdmin" to isAdmin), SetOptions.merge())
            .await()
    }
) : ViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun setAdminStatus(
        targetUid: String,
        isAdmin: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        val cleanUid = targetUid.trim()
        if (cleanUid.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("Enter a valid User UID")))
            return
        }
        if (_isBusy.value) return

        viewModelScope.launch {
            _isBusy.value = true
            try {
                updateAdminFlag(cleanUid, isAdmin)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                _isBusy.value = false
            }
        }
    }
}
