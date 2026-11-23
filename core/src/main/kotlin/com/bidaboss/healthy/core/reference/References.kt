package com.bidaboss.healthy.core.reference

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** D'où vient un repère : lu à la source, à confirmer, ou choisi par l'app (jamais présenté comme officiel). */
enum class ReferenceStatus(val label: String) {
    VERIFIED("vérifié"),
    TO_CONFIRM("à confirmer"),
    INDICATIVE("indicatif, non officiel"),
    APP_GOAL("Objectif de l'app"),
}

/** Forme du repère : un minimum, une zone, ou pas de chiffre (on montre seulement la tendance). */
enum class ReferenceKind { MINIMUM, RANGE, TREND }

enum class ReferenceId { CALORIES, PROTEIN, FIBER, CARBS, FAT, WATER, ACTIVITY, SLEEP, STEPS, OTHER }

/**
 * Une ligne de la table des repères (PRD « repères nationaux »). La table est une donnée du code, mise à jour par une
 * nouvelle version de l'app, jamais en ligne. [measurable] = l'app a les données pour placer le propriétaire ; sinon la ligne
 * n'est pas affichée (jamais à zéro). [url] reste null tant que l'adresse exacte de la source n'a pas été relue.
 */
data class Reference(
    val id: ReferenceId,
    val name: String,
    val unit: String,
    val kind: ReferenceKind,
    val min: Double? = null,
    val max: Double? = null,
    val rule: String,
    val source: String,
    val url: String? = null,
    val readOn: String? = null,
    val status: ReferenceStatus,
    val measurable: Boolean = false,
    /** Précision à lire avec le repère (ce qu'il couvre, ce qui le distingue de ce que l'app note). */
    val note: String? = null,
)

object References {

    const val READ_ON = "2026-11-23"
    private const val OMS = "OMS, activité physique"
    private const val EFSA = "EFSA, apports de référence"
    private const val SPF = "Santé publique France (PNNS)"

