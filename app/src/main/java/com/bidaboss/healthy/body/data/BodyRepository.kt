package com.bidaboss.healthy.body.data

import com.bidaboss.healthy.core.body.ActivityLevel
import com.bidaboss.healthy.core.body.BodyProfile
import com.bidaboss.healthy.core.body.Goal
import com.bidaboss.healthy.core.body.Maintenance
import com.bidaboss.healthy.core.body.MaintenanceEstimator
import com.bidaboss.healthy.core.body.ProposalState
import com.bidaboss.healthy.core.body.Sex
import com.bidaboss.healthy.core.body.WeighIn
import com.bidaboss.healthy.core.importformat.MealType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Profil et pesées. Rien de Room ne sort d'ici : l'interface reçoit les modèles de `core.body`. */
class BodyRepository(private val dao: BodyDao) {

    fun observeProfile(): Flow<BodyProfile?> = dao.observeProfile().map { entity ->
        entity?.let {
            val sex = Sex.fromCode(it.sex) ?: return@let null
            val activity = ActivityLevel.fromCode(it.activity) ?: return@let null
            BodyProfile(
                sex = sex,
                birthDate = LocalDate.parse(it.birthDate),
                heightCm = it.heightCm,
                weightKg = it.weightKg,
                activity = activity,
                firstName = it.firstName,
                goal = Goal.fromCode(it.goal) ?: Goal.MAINTENANCE,
                maintenanceAdjustmentKcal = it.maintenanceAdjustmentKcal,
                adjustmentActivity = ActivityLevel.fromCode(it.adjustmentActivity),
            )
        }
    }

    /** Décisions déjà prises sur les propositions de maintien. */
    fun observeProposalState(): Flow<ProposalState> = dao.observeProfile().map { entity ->
        ProposalState(
            decidedOn = entity?.proposalDecidedOn?.let(LocalDate::parse),
            snoozedUntil = entity?.proposalSnoozedUntil?.let(LocalDate::parse),
        )
    }

    /** Adopte un ajustement (écart en kcal avec la formule) ; le niveau d'activité du moment est gardé pour le rappel. */
    suspend fun adoptAdjustment(kcal: Int, today: LocalDate) {
        val activity = dao.getProfile()?.activity ?: return
        dao.setAdjustment(kcal, activity, today.toString())
    }

    /** « Revenir à la formule » : retire l'ajustement. */
    suspend fun clearAdjustment(today: LocalDate) = dao.setAdjustment(0, null, today.toString())

    /** « Garder la formule » : ferme la proposition, la suivante viendra après 21 nouveaux jours. */
    suspend fun declineProposal(today: LocalDate) = dao.setDecision(today.toString())

    /** « Pas maintenant » : masque la carte d'Aujourd'hui 14 jours. */
    suspend fun snoozeProposal(today: LocalDate) =
        dao.setSnooze(today.plusDays(MaintenanceEstimator.SNOOZE_DAYS).toString())

    /** Enregistre le profil ; l'ajustement du maintien et les décisions déjà prises sont conservés. */
    suspend fun saveProfile(profile: BodyProfile, nowEpochMs: Long = System.currentTimeMillis()) {
        val old = dao.getProfile()
        dao.saveProfile(
            ProfileEntity(
                sex = profile.sex.code,
                firstName = profile.firstName,
                birthDate = profile.birthDate.toString(),
                heightCm = profile.heightCm,
                weightKg = profile.weightKg,
                activity = profile.activity.code,
                goal = profile.goal.code,
                updatedAtEpochMs = nowEpochMs,
                maintenanceAdjustmentKcal = old?.maintenanceAdjustmentKcal ?: 0,
                adjustmentActivity = old?.adjustmentActivity,
                proposalDecidedOn = old?.proposalDecidedOn,
                proposalSnoozedUntil = old?.proposalSnoozedUntil,
            ),
        )
    }

    /**
     * Premier lancement : enregistre le profil et, s'il n'y a pas encore de pesée ce jour-là,
     * le poids saisi comme première pesée datée.
     */
    suspend fun saveProfileWithFirstWeighIn(profile: BodyProfile, today: LocalDate, nowEpochMs: Long = System.currentTimeMillis()) {
        saveProfile(profile, nowEpochMs)
        if (dao.observeWeighIns().first().none { it.localDate == today.toString() }) {
            saveWeighIn(WeighIn(today, profile.weightKg), nowEpochMs)
        }
    }

