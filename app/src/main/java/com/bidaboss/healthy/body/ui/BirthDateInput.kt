package com.bidaboss.healthy.body.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Le champ ne garde que les chiffres (8 au plus) : « 07121987 ». */
fun birthDateDigits(text: String): String = text.filter { it.isDigit() }.take(8)

/** Ajoute les barres pendant la frappe : « 07121987 » s'affiche « 07/12/1987 ». */
object BirthDateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('/')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + slashesBefore(offset)
            override fun transformedToOriginal(offset: Int): Int =
                (offset - (if (offset > 2) 1 else 0) - (if (offset > 5) 1 else 0)).coerceIn(0, digits.length)

            // Une barre n'apparaît qu'une fois le chiffre suivant tapé.
            private fun slashesBefore(offset: Int) =
                (if (offset > 2 || (offset == 2 && digits.length > 2)) 1 else 0) +
                    (if (offset > 4 || (offset == 4 && digits.length > 4)) 1 else 0)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
