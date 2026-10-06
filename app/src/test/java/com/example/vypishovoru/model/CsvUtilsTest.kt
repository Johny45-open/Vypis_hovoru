package com.example.vypishovoru.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvUtilsTest {

    @Test
    fun `1 prosta firma zustane beze zmeny`() {
        assertEquals("ACME s.r.o.", escapeCsvField("ACME s.r.o."))
    }

    @Test
    fun `2 firma s carkou se obali uvozovkami`() {
        assertEquals("\"ACME, s.r.o.\"", escapeCsvField("ACME, s.r.o."))
    }

    @Test
    fun `3 firma s uvozovkami zdvoji uvozovky a obali pole`() {
        assertEquals("\"\"\"ACME\"\" s.r.o.\"", escapeCsvField("\"ACME\" s.r.o."))
    }

    @Test
    fun `4 hodnota s novym radkem se obali uvozovkami beze ztraty dat`() {
        assertEquals("\"ACME\ns.r.o.\"", escapeCsvField("ACME\ns.r.o."))
    }

    @Test
    fun `5 hodnota s carriage return se obali uvozovkami`() {
        assertEquals("\"ACME\rs.r.o.\"", escapeCsvField("ACME\rs.r.o."))
    }

    @Test
    fun `6 kombinace carky uvozovek a noveho radku`() {
        val input = "\"ACME\", s.r.o.\nPraha"
        val expected = "\"\"\"ACME\"\", s.r.o.\nPraha\""
        assertEquals(expected, escapeCsvField(input))
    }

    @Test
    fun `7 prazdny retezec zustane prazdny`() {
        assertEquals("", escapeCsvField(""))
    }

    @Test
    fun `8 jmeno s carkou se neposkodi nahrazenim mezerou`() {
        // Regrese proti starému .replace(",", " "): data musí zůstat přesná.
        assertEquals("\"Novák, Jan\"", escapeCsvField("Novák, Jan"))
    }
}
