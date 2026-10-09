package com.example.vypishovoru.model

/**
 * RFC 4180 CSV escapování jednoho pole.
 *
 * Pravidla:
 * - pokud hodnota obsahuje čárku, uvozovku, \n nebo \r, obalí se dvojitými
 *   uvozovkami a každý vnitřní znak " se zdvojí na ""
 * - jinak se vrátí beze změny (žádná ztráta dat, žádné nahrazování čárek mezerou)
 */
fun escapeCsvField(value: String): String {
    if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
    return value
}

/**
 * Čistá, unit-testovatelná volba první neprázdné firmy.
 *
 * Používá ji ViewModel po načtení Organization.COMPANY hodnot seřazených
 * deterministicky (ORDER BY COMPANY COLLATE NOCASE ASC): první non-blank vítězí.
 *
 * @param values COMPANY hodnoty v deterministickém pořadí dotazu
 * @return první non-blank hodnota, jinak null
 */
fun pickFirstNonBlank(values: List<String?>): String? {
    return values.firstOrNull { !it.isNullOrBlank() }
}

/**
 * Excelová direktiva oddělovače. Musí být úplně prvním textovým řádkem
 * exportovaného CSV, před hlavičkou. Není součástí RFC 4180 — je určena
 * pro přímé otevření v desktopovém Microsoft Excelu, aby se sloupce
 * rozdělily automaticky i na systémech s jiným výchozím oddělovačem
 * (např. středník v českém prostředí).
 */
const val CSV_SEPARATOR_DIRECTIVE = "sep=,"

/** Hlavička exportu: přesně pět sloupců v tomto pořadí. */
const val CSV_HEADER = "Jméno,Organizace,Číslo,Trvání (s),Datum"

/** UTF-8 BOM. V souboru smí být přesně jednou, na úplném začátku před `sep=,`. */
val UTF8_BOM: ByteArray = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

/**
 * Čisté sestavení celého textového obsahu CSV exportu (bez BOM).
 *
 * Logická struktura výsledku:
 * ```
 * sep=,
 * Jméno,Organizace,Číslo,Trvání (s),Datum
 * ...datové řádky...
 * ```
 * Escapování, formát čísel i pořadí sloupců jsou shodné s dosavadním
 * kontraktem `CallLogViewModel.generateCsvData()`.
 *
 * @param items záznamy v pořadí, v jakém se mají exportovat
 * @param formatDate formátování data (ViewModel předává `SimpleDateFormat`)
 */
fun buildCallLogCsv(
    items: List<CallLogItem>,
    formatDate: (Long) -> String
): String {
    val csvRows = items.joinToString("\n") {
        val name = escapeCsvField(it.name ?: "")
        val org = escapeCsvField(it.organization ?: "")
        // Apostrof před číslem zajistí, že Excel bude hodnotu brát jako text a ne jako vzorec.
        // Uvozovky v čísle jsou zdvojeny, pole je vždy v uvozovkách (konzistentní CSV).
        val safeNumber = it.number.replace("\"", "\"\"")
        val formattedNumber = "'$safeNumber"
        val formattedDate = formatDate(it.date)
        "${name},${org},\"${formattedNumber}\",${it.duration},${formattedDate}"
    }
    return "$CSV_SEPARATOR_DIRECTIVE\n$CSV_HEADER\n$csvRows"
}

/**
 * Čisté zakódování hotového CSV textu do bajtů výsledného souboru:
 * přesně jeden UTF-8 BOM na začátku + text v UTF-8.
 */
fun encodeCsvFileBytes(csvText: String): ByteArray {
    val body = csvText.toByteArray(Charsets.UTF_8)
    return UTF8_BOM + body
}