    /** La table complète, les repères mesurables en premier. */
    val catalog: List<Reference> = listOf(
        Reference(
            ReferenceId.CALORIES, "Calories", "kcal", ReferenceKind.RANGE,
            rule = "ton maintien, à 10 % près",
            source = "Ton maintien calculé par l'app (taille, poids, âge, activité). L'EFSA donne des besoins moyens par âge, sexe et niveau d'activité.",
            readOn = READ_ON, status = ReferenceStatus.APP_GOAL, measurable = true,
        ),
        Reference(
            ReferenceId.PROTEIN, "Protéines", "g/kg", ReferenceKind.MINIMUM, min = 0.83,
            rule = "0,83 g/kg ou plus",
            source = "$EFSA : 0,83 g par kilo et par jour (apport recommandé, adultes) ; 0,66 g/kg = besoin moyen.",
            readOn = READ_ON, status = ReferenceStatus.VERIFIED, measurable = true,
            note = "Un minimum : jamais d'alerte au-dessus.",
        ),
        Reference(
            ReferenceId.FIBER, "Fibres", "g", ReferenceKind.MINIMUM, min = 25.0,
            rule = "25 g ou plus",
            source = "$EFSA : 25 g par jour (apport adéquat, adultes).",
            readOn = READ_ON, status = ReferenceStatus.VERIFIED, measurable = true,
            note = "Un minimum : jamais d'alerte au-dessus.",
        ),
        Reference(
            ReferenceId.CARBS, "Glucides", "% de l'énergie", ReferenceKind.RANGE, min = 45.0, max = 60.0,
            rule = "45 à 60 % de l'énergie",
            source = "$EFSA : 45 à 60 % de l'énergie (adultes).",
            readOn = READ_ON, status = ReferenceStatus.VERIFIED, measurable = true,
            note = "Calculé sur les jours dont tous les repas portent leurs macros.",
        ),
        Reference(
            ReferenceId.FAT, "Lipides", "% de l'énergie", ReferenceKind.RANGE, min = 20.0, max = 35.0,
            rule = "20 à 35 % de l'énergie",
            source = "$EFSA : 20 à 35 % de l'énergie (adultes).",
            readOn = READ_ON, status = ReferenceStatus.VERIFIED, measurable = true,
            note = "Calculé sur les jours dont tous les repas portent leurs macros.",
        ),
        Reference(
            ReferenceId.WATER, "Eau", "L", ReferenceKind.MINIMUM,
            rule = "2,5 L (hommes) ou 2 L (femmes)",
            source = "$EFSA : 2,5 L par jour pour les hommes, 2 L pour les femmes, boissons et eau des aliments compris.",
            readOn = READ_ON, status = ReferenceStatus.VERIFIED, measurable = true,
            note = "Ce repère est plus large que l'eau que tu notes : il compte aussi l'eau des aliments.",
        ),
        Reference(
            ReferenceId.ACTIVITY, "Activité", "min/sem.", ReferenceKind.RANGE, min = 150.0, max = 300.0,
            rule = "150 à 300 min par semaine",
            source = "$OMS : 150 à 300 min d'activité modérée par semaine, renforcement musculaire bénéfique.",
            readOn = READ_ON, status = ReferenceStatus.VERIFIED, measurable = true,
        ),
        Reference(
            ReferenceId.SLEEP, "Sommeil", "h", ReferenceKind.MINIMUM, min = 7.0,
            rule = "7 h par nuit ou plus",
            source = "$SPF (page Sommeil) : 7 heures minimum par nuit pour les adultes de 18 à 64 ans ; moyenne observée en France 7 h 32 (2024).",
            readOn = READ_ON, status = ReferenceStatus.VERIFIED, measurable = true,
            note = "Hors de 18 à 64 ans, l'app montre ta tendance sans zone.",
        ),
        Reference(
            ReferenceId.STEPS, "Pas", "/jour", ReferenceKind.RANGE, min = 7000.0, max = 10000.0,
            rule = "7 000 à 10 000 pas par jour (indicatif)",
            source = "Aucun repère officiel en pas par jour : zone indicative choisie par le propriétaire, non officielle.",
            status = ReferenceStatus.INDICATIVE, measurable = true,
            note = "Visible seulement si la montre est connectée.",
        ),
        // Repères lus à la source mais que l'app ne peut pas mesurer (elle ne note ni le sodium ni les aliments par groupe) : non affichés.
        other("Sodium", "2 g par jour (environ 5 g de sel)", EFSA),
        other("Sucres ajoutés, graisses saturées, graisses trans", "aussi faibles que possible, sans chiffre", EFSA),
        other("Endurance et renforcement", "30 min d'endurance au moins 5 j/sem ; renforcement 1 à 2 j/sem", SPF),
        other("Fruits et légumes", "au moins 5 par jour", SPF),
        other("Féculents", "à chaque repas, dont 1 complet par jour", SPF),
        other("Produits laitiers", "2 par jour", SPF),
        other("Poisson", "2 fois par semaine, dont 1 gras", SPF),
        other("Légumes secs", "au moins 2 fois par semaine", SPF),
        other("Viande (hors volaille)", "500 g par semaine au plus", SPF),
        other("Charcuterie", "150 g par semaine au plus", SPF),
        other("Boissons", "l'eau est la seule boisson recommandée", SPF),
    )

    val measurable: List<Reference> get() = catalog.filter { it.measurable }

    fun of(id: ReferenceId): Reference = catalog.first { it.id == id }

    private fun other(name: String, rule: String, source: String) = Reference(
        ReferenceId.OTHER, name, "", ReferenceKind.TREND, rule = rule, source = source,
        readOn = READ_ON, status = ReferenceStatus.VERIFIED, measurable = false,
    )
}

/** Où se place une valeur. Mots du PRD : jamais « mauvais », aucune alerte, aucune couleur rouge. */
enum class ReferencePosition(val label: String) {
    BELOW("sous le repère"),
    IN("dans le repère"),
    ABOVE("au-dessus du repère"),
    TREND("tendance"),
}

/** Ce que l'app a noté un jour. Une mesure absente est null, jamais zéro. */
data class RefDay(
    val kcal: Int?,
    val proteinG: Double?,
    val fiberG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val waterMl: Int?,
    val sportMinutes: Int,
    val sleepMin: Int?,
    val steps: Int?,
    /** Eau à boire ce jour-là (mL), null sans profil. */
    val waterGoalMl: Int? = null,
    val maintenanceKcal: Int? = null,
)

/** Ce qu'il faut du profil pour les repères qui dépendent du sexe (eau) et de l'âge (sommeil). */
data class ReferenceProfile(val male: Boolean, val ageYears: Int)

/** L'objectif de l'app à côté du repère officiel : jamais à sa place. */
data class AppGoalReading(val text: String, val position: ReferencePosition)

