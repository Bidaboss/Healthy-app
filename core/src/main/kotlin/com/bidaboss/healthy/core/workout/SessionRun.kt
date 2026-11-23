package com.bidaboss.healthy.core.workout

/** Un exercice tel qu'il est prévu dans la version de séance choisie. */
data class PlannedExercise(
    val name: String,
    val sets: Int,
    val reps: Int? = null,
    val durationS: Int? = null,
    val restS: Int? = null,
    val instructions: String? = null,
    val easierVariant: String? = null,
    val harderVariant: String? = null,
    val notes: String? = null,
    /** Liens https vers une vidéo de démonstration de l'exercice et de ses variantes. */
    val videoUrl: String? = null,
    val easierVideoUrl: String? = null,
    val harderVideoUrl: String? = null,
) {
    /** Valeur prévue pour une série : répétitions, ou secondes pour un exercice chronométré. */
    val plannedValue: Int get() = reps ?: durationS ?: 0
    val isTimed: Boolean get() = reps == null && durationS != null
}

/** Ce qui a été réellement fait pour un exercice. */
data class ExerciseResult(
    /** Une valeur par série terminée (répétitions ou secondes). */
    val doneValues: List<Int> = emptyList(),
    val skipped: Boolean = false,
)

sealed interface RunStep {
    data class WarmUp(val text: String) : RunStep

    /** Série [setIndex] (à partir de 0) de l'exercice [exerciseIndex]. */
    data class Work(val exerciseIndex: Int, val setIndex: Int) : RunStep

    /** Repos avant la série suivante. */
    data class Rest(val seconds: Int, val next: Work) : RunStep

    data class CoolDown(val text: String) : RunStep

    /** Tous les exercices sont passés : ressenti et douleur éventuelle. */
    data object Feedback : RunStep
}

data class SessionRunState(
    val step: RunStep,
    val results: List<ExerciseResult>,
)

/**
 * Déroulé d'une séance guidée : échauffement, séries avec repos, retour au calme, ressenti.
 * Logique pure (aucun chrono ici) : l'écran appelle une transition à chaque appui ou fin de compte à rebours.
 */
