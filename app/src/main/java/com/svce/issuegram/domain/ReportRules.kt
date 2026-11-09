package com.svce.issuegram.domain

import java.util.UUID

object ReportRules {
    val categories = listOf("Public Works", "Power & Lighting", "Water & Sanitation", "Smart City / IT", "Roads & Transit")

    fun validate(caption: String, category: String, location: String, latitude: Double, longitude: Double) {
        require(caption.trim().length in 5..500) { "Describe the issue in 5–500 characters." }
        require(category in categories) { "Choose an issue category." }
        require(location.trim().isNotEmpty()) { "Choose the issue location." }
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) {
            "Choose a valid issue location."
        }
    }

    fun randomUsername(): String = "citizen_" + UUID.randomUUID().toString().replace("-", "").take(12)
}
