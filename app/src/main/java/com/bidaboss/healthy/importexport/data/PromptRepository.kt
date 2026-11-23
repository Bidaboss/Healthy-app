package com.bidaboss.healthy.importexport.data

import android.content.Context
import com.bidaboss.healthy.body.data.BodyRepository
import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.BodyProfile
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.body.Sex
import com.bidaboss.healthy.core.importformat.ImportedExercise
import com.bidaboss.healthy.core.importformat.ImportedPlan
import com.bidaboss.healthy.core.importformat.ImportedSession
import com.bidaboss.healthy.core.importformat.ImportedSessionVariant
import com.bidaboss.healthy.core.importformat.PlanWriter
import com.bidaboss.healthy.core.prompts.FormatDoc
import com.bidaboss.healthy.core.prompts.Prompts
import com.bidaboss.healthy.core.prompts.SportPromptAnswers
import com.bidaboss.healthy.workout.data.Equipment
import com.bidaboss.healthy.workout.data.PainZone
import com.bidaboss.healthy.workout.data.SportGoal
import com.bidaboss.healthy.workout.data.SportLevel
import com.bidaboss.healthy.workout.data.SportProfile
import com.bidaboss.healthy.workout.data.SportProfileRepository
import com.bidaboss.healthy.workout.data.WorkoutLogRepository
import com.bidaboss.healthy.workout.data.WorkoutPlan
import com.bidaboss.healthy.workout.data.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

/** Le profil n'est pas rempli (accueil quitté pour restaurer une sauvegarde, puis import abandonné). */
class MissingProfileException : IllegalStateException("Profil manquant")

/**
 * Prépare les prompts à copier (Réglages). Le format vient des assets `format/`, copiés depuis
 * docs/ à la construction de l'app (tâche copyFormatDocs).
 */
