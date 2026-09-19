// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 capad.io <capad.xyz@gmail.com>
// GlyphMaps - licensed under AGPL-3.0 (see LICENSE).
// Brand assets (name, icon, artwork) are NOT covered by the AGPL; see NOTICE.

package com.glyphnavtoy.nav

import com.glyphnavtoy.glyph.Maneuver

/**
 * Pure Maps Live Update parsing. No Android types, so unit tests run on CI
 * without Robolectric. [com.glyphnavtoy.service.MapsNotificationListener]
 * applies the same rules to notification extras.
 */
object MapsNavParse {

    val MAPS_PACKAGES = setOf(
        "com.google.android.apps.maps",
        "com.google.android.apps.mapslite",
    )

    private val DISTANCE_REGEX = Regex(
        """(\d+(?:\.\d+)?)\s*(kilometres?|kilometers?|km|miles?|mi|metres?|meters?|mtrs?|yards?|yds?|yd|feet|ft|m)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val TITLE_SPLIT = Regex("""\s*[·•\u2013\u2014|:]+\s+""")

    fun isMapsPackage(packageName: String?): Boolean =
        packageName != null && packageName in MAPS_PACKAGES

    fun parseDistanceMeters(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val normalized = s.replace(' ', ' ').replace(' ', ' ').replace(',', '.')
        val m = DISTANCE_REGEX.find(normalized) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "m", "meter", "meters", "metre", "metres", "mtr", "mtrs" -> value.toInt()
            "km", "kilometer", "kilometers", "kilometre", "kilometres" -> (value * 1000).toInt()
            "ft", "feet" -> (value * 0.3048).toInt()
            "yd", "yds", "yard", "yards" -> (value * 0.9144).toInt()
            "mi", "mile", "miles" -> (value * 1609.34).toInt()
            else -> null
        }
    }

    fun stripDistancePrefix(title: String): String {
        val parts = TITLE_SPLIT.split(title, limit = 2)
        return if (parts.size == 2 && parseDistanceMeters(parts[0]) != null) {
            parts[1].trim()
        } else {
            title.trim()
        }
    }

    fun parseDistanceFromTitle(title: String): Int? {
        val parts = TITLE_SPLIT.split(title, limit = 2)
        if (parts.size == 2) return parseDistanceMeters(parts[0])
        return parseDistanceMeters(title)
    }

    fun normalizeForLookup(text: String): String =
        text.lowercase()
            .replace(' ', ' ')
            .replace(Regex("[\u2013\u2014\u2212]"), "-")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    fun maneuverFromTitle(title: String): Maneuver =
        Maneuver.fromMapsString(normalizeForLookup(stripDistancePrefix(title)))

    fun resolveDistance(shortCritical: String?, title: String, text: String = ""): Int? =
        parseDistanceMeters(shortCritical)
            ?: parseDistanceFromTitle(title)
            ?: parseDistanceMeters(text)
}
