package com.killindodo.familytrack.core

import java.util.Locale

/** "2 min ago" strings, EN/HI aware. Pure Kotlin. */
object RelativeTime {
    fun format(thenMs: Long, nowMs: Long, languageCode: String = "en"): String {
        val sec = ((nowMs - thenMs) / 1000L).coerceAtLeast(0)
        val min = sec / 60
        val hr = min / 60
        val day = hr / 24
        val hindi = languageCode.lowercase(Locale.US).startsWith("hi")
        return when {
            min < 1 -> if (hindi) "अभी-अभी" else "just now"
            hr < 1 -> if (hindi) String.format(Locale.US, "%d मिनट पहले", min) else String.format(Locale.US, "%d min ago", min)
            day < 1 -> if (hindi) String.format(Locale.US, "%d घंटे पहले", hr) else String.format(Locale.US, "%d hr ago", hr)
            else -> if (hindi) String.format(Locale.US, "%d दिन पहले", day) else String.format(Locale.US, "%d days ago", day)
        }
    }
}
