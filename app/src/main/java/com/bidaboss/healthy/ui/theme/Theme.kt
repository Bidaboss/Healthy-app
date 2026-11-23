package com.bidaboss.healthy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.bidaboss.healthy.settings.data.AppTheme

// Palette fixe « vert frais » : identique sur tous les téléphones (pas de couleurs dynamiques).
internal val LightColors = lightColorScheme(
    primary = Color(0xFF1F7A47),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F0B1),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    // Indicateur d'onglet actif bien visible sur la barre claire (≥ 3:1, vérifié par ThemesTest).
    secondaryContainer = Color(0xFF1F7A47),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF38656A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF002023),
    background = Color(0xFFF3FAF5),
    onBackground = Color(0xFF17301F),
    surface = Color(0xFFF3FAF5),
    onSurface = Color(0xFF17301F),
    surfaceVariant = Color(0xFFD3EEDC),
    onSurfaceVariant = Color(0xFF44584B),
    outline = Color(0xFF72796F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    // Cartes blanches unies sur les fonds à motif (sans ces valeurs, Material 3 prend un gris violet).
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EE),
    surfaceContainer = Color(0xFFEAEFE8),
    surfaceContainerHigh = Color(0xFFE4EAE3),
    surfaceContainerHighest = Color(0xFFFFFFFF),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD497),
    onPrimary = Color(0xFF00390B),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFB7F0B1),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF253423),
    // Vert clair : l'onglet actif doit se voir sur la barre sombre (≥ 3:1).
    secondaryContainer = Color(0xFF9CD497),
    onSecondaryContainer = Color(0xFF00390B),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF1F4D52),
    onTertiaryContainer = Color(0xFFBCEBF0),
    background = Color(0xFF101510),
    onBackground = Color(0xFFE0E4DB),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFE0E4DB),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D17),
    surfaceContainer = Color(0xFF1C211B),
    surfaceContainerHigh = Color(0xFF262B25),
    surfaceContainerHighest = Color(0xFF313630),
)

// Thème pirate (fil Design, 11/11/2026) : parchemin et encre marine, nuit en mer en sombre.
internal val PirateLightColors = lightColorScheme(
    primary = Color(0xFF1E2A3A),
    onPrimary = Color(0xFFF4E9D2),
    primaryContainer = Color(0xFFD6DEE8),
    onPrimaryContainer = Color(0xFF0E1722),
    secondary = Color(0xFF6B4F2A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF1E2A3A),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFA8321F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7D3C9),
    onTertiaryContainer = Color(0xFF3B0904),
    background = Color(0xFFF4E9D2),
    onBackground = Color(0xFF1E2A3A),
    surface = Color(0xFFF4E9D2),
    onSurface = Color(0xFF1E2A3A),
    surfaceVariant = Color(0xFFE3D4B4),
    onSurfaceVariant = Color(0xFF4A4335),
    outline = Color(0xFF7D7259),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    // Cartes en parchemin plus clair, posées sur le fond parchemin.
    surfaceContainerLowest = Color(0xFFFBF4E4),
    surfaceContainerLow = Color(0xFFF0E3C8),
    surfaceContainer = Color(0xFFEBDDBF),
    surfaceContainerHigh = Color(0xFFE5D6B6),
    surfaceContainerHighest = Color(0xFFFBF4E4),
)

internal val PirateDarkColors = darkColorScheme(
    primary = Color(0xFFF0C766),
    onPrimary = Color(0xFF14202E),
    primaryContainer = Color(0xFF2E4058),
    onPrimaryContainer = Color(0xFFF4E9D2),
    secondary = Color(0xFFD9C29A),
    onSecondary = Color(0xFF2A1C08),
    // Or : l'onglet actif doit se voir sur la barre marine.
    secondaryContainer = Color(0xFFE0A526),
    onSecondaryContainer = Color(0xFF2A1C08),
    tertiary = Color(0xFFFFB4A6),
    onTertiary = Color(0xFF5F1409),
    tertiaryContainer = Color(0xFF8A2416),
    onTertiaryContainer = Color(0xFFFFDAD3),
    background = Color(0xFF14202E),
    onBackground = Color(0xFFF4E9D2),
    surface = Color(0xFF14202E),
    onSurface = Color(0xFFF4E9D2),
    surfaceVariant = Color(0xFF2E3D50),
    onSurfaceVariant = Color(0xFFD3C7AC),
    outline = Color(0xFF8E8571),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF0E1722),
    surfaceContainerLow = Color(0xFF1A2838),
    surfaceContainer = Color(0xFF1E2D3F),
    surfaceContainerHigh = Color(0xFF26364A),
    surfaceContainerHighest = Color(0xFF2B3C52),
)

