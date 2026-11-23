package com.bidaboss.healthy.today.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bidaboss.healthy.core.progress.Growth
import com.bidaboss.healthy.core.progress.Mood
import com.bidaboss.healthy.ui.Pousse

/** Message simple quand Pousse passe à l'étape suivante ; « OK » le ferme pour de bon. */
@Composable
fun LevelUpBanner(message: String, growth: Growth, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Pousse(Mood.PROUD, growth = growth)
            Column(Modifier.weight(1f)) {
                Text("Pousse a grandi !", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}
