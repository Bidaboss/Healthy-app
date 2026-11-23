package com.bidaboss.healthy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.core.progress.Mood

/** État vide illustré : Pousse, une phrase, et au plus un bouton (fil Design, 12/11/2026). */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    mood: Mood = Mood.HAPPY,
    size: Dp = 88.dp,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pousse(mood, size = size)
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}
