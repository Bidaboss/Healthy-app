package com.bidaboss.healthy.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.core.route.RouteDirection
import com.bidaboss.healthy.core.route.RoutePlanning
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.workout.data.route.RouteFailure
import com.bidaboss.healthy.workout.data.route.SavedRoute
import java.time.LocalDate

/** Message simple pour chaque panne du calcul. Jamais d'adresse : on dit seulement laquelle (départ, étape, arrivée). */
internal fun failureMessage(failure: RouteFailure): String = when (failure) {
    is RouteFailure.AddressNotFound -> {
        val which = when (failure.index) {
            0 -> "du départ"
            failure.count - 1 -> "de l'arrivée"
            else -> "de l'étape ${failure.index}"
        }
        "Je ne trouve pas l'adresse $which. Vérifie le numéro, la rue et la ville."
    }
    RouteFailure.NoRoute -> "Je ne trouve pas de chemin à vélo entre ces points. Tu peux saisir la distance toi-même."
    RouteFailure.NoConnection -> "Pas d'internet pour l'instant. Tu peux saisir la distance toi-même."
    RouteFailure.Timeout -> "Le service met trop de temps à répondre. Réessaie plus tard ou saisis la distance toi-même."
    is RouteFailure.Http -> "Le service de calcul est indisponible pour l'instant (code ${failure.code}). Réessaie plus tard ou saisis la distance toi-même."
    RouteFailure.Unreadable -> "La réponse du service est illisible. Réessaie plus tard ou saisis la distance toi-même."
    is RouteFailure.Other -> "Le calcul a échoué (${failure.kind}). Tu peux saisir la distance toi-même."
}

