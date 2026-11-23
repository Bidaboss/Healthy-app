package com.bidaboss.healthy.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.R
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.theme.HealthyTheme

/** Les 4 onglets qui ont un décor photo (Moi n'en a pas). */
enum class PhotoTab { TODAY, MEALS, WORKOUT, HEALTH }

/** Hauteur de la photo, comme dans le prototype : elle couvre le haut de l'écran, derrière les cartes. */
private val PhotoHeight = 520.dp

/**
 * Fond photo d'un onglet (refonte du 23/11/2026) : la photo du thème couvre le haut de l'écran derrière les
 * cartes et se fond peu à peu dans la couleur de fond. À poser **dans** le contenu qui défile (colonne avec
 * `verticalScroll`, ou en-tête de liste) : la photo défile alors avec lui. Le contenu se pose par-dessus ;
 * les titres sur la photo utilisent [PhotoTitle] (blanc, ombre douce).
 */
@Composable
fun PhotoBackground(
    tab: PhotoTab,
    modifier: Modifier = Modifier,
    theme: AppTheme = HealthyTheme.ambiance.theme,
    photoHeight: Dp = PhotoHeight,
    content: @Composable BoxScope.() -> Unit,
) {
    val fade = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(PhotoBackgrounds.drawableOf(tab, theme)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth().height(photoHeight),
        )
        // Voile sombre léger en haut pour la lisibilité des titres blancs, puis fondu vers la couleur de fond.
        val scrim = PhotoBackgrounds.scrimOf(theme)
        Box(
            Modifier
                .fillMaxWidth()
                .height(photoHeight)
                .background(
                    Brush.verticalGradient(
                        0f to scrim.copy(alpha = 0.38f),
                        0.22f to scrim.copy(alpha = 0.18f),
                        0.55f to scrim.copy(alpha = 0.52f),
                        1f to fade,
                    ),
                ),
        )
        content()
    }
}

/** Titre posé sur la photo : blanc avec une ombre douce, pour rester lisible sur toutes les images. */
@Composable
fun PhotoTitle(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.headlineMedium) {
    Text(
        text,
        modifier = modifier,
        color = Color.White,
        style = style.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 2f), blurRadius = 12f)),
    )
}

/** Quelle image pour quel onglet et quel thème : 12 décors, 4 par thème (WebP léger dans `drawable-nodpi`). */
object PhotoBackgrounds {
    @DrawableRes
    fun drawableOf(tab: PhotoTab, theme: AppTheme): Int = when (theme) {
        AppTheme.CLASSIC -> when (tab) {
            PhotoTab.TODAY -> R.drawable.decor_classique_aujourdhui
            PhotoTab.MEALS -> R.drawable.decor_classique_repas
            PhotoTab.WORKOUT -> R.drawable.decor_classique_sport
            PhotoTab.HEALTH -> R.drawable.decor_classique_sante
        }
        AppTheme.PIRATE -> when (tab) {
            PhotoTab.TODAY -> R.drawable.decor_pirate_aujourdhui
            PhotoTab.MEALS -> R.drawable.decor_pirate_repas
            PhotoTab.WORKOUT -> R.drawable.decor_pirate_sport
            PhotoTab.HEALTH -> R.drawable.decor_pirate_sante
        }
        AppTheme.TRAVEL -> when (tab) {
            PhotoTab.TODAY -> R.drawable.decor_voyage_aujourdhui
            PhotoTab.MEALS -> R.drawable.decor_voyage_repas
            PhotoTab.WORKOUT -> R.drawable.decor_voyage_sport
            PhotoTab.HEALTH -> R.drawable.decor_voyage_sante
        }
    }

    /** Teinte du voile par thème (vert sapin, nuit marine, bleu océan). */
    fun scrimOf(theme: AppTheme): Color = when (theme) {
        AppTheme.CLASSIC -> Color(0xFF082616)
        AppTheme.PIRATE -> Color(0xFF080E1A)
        AppTheme.TRAVEL -> Color(0xFF08283A)
    }
}
