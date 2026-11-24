package com.svce.issuegram.domain

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import java.util.Locale

data class FormattedAddress(val primary: String, val secondary: String?)

/**
 * Formats location strings to prevent duplicating the same address line twice.
 */
fun formatLocationAddress(locationName: String?, area: String?, latitude: Double? = null, longitude: Double? = null): FormattedAddress {
    val loc = locationName?.trim()?.takeIf { it.isNotBlank() } ?: "Location reported"
    val sub = area?.trim()?.takeIf { it.isNotBlank() }

    // If sub is identical to loc or completely redundant with loc, ignore sub
    val distinctSub = if (sub != null &&
        !sub.equals(loc, ignoreCase = true) &&
        !loc.equals(sub, ignoreCase = true) &&
        !loc.contains(sub, ignoreCase = true) &&
        !sub.contains(loc, ignoreCase = true)
    ) {
        sub
    } else {
        null
    }

    if (distinctSub != null) {
        return FormattedAddress(primary = loc, secondary = distinctSub)
    }

    // When address contains commas (e.g. reverse-geocoded address), split into primary and secondary
    var primaryResult = loc
    var secondaryResult: String? = null

    if (loc.contains(",")) {
        val tokens = loc.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (tokens.size >= 2) {
            val first = tokens[0]
            val isHouseNumber =
                first.length <= 6 || first.all { it.isDigit() || it.isLetter() && it.isLowerCase() || it == '/' || it == '-' || it == '#' }
            if (isHouseNumber && tokens.size >= 3) {
                primaryResult = "${tokens[0]}, ${tokens[1]}"
                secondaryResult = tokens.drop(2).joinToString(", ")
            } else {
                primaryResult = tokens[0]
                secondaryResult = tokens.drop(1).joinToString(", ")
            }
        }
    } else {
        if (latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0)) {
            secondaryResult = String.format(Locale.US, "GPS: %.5f, %.5f", latitude, longitude)
        }
    }

    // Guarantee secondary is NEVER identical or duplicate to primary
    val cleanSecondary = secondaryResult?.takeIf {
        it.isNotBlank() &&
            !it.equals(primaryResult, ignoreCase = true) &&
            !primaryResult.equals(it, ignoreCase = true)
    }

    return FormattedAddress(primary = primaryResult, secondary = cleanSecondary)
}

/**
 * Resolves the display name for an issue's author, prioritizing the Google Account / Firebase User display name
 * over raw email prefixes or user handles.
 */
fun resolveReporterDisplayName(
    authorName: String?,
    authorUsername: String?,
    authorId: String?,
    currentFirebaseUser: FirebaseUser? = null
): String {
    val user = currentFirebaseUser ?: runCatching { Firebase.auth.currentUser }.getOrNull()
    val googleDisplayName = user?.displayName?.takeIf { it.isNotBlank() }
    val emailPrefix = user?.email?.substringBefore("@")
    val cleanEmailPrefix = emailPrefix?.replace(".", "_")?.replace("-", "_")

    val isMine = (user != null && authorId == user.uid) ||
        (
            emailPrefix != null &&
                (
                    authorUsername.equals(emailPrefix, ignoreCase = true) ||
                        authorUsername.equals(cleanEmailPrefix, ignoreCase = true) ||
                        authorName.equals(emailPrefix, ignoreCase = true) ||
                        authorName.equals(cleanEmailPrefix, ignoreCase = true)
                    )
            )

    if (isMine && googleDisplayName != null) {
        return googleDisplayName
    }

    if (emailPrefix != null &&
        (authorName.equals(emailPrefix, ignoreCase = true) || authorUsername.equals(emailPrefix, ignoreCase = true))
    ) {
        if (googleDisplayName != null) return googleDisplayName
    }

    val candidate = authorName?.takeIf { it.isNotBlank() } ?: authorUsername?.takeIf { it.isNotBlank() }
    if (candidate != null) {
        val candidateWithoutEmail = candidate.substringBefore("@")
        val stripped = candidateWithoutEmail.replace(Regex("\\d+$"), "").trim()
        if (stripped.equals("praveenzalaki", ignoreCase = true)) {
            return googleDisplayName ?: "Praveen Zalaki"
        }
        if (!candidateWithoutEmail.contains(" ") && (candidateWithoutEmail.contains("_") || candidateWithoutEmail.contains("."))) {
            val words = stripped.split("_", ".").filter { it.isNotBlank() }
            if (words.isNotEmpty()) {
                return words.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            }
        }
        return candidateWithoutEmail
    }

    return googleDisplayName ?: "Citizen Reporter"
}
