package com.bidaboss.healthy.core.motivation

import com.bidaboss.healthy.core.body.Hydration
import kotlin.random.Random

/**
 * Ton des notifications de motivation, choisi dans Réglages (demande du propriétaire, 12/11/2026).
 * Le nom est gardé tel quel dans les préférences.
 */
enum class MotivationTone { GENTLE, TRASH, SUPER_TRASH }

/** Les deux notifications du jour : vers 12 h 30 (repas, eau) et vers 19 h (séance, bilan). */
enum class MotivationSlot(val hour: Int, val minute: Int) {
    NOON(12, 30),
    EVENING(19, 0),
}

/** Ce qu'on sait de la journée au moment de la notification. */
data class DaySnapshot(
    val mealCount: Int,
    val waterMl: Int,
    /** Objectif d'eau du jour ; sans profil, on prend le minimum ([Hydration.MIN_ML]). */
    val waterTargetMl: Int,
    /** Titre de la séance prévue aujourd'hui par le plan, ou null. */
    val plannedSession: String?,
    val sessionsDone: Int,
)

/** Ce que la notification relève dans la journée. */
enum class Situation { NO_MEAL, LOW_WATER, SESSION_PENDING, SESSION_DONE, ALL_GOOD }

data class MotivationMessage(val title: String, val text: String)

object Motivation {

    /**
     * Le midi : d'abord les repas, puis l'eau (moins de 40 % de l'objectif).
     * Le soir : d'abord la séance prévue et pas faite, puis les repas, puis l'eau (moins de 80 %).
     */
    fun situation(slot: MotivationSlot, day: DaySnapshot): Situation {
        val target = day.waterTargetMl.coerceAtLeast(1)
        return when (slot) {
            MotivationSlot.NOON -> when {
                day.mealCount == 0 -> Situation.NO_MEAL
                day.waterMl * 10 < target * 4 -> Situation.LOW_WATER
                else -> Situation.ALL_GOOD
            }
            MotivationSlot.EVENING -> when {
                day.plannedSession != null && day.sessionsDone == 0 -> Situation.SESSION_PENDING
                day.mealCount == 0 -> Situation.NO_MEAL
                day.waterMl * 10 < target * 8 -> Situation.LOW_WATER
                day.sessionsDone > 0 -> Situation.SESSION_DONE
                else -> Situation.ALL_GOOD
            }
        }
    }

    /** Un message au hasard parmi ceux du ton et de la situation, avec les valeurs du jour. */
    fun message(tone: MotivationTone, slot: MotivationSlot, day: DaySnapshot, random: Random = Random.Default): MotivationMessage {
        val situation = situation(slot, day)
        val (title, text) = MotivationTexts.of(tone, situation).random(random)
        return MotivationMessage(fill(title, day), fill(text, day))
    }

    private fun fill(template: String, day: DaySnapshot): String = template
        .replace("{session}", day.plannedSession ?: "Ta séance")
        .replace("{water}", Hydration.formatLiters(day.waterMl))
        .replace("{target}", Hydration.formatLiters(day.waterTargetMl))
}

/**
 * Les textes, par ton et par situation : un titre et un texte. {session}, {water} et {target}
 * sont remplacés par la séance du jour, l'eau bue et l'objectif. Les textes ne sont pas genrés :
 * l'app est partagée avec des proches.
 */
object MotivationTexts {

    fun of(tone: MotivationTone, situation: Situation): List<Pair<String, String>> = when (tone) {
        MotivationTone.GENTLE -> gentle(situation)
        MotivationTone.TRASH -> trash(situation)
        MotivationTone.SUPER_TRASH -> superTrash(situation)
    }

    private fun gentle(situation: Situation) = when (situation) {
        Situation.NO_MEAL -> listOf(
            "Et ce repas ?" to "Pense à noter ton repas quand tu as un moment 🙂",
            "Petit rappel tout doux" to "Rien n'est noté pour l'instant. Pas grave, il est encore temps.",
            "Bon appétit !" to "N'oublie pas de noter ce que tu manges, ça t'aide à y voir clair 💚",
        )
        Situation.LOW_WATER -> listOf(
            "Un verre d'eau ?" to "Tu en es à {water} sur {target}. Ton corps te dira merci 💧",
            "Pause hydratation" to "Un petit verre maintenant ? Chaque gorgée compte.",
            "Pense à boire" to "{water} pour l'instant. Un verre et tu te rapproches de {target} 🙂",
        )
        Situation.SESSION_PENDING -> listOf(
            "Ta séance t'attend" to "{session} t'attend quand tu veux 💚",
            "Tu vas te sentir bien" to "Quelques minutes pour {session}, et tu ne le regretteras pas.",
            "On y va ?" to "{session} est prévue aujourd'hui. Même une version courte compte.",
        )
        Situation.SESSION_DONE -> listOf(
            "Séance faite 💪" to "Tu as fait ta séance aujourd'hui. Profite de ta soirée, tu l'as méritée.",
            "Bravo !" to "Séance faite, tu avances. Repose-toi bien.",
        )
        Situation.ALL_GOOD -> listOf(
            "Belle journée !" to "Tout est noté, tu prends soin de toi. Bravo 🌟",
            "Bravo à toi" to "Chaque petit pas compte, et tu en fais plein.",
            "Continue comme ça" to "Ta journée est bien remplie. Garde ce rythme 💚",
        )
    }