// Thème voyage (fil Design, 11/11/2026) : sable clair et bleu océan, ciel de nuit en sombre.
internal val TravelLightColors = lightColorScheme(
    primary = Color(0xFF1C6E8C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6E7F2),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF1F7F75),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF1C6E8C),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFA6520F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF331200),
    background = Color(0xFFFBF3E4),
    onBackground = Color(0xFF1B2B33),
    surface = Color(0xFFFBF3E4),
    onSurface = Color(0xFF1B2B33),
    surfaceVariant = Color(0xFFEADFCB),
    onSurfaceVariant = Color(0xFF4A4A3F),
    outline = Color(0xFF7A7566),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFF4EBDA),
    surfaceContainer = Color(0xFFEFE5D2),
    surfaceContainerHigh = Color(0xFFE9DFCB),
    surfaceContainerHighest = Color(0xFFFFFDF8),
)

internal val TravelDarkColors = darkColorScheme(
    primary = Color(0xFF7FC8E3),
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF145A73),
    onPrimaryContainer = Color(0xFFC6E7F2),
    secondary = Color(0xFF6FD8CB),
    onSecondary = Color(0xFF003732),
    // Turquoise : l'onglet actif doit se voir sur la barre sombre (≥ 3:1).
    secondaryContainer = Color(0xFF6FD8CB),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF4F2500),
    tertiaryContainer = Color(0xFF7A3E0A),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF0F2433),
    onBackground = Color(0xFFFBF3E4),
    surface = Color(0xFF0F2433),
    onSurface = Color(0xFFFBF3E4),
    surfaceVariant = Color(0xFF2A4252),
    onSurfaceVariant = Color(0xFFCFC6B5),
    outline = Color(0xFF8C8676),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF0A1A25),
    surfaceContainerLow = Color(0xFF162E3F),
    surfaceContainer = Color(0xFF1A3345),
    surfaceContainerHigh = Color(0xFF223D51),
    surfaceContainerHighest = Color(0xFF284558),
)

/**
 * Ce qui change d'un thème à l'autre au-delà du jeu de couleurs Material : couleurs de l'en-tête,
 * de l'accent et des récompenses. La mascotte, les motifs, les récompenses et les textes suivent
 * [theme] (voir Pousse.kt, Patterns.kt, Confetti.kt et ThemeTexts.kt).
 */
@Immutable
data class Ambiance(
    val theme: AppTheme,
    /** Fond de l'en-tête d'Aujourd'hui et texte posé dessus. */
    val header: Color,
    val onHeader: Color,
    /** Accent pour le bouton principal d'un écran. */
    val accent: Color,
    val onAccent: Color,
    /** Couleur des réussites (étoiles, étincelles, pièces, tampons). */
    val sparkle: Color,
    val flame: Color,
    /** Encre des motifs de fond des écrans Repas et Sport. */
    val patternInk: Color,
)

private val ClassicAmbiance = Ambiance(
    theme = AppTheme.CLASSIC,
    header = Color(0xFF1F7A47),
    onHeader = Color.White,
    accent = Color(0xFFFF8A65),
    onAccent = Color(0xFF3A1408),
    sparkle = Color(0xFFFFC857),
    flame = Color(0xFFFF7043),
    patternInk = Color(0xFF1F7A47),
)

private val PirateAmbiance = Ambiance(
    theme = AppTheme.PIRATE,
    header = Color(0xFF1E2A3A),
    onHeader = Color(0xFFF4E9D2),
    accent = Color(0xFFC0392B),
    onAccent = Color.White,
    sparkle = Color(0xFFE0A526),
    flame = Color(0xFFE67E22),
    patternInk = Color(0xFF1E2A3A),
)

private val TravelAmbiance = Ambiance(
    theme = AppTheme.TRAVEL,
    header = Color(0xFF1C6E8C),
    onHeader = Color.White,
    accent = Color(0xFFF28C38),
    onAccent = Color(0xFF3A1A00),
    sparkle = Color(0xFF2BB3A3),
    flame = Color(0xFFF28C38),
    patternInk = Color(0xFF1C6E8C),
)

internal fun ambianceOf(theme: AppTheme): Ambiance = when (theme) {
    AppTheme.CLASSIC -> ClassicAmbiance
    AppTheme.PIRATE -> PirateAmbiance
    AppTheme.TRAVEL -> TravelAmbiance
}

internal fun colorSchemeOf(theme: AppTheme, dark: Boolean) = when (theme) {
    AppTheme.CLASSIC -> if (dark) DarkColors else LightColors
    AppTheme.PIRATE -> if (dark) PirateDarkColors else PirateLightColors
    AppTheme.TRAVEL -> if (dark) TravelDarkColors else TravelLightColors
}

private val LocalAmbiance = staticCompositionLocalOf { ClassicAmbiance }

/**
 * Thème clair ou sombre selon le réglage du téléphone. [theme] vient de Réglages et s'applique
 * tout de suite, sans redémarrer l'app.
 */
@Composable
fun HealthyTheme(theme: AppTheme = AppTheme.CLASSIC, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAmbiance provides ambianceOf(theme)) {
        MaterialTheme(
            colorScheme = colorSchemeOf(theme, isSystemInDarkTheme()),
            typography = HealthyTypography,
            shapes = HealthyShapes,
            content = content,
        )
    }
}

/** Accès à l'ambiance en cours, comme `MaterialTheme.colorScheme`. */
object HealthyTheme {
    val ambiance: Ambiance
        @Composable @ReadOnlyComposable
        get() = LocalAmbiance.current
}
