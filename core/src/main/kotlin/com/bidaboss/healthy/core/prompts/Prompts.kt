package com.bidaboss.healthy.core.prompts

/**
 * Prompts à copier dans l'IA de son choix (Réglages). La partie « format » vient toujours de
 * docs/format-import.md et des exemples, copiés dans l'app à la construction : le prompt suit donc
 * automatiquement la version du format.
 */
object FormatDoc {

    /** Le texte d'une section « ## … » (titre compris) jusqu'à la section suivante de même niveau. */
    fun section(markdown: String, headingStart: String): String? {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { it.startsWith("## ") && it.removePrefix("## ").startsWith(headingStart) }
        if (start < 0) return null
        val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        return lines.subList(start, end).joinToString("\n").trim()
    }

    /** Règles générales, enveloppe et section du type demandé, puis l'exemple complet. */
    fun forType(markdown: String, typeHeading: String, example: String): String = listOfNotNull(
        section(markdown, "Règles générales"),
        section(markdown, "Enveloppe"),
        section(markdown, typeHeading),
        "Exemple complet et valide :\n```json\n${example.trim()}\n```",
    ).joinToString("\n\n")

    const val MEAL_HEADING = "Type `meal`"
    const val PLAN_HEADING = "Type `workout_plan`"
}

/** Réponses du profil sportif, déjà mises en mots simples pour le prompt. */
data class SportPromptAnswers(
    val firstName: String,
    val sex: String,
    val age: Int,
    val heightCm: Int,
    val weightKg: Int,
    val activity: String,
    val level: String,
    val goal: String,
    val sessionsPerWeek: Int,
    val days: String,
    val durationMin: Int,
    val painZones: String,
    val medicalFollowUp: String,
    val medicalNotes: String,
    val equipment: String,
    val avoid: String,
    /** Maintien réel adopté (phrase toute faite), null si la personne utilise la formule seule. */
    val maintenance: String? = null,
)

object Prompts {

    /** Prompt repas : aucune donnée personnelle. */
    fun meal(formatPart: String): String = """
        |Tu es un nutritionniste. Je vais t'envoyer une ou plusieurs photos d'un même repas. Garde-les toutes en tête et attends que j'écrive « repas terminé » avant de répondre avec le fichier.
        |
        |Quand j'écris « repas terminé » :
        |1. Si tu as un doute sur les matières grasses (huile, beurre), les sauces ou les boissons, pose-moi d'abord une question courte.
        |2. Estime chaque aliment : nom en français, quantité en grammes, calories et macros pour cette quantité (pas pour 100 g), fibres si possible.
        |3. Indique ta confiance (haute, moyenne ou faible) et tes hypothèses (par exemple « 1 c. à soupe d'huile supposée »).
        |4. Mets "source": "estimation_ia" sur chaque aliment.
        |5. Pour tout ce qui se boit (lait, café, thé, jus, soupe, soda), mets "drink": "boisson" et donne toujours "quantity_g" (1 g = 1 ml) : cela compte dans mon eau bue. Pour le vin, la bière ou un alcool, mets "drink": "alcool" (ne compte pas dans l'eau). Rien pour un aliment ordinaire.
        |
        |Réponds alors uniquement avec le fichier JSON, sans texte avant ni après, en respectant exactement le format ci-dessous. Un repas = un seul fichier, même avec plusieurs photos. Génère un nouvel identifiant UUID v4 pour le champ "id" et mets la date et l'heure actuelles dans "created_at" (heure locale sans décalage si tu n'es pas sûr du fuseau).
        |
        |--- FORMAT ATTENDU ---
        |$formatPart
    """.trimMargin()