class PromptRepository(
    context: Context,
    private val bodyRepository: BodyRepository,
    private val sportProfileRepository: SportProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val workoutLogRepository: WorkoutLogRepository,
) {
    private val assets = context.applicationContext.assets

    private suspend fun asset(name: String): String = withContext(Dispatchers.IO) {
        assets.open("format/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private suspend fun planFormat() =
        FormatDoc.forType(asset("format-import.md"), FormatDoc.PLAN_HEADING, asset("plan-sport-maison.json"))

    /** Prompt repas : ne contient aucune donnée personnelle. */
    suspend fun mealPrompt(): String =
        Prompts.meal(FormatDoc.forType(asset("format-import.md"), FormatDoc.MEAL_HEADING, asset("repas-dejeuner.json")))

    /** Prompt programme sport : contient le profil (l'écran prévient avant de copier). */
    suspend fun sportPrompt(today: LocalDate = LocalDate.now()): String {
        val answers = answers(today) ?: throw MissingProfileException()
        return Prompts.sportPlan(answers, planFormat())
    }

    /** Prompt d'ajustement : profil, plan actuel et bilan des 28 derniers jours. */
    suspend fun adjustmentPrompt(today: LocalDate = LocalDate.now()): String {
        val answers = answers(today) ?: throw MissingProfileException()
        val summary = with(answers) {
            "$firstName, $sex, $age ans, $heightCm cm, $weightKg kg, niveau $level, objectif $goal, " +
                "$sessionsPerWeek séances par semaine ($days), douleurs : $painZones, matériel : $equipment" +
                maintenance?.let { ". $it" }.orEmpty()
        }
        val plan = workoutRepository.getActivePlan()?.let { PlanWriter.write(it.toImported()) }
        val report = workoutLogRepository.exportTrainingReport(today, UUID.randomUUID().toString(), System.currentTimeMillis())
        return Prompts.adjustment(summary, plan, report, planFormat())
    }

    private suspend fun answers(today: LocalDate): SportPromptAnswers? {
        val body = bodyRepository.observeProfile().first() ?: return null
        val sport = sportProfileRepository.observe().first() ?: SportProfile()
        val weighIns = bodyRepository.observeWeighIns().first()
        val weight = Maintenance.referenceWeightKg(body.weightKg, weighIns, today)
        return sportAnswers(body, sport, weight, today, Maintenance.maintenanceKcal(body, weighIns, today))
    }

    private fun WorkoutPlan.toImported() = ImportedPlan(
        name = name,
        description = description,
        weeks = weeks,
        sessions = sessions.map { s ->
            ImportedSession(
                title = s.title,
                dayOfWeek = s.dayOfWeek,
                notes = null,
                variants = s.variants.map { v ->
                    ImportedSessionVariant(
                        durationMin = v.durationMin,
                        warmUp = v.warmUp,
                        coolDown = v.coolDown,
                        exercises = v.exercises.map { e ->
                            ImportedExercise(
                                e.name, e.sets, e.reps, e.durationS, e.restS, e.instructions, e.easierVariant,
                                e.harderVariant, e.notes, e.videoUrl, e.easierVideoUrl, e.harderVideoUrl,
                            )
                        },
                    )
                },
            )
        },
    )
}

private val dayNames = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")

/** Met les réponses en mots simples pour le prompt (fonction pure, testée). */
fun sportAnswers(
    body: BodyProfile,
    sport: SportProfile,
    referenceWeightKg: Double,
    today: LocalDate,
    maintenanceKcal: Int? = null,
) = SportPromptAnswers(
    maintenance = maintenanceKcal?.takeIf { body.maintenanceAdjustmentKcal != 0 }?.let {
        "Maintien réel estimé : environ $it kcal par jour (calculé à partir de mes repas et de mon poids)"
    },
    firstName = body.firstName.orEmpty().ifBlank { "(non indiqué)" },
    sex = if (body.sex == Sex.MALE) "homme" else "femme",
    age = Maintenance.ageYears(body.birthDate, today),
    heightCm = body.heightCm.roundToInt(),
    weightKg = referenceWeightKg.roundToInt(),
    activity = when (body.activity) {
        ActivityLevel.SEDENTARY -> "sédentaire"
        ActivityLevel.LIGHT -> "un peu actif"
        ActivityLevel.ACTIVE -> "actif"
        ActivityLevel.VERY_ACTIVE -> "très actif"
    },
    level = when (sport.level) {
        SportLevel.BEGINNER -> "débutant (pas de sport depuis longtemps)"
        SportLevel.LIGHT -> "un peu actif (marche, sport occasionnel)"
        SportLevel.REGULAR -> "régulier (1 à 2 séances par semaine)"
        SportLevel.SPORTY -> "sportif (3 séances ou plus par semaine)"
    },
    goal = when (sport.goal) {
        SportGoal.GENERAL -> "forme générale"
        SportGoal.STRENGTH -> "renforcement musculaire"
        SportGoal.ENDURANCE -> "endurance (cardio)"
        SportGoal.MOBILITY -> "mobilité et souplesse"
    },
    sessionsPerWeek = sport.sessionsPerWeek,
    days = sport.days.joinToString(", ") { dayNames[it - 1] }.ifEmpty { "au choix" },
    durationMin = sport.durationMin,
    painZones = (
        sport.painZones.filter { it != PainZone.OTHER }.map {
            when (it) {
                PainZone.LOWER_BACK -> "dos (lombaires)"
                PainZone.KNEES -> "genoux"
                PainZone.SHOULDERS -> "épaules"
                PainZone.WRISTS -> "poignets"
                PainZone.ANKLES -> "chevilles"
                PainZone.HIPS -> "hanches"
                PainZone.GROIN -> "aine (pubalgie)"
                PainZone.NECK -> "nuque"
                PainZone.OTHER -> ""
            }
        } + listOfNotNull(sport.painOther.takeIf { PainZone.OTHER in sport.painZones && it.isNotBlank() })
        ).joinToString(", ").ifEmpty { "aucune" },
    medicalFollowUp = if (sport.medicalFollowUp) "oui" else "non",
    medicalNotes = sport.medicalNotes.ifBlank { "aucune" },
    equipment = sport.equipment.joinToString(", ") {
        when (it) {
            Equipment.CHAIR -> "chaise solide"
            Equipment.WALL -> "mur"
            Equipment.MAT -> "tapis"
            Equipment.BAND -> "élastique"
            Equipment.PULL_UP_BAR -> "barre de traction"
            Equipment.DUMBBELLS -> "haltères ou bouteilles d'eau"
            Equipment.STEP -> "escalier ou marche"
        }
    }.ifEmpty { "aucun" },
    avoid = sport.avoid.ifBlank { "aucun" },
)
