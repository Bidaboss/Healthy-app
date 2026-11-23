package com.bidaboss.healthy.meals.data

import com.bidaboss.healthy.core.openfoodfacts.Barcodes
import com.bidaboss.healthy.core.openfoodfacts.OffProduct
import com.bidaboss.healthy.core.openfoodfacts.OffProductDraft
import com.bidaboss.healthy.core.openfoodfacts.ProductLookup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Un produit retenu : ses valeurs, et s'il vient d'Open Food Facts ou de l'étiquette saisie. */
data class ScannedProduct(val product: OffProduct, val fromOpenFoodFacts: Boolean)

/** Résultat de « Mettre à jour depuis Open Food Facts ». */
sealed interface RefreshResult {
    data object Updated : RefreshResult

    /** Open Food Facts n'a pas de valeurs complètes pour ce code : le produit reste tel quel. */
    data object Unchanged : RefreshResult

    data class Failed(val reason: FoodSearchFailure) : RefreshResult
}

/** Ce que donne le scan d'un code-barres. */
sealed interface ScanResult {
    /** Produit prêt à ajouter. [remembered] : il était déjà sur le téléphone (« Déjà scanné »). */
    data class Known(val product: ScannedProduct, val remembered: Boolean) : ScanResult

    /** Open Food Facts ne connaît pas ce code, ou sans calories : à saisir depuis l'étiquette. */
    data class Unknown(val code: String, val draft: OffProductDraft?) : ScanResult

    /** Open Food Facts n'a pas pu répondre (pas de connexion, panne). */
    data class Failed(val code: String, val reason: FoodSearchFailure) : ScanResult

    /** Le code lu n'est pas un code-barres valable. */
    data object InvalidCode : ScanResult
}

/**
 * Produits scannés : on regarde d'abord le téléphone (aucun réseau), puis Open Food Facts avec le seul code.
 * Tout produit trouvé ou saisi est retenu pour la prochaine fois, même sans internet.
 */
class ScannedProductRepository(
    private val dao: ScannedProductDao,
    private val remote: FoodSearchRepository,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun scan(rawCode: String): ScanResult {
        val code = Barcodes.normalize(rawCode) ?: return ScanResult.InvalidCode
        dao.get(code)?.let { saved ->
            dao.upsert(saved.copy(lastUsedEpochMs = now()))
            return ScanResult.Known(saved.toModel(), remembered = true)
        }
        return when (val fetched = remote.lookupProduct(code)) {
            is ProductFetch.Failure -> ScanResult.Failed(code, fetched.reason)
            is ProductFetch.Success -> when (val lookup = fetched.lookup) {
                is ProductLookup.Found -> {
                    val product = ScannedProduct(lookup.product, fromOpenFoodFacts = true)
                    dao.upsert(product.toEntity(now()))
                    ScanResult.Known(product, remembered = false)
                }
                is ProductLookup.Incomplete -> ScanResult.Unknown(code, lookup.draft)
                ProductLookup.NotFound -> ScanResult.Unknown(code, null)
            }
        }
    }

    /** Tous les produits retenus, les plus récemment utilisés d'abord. */
    fun observeAll(): Flow<List<ScannedProduct>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    /** Correction à la main d'un produit retenu : il garde son origine et sa date d'usage. Les repas passés ne changent pas. */
    suspend fun update(product: OffProduct) {
        val saved = dao.get(product.code) ?: return
        dao.upsert(ScannedProduct(product, saved.fromOpenFoodFacts).toEntity(saved.lastUsedEpochMs))
    }

    suspend fun delete(code: String) = dao.delete(code)

    /** « Mettre à jour depuis Open Food Facts » : à la demande seulement, jamais automatique. */
    suspend fun refresh(code: String): RefreshResult {
        val saved = dao.get(code) ?: return RefreshResult.Unchanged
        return when (val fetched = remote.lookupProduct(code)) {
            is ProductFetch.Failure -> RefreshResult.Failed(fetched.reason)
            is ProductFetch.Success -> when (val lookup = fetched.lookup) {
                is ProductLookup.Found -> {
                    dao.upsert(ScannedProduct(lookup.product, fromOpenFoodFacts = true).toEntity(saved.lastUsedEpochMs))
                    RefreshResult.Updated
                }
                // Open Food Facts n'a plus (ou pas) de valeurs complètes : on garde celles du téléphone.
                else -> RefreshResult.Unchanged
            }
        }
    }

    /** Produit saisi depuis l'étiquette : retenu sous son code. Rien n'est envoyé à Open Food Facts. */
    suspend fun saveFromLabel(code: String, product: OffProduct): ScannedProduct {
        val saved = ScannedProduct(product.copy(code = code), fromOpenFoodFacts = false)
        dao.upsert(saved.toEntity(now()))
        return saved
    }
}

private fun ScannedProductEntity.toModel() = ScannedProduct(
    OffProduct(code, name, brand, kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g, fiberPer100g, servingG),
    fromOpenFoodFacts,
)

private fun ScannedProduct.toEntity(nowMs: Long) = ScannedProductEntity(
    code = product.code,
    name = product.name,
    brand = product.brand,
    kcalPer100g = product.kcalPer100g,
    proteinPer100g = product.proteinPer100g,
    carbsPer100g = product.carbsPer100g,
    fatPer100g = product.fatPer100g,
    fiberPer100g = product.fiberPer100g,
    servingG = product.servingG,
    fromOpenFoodFacts = fromOpenFoodFacts,
    lastUsedEpochMs = nowMs,
)
