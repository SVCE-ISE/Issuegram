package com.svce.issuegram.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun displayTime(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return try { DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.parse(value)) }
    catch (_: Exception) { value }
}
