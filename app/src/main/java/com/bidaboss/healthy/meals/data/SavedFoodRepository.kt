package com.bidaboss.healthy.meals.data

import com.bidaboss.healthy.core.importformat.DrinkKind
import com.bidaboss.healthy.core.importformat.FoodSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Un aliment de « Mes aliments » tel que l'interface l'affiche : ses valeurs sont celles de [portionG]. */
data class SavedFood(
    val id: Long,
    val name: String,
    val portionG: Double?,
    val kcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val source: FoodSource,
    val drink: DrinkKind?,
) {
    /** L'aliment prêt à entrer dans un repas, avec sa portion par défaut. */
    fun toFood() = Food(
        name = name,
        quantityG = portionG,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        fiberG = fiberG,
        source = source,
        drink = drink,
    )
}

/** Aliment à créer (id nul) ou à corriger. */
data class SavedFoodDraft(
    val id: Long?,
    val name: String,
    val portionG: Double?,
    val kcal: Double,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val source: FoodSource = FoodSource.MANUAL,
    val drink: DrinkKind? = null,
)

/** Résultat de [SavedFoodRepository.save]. */
sealed interface SaveFoodResult {
    data class Saved(val id: Long) : SaveFoodResult

    /** Un autre aliment porte déjà ce nom : rien n'est enregistré. */
    data object NameTaken : SaveFoodResult
}

/** « Mes aliments » : la liste des favoris (marque-page sur un aliment, ou création à la main). Aucun réseau. */
class SavedFoodRepository(
    private val dao: SavedFoodDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun observeAll(): Flow<List<SavedFood>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    /** Les noms réduits des aliments enregistrés : sert à allumer le marque-page des aliments d'un repas. */
    fun observeKeys(): Flow<Set<String>> = dao.observeAll().map { rows -> rows.mapTo(HashSet()) { it.nameKey } }

    suspend fun get(id: Long): SavedFood? = dao.get(id)?.toModel()

    suspend fun save(draft: SavedFoodDraft): SaveFoodResult {
        val key = FoodHistory.normalize(draft.name)
        val existing = draft.id?.let { dao.get(it) }
        if (existing == null) {
            val id = dao.insert(draft.toEntity(key, createdAt = now()))
            return if (id == -1L) SaveFoodResult.NameTaken else SaveFoodResult.Saved(id)
        }
        val clash = dao.getByKey(key)
        if (clash != null && clash.id != existing.id) return SaveFoodResult.NameTaken
        dao.update(draft.toEntity(key, createdAt = existing.createdAtEpochMs).copy(id = existing.id))
        return SaveFoodResult.Saved(existing.id)
    }

    /**
     * Marque-page d'un aliment d'un repas : l'ajoute à « Mes aliments » avec ses valeurs actuelles (sa quantité
     * devient la portion par défaut), ou l'en retire s'il y est déjà. Rend vrai s'il y est maintenant.
     */
    suspend fun toggle(food: Food): Boolean {
        val key = FoodHistory.normalize(food.name)
        if (key.isEmpty()) return false
        if (dao.getByKey(key) != null) {
            dao.deleteByKey(key)
            return false
        }
        dao.insert(
            SavedFoodDraft(
                id = null,
                name = food.name.trim(),
                portionG = food.quantityG,
                kcal = food.kcal,
                proteinG = food.proteinG,
                carbsG = food.carbsG,
                fatG = food.fatG,
                fiberG = food.fiberG,
                source = food.source,
                drink = food.drink,
            ).toEntity(key, createdAt = now()),
        )
        return true
    }

    suspend fun delete(id: Long) = dao.delete(id)

    private fun SavedFoodDraft.toEntity(key: String, createdAt: Long) = SavedFoodEntity(
        name = name.trim(),
        nameKey = key,
        defaultPortionG = portionG,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        fiberG = fiberG,
        source = source.code,
        drink = drink?.code,
        createdAtEpochMs = createdAt,
    )

    private fun SavedFoodEntity.toModel() = SavedFood(
        id = id,
        name = name,
        portionG = defaultPortionG,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        fiberG = fiberG,
        source = FoodSource.fromCode(source) ?: FoodSource.MANUAL,
        drink = drink?.let(DrinkKind::fromCode),
    )
}
