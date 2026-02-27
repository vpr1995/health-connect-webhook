package com.hcwebhook.app.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ManualSyncCardTest {

    @Test
    fun buildSyncDateRange_convertsSelectedDaysToLocalDayBounds() {
        val fromDate = LocalDate.of(2026, 1, 10)
        val toDate = LocalDate.of(2026, 1, 12)
        val fromMillis = fromDate.toEpochDay() * MILLIS_PER_DAY
        val toMillis = toDate.toEpochDay() * MILLIS_PER_DAY

        val (fromInstant, toInstant) = buildSyncDateRange(fromMillis, toMillis)
        val zone = ZoneId.systemDefault()

        assertEquals(fromDate, fromInstant!!.atZone(zone).toLocalDate())
        assertEquals(toDate.plusDays(1), toInstant!!.atZone(zone).toLocalDate())
    }

    @Test
    fun buildSyncDateRange_supportsOpenEndedRange() {
        val toDate = LocalDate.of(2026, 1, 12)
        val toMillis = toDate.toEpochDay() * MILLIS_PER_DAY

        val (fromInstant, toInstant) = buildSyncDateRange(null, toMillis)

        assertNull(fromInstant)
        assertEquals(
            toDate.plusDays(1),
            toInstant!!.atZone(ZoneId.systemDefault()).toLocalDate()
        )
    }

    @Test
    fun formatDateLabel_formatsUtcDateMillis() {
        val dateMillis = LocalDate.of(2026, 1, 12).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        assertEquals("2026-01-12", formatDateLabel(dateMillis, "Now"))
        assertEquals("Now", formatDateLabel(null, "Now"))
    }
}
