package com.bidaboss.healthy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.R

/**
 * Nunito, police arrondie embarquée dans l'app (licence SIL OFL 1.1 : licenses/Nunito-OFL.txt).
 * Un seul fichier « variable » : chaque graisse est une variation de ce fichier (Android 8 et plus).
 */
@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Nunito = FontFamily(nunito(400), nunito(500), nunito(600), nunito(700), nunito(800), nunito(900))

private val base = Typography()

private fun TextStyle.nunito(weight: FontWeight) = copy(fontFamily = Nunito, fontWeight = weight)

/** Titres et chiffres en Nunito très gras (800), corps de texte en Nunito 500 (fil Design, 12/11/2026). */
val HealthyTypography = Typography(
    displayLarge = base.displayLarge.nunito(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.nunito(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.nunito(FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.nunito(FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.nunito(FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.nunito(FontWeight.ExtraBold),
    titleLarge = base.titleLarge.nunito(FontWeight.ExtraBold),
    titleMedium = base.titleMedium.nunito(FontWeight.Bold),
    titleSmall = base.titleSmall.nunito(FontWeight.Bold),
    bodyLarge = base.bodyLarge.nunito(FontWeight.Medium),
    bodyMedium = base.bodyMedium.nunito(FontWeight.Medium),
    bodySmall = base.bodySmall.nunito(FontWeight.Medium),
    labelLarge = base.labelLarge.nunito(FontWeight.Bold),
    labelMedium = base.labelMedium.nunito(FontWeight.Bold),
    labelSmall = base.labelSmall.nunito(FontWeight.Bold),
)

/** Formes plus rondes : cartes à 24 dp ; les boutons Material 3 sont déjà en pilule. */
val HealthyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