/** Une ligne prête à afficher : valeur, position, et zone du repère sur une échelle de 0 à [scaleMax]. */
data class ReferenceLine(
    val reference: Reference,
    val value: Double,
    val valueText: String,
    val ruleText: String,
    val position: ReferencePosition,
    val scaleMax: Double,
    val zoneStart: Double?,
    val zoneEnd: Double?,
    val appGoal: AppGoalReading? = null,
) {
    val valueFraction: Float get() = (value / scaleMax).coerceIn(0.0, 1.0).toFloat()
    val zoneStartFraction: Float? get() = zoneStart?.let { (it / scaleMax).coerceIn(0.0, 1.0).toFloat() }
    val zoneEndFraction: Float? get() = zoneEnd?.let { (it / scaleMax).coerceIn(0.0, 1.0).toFloat() }
}

data class ReferenceReport(
    val periodDays: Int,
    val lines: List<ReferenceLine>,
    /** Moins de 80 % des jours avec repas notés : repère indicatif. */
    val indicative: Boolean,
    /** Poids, sexe ou âge manquants : les repères liés au profil sont masqués. */
    val profileMissing: Boolean,
)

/** Place les valeurs du propriétaire par rapport aux repères, sur [periodDays] jours (docs/prd/reperes-nationaux.md). */
object ReferenceReports {

    const val MIN_LOGGED_SHARE = 0.8
    const val PROTEIN_APP_GOAL_G_PER_KG = 1.6
    const val FIBER_APP_GOAL_G = 30.0
    private const val SLEEP_MIN_AGE = 18
    private const val SLEEP_MAX_AGE = 64
    private const val EPSILON = 1e-9

