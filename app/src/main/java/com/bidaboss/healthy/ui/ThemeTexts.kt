package com.bidaboss.healthy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.core.progress.Motivation
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.theme.HealthyTheme

/**
 * Titres et messages qui changent avec le thème (fil Design, 11/11/2026). Les libellés des onglets
 * et des boutons, eux, ne changent jamais : ils restent clairs dans tous les thèmes.
 */
class ThemeTexts private constructor(private val theme: AppTheme) {

    /** Ligne au-dessus du bonjour, devant la date. */
    val dayTitle: String? = when (theme) {
        AppTheme.CLASSIC -> null
        AppTheme.PIRATE -> "Journal de bord du jour"
        AppTheme.TRAVEL -> "Carnet de voyage du jour"
    }

    fun greeting(firstName: String?): String {
        val hello = if (theme == AppTheme.PIRATE) "Ohé" else "Salut"
        return firstName?.let { "$hello $it !" } ?: "$hello !"
    }

    /** Petite phrase sous le bonjour. */
    fun headerMessage(mood: Mood, mealCount: Int): String = when (theme) {
        AppTheme.CLASSIC -> Motivation.message(mood, mealCount)
        AppTheme.PIRATE -> when (mood) {
            Mood.PROUD -> "Séance faite, cap tenu !"
            Mood.ENCOURAGING -> "Hisse les voiles, ta séance t'attend."
            Mood.HAPPY -> if (mealCount == 0) "Note ton premier repas au journal de bord." else "Bon vent pour la journée."
        }
        AppTheme.TRAVEL -> when (mood) {
            Mood.PROUD -> "Séance faite, étape franchie !"
            Mood.ENCOURAGING -> "Belle escale, on repart ? Ta séance t'attend."
            Mood.HAPPY -> if (mealCount == 0) "Note ton premier repas au carnet." else "Belle journée de voyage."
        }
    }

    /** Aujourd'hui, jour sans séance prévue ni activité. */
    val restDay: String = when (theme) {
        AppTheme.CLASSIC -> "Jour de repos, Pousse recharge ses feuilles."
        AppTheme.PIRATE -> "Jour de relâche, Capitaine Pousse se repose au port."
        AppTheme.TRAVEL -> "Jour d'escale, Pousse profite de la vue."
    }

    /** Repas : aucun repas ce jour-là. */
    val noMeal: String = when (theme) {
        AppTheme.CLASSIC -> "Rien dans l'assiette pour l'instant."
        AppTheme.PIRATE -> "Rien au journal de bord pour ce jour."
        AppTheme.TRAVEL -> "Rien au carnet de voyage pour ce jour."
    }

    /** Sport : pas encore de plan. */
    val noPlan: String = when (theme) {
        AppTheme.CLASSIC -> "Pas encore de plan sportif. Pousse attend le départ !"
        AppTheme.PIRATE -> "Pas encore de carte au trésor : importe ton plan sportif."
        AppTheme.TRAVEL -> "Pas encore d'itinéraire : importe ton plan sportif."
    }

    /** Ajouté au bilan des 7 derniers jours quand la moyenne est dans la cible. */
    val onTarget: String? = when (theme) {
        AppTheme.CLASSIC -> null
        AppTheme.PIRATE -> "Cap tenu !"
        AppTheme.TRAVEL -> "Étape franchie !"
    }

    /** Chiffre de la série : le nombre de semaines. */
    fun streakValue(weeks: Int): String = "$weeks"

    /** Unité sous le chiffre de la série : « sem. » ou « étapes » en Voyage. */
    fun streakUnit(weeks: Int): String = when (theme) {
        AppTheme.TRAVEL -> if (weeks == 1) "étape" else "étapes"
        else -> "sem."
    }

    fun streakDescription(weeks: Int): String {
        val unit = if (weeks == 1) "semaine active" else "semaines actives"
        return when (theme) {
            AppTheme.TRAVEL -> "$weeks ${streakUnit(weeks)} du voyage : $weeks $unit d'affilée"
            else -> "$weeks $unit d'affilée"
        }
    }

    val badgesTitle: String = when (theme) {
        AppTheme.CLASSIC -> "Mes badges"
        AppTheme.PIRATE -> "Mon trésor"
        AppTheme.TRAVEL -> "Mon passeport"
    }

    val celebrationTitle: String = when (theme) {
        AppTheme.CLASSIC -> "Bravo !"
        AppTheme.PIRATE -> "Séance terminée, moussaillon !"
        AppTheme.TRAVEL -> "Étape franchie !"
    }

    val celebrationText: String = when (theme) {
        AppTheme.CLASSIC -> "Séance enregistrée. Pousse est fière de toi."
        AppTheme.PIRATE -> "Séance notée au journal de bord. Capitaine Pousse est fière de toi."
        AppTheme.TRAVEL -> "Séance notée au carnet de voyage. Belle escale, on repart bientôt ?"
    }

    companion object {
        private val all = AppTheme.entries.associateWith { ThemeTexts(it) }

        fun of(theme: AppTheme): ThemeTexts = all.getValue(theme)

        /** Textes du thème en cours. */
        val current: ThemeTexts
            @Composable @ReadOnlyComposable
            get() = of(HealthyTheme.ambiance.theme)
    }
}
