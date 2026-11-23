package com.bidaboss.healthy.core.openfoodfacts

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.net.URLEncoder

/** Un produit Open Food Facts, avec ses valeurs pour 100 g. */
data class OffProduct(
    val code: String,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?,
    val fiberPer100g: Double?,
    /** Taille d'une portion en grammes, si le fabricant l'indique. */
    val servingG: Double?,
) {
    /** Valeurs pour une quantité donnée, arrondies à 0,1. */
    fun forQuantity(quantityG: Double): OffNutrients {
        fun scale(per100g: Double?): Double? = per100g?.let { Math.round(it * quantityG / 100.0 * 10) / 10.0 }
        return OffNutrients(
            kcal = scale(kcalPer100g) ?: 0.0,
            proteinG = scale(proteinPer100g),
            carbsG = scale(carbsPer100g),
            fatG = scale(fatPer100g),
            fiberG = scale(fiberPer100g),
        )
    }
}

data class OffNutrients(
    val kcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
)

/**
 * Un produit lu dans la fiche Open Food Facts, éventuellement incomplet : sert à préremplir la saisie de
 * l'étiquette quand il manque les calories.
 */
data class OffProductDraft(
    val code: String,
    val name: String?,
    val brand: String?,
    val kcalPer100g: Double?,
    val proteinPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?,
    val fiberPer100g: Double?,
    val servingG: Double?,
)

/** Résultat de la recherche d'un produit par son code-barres. */
sealed interface ProductLookup {
    /** Produit connu, avec nom et calories. */
    data class Found(val product: OffProduct) : ProductLookup

    /** Produit connu d'Open Food Facts mais sans nom ou sans calories : à compléter à la main. */
    data class Incomplete(val draft: OffProductDraft) : ProductLookup

    /** Code inconnu d'Open Food Facts. */
    data object NotFound : ProductLookup
}

/** Codes-barres : seuls les chiffres comptent, et la clé est la même quel que soit le format lu. */
object Barcodes {
    /**
     * Code normalisé (chiffres seulement) ou null s'il n'est pas valable : 8 chiffres (EAN-8) ou 12/13 (UPC-A et
     * EAN-13, tous deux écrits sur 13 chiffres comme le fait Open Food Facts), avec la clé de contrôle exacte.
     */
    fun normalize(raw: String): String? {
        val digits = raw.trim()
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        val code = when (digits.length) {
            8, 13 -> digits
            12 -> "0$digits"
            else -> return null
        }
        return code.takeIf { checkDigitIsValid(it) }
    }

    /** Clé de contrôle EAN/UPC : le dernier chiffre complète la somme pondérée (3, 1, 3, 1… depuis la droite). */
    private fun checkDigitIsValid(code: String): Boolean {
        val digits = code.map { it - '0' }
        val sum = digits.dropLast(1).reversed().withIndex().sumOf { (i, d) -> if (i % 2 == 0) d * 3 else d }
        return (10 - sum % 10) % 10 == digits.last()
    }
}

/** Construction de l'adresse de recherche et lecture de la réponse d'Open Food Facts. */
object OpenFoodFacts {
    const val HOST = "world.openfoodfacts.org"

    /** Nouveau moteur de recherche d'Open Food Facts : rapide, et disponible quand [HOST] est saturé. */
    const val SEARCH_HOST = "search.openfoodfacts.org"
    private const val FIELDS = "code,product_name,product_name_fr,brands,nutriments,serving_quantity"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Adresses à essayer dans l'ordre : le nouveau moteur d'abord, l'ancienne recherche en secours.
     * Mesuré le 11/11/2026 : l'ancienne répondait « 503 indisponible » 4 fois sur 5, le nouveau moteur
     * répondait en moins d'une seconde.
     */
    fun searchUrls(query: String, pageSize: Int = 20): List<String> =
        listOf(fastSearchUrl(query, pageSize), searchUrl(query, pageSize))

    fun fastSearchUrl(query: String, pageSize: Int = 20): String {
        val terms = URLEncoder.encode(query.trim(), "UTF-8")
        return "https://$SEARCH_HOST/search?q=$terms&langs=fr&page_size=$pageSize&fields=$FIELDS"
    }

    fun searchUrl(query: String, pageSize: Int = 20): String {
        val terms = URLEncoder.encode(query.trim(), "UTF-8")
        return "https://$HOST/cgi/search.pl?search_terms=$terms&search_simple=1&action=process&json=1" +
            "&page_size=$pageSize&lc=fr&cc=fr&fields=$FIELDS"
    }

