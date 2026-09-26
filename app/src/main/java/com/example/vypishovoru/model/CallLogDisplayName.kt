package com.example.vypishovoru.model

/**
 * Jediný zdroj pravdy pro zobrazovaný název kontaktu.
 *
 * Priorita: jméno > firma > "Neznámý kontakt".
 * Jméno má vždy přednost, aby se nezměnilo chování běžných osobních kontaktů.
 */
const val UNKNOWN_CONTACT = "Neznámý kontakt"

/** Hlavní titulek řádku výpisu i TalkBack oznámení. */
val CallLogItem.displayName: String
    get() = when {
        !name.isNullOrBlank() -> name
        !organization.isNullOrBlank() -> organization
        else -> UNKNOWN_CONTACT
    }

/**
 * Druhý řádek v UI (a druhá část TalkBack textu).
 * Non-null pouze když existuje současně neprázdné jméno i firma.
 */
val CallLogItem.organizationSubtitle: String?
    get() = if (!name.isNullOrBlank() && !organization.isNullOrBlank()) organization else null

/**
 * Čistá, unit-testovatelná stavba accessibility textu.
 * Používá stejné [displayName] a [organizationSubtitle] jako vizuální UI,
 * firma se nikdy nezdvojuje.
 */
fun buildCallLogContentDescription(
    log: CallLogItem,
    accessiblePhoneNumber: String,
    formattedDate: String
): String {
    val parts = mutableListOf(log.displayName)
    // U kontaktu "jméno + firma" oznam i firmu; u "pouze firma" už je firma v displayName.
    log.organizationSubtitle?.let { parts.add(it) }
    parts.add("Číslo: $accessiblePhoneNumber")
    parts.add("Datum: $formattedDate")
    parts.add("Délka hovoru: ${log.accessibleDuration}")
    return parts.joinToString(". ")
}
