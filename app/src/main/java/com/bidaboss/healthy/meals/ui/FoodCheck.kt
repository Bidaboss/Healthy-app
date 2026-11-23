package com.bidaboss.healthy.meals.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.core.importformat.Confidence
import com.bidaboss.healthy.meals.data.Food

/**
 * Estimation peu sûre (confiance faible) pas encore corrigée à la main : une correction passe la
 * source en « manuel » et efface la confiance (fil Nutrition, 12/11/2026).
 */
internal fun Food.needsCheck(): Boolean = confidence == Confidence.LOW

/** Pastille « à vérifier », couleur d'alerte douce du thème. */
@Composable
internal fun ToCheckBadge(modifier: Modifier = Modifier) {
    Text(
        "à vérifier",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Hypothèse faite pour estimer l'aliment (ex. « portion standard, huile non comptée »), en petit. */
@Composable
internal fun AssumptionText(text: String, maxLines: Int = 2) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
