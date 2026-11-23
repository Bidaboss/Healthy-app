package com.bidaboss.healthy.core.importformat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Représentation brute du JSON. Tout est nullable : les règles « obligatoire »
// sont vérifiées par ImportParser pour donner des messages d'erreur en français.

@Serializable
internal data class MealDto(
    @SerialName("eaten_at") val eatenAt: String? = null,
    @SerialName("meal_type") val mealType: String? = null,
    val title: String? = null,
    val notes: String? = null,
    val items: List<FoodDto>? = null,
)

@Serializable
internal data class FoodDto(
    val name: String? = null,
    @SerialName("quantity_g") val quantityG: Double? = null,
    val portion: String? = null,
    val kcal: Double? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("fiber_g") val fiberG: Double? = null,
    val source: String? = null,
    val confidence: String? = null,
    val assumptions: String? = null,
    @SerialName("off_code") val offCode: String? = null,
    val drink: String? = null,
)

@Serializable
internal data class PlanDto(
    val name: String? = null,
    val description: String? = null,
    val weeks: Int? = null,
    val sessions: List<SessionDto>? = null,
)

@Serializable
internal data class SessionDto(
    val title: String? = null,
    @SerialName("day_of_week") val dayOfWeek: Int? = null,
    val notes: String? = null,
    val variants: List<VariantDto>? = null,
)

@Serializable
internal data class VariantDto(
    @SerialName("duration_min") val durationMin: Int? = null,
    @SerialName("warm_up") val warmUp: String? = null,
    @SerialName("cool_down") val coolDown: String? = null,
    val exercises: List<ExerciseDto>? = null,
)

@Serializable
internal data class ExerciseDto(
    val name: String? = null,
    val sets: Int? = null,
    val reps: Int? = null,
    @SerialName("duration_s") val durationS: Int? = null,
    @SerialName("rest_s") val restS: Int? = null,
    val instructions: String? = null,
    @SerialName("easier_variant") val easierVariant: String? = null,
    @SerialName("harder_variant") val harderVariant: String? = null,
    val notes: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("easier_video_url") val easierVideoUrl: String? = null,
    @SerialName("harder_video_url") val harderVideoUrl: String? = null,
)
