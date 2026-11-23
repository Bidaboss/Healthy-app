package com.bidaboss.healthy.health.data

import androidx.health.connect.client.records.ExerciseSessionRecord

/**
 * Nom français d'une séance de la montre, choisi parmi les types du journal d'activités pour que
 * l'estimation des calories (`ActivityCalories`) les reconnaisse. Un type inconnu garde le titre
 * donné par la montre, sinon « Sport ».
 */
internal fun watchActivityName(exerciseType: Int, title: String?): String = when (exerciseType) {
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Marche"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Randonnée"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    -> "Course"

    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
    -> "Vélo"

    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    -> "Natation"

    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
    -> "Renforcement"

    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Musculation"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Étirements"
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptique"
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
    -> "Rameur"

    ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Danse"
    ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "Escalade"
    ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Football"
    ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Tennis"
    ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "Badminton"
    else -> title?.trim()?.takeIf { it.isNotEmpty() } ?: "Sport"
}