    fun observeWeighIns(): Flow<List<WeighIn>> = dao.observeWeighIns().map { rows ->
        rows.map { WeighIn(LocalDate.parse(it.localDate), it.weightKg, it.waistCm) }
    }

    /** Maintien du jour (kcal), null sans profil. */
    fun observeMaintenanceKcal(date: LocalDate): Flow<Int?> =
        combine(observeProfile(), observeWeighIns()) { profile, weighIns ->
            profile?.let { Maintenance.maintenanceKcal(it, weighIns, date) }
        }

    /** Poids de référence du jour (kg), null sans profil. */
    fun observeReferenceWeightKg(date: LocalDate): Flow<Double?> =
        combine(observeProfile(), observeWeighIns()) { profile, weighIns ->
            profile?.let { Maintenance.referenceWeightKg(it.weightKg, weighIns, date) }
        }

    /** Enregistre la pesée du jour (remplace celle déjà saisie ce jour-là). */
    suspend fun saveWeighIn(weighIn: WeighIn, nowEpochMs: Long = System.currentTimeMillis()) {
        dao.saveWeighIn(
            WeighInEntity(
                localDate = weighIn.date.toString(),
                weightKg = weighIn.weightKg,
                waistCm = weighIn.waistCm,
                createdAtEpochMs = nowEpochMs,
            ),
        )
    }

    suspend fun deleteWeighIn(date: LocalDate) = dao.deleteWeighIn(date.toString())

    /** Eau bue par jour (mL) entre deux dates incluses, ajouts + boissons des repas ; un jour sans eau est absent. */
    fun observeDailyWater(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        dao.observeDailyWater(from.toString(), to.toString()).map { rows ->
            rows.associate { LocalDate.parse(it.localDate) to it.ml }
        }

    /** Ajouts d'eau d'un jour, du plus récent au plus ancien (pour savoir si un verre est déjà noté). */
    fun observeWaterIntakes(date: LocalDate): Flow<List<WaterIntake>> =
        dao.observeWaterIntakes(date.toString()).map { rows -> rows.map { WaterIntake(it.id, it.ml, it.createdAtEpochMs) } }

    /** Boissons des repas d'un jour : comptées dans l'eau, modifiables seulement depuis le repas. */
    fun observeMealDrinks(date: LocalDate): Flow<List<MealDrink>> =
        dao.observeMealDrinks(date.toString()).map { rows ->
            rows.map { MealDrink(it.name, it.ml, MealType.fromCode(it.mealType) ?: MealType.SNACK, it.eatenAtEpochMs) }
        }

    /** Supprime un ajout d'eau précis (ajouté par erreur ou en double). */
    suspend fun deleteWater(id: Long): Boolean = dao.deleteWater(id) > 0

    /** Remet un ajout d'eau supprimé par erreur, avec la même heure, dans la journée [date]. */
    suspend fun restoreWater(date: LocalDate, intake: WaterIntake): Boolean = addWater(date, intake.ml, intake.addedAtEpochMs)

    /** Ajoute de l'eau ; rend false si la quantité est hors limites (1 mL à 3 L d'un coup). */
    suspend fun addWater(date: LocalDate, ml: Int, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        if (ml !in 1..MAX_WATER_ADD_ML) return false
        dao.insertWater(WaterIntakeEntity(localDate = date.toString(), ml = ml, createdAtEpochMs = nowEpochMs))
        return true
    }

    /** Annule le dernier ajout d'eau du jour. Rend false s'il n'y avait rien à annuler. */
    suspend fun undoLastWater(date: LocalDate): Boolean = dao.deleteLastWater(date.toString()) > 0

    companion object {
        const val MAX_WATER_ADD_ML = 3000
    }
}

/** Une boisson notée dans un repas, comptée dans l'eau du jour. */
data class MealDrink(val name: String, val ml: Int, val mealType: MealType, val eatenAtEpochMs: Long)

/** Un ajout d'eau : quantité et heure de l'ajout. */
data class WaterIntake(val id: Long, val ml: Int, val addedAtEpochMs: Long)