class SessionRun(
    val exercises: List<PlannedExercise>,
    private val warmUp: String? = null,
    private val coolDown: String? = null,
) {
    init {
        require(exercises.isNotEmpty()) { "Une séance contient au moins un exercice" }
        require(exercises.all { it.sets >= 1 }) { "Chaque exercice a au moins une série" }
    }

    fun start(): SessionRunState = SessionRunState(
        step = warmUp?.let { RunStep.WarmUp(it) } ?: RunStep.Work(0, 0),
        results = exercises.map { ExerciseResult() },
    )

    /** Passe l'échauffement, le repos ou le retour au calme. Sans effet pendant une série. */
    fun next(state: SessionRunState): SessionRunState = when (val step = state.step) {
        is RunStep.WarmUp -> state.copy(step = RunStep.Work(0, 0))
        is RunStep.Rest -> state.copy(step = step.next)
        is RunStep.CoolDown -> state.copy(step = RunStep.Feedback)
        is RunStep.Work, RunStep.Feedback -> state
    }

    /** Enregistre la série en cours avec ce qui a été fait, puis passe au repos ou à la suite. */
    fun completeSet(state: SessionRunState, doneValue: Int): SessionRunState {
        val step = state.step as? RunStep.Work ?: return state
        val exercise = exercises[step.exerciseIndex]
        val results = state.results.toMutableList()
        val current = results[step.exerciseIndex]
        results[step.exerciseIndex] = current.copy(doneValues = current.doneValues + doneValue.coerceAtLeast(0))

        val following = if (step.setIndex + 1 < exercise.sets) {
            RunStep.Work(step.exerciseIndex, step.setIndex + 1)
        } else {
            nextExercise(step.exerciseIndex)
        }
        val rest = exercise.restS ?: 0
        val nextStep = if (following is RunStep.Work && rest > 0) RunStep.Rest(rest, following) else following
        return SessionRunState(nextStep, results)
    }

    /** Abandonne l'exercice en cours (les séries déjà faites sont gardées) et passe au suivant, sans repos. */
    fun skipExercise(state: SessionRunState): SessionRunState {
        val index = when (val step = state.step) {
            is RunStep.Work -> step.exerciseIndex
            is RunStep.Rest -> step.next.exerciseIndex
            else -> return state
        }
        val results = state.results.toMutableList()
        results[index] = results[index].copy(skipped = true)
        return SessionRunState(nextExercise(index), results)
    }

    /**
     * Termine la séance tout de suite : on va au ressenti en gardant ce qui a déjà été fait,
     * pour qu'une séance écourtée puisse quand même être enregistrée.
     */
    fun finishNow(state: SessionRunState): SessionRunState = state.copy(step = RunStep.Feedback)

    /**
     * Séance en cours sous forme de texte court, pour la retrouver si Android ferme l'app
     * (appel téléphonique, mémoire). Les textes d'échauffement et de retour au calme ne sont pas
     * copiés : ils viennent du plan.
     */
    fun encode(state: SessionRunState): String {
        val step = when (val s = state.step) {
            is RunStep.WarmUp -> "W"
            is RunStep.Work -> "X,${s.exerciseIndex},${s.setIndex}"
            is RunStep.Rest -> "R,${s.seconds},${s.next.exerciseIndex},${s.next.setIndex}"
            is RunStep.CoolDown -> "C"
            RunStep.Feedback -> "F"
        }
        val results = state.results.joinToString(";") { result ->
            (if (result.skipped) "s" else "d") + result.doneValues.joinToString("") { ",$it" }
        }
        return "$step|$results"
    }

    /** Inverse de [encode]. Rend null si le texte ne correspond pas (ou plus) à cette séance. */
    fun decode(text: String): SessionRunState? {
        val parts = text.split('|')
        if (parts.size != 2) return null
        val results = parts[1].split(';').map { raw ->
            val fields = raw.split(',')
            val skipped = when (fields[0]) {
                "s" -> true
                "d" -> false
                else -> return null
            }
            val values = fields.drop(1).map { it.toIntOrNull()?.takeIf { v -> v >= 0 } ?: return null }
            ExerciseResult(values, skipped)
        }
        if (results.size != exercises.size) return null
        if (results.indices.any { results[it].doneValues.size > exercises[it].sets }) return null

        val fields = parts[0].split(',')
        val numbers = fields.drop(1).map { it.toIntOrNull() ?: return null }
        fun work(exerciseIndex: Int, setIndex: Int): RunStep.Work? =
            RunStep.Work(exerciseIndex, setIndex).takeIf {
                exerciseIndex in exercises.indices && setIndex in 0 until exercises[exerciseIndex].sets
            }
        val step: RunStep = when {
            fields[0] == "W" && numbers.isEmpty() -> RunStep.WarmUp(warmUp ?: return null)
            fields[0] == "C" && numbers.isEmpty() -> RunStep.CoolDown(coolDown ?: return null)
            fields[0] == "F" && numbers.isEmpty() -> RunStep.Feedback
            fields[0] == "X" && numbers.size == 2 -> work(numbers[0], numbers[1]) ?: return null
            fields[0] == "R" && numbers.size == 3 && numbers[0] > 0 ->
                RunStep.Rest(numbers[0], work(numbers[1], numbers[2]) ?: return null)
            else -> return null
        }
        return SessionRunState(step, results)
    }

    private fun nextExercise(index: Int): RunStep = when {
        index + 1 < exercises.size -> RunStep.Work(index + 1, 0)
        coolDown != null -> RunStep.CoolDown(coolDown)
        else -> RunStep.Feedback
    }
}
