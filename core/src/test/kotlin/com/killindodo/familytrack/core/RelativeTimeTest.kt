package com.killindodo.familytrack.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now = 10 * 60_000L

    @Test
    fun english() {
        assertEquals("just now", RelativeTime.format(now - 30_000, now, "en"))
        assertEquals("2 min ago", RelativeTime.format(now - 2 * 60_000, now, "en"))
        assertEquals("3 hr ago", RelativeTime.format(now - 3 * 3_600_000, now, "en"))
        assertEquals("2 days ago", RelativeTime.format(now - 2 * 86_400_000, now, "en"))
    }

    @Test
    fun hindi() {
        assertEquals("अभी-अभी", RelativeTime.format(now - 5_000, now, "hi-IN"))
        assertEquals("2 मिनट पहले", RelativeTime.format(now - 2 * 60_000, now, "hi"))
        assertEquals("3 घंटे पहले", RelativeTime.format(now - 3 * 3_600_000, now, "hi"))
        assertEquals("2 दिन पहले", RelativeTime.format(now - 2 * 86_400_000, now, "hi"))
    }
}