/** « Mes trajets » : la liste, la création, la modification et la suppression. */
@Composable
fun RoutesScreen(
    onEdit: (routeId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutesViewModel = viewModel(factory = healthyViewModelFactory { RoutesViewModel(it.savedRouteRepository) }),
) {
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<SavedRoute?>(null) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mes trajets", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Enregistre un trajet une fois : la distance est calculée une seule fois, puis tu l'ajoutes en un appui, même sans internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(" Nouveau trajet")
        }
        val list = routes
        if (list != null && list.isEmpty()) {
            Text("Aucun trajet pour l'instant.", style = MaterialTheme.typography.bodyMedium)
        }
        list?.forEach { route ->
            Card(modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Modifier le trajet") { onEdit(route.id) }) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(route.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            route.distanceKm?.let { RoutePlanning.formatKm(it) + (if (route.distanceCalculated) "" else " (saisie)") }
                                ?: "Distance à calculer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { toDelete = route }) { Text("Supprimer") }
                }
            }
        }
    }
    toDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer ce trajet ?") },
            text = { Text("${route.name}. Les activités déjà ajoutées avec ce trajet restent dans ton journal.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    viewModel.delete(route.id)
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}

/** Création ou modification d'un trajet : départ, étapes, arrivée, distance. */
@Composable
fun RouteEditorScreen(
    routeId: Long?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteEditorViewModel = viewModel(
        key = "route-editor-$routeId",
        factory = healthyViewModelFactory { RouteEditorViewModel(it.savedRouteRepository, routeId) },
    ),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.saved) { if (s.saved) onClose() }
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (routeId == null) "Nouveau trajet" else "Modifier le trajet", style = MaterialTheme.typography.headlineMedium)
        if (s.loading) return@Column
        OutlinedTextField(
            value = s.name, onValueChange = viewModel::onName, label = { Text("Nom du trajet") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.start, onValueChange = viewModel::onStart, label = { Text("Départ (adresse)") },
            modifier = Modifier.fillMaxWidth(),
        )
        s.stops.forEachIndexed { index, stop ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = stop, onValueChange = { viewModel.onStop(index, it) }, label = { Text("Étape ${index + 1}") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.removeStop(index) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Retirer l'étape ${index + 1}")
                }
            }
        }
        if (s.stops.size < RoutePlanning.MAX_STOPS) {
            OutlinedButton(onClick = viewModel::addStop) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(" Ajouter une étape")
            }
        }
        OutlinedTextField(
            value = s.end, onValueChange = viewModel::onEnd, label = { Text("Arrivée (adresse)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Distance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        s.distanceKm?.let {
            Text(
                "${RoutePlanning.formatKm(it)} (un sens)" + if (s.distanceCalculated) " · calculée" else " · saisie",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Rappel visible avant tout envoi : les adresses sortent du téléphone, une seule fois, à l'appui du bouton.
        Text(
            "Les adresses sont envoyées à OpenStreetMap pour calculer la distance. Rien n'est envoyé tant que tu n'appuies pas sur le bouton.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = viewModel::calculate, enabled = s.canCalculate, modifier = Modifier.fillMaxWidth()) {
            Text(if (s.calculating) "Calcul en cours…" else if (s.distanceCalculated) "Recalculer la distance" else "Calculer la distance")
        }
        s.failure?.let { Text(failureMessage(it), color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = s.manualText, onValueChange = viewModel::onManualDistance,
            label = { Text("Ou saisir la distance moi-même (km)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = viewModel::save, enabled = s.canSave, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Enregistrer")
        }
        if (s.distanceKm == null) {
            Text(
                "Sans distance, le trajet est enregistré mais ne peut pas encore être ajouté au journal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** « Ajouter une activité » : type, durée, jour, zones, et « Mes trajets » en un appui. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddActivityScreen(
    onClose: () -> Unit,
    onOpenRoutes: () -> Unit,
    onEditRoute: (routeId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = viewModel(
        factory = healthyViewModelFactory {
            WorkoutViewModel(it.workoutRepository, it.workoutLogRepository, it.referenceWeightKg, it.isWatchConnected, it.savedRouteRepository)
        },
    ),
) {
    val today = remember { LocalDate.now() }
    val form = remember { ActivityFormState(null, today) }
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Ajouter une activité", style = MaterialTheme.typography.headlineMedium)
        ActivityFormFields(form, today)
        Button(
            onClick = {
                form.minutes?.let {
                    viewModel.addActivity(form.type, it, form.date, form.parts)
                    onClose()
                }
            },
            enabled = form.valid,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Ajouter") }

        if (!form.other && typeHasRoutes(form.type)) {
            Text("Mes trajets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            if (routes.isEmpty()) {
                Text("Aucun trajet enregistré.", style = MaterialTheme.typography.bodyMedium)
            }
            routes.forEach { route ->
                RouteChoiceCard(
                    route = route,
                    speedKmh = routeSpeedKmh(form.type),
                    onAdd = { direction, minutes ->
                        viewModel.addRouteActivity(form.type, route, direction, minutes, form.date)
                        onClose()
                    },
                    onEdit = { onEditRoute(route.id) },
                )
            }
            OutlinedButton(onClick = { onEditRoute(null) }, modifier = Modifier.fillMaxWidth()) { Text("Nouveau trajet") }
            TextButton(onClick = onOpenRoutes) { Text("Gérer mes trajets") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteChoiceCard(
    route: SavedRoute,
    speedKmh: Double,
    onAdd: (RouteDirection, Int) -> Unit,
    onEdit: () -> Unit,
) {
    var direction by remember { mutableStateOf(RouteDirection.ROUND_TRIP) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(route.name, style = MaterialTheme.typography.titleMedium)
            val oneWay = route.distanceKm
            if (oneWay == null) {
                Text("La distance n'est pas encore calculée.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onEdit) { Text("Calculer la distance") }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RouteDirection.entries.forEach { d ->
                        FilterChip(selected = direction == d, onClick = { direction = d }, label = { Text(d.label) })
                    }
                }
                val km = direction.distanceKm(oneWay)
                val minutes = RoutePlanning.estimateMinutes(km, speedKmh).coerceAtMost(com.bidaboss.healthy.workout.data.WorkoutLogRepository.MAX_ACTIVITY_MINUTES)
                Text(
                    "${RoutePlanning.formatKm(km)} · environ $minutes min à ${speedKmh.toInt()} km/h (modifiable ensuite)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { onAdd(direction, minutes) }, modifier = Modifier.fillMaxWidth()) { Text("Ajouter ce trajet") }
            }
        }
    }
}
