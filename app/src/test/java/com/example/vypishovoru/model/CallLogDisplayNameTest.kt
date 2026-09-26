package com.example.vypishovoru.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogDisplayNameTest {

    private fun item(name: String?, org: String?) = CallLogItem(
        name = name,
        organization = org,
        number = "+420123456789",
        duration = 65L,
        date = 0L,
        accessibleDuration = "1 minut 5 sekund"
    )

    @Test
    fun `1 osobni kontakt se jmenem zachova jmeno`() {
        val log = item("Jan Novák", null)
        assertEquals("Jan Novák", log.displayName)
        assertNull(log.organizationSubtitle)
    }

    @Test
    fun `2 firemni kontakt bez jmena zobrazi firmu`() {
        val log = item(null, "ABC s.r.o.")
        assertEquals("ABC s.r.o.", log.displayName)
        assertNull(log.organizationSubtitle)
    }

    @Test
    fun `3 kontakt se jmenem i firmou ma jmeno jako titulek a firmu jako podtitulek`() {
        val log = item("Jan Novák", "ABC s.r.o.")
        assertEquals("Jan Novák", log.displayName)
        assertEquals("ABC s.r.o.", log.organizationSubtitle)
    }

    @Test
    fun `4 kontakt bez jmena a firmy je Neznamy kontakt`() {
        val log = item(null, null)
        assertEquals(UNKNOWN_CONTACT, log.displayName)
        assertEquals("Neznámý kontakt", log.displayName)
        assertNull(log.organizationSubtitle)
    }

    @Test
    fun `5 blank name plus validni firma vrati firmu`() {
        assertEquals("ABC s.r.o.", item("   ", "ABC s.r.o.").displayName)
        assertEquals("ABC s.r.o.", item("", "ABC s.r.o.").displayName)
    }

    @Test
    fun `6 validni jmeno plus blank firma vrati jmeno bez podtitulku`() {
        val log = item("Jan Novák", "   ")
        assertEquals("Jan Novák", log.displayName)
        assertNull(log.organizationSubtitle)
    }

    @Test
    fun `7 blank jmeno i blank firma vrati Neznamy kontakt`() {
        val log = item("  ", "  ")
        assertEquals(UNKNOWN_CONTACT, log.displayName)
        assertNull(log.organizationSubtitle)
    }

    @Test
    fun `8 accessibility text firemniho kontaktu obsahuje firmu a ne Neznamy kontakt`() {
        val log = item(null, "ABC s.r.o.")
        val desc = buildCallLogContentDescription(log, "+420 123 456 789", "01.01.2026 10:00")
        assertTrue(desc.contains("ABC s.r.o."))
        assertFalse(desc.contains("Neznámý kontakt"))
        assertTrue(desc.contains("Číslo:"))
        assertTrue(desc.contains("Datum:"))
        assertTrue(desc.contains("Délka hovoru:"))
    }

    @Test
    fun `accessibility text kontaktu se jmenem i firmou obsahuje oboji a firmu jen jednou`() {
        val log = item("Jan Novák", "ABC s.r.o.")
        val desc = buildCallLogContentDescription(log, "+420 123 456 789", "01.01.2026 10:00")
        assertTrue(desc.startsWith("Jan Novák. ABC s.r.o."))
        assertEquals(1, desc.split("ABC s.r.o.").size - 1)
    }

    @Test
    fun `accessibility text neznameho kontaktu zachova Neznamy kontakt`() {
        val log = item(null, null)
        val desc = buildCallLogContentDescription(log, "+420 123 456 789", "01.01.2026 10:00")
        assertTrue(desc.startsWith("Neznámý kontakt."))
    }
}
