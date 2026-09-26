package com.example.vypishovoru.model

/**
 * Reprezentuje jeden záznam v historii hovorů.
 *
 * @property name Jméno kontaktu (pokud existuje, jinak null).
 * @property organization Název organizace kontaktu (pokud existuje, jinak null).
 * @property number Telefonní číslo.
 * @property duration Sekundy trvání hovoru.
 * @property date Datum hovoru v milisekundách.
 * @property accessibleDuration Formátovaný text pro čtečky obrazovky (např. "5 minut 10 sekund").
 */
data class CallLogItem(
    val name: String?,
    val organization: String?,
    val number: String,
    val duration: Long,
    val date: Long,
    val accessibleDuration: String
)
