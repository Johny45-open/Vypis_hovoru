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
