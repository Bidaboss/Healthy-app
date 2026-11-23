package com.bidaboss.healthy.body.data

import com.bidaboss.healthy.core.progress.Growth

/** Ce que l'onglet Moi montre de la progression : Pousse (selon la série) et les badges. */
data class MeProgress(
    val streakWeeks: Int = 0,
    val growth: Growth = Growth.forStreak(0),
    val unlockedBadges: Int = 0,
    val totalBadges: Int = 0,
    /** Titres des badges débloqués, les plus avancés d'abord. */
    val unlockedTitles: List<String> = emptyList(),
    /** Le prochain badge à gagner, avec où l'on en est. */
    val nextBadgeTitle: String? = null,
    val nextBadgeProgress: String? = null,
)
