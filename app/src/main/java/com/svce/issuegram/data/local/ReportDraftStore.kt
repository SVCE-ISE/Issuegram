package com.svce.issuegram.data.local

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReportDraft(
    val caption: String = "", val category: String = "Public Works", val locationName: String = "",
    val detail: String = "", val latitude: Double? = null, val longitude: Double? = null,
    val photoPath: String? = null, val pickedUri: String? = null,
)

class ReportDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("issuegram_report_draft_v2", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    fun read(): ReportDraft = prefs.getString("draft", null)?.let { runCatching { json.decodeFromString<ReportDraft>(it) }.getOrNull() } ?: ReportDraft()
    fun save(draft: ReportDraft) { prefs.edit { putString("draft", json.encodeToString(draft)) } }
    fun clear() { prefs.edit { clear() } }
}