    private fun trash(situation: Situation) = when (situation) {
        Situation.NO_MEAL -> listOf(
            "Repas fantôme" to "Rien de noté. Les calories, elles, n'ont pas oublié de s'installer sur tes hanches.",
            "Allô ?" to "Ton journal est plus vide que ta motivation. Note ce que tu as mangé.",
            "Toujours rien ?" to "Tu crois que ne pas noter le kebab l'empêche de compter ? Spoiler : non.",
            "Mangé en cachette ?" to "Aucun repas noté. Ton ventre, lui, tient les comptes.",
        )
        Situation.LOW_WATER -> listOf(
            "T'es un cactus ?" to "{water} sur {target}. Même une plante verte s'hydrate mieux que toi.",
            "Mode biscotte" to "{water} bus sur {target}. Tu vas finir aussi sec qu'un pruneau.",
            "Le chameau, là" to "Seulement {water}. Tu stockes l'eau dans tes bosses ? Va boire.",
        )
        Situation.SESSION_PENDING -> listOf(
            "Le canapé gagne encore" to "{session} t'attend. Ton canapé a déjà la forme de tes fesses, ne l'aide pas.",
            "Debout, la limace" to "{session} est prévue aujourd'hui. Tes abdos sont en mode avion depuis trop longtemps.",
            "Excuse du jour ?" to "La fatigue, pas le temps, le dos… On connaît. {session}, maintenant.",
            "20 minutes, pas plus" to "{session}, c'est moins long qu'un épisode. Ton jean te supplie de le faire.",
        )
        Situation.SESSION_DONE -> listOf(
            "Bon, t'as transpiré" to "Séance faite. Tes poignées d'amour tremblent de peur. Continue.",
            "Enfin un effort" to "Séance faite. Je retire une partie de ce que j'ai dit sur toi. Une petite partie.",
        )
        Situation.ALL_GOOD -> listOf(
            "Pas mal… pour une fois" to "Journée propre. Ne t'habitue pas aux compliments.",
            "Miracle" to "Tout est noté, tu as bu. Qui es-tu et qu'as-tu fait du vrai toi ?",
            "Ok, respect" to "Rien à te reprocher aujourd'hui. Ça m'énerve, mais bravo.",
        )
    }

    private fun superTrash(situation: Situation) = when (situation) {
        Situation.NO_MEAL -> listOf(
            "Espèce de patate" to "Zéro repas noté. Même une patate tiendrait mieux son journal que toi.",
            "Gros flemmard" to "T'as la flemme de noter ce que tu bouffes, mais pas la flemme de bouffer. Chapeau l'artiste.",
            "Tête de pioche" to "Toujours rien de noté. Tu comptes sur la magie ? Les calories, elles, bossent.",
            "Bouffe fantôme" to "Rien dans le journal, tout dans le bide. Note, espèce de mollusque.",
        )
        Situation.LOW_WATER -> listOf(
            "Espèce de cactus" to "{water} sur {target}. Même ton ficus boit plus que toi, tête de linotte.",
            "Sac à poussière" to "{water} bus. Tu vas te dessécher comme un vieux saucisson. Va boire, banane.",
            "Le dromadaire" to "{water} sur {target}. T'attends quoi, la pluie ? Remplis ce verre, tête de nœud.",
        )
        Situation.SESSION_PENDING -> listOf(
            "Debout, grosse larve" to "{session} t'attend. Ton canapé demande un congé maladie, il en peut plus de toi.",
            "Espèce de mollusque" to "{session} prévue aujourd'hui. Une huître fait plus de sport que toi.",
            "Feignasse" to "Tes muscles ont posé une main courante pour abandon. {session}, maintenant, boulet.",
            "Allez, la baleine échouée" to "{session}. 20 minutes. Même ta grand-mère y arrive, et elle a une hanche en titane.",
            "Bouge ton gras" to "{session} t'attend. Ton jean a demandé l'asile politique.",
        )
        Situation.SESSION_DONE -> listOf(
            "Pas mal, la larve" to "Séance faite. T'es toujours une larve, mais une larve qui transpire. Progrès.",
            "Ah quand même" to "Séance faite. Je range les insultes pour ce soir. Demain je les ressors.",
        )
        Situation.ALL_GOOD -> listOf(
            "Bon, ça va" to "Journée nickel. Je cherche une insulte et j'en trouve pas. Ça te fait plaisir, hein ?",
            "Qui t'a piraté ?" to "Tout est noté, tu as bu. C'est louche. Profite, demain je te redescends.",
            "Respect, tête de mule" to "Rien à redire. Ça m'arrache la bouche de le dire, mais bravo.",
        )
    }
}