    fun build(days: List<RefDay>, weightKg: Double?, profile: ReferenceProfile?): ReferenceReport {
        val period = days.size
        val loggedDays = days.count { it.kcal != null }
        val indicative = period > 0 && loggedDays < MIN_LOGGED_SHARE * period
        val lines = mutableListOf<ReferenceLine>()

        // Calories : moyenne des jours notés face à la moyenne du maintien de ces jours.
        val kcalDays = days.filter { it.kcal != null && it.maintenanceKcal != null }
        if (kcalDays.isNotEmpty()) {
            val avg = kcalDays.sumOf { it.kcal!! } / kcalDays.size.toDouble()
            val maintenance = kcalDays.sumOf { it.maintenanceKcal!! } / kcalDays.size.toDouble()
            val ref = References.of(ReferenceId.CALORIES)
            lines += lineWithZone(ref, avg, "${grouped(avg)} kcal", "maintien ${grouped(maintenance)}", 0.9 * maintenance, 1.1 * maintenance)
        }

        // Protéines : g/kg du poids ; repère EFSA, objectif de l'app à côté.
        val proteinDays = days.mapNotNull { it.proteinG }
        if (proteinDays.isNotEmpty() && weightKg != null && weightKg > 0) {
            val perKg = proteinDays.average() / weightKg
            val ref = References.of(ReferenceId.PROTEIN)
            val goal = AppGoalReading("Objectif de l'app : ${decimal(PROTEIN_APP_GOAL_G_PER_KG, 1)} g/kg", position(perKg, PROTEIN_APP_GOAL_G_PER_KG, null))
            lines += lineWithZone(ref, perKg, "${decimal(perKg, 1)} g/kg", ref.rule, ref.min!!, null, goal)
        }

        val fiberDays = days.mapNotNull { it.fiberG }
        if (fiberDays.isNotEmpty()) {
            val avg = fiberDays.average()
            val ref = References.of(ReferenceId.FIBER)
            val goal = AppGoalReading("Objectif de l'app : ${FIBER_APP_GOAL_G.roundToInt()} g", position(avg, FIBER_APP_GOAL_G, null))
            lines += lineWithZone(ref, avg, "${avg.roundToInt()} g", ref.rule, ref.min!!, null, goal)
        }

        // Glucides et lipides : part de l'énergie, sur les jours dont tous les repas portent leurs macros.
        val macroDays = days.filter { it.kcal != null && it.kcal > 0 && it.carbsG != null && it.fatG != null }
        if (macroDays.isNotEmpty()) {
            val energy = macroDays.sumOf { it.kcal!! }.toDouble()
            val carbs = macroDays.sumOf { it.carbsG!! } * 4 / energy * 100
            val fat = macroDays.sumOf { it.fatG!! } * 9 / energy * 100
            val carbsRef = References.of(ReferenceId.CARBS)
            val fatRef = References.of(ReferenceId.FAT)
            lines += lineWithZone(carbsRef, carbs, "${carbs.roundToInt()} %", carbsRef.rule, carbsRef.min!!, carbsRef.max)
            lines += lineWithZone(fatRef, fat, "${fat.roundToInt()} %", fatRef.rule, fatRef.min!!, fatRef.max)
        }

        // Eau : repère EFSA selon le sexe, objectif de l'app à côté.
        val waterDays = days.filter { it.waterMl != null && it.waterGoalMl != null }
        if (waterDays.isNotEmpty() && profile != null) {
            val avg = waterDays.sumOf { it.waterMl!! } / waterDays.size / 1000.0
            val goal = waterDays.sumOf { it.waterGoalMl!! } / waterDays.size / 1000.0
            val ref = References.of(ReferenceId.WATER)
            val official = if (profile.male) 2.5 else 2.0
            val rule = "${decimal(official, 1)} L (${if (profile.male) "hommes" else "femmes"})"
            lines += lineWithZone(ref, avg, "${decimal(avg, 1)} L", rule, official, null, AppGoalReading("Objectif de l'app : ${decimal(goal, 1)} L", position(avg, goal, null)))
        }

        // Activité : total des minutes ramené à une semaine, avec ou sans montre.
        if (period > 0 && days.any { it.sportMinutes > 0 }) {
            val perWeek = days.sumOf { it.sportMinutes } * 7.0 / period
            val ref = References.of(ReferenceId.ACTIVITY)
            lines += lineWithZone(ref, perWeek, "${perWeek.roundToInt()} min/sem.", ref.rule, ref.min!!, ref.max)
        }

        // Sommeil : repère de Santé publique France pour les 18 à 64 ans, sinon tendance seule.
        val sleepDays = days.mapNotNull { it.sleepMin }
        if (sleepDays.isNotEmpty()) {
            val avg = sleepDays.average()
            val ref = References.of(ReferenceId.SLEEP)
            lines += if (profile != null && profile.ageYears in SLEEP_MIN_AGE..SLEEP_MAX_AGE) {
                lineWithZone(ref, avg / 60, hoursText(avg), ref.rule, ref.min!!, null)
            } else {
                trend(ref.copy(rule = "pas de repère pour cet âge"), avg, hoursText(avg))
            }
        }
        val stepDays = days.mapNotNull { it.steps }
        if (stepDays.isNotEmpty()) {
            val avg = stepDays.average()
            val ref = References.of(ReferenceId.STEPS)
            lines += lineWithZone(ref, avg, "${grouped(avg)} pas", ref.rule, ref.min!!, ref.max)
        }

        return ReferenceReport(period, lines, indicative, profileMissing = profile == null || weightKg == null)
    }

    /** Position d'une valeur : un minimum n'est jamais « au-dessus », une zone peut l'être. */
    fun position(value: Double, min: Double?, max: Double?): ReferencePosition = when {
        min != null && value < min - EPSILON -> ReferencePosition.BELOW
        max != null && value > max + EPSILON -> ReferencePosition.ABOVE
        else -> ReferencePosition.IN
    }

    private fun lineWithZone(
        ref: Reference, value: Double, valueText: String, ruleText: String, min: Double, max: Double?, appGoal: AppGoalReading? = null,
    ): ReferenceLine {
        val top = max ?: (min * 1.5)
        val scale = max(top * 1.15, value * 1.1)
        return ReferenceLine(ref, value, valueText, ruleText, position(value, min, max), scale, min, max ?: scale, appGoal)
    }

    private fun trend(ref: Reference, value: Double, valueText: String) =
        ReferenceLine(ref, value, valueText, ref.rule, ReferencePosition.TREND, value * 1.5, null, null)

    private fun decimal(value: Double, digits: Int): String = "%.${digits}f".format(java.util.Locale.ROOT, value).replace('.', ',')

    private fun grouped(value: Double): String {
        val digits = abs(value).roundToInt().toString()
        val spaced = digits.reversed().chunked(3).joinToString("\u202F").reversed()
        return if (value < 0) "-$spaced" else spaced
    }

    private fun hoursText(minutes: Double): String {
        val total = minutes.roundToInt()
        return "${total / 60} h ${"%02d".format(total % 60)}"
    }
}
