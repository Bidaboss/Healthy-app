package com.bidaboss.healthy.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.R
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.theme.HealthyTheme

/** Les 4 onglets qui ont un décor photo (Moi n'en a pas). */
enum class PhotoTab { TODAY, MEALS, WORKOUT, HEALTH }

/**
 * Hauteur de la photo, comme dans le prototype (champ `fToday`) : elle couvre les 1040 dp du haut du contenu,
 * pas seulement un bandeau d'en-tête. Dessous, c'est la couleur de fond.
 */
private val PhotoHeight = 1040.dp

/**
 * Fond photo d'un onglet (refonte du 23/11/2026, aligné sur le prototype) : la photo du thème, recadrée
 * « cover » et centrée en haut, couvre les 1040 dp du haut du **contenu** derrière les cartes, défile avec lui
 * et se fond dans la couleur de fond (voile 0,38 → 0,18 à 22 % → 0,52 à 55 % → couleur de fond à 100 %).
 * Elle est dessinée derrière le contenu sans compter dans sa taille. Les titres posés dessus utilisent
 * [PhotoTitle] (blanc, ombre douce).
 *
 * À poser autour du contenu qui défile (colonne avec `verticalScroll`) : la photo bouge avec lui. Pour une
 * `LazyColumn`, utiliser [photoBackdrop] sur son parent fixe avec le décalage de la liste.
 */
@Composable
fun PhotoBackground(
    tab: PhotoTab,
    modifier: Modifier = Modifier,
    theme: AppTheme = HealthyTheme.ambiance.theme,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().photoBackdrop(tab, theme), content = content)
}

/**
 * Dessine le fond photo derrière ce composant. [scrollPx] donne de combien le contenu a défilé (0 pour un
 * contenu qui défile avec le composant lui-même, le décalage de la liste pour un parent fixe).
 */
@Composable
fun Modifier.photoBackdrop(
    tab: PhotoTab,
    theme: AppTheme = HealthyTheme.ambiance.theme,
    scrollPx: () -> Int = { 0 },
): Modifier {
    val photo = ImageBitmap.imageResource(PhotoBackgrounds.drawableOf(tab, theme))
    val scrim = PhotoBackgrounds.scrimOf(theme)
    val fade = MaterialTheme.colorScheme.background
    val height = with(LocalDensity.current) { PhotoHeight.toPx() }
    return drawBehind { drawPhotoBackdrop(photo, scrim, fade, height, scrollPx()) }
}

/** Le dessin du fond photo (voir [photoBackdrop]) ; [height] est la hauteur couverte, en pixels. */
internal fun DrawScope.drawPhotoBackdrop(photo: ImageBitmap, scrim: Color, fade: Color, height: Float, offset: Int) {
    // Entièrement défilée hors de l'écran : rien à dessiner.
    if (offset >= height) return
    val w = size.width
    // « cover » centré en haut : on garde la partie centrale de la photo qui remplit w × height.
    val scale = maxOf(w / photo.width, height / photo.height)
    val srcW = minOf(photo.width.toFloat(), w / scale)
    val srcH = minOf(photo.height.toFloat(), height / scale)
    translate(top = -offset.toFloat()) {
        drawImage(
            image = photo,
            srcOffset = IntOffset(((photo.width - srcW) / 2f).roundToInt(), 0),
            srcSize = IntSize(srcW.roundToInt(), srcH.roundToInt()),
            dstSize = IntSize(w.roundToInt(), height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
        drawRect(
            brush = Brush.verticalGradient(
                0f to scrim.copy(alpha = 0.38f),
                0.22f to scrim.copy(alpha = 0.18f),
                0.55f to scrim.copy(alpha = 0.52f),
                1f to fade,
                startY = 0f,
                endY = height,
            ),
            size = Size(w, height),
        )
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
