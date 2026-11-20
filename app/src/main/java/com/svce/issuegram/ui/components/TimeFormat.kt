package com.svce.issuegram.ui.components

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.svce.issuegram.R
import java.text.DateFormat
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.Locale

/** Minutes and hours via platform formatting; days up to 30 in words; older shows the date. */
@Composable
fun relativeTime(instant: Instant, now: Instant = Instant.now()): String {
    val elapsed = Duration.between(instant, now)
    val hours = elapsed.toHours()
    val days = elapsed.toDays()
    return when {
        elapsed.seconds < 60 -> stringResource(R.string.time_just_now)
        hours < 24 -> DateUtils.getRelativeTimeSpanString(
            instant.toEpochMilli(),
            now.toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
        days <= 30 -> pluralStringResource(
            R.plurals.days_ago,
            days.toInt(),
            days.toInt(),
        )
        else -> absoluteDateTime(instant)
    }
}

fun absoluteDateTime(instant: Instant): String =
    DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        Locale.getDefault(),
    ).format(Date.from(instant))
