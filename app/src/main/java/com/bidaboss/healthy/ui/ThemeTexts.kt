package com.bidaboss.healthy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.core.progress.Motivation
import com.bidaboss.healthy.settings.data.AppTheme
import com.bidaboss.healthy.ui.theme.HealthyTheme
import java.util.Locale

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
        AppTheme.SPACE -> "Mission du jour"
        AppTheme.ARCADE -> "Niveau du jour"
        AppTheme.MOUNTAIN -> "Carnet de course du jour"
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
        AppTheme.SPACE -> when (mood) {
            Mood.PROUD -> "Mission du jour accomplie !"
            Mood.ENCOURAGING -> "Compte à rebours lancé : ta séance t'attend."
            Mood.HAPPY -> if (mealCount == 0) "Fais le plein : note ton premier repas." else "Bon vol pour la journée."
        }
        AppTheme.ARCADE -> when (mood) {
            Mood.PROUD -> "Niveau terminé ! +1 vie."
            Mood.ENCOURAGING -> "Continuer ? Ta séance t'attend."
            Mood.HAPPY -> if (mealCount == 0) "Note un repas pour lancer la partie." else "Bonne partie aujourd'hui."
        }
        AppTheme.MOUNTAIN -> when (mood) {
            Mood.PROUD -> "Sommet atteint !"
            Mood.ENCOURAGING -> "Le sentier t'attend : ta séance est prévue."
            Mood.HAPPY -> if (mealCount == 0) "Note ton premier repas avant la montée." else "Belle journée en montagne."
        }
    }

    /** Aujourd'hui, jour sans séance prévue ni activité. */
    val restDay: String = when (theme) {
        AppTheme.CLASSIC -> "Jour de repos, Pousse recharge ses feuilles."
        AppTheme.PIRATE -> "Jour de relâche, Capitaine Pousse se repose au port."
        AppTheme.TRAVEL -> "Jour d'escale, Pousse profite de la vue."
        AppTheme.SPACE -> "Jour de repos, Pousse flotte en apesanteur."
        AppTheme.ARCADE -> "Pause : Pousse recharge ses vies."
        AppTheme.MOUNTAIN -> "Belle étape, on souffle au refuge."
    }

    /** Repas : aucun repas ce jour-là. */
    val noMeal: String = when (theme) {
        AppTheme.CLASSIC -> "Rien dans l'assiette pour l'instant."
        AppTheme.PIRATE -> "Rien au journal de bord pour ce jour."
        AppTheme.TRAVEL -> "Rien au carnet de voyage pour ce jour."
        AppTheme.SPACE -> "Rien au carnet de bord de la station pour ce jour."
        AppTheme.ARCADE -> "Aucun repas dans la partie pour ce jour."
        AppTheme.MOUNTAIN -> "Rien au carnet de course pour ce jour."
    }

    /** Sport : pas encore de plan. */
    val noPlan: String = when (theme) {
        AppTheme.CLASSIC -> "Pas encore de plan sportif. Pousse attend le départ !"
        AppTheme.PIRATE -> "Pas encore de carte au trésor : importe ton plan sportif."
        AppTheme.TRAVEL -> "Pas encore d'itinéraire : importe ton plan sportif."
        AppTheme.SPACE -> "Pas encore de plan de vol : importe ton plan sportif."
        AppTheme.ARCADE -> "Pas encore de niveaux : importe ton plan sportif."
        AppTheme.MOUNTAIN -> "Pas encore de topo : importe ton plan sportif."
    }

    /** Ajouté au bilan des 7 derniers jours quand la moyenne est dans la cible. */
    val onTarget: String? = when (theme) {
        AppTheme.CLASSIC -> null
        AppTheme.PIRATE -> "Cap tenu !"
        AppTheme.TRAVEL -> "Étape franchie !"
        AppTheme.SPACE -> "Trajectoire parfaite !"
        AppTheme.ARCADE -> "+1 vie"
        AppTheme.MOUNTAIN -> "Bon rythme de marche !"
    }

    /** Chiffre de la série : le nombre de semaines, ou l'altitude en Montagne (400 m par semaine). */
    fun streakValue(weeks: Int): String = when (theme) {
        AppTheme.MOUNTAIN -> altitude(weeks)
        else -> "$weeks"
    }

    /** Unité sous le chiffre de la série : « sem. », « étapes », « missions », « niveau » ou « m ». */
    fun streakUnit(weeks: Int): String = when (theme) {
        AppTheme.TRAVEL -> if (weeks == 1) "étape" else "étapes"
        AppTheme.SPACE -> if (weeks == 1) "mission" else "missions"
        AppTheme.ARCADE -> "niveau"
        AppTheme.MOUNTAIN -> "m"
        else -> "sem."
    }

    fun streakDescription(weeks: Int): String {
        val unit = if (weeks == 1) "semaine active" else "semaines actives"
        return when (theme) {
            AppTheme.TRAVEL -> "$weeks ${streakUnit(weeks)} du voyage : $weeks $unit d'affilée"
            AppTheme.SPACE -> "$weeks ${streakUnit(weeks)} : $weeks $unit d'affilée"
            AppTheme.ARCADE -> "Niveau $weeks : $weeks $unit d'affilée"
            AppTheme.MOUNTAIN -> "${altitude(weeks)} m d'altitude : $weeks $unit d'affilée"
            else -> "$weeks $unit d'affilée"
        }
    }

    val badgesTitle: String = when (theme) {
        AppTheme.CLASSIC -> "Mes badges"
        AppTheme.PIRATE -> "Mon trésor"
        AppTheme.TRAVEL -> "Mon passeport"
        AppTheme.SPACE -> "Mes planètes"
        AppTheme.ARCADE -> "Mes trophées"
        AppTheme.MOUNTAIN -> "Mes écussons"
    }

    val celebrationTitle: String = when (theme) {
        AppTheme.CLASSIC -> "Bravo !"
        AppTheme.PIRATE -> "Séance terminée, moussaillon !"
        AppTheme.TRAVEL -> "Étape franchie !"
        AppTheme.SPACE -> "Décollage réussi !"
        AppTheme.ARCADE -> "Niveau terminé !"
        AppTheme.MOUNTAIN -> "Sommet atteint !"
    }

    val celebrationText: String = when (theme) {
        AppTheme.CLASSIC -> "Séance enregistrée. Pousse est fière de toi."
        AppTheme.PIRATE -> "Séance notée au journal de bord. Capitaine Pousse est fière de toi."
        AppTheme.TRAVEL -> "Séance notée au carnet de voyage. Belle escale, on repart bientôt ?"
        AppTheme.SPACE -> "Séance enregistrée. Mission du jour accomplie, Pousse est fière de toi."
        AppTheme.ARCADE -> "Séance enregistrée. +1 vie pour Pousse !"
        AppTheme.MOUNTAIN -> "Séance enregistrée. Fanion planté, Pousse est fière de toi."
    }

    /** Altitude de la série en Montagne : 400 m par semaine, espace fine comme séparateur des milliers. */
    private fun altitude(weeks: Int): String = "%,d".format(Locale.FRANCE, weeks * 400).replace('\u202F', ' ').replace('\u00A0', ' ')

    companion object {
        private val all = AppTheme.entries.associateWith { ThemeTexts(it) }

        fun of(theme: AppTheme): ThemeTexts = all.getValue(theme)

        /** Textes du thème en cours. */
        val current: ThemeTexts
            @Composable @ReadOnlyComposable
            get() = of(HealthyTheme.ambiance.theme)
    }
}
