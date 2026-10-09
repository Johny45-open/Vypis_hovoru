package com.example.vypishovoru.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportStructureTest {

    private fun item(
        name: String? = null,
        org: String? = null,
        number: String = "+420733255671",
        duration: Long = 3L,
        date: Long = 0L
    ) = CallLogItem(
        name = name,
        organization = org,
        number = number,
        duration = duration,
        date = date,
        accessibleDuration = "3 sekund"
    )

    private val fixedDate: (Long) -> String = { "2026-01-01 10:00:00" }

    @Test
    fun `1 prvni radek je presne sep a druhy je hlavicka s peti sloupci`() {
        val csv = buildCallLogCsv(listOf(item(name = "Tatka")), fixedDate)
        val lines = csv.split("\n")
        assertEquals("sep=,", lines[0])
        assertEquals("Jméno,Organizace,Číslo,Trvání (s),Datum", lines[1])
        assertEquals(5, lines[1].split(",").size)
    }

    @Test
    fun `2 prazdny export obsahuje jen direktivu a hlavicku`() {
        val csv = buildCallLogCsv(emptyList(), fixedDate)
        assertEquals("sep=,\n$CSV_HEADER\n", csv)
    }

    @Test
    fun `3 datove radky zachovavaji poradi a obsah`() {
        val items = listOf(
            item(name = "Prvni", duration = 1L),
            item(name = "Druhy", duration = 2L)
        )
        val csv = buildCallLogCsv(items, fixedDate)
        val lines = csv.split("\n")
        assertEquals(4, lines.size)
        assertTrue(lines[2].startsWith("Prvni,"))
        assertTrue(lines[3].startsWith("Druhy,"))
        assertTrue(lines[2].contains(",1,2026-01-01 10:00:00"))
        assertTrue(lines[3].contains(",2,2026-01-01 10:00:00"))
    }

    @Test
    fun `4 jmena a firmy s carkami a uvozovkami zustanou spravne escapovane`() {
        val csv = buildCallLogCsv(
            listOf(item(name = "Novák, Jan", org = "\"ACME\" s.r.o.")),
            fixedDate
        )
        val dataLine = csv.split("\n")[2]
        assertTrue(dataLine.startsWith("\"Novák, Jan\",\"\"\"ACME\"\" s.r.o.\","))
    }

    @Test
    fun `5 ceska diakritika se zachova`() {
        val csv = buildCallLogCsv(
            listOf(item(name = "Taťka", org = "Babička")),
            fixedDate
        )
        assertTrue(csv.contains("Taťka"))
        assertTrue(csv.contains("Babička"))
        assertTrue(csv.contains("Jméno,Organizace,Číslo,Trvání (s),Datum"))
    }

    @Test
    fun `6 prazdna pole nerozhodi pocet ani poradi sloupcu`() {
        val csv = buildCallLogCsv(listOf(item(name = null, org = null)), fixedDate)
        val dataLine = csv.split("\n")[2]
        // name="" org="" -> řádek začíná ",," a telefonní pole následuje jako třetí
        assertTrue(dataLine.startsWith(",,"))
        assertTrue(dataLine.contains(",3,2026-01-01 10:00:00"))
    }

    @Test
    fun `7 telefonni cisla dodrzuji dosavadni kontrakt apostrof v uvozovkach`() {
        val csvPlus = buildCallLogCsv(listOf(item(number = "+420733255671")), fixedDate)
        assertTrue(csvPlus.split("\n")[2].contains("\"'+420733255671\""))

        val csvZero = buildCallLogCsv(listOf(item(number = "0608928166")), fixedDate)
        assertTrue(csvZero.split("\n")[2].contains("\"'0608928166\""))

        val csvQuote = buildCallLogCsv(listOf(item(number = "123\"456")), fixedDate)
        assertTrue(csvQuote.split("\n")[2].contains("\"'123\"\"456\""))
    }

    @Test
    fun `8 encodeCsvFileBytes zapise BOM presne jednou na zacatek pred sep`() {
        val csv = buildCallLogCsv(listOf(item(name = "Tatka")), fixedDate)
        val bytes = encodeCsvFileBytes(csv)

        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])

        val body = bytes.drop(3).toByteArray().toString(Charsets.UTF_8)
        assertEquals(csv, body)
        assertTrue(body.startsWith("sep=,\n"))

        // BOM sekvence se v celém souboru vyskytuje právě jednou (na začátku)
        var occurrences = 0
        for (i in bytes.indices) {
            if (i + 2 < bytes.size &&
                bytes[i] == 0xEF.toByte() &&
                bytes[i + 1] == 0xBB.toByte() &&
                bytes[i + 2] == 0xBF.toByte()
            ) {
                occurrences++
            }
        }
        assertEquals(1, occurrences)
    }
}
