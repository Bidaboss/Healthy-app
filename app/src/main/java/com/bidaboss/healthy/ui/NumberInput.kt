package com.bidaboss.healthy.ui

/** Lit un nombre tapé par l'utilisateur, avec une virgule ou un point. null si illisible. */
fun parseNumber(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Affiche un nombre dans un champ de saisie : sans « ,0 » inutile, avec une virgule. */
fun Double?.toInputText(): String {
    val value = this ?: return ""
    return if (value == Math.floor(value)) value.toLong().toString() else value.toString().replace('.', ',')
}