    /**
     * Lit la réponse de recherche. Les produits sans nom ou sans calories sont ignorés
     * (ils ne servent à rien pour le journal). Renvoie null si la réponse est illisible.
     * Les deux moteurs sont acceptés : l'ancien range les produits dans « products », le nouveau dans « hits ».
     */
    fun parseSearchResponse(body: String): List<OffProduct>? {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return null
        val products = (root["products"] ?: root["hits"]) as? JsonArray ?: return null
        return products.mapNotNull { (it as? JsonObject)?.toProduct() }
    }

    /** Adresse de la fiche d'un produit : seul le code est envoyé, et seulement les champs utiles. */
    fun productUrl(code: String): String = "https://$HOST/api/v2/product/${URLEncoder.encode(code, "UTF-8")}.json?fields=$FIELDS"

    /**
     * Lit la fiche d'un produit. Renvoie null si la réponse est illisible ; [ProductLookup.NotFound] si
     * Open Food Facts ne connaît pas le code ; [ProductLookup.Incomplete] s'il manque le nom ou les calories.
     * Les calories viennent de `energy-kcal_100g`, ou des kJ divisés par 4,184 si les kcal manquent.
     */
    fun parseProductResponse(body: String, code: String): ProductLookup? {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return null
        val product = root["product"] as? JsonObject
        val status = (root["status"] as? JsonPrimitive)?.doubleOrNull
        if (product == null) return if (status == 0.0 || root.containsKey("status")) ProductLookup.NotFound else null
        val nutriments = product["nutriments"] as? JsonObject
        val kcal = nutriments?.number("energy-kcal_100g")
            ?: (nutriments?.number("energy-kj_100g") ?: nutriments?.number("energy_100g"))?.let { Math.round(it / KJ_PER_KCAL * 10) / 10.0 }
        val draft = OffProductDraft(
            code = code,
            name = product.text("product_name_fr") ?: product.text("product_name"),
            brand = product.firstBrand(),
            kcalPer100g = kcal,
            proteinPer100g = nutriments?.number("proteins_100g"),
            carbsPer100g = nutriments?.number("carbohydrates_100g"),
            fatPer100g = nutriments?.number("fat_100g"),
            fiberPer100g = nutriments?.number("fiber_100g"),
            servingG = product.number("serving_quantity")?.takeIf { it > 0 },
        )
        val name = draft.name
        val kcalValue = draft.kcalPer100g
        if (name == null || kcalValue == null) return ProductLookup.Incomplete(draft)
        return ProductLookup.Found(
            OffProduct(
                code = code,
                name = name,
                brand = draft.brand,
                kcalPer100g = kcalValue,
                proteinPer100g = draft.proteinPer100g,
                carbsPer100g = draft.carbsPer100g,
                fatPer100g = draft.fatPer100g,
                fiberPer100g = draft.fiberPer100g,
                servingG = draft.servingG,
            ),
        )
    }

    private const val KJ_PER_KCAL = 4.184

    private fun JsonObject.toProduct(): OffProduct? {
        val name = (text("product_name_fr") ?: text("product_name")) ?: return null
        val code = text("code") ?: return null
        val nutriments = this["nutriments"] as? JsonObject ?: return null
        val kcal = nutriments.number("energy-kcal_100g") ?: return null
        return OffProduct(
            code = code,
            name = name,
            brand = firstBrand(),
            kcalPer100g = kcal,
            proteinPer100g = nutriments.number("proteins_100g"),
            carbsPer100g = nutriments.number("carbohydrates_100g"),
            fatPer100g = nutriments.number("fat_100g"),
            fiberPer100g = nutriments.number("fiber_100g"),
            servingG = number("serving_quantity")?.takeIf { it > 0 },
        )
    }

    /** La marque est un texte « A, B » dans l'ancien moteur, une liste dans le nouveau. */
    private fun JsonObject.firstBrand(): String? {
        val first = when (val brands = this["brands"]) {
            is JsonArray -> (brands.firstOrNull() as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> brands.contentOrNull?.substringBefore(',')
            else -> null
        }
        return first?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** Open Food Facts renvoie les nombres tantôt en nombre, tantôt en texte. */
    private fun JsonObject.number(key: String): Double? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        val value = primitive.doubleOrNull ?: primitive.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
        return value?.takeIf { it >= 0 && !it.isNaN() && !it.isInfinite() }
    }
}
