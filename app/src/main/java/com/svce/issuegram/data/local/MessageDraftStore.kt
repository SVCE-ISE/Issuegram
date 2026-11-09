package com.svce.issuegram.data.local

import android.content.Context
import androidx.core.content.edit

/** Private, per-identity and per-conversation drafts. Excluded from backup with the session. */
class MessageDraftStore(context: Context) {
    private val preferences = context.getSharedPreferences("issuegram_message_drafts", Context.MODE_PRIVATE)
    fun read(userId: String, channelId: String): String = preferences.getString("$userId:$channelId", "").orEmpty()
    fun save(userId: String, channelId: String, text: String) {
        preferences.edit {
            if (text.isBlank()) remove("$userId:$channelId") else putString("$userId:$channelId", text.take(2000))
        }
    }
}