    /** Prompt programme sport (spec du fil Coach, /mnt/project-files/sport/prompt-programme-sport.md). */
    fun sportPlan(a: SportPromptAnswers, formatPart: String): String = """
        |Tu es un coach sportif diplômé. Crée-moi un programme d'entraînement au poids du corps, à la maison, sur 4 semaines, que j'importerai dans mon application Healthy.
        |
        |Mon profil :
        |- Prénom : ${a.firstName}
        |- Sexe : ${a.sex}, âge : ${a.age} ans, taille : ${a.heightCm} cm, poids : ${a.weightKg} kg
        |- Activité habituelle : ${a.activity}
        |- Niveau sportif : ${a.level}
        |- Objectif principal : ${a.goal}
        |- Séances par semaine : ${a.sessionsPerWeek}, les jours suivants : ${a.days}
        |- Durée habituelle d'une séance : ${a.durationMin} minutes${a.maintenance?.let { "\n|- $it" }.orEmpty()}
        |- Zones sensibles ou douleurs : ${a.painZones}
        |- Suivi par un kiné ou un médecin : ${a.medicalFollowUp}. Consignes reçues : ${a.medicalNotes}
        |- Matériel disponible : ${a.equipment}
        |- Exercices à éviter : ${a.avoid}
        |
        |Règles du programme :
        |1. Chaque séance existe en trois versions complètes de 15, 30 et 45 minutes, chacune avec échauffement, exercices et retour au calme. La version courte garde l'essentiel, elle n'est pas une version longue coupée.
        |2. Varie les séances dans la semaine (haut du corps, bas du corps, cardio doux, gainage) et mets un bloc d'abdominaux dans chaque séance, en commençant par les abdos profonds (transverse : respiration abdominale, dead bug, planche).
        |3. Nomme chaque exercice simplement, en français, avec son nom courant pour que je puisse le chercher en vidéo. Remplis "video_url" avec un lien de recherche YouTube sur ce nom, complet et sans espace : https://www.youtube.com/results?search_query=nom+de+l+exercice (mots séparés par des +). Fais de même pour les variantes avec "easier_video_url" et "harder_video_url". Si tu n'as pas de lien, n'écris pas le champ.
        |4. Pour chaque exercice, donne des consignes pas à pas : position de départ, mouvement, respiration, erreur fréquente à éviter. Ajoute une variante plus facile et une plus difficile.
        |5. Indique séries × répétitions ou une durée, et le temps de repos.
        |6. Exclus tout exercice qui sollicite mes zones sensibles. Si je suis suivi par un kiné ou un médecin, respecte ses consignes et rappelle dans la description du plan que son avis passe avant le programme.
        |7. Adapte l'intensité à mon niveau : progression douce d'une semaine à l'autre, jamais jusqu'à la douleur.
        |8. N'utilise que le matériel que j'ai indiqué.
        |9. Place chaque séance sur un de mes jours avec "day_of_week" (1 = lundi … 7 = dimanche).
        |
        |Réponds uniquement avec le fichier JSON, sans texte avant ni après, en respectant exactement le format ci-dessous. Génère un nouvel identifiant UUID v4 pour le champ "id" et mets la date et l'heure actuelles dans "created_at".
        |
        |--- FORMAT ATTENDU ---
        |$formatPart
    """.trimMargin()

    /** Prompt d'ajustement après 4 semaines : profil, plan actuel, bilan, format. */
    fun adjustment(profileSummary: String, currentPlanJson: String?, reportJson: String, formatPart: String): String = """
        |Tu es mon coach sportif. Voici mon programme actuel et mon bilan des 4 dernières semaines (séances faites, répétitions réalisées, exercices sautés, ressentis, douleurs signalées, activités libres). Ajuste le programme pour les 4 semaines suivantes : augmente la difficulté là où c'était trop facile, allège ou remplace ce qui était trop dur ou douloureux, garde les mêmes règles et le même format. Réponds uniquement avec le nouveau fichier JSON, avec un nouvel identifiant UUID.
        |
        |Mon profil : $profileSummary
        |
        |--- PROGRAMME ACTUEL ---
        |${currentPlanJson ?: "(aucun plan importé)"}
        |
        |--- BILAN ---
        |$reportJson
        |
        |--- FORMAT ATTENDU ---
        |$formatPart
    """.trimMargin()
}
