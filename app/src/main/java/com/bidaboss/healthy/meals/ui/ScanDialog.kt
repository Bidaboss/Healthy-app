package com.bidaboss.healthy.meals.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bidaboss.healthy.meals.data.Food
import com.bidaboss.healthy.ui.formatGrams
import com.bidaboss.healthy.ui.formatKcal
import com.bidaboss.healthy.ui.healthyViewModelFactory
import com.bidaboss.healthy.ui.parseNumber

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Scan d'un code-barres en plein écran : visée, produit, quantité, saisie de l'étiquette si le produit est inconnu. */
@Composable
fun ScanDialog(
    onFoodChosen: (Food) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ScanViewModel = viewModel(
        key = "scan",
        factory = healthyViewModelFactory { ScanViewModel(it.scannedProductRepository) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }

    LaunchedEffect(Unit) { viewModel.reset() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                !granted -> CameraPermissionScreen(
                    denied = denied,
                    onAsk = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    onDismiss = onDismiss,
                )
                state.step == ScanStep.SCANNING || state.step == ScanStep.LOADING ->
                    ScanningScreen(state, viewModel, onDismiss)
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state.step) {
                        ScanStep.PRODUCT -> ProductStep(state, viewModel, onFoodChosen)
                        ScanStep.UNKNOWN -> UnknownStep(state, viewModel, onDismiss)
                        ScanStep.FAILED -> FailedStep(state, viewModel, onDismiss)
                        ScanStep.LABEL -> LabelStep(state, viewModel)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionScreen(denied: Boolean, onAsk: () -> Unit, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Scanner un produit", style = MaterialTheme.typography.headlineSmall)
        if (!denied) {
            Text(
                "Healthy a besoin de la caméra pour lire le code-barres du produit. " +
                    "Aucune photo n'est gardée ni envoyée.",
            )
            Button(onClick = onAsk, modifier = Modifier.fillMaxWidth()) { Text("Autoriser la caméra") }
        } else {
            Text(
                "La caméra est refusée, donc le scan ne peut pas marcher. Tu peux l'autoriser dans les réglages " +
                    "d'Android, ou chercher le produit par son nom.",
            )
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir les réglages") }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
    }
}

@Composable
private fun ScanningScreen(state: ScanUiState, viewModel: ScanViewModel, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var torch by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    val loading = state.step == ScanStep.LOADING
    Box(modifier = Modifier.fillMaxSize()) {
        BarcodeCamera(
            torch = torch,
            onCode = { code ->
                // La caméra lit plusieurs images par seconde : le ViewModel ne prend que la première.
                if (viewModel.uiState.value.step == ScanStep.SCANNING) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onCodeRead(code)
                }
            },
            onTorchAvailable = { hasTorch = it },
            modifier = Modifier.fillMaxSize(),
        )
        // Cadre de visée.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .height(150.dp)
                .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        ) {
            Text(
                if (loading) "Recherche du produit…" else "Place le code-barres dans le cadre.",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.message?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                }
            }
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Annuler") }
                if (hasTorch) {
                    OutlinedButton(onClick = { torch = !torch }, modifier = Modifier.weight(1f)) {
                        Text(if (torch) "Éteindre la lampe" else "Allumer la lampe")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductStep(state: ScanUiState, viewModel: ScanViewModel, onFoodChosen: (Food) -> Unit) {
    val scanned = state.product ?: return
    val product = scanned.product
    Text(product.name, style = MaterialTheme.typography.titleLarge)
    product.brand?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text("Pour 100 g : ${formatKcal(product.kcalPer100g)}")
    when {
        state.remembered -> Note("Déjà scanné")
        !scanned.fromOpenFoodFacts -> Note("Saisi depuis l'étiquette")
    }
    product.servingG?.let { Note("1 portion = ${formatGrams(it)}") }
    OutlinedTextField(
        value = state.quantityG,
        onValueChange = viewModel::setQuantity,
        label = { Text("Quantité mangée (g)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    parseNumber(state.quantityG)?.takeIf { it > 0 }?.let { quantity ->
        val nutrients = product.forQuantity(quantity)
        Text(formatKcal(nutrients.kcal), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(
            "Protéines ${formatGrams(nutrients.proteinG ?: 0.0)} · Glucides ${formatGrams(nutrients.carbsG ?: 0.0)} · " +
                "Lipides ${formatGrams(nutrients.fatG ?: 0.0)}",
        )
    }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = viewModel::scanAgain, modifier = Modifier.weight(1f)) { Text("Autre produit") }
        Button(onClick = { viewModel.buildFood()?.let(onFoodChosen) }, modifier = Modifier.weight(1f)) { Text("Ajouter") }
    }
    Note("Données : Open Food Facts (base libre, valeurs indicatives).")
}

@Composable
private fun UnknownStep(state: ScanUiState, viewModel: ScanViewModel, onDismiss: () -> Unit) {
    Text("Produit inconnu d'Open Food Facts", style = MaterialTheme.typography.titleLarge)
    Text("Code : ${state.code}")
    Text("Tu peux recopier les valeurs de l'étiquette une seule fois : Healthy s'en souviendra au prochain scan.")
    Button(onClick = viewModel::openLabel, modifier = Modifier.fillMaxWidth()) { Text("Saisir l'étiquette") }
    OutlinedButton(onClick = viewModel::scanAgain, modifier = Modifier.fillMaxWidth()) { Text("Scanner un autre produit") }
    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
}

@Composable
private fun FailedStep(state: ScanUiState, viewModel: ScanViewModel, onDismiss: () -> Unit) {
    Text("Produit introuvable pour le moment", style = MaterialTheme.typography.titleLarge)
    state.failure?.let {
        Text(it.scanMessage(), color = MaterialTheme.colorScheme.error)
        Note(it.technicalDetail())
    }
    Button(onClick = viewModel::openLabel, modifier = Modifier.fillMaxWidth()) { Text("Saisir l'étiquette") }
    OutlinedButton(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) { Text("Réessayer") }
    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
}

@Composable
private fun LabelStep(state: ScanUiState, viewModel: ScanViewModel) {
    val form = state.label
    Text("Saisir l'étiquette", style = MaterialTheme.typography.titleLarge)
    Text("Code : ${state.code}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Recopie les valeurs pour 100 g. Rien n'est envoyé à Open Food Facts.", style = MaterialTheme.typography.bodySmall)
    LabelField("Nom du produit", form.name, KeyboardType.Text) { viewModel.setLabel(form.copy(name = it)) }
    LabelField("Marque (facultatif)", form.brand, KeyboardType.Text) { viewModel.setLabel(form.copy(brand = it)) }
    LabelField("Calories pour 100 g (kcal)", form.kcal, KeyboardType.Decimal) { viewModel.setLabel(form.copy(kcal = it)) }
    LabelField("Protéines pour 100 g (g, facultatif)", form.protein, KeyboardType.Decimal) { viewModel.setLabel(form.copy(protein = it)) }
    LabelField("Glucides pour 100 g (g, facultatif)", form.carbs, KeyboardType.Decimal) { viewModel.setLabel(form.copy(carbs = it)) }
    LabelField("Lipides pour 100 g (g, facultatif)", form.fat, KeyboardType.Decimal) { viewModel.setLabel(form.copy(fat = it)) }
    LabelField("Fibres pour 100 g (g, facultatif)", form.fiber, KeyboardType.Decimal) { viewModel.setLabel(form.copy(fiber = it)) }
    LabelField("Une portion (g, facultatif)", form.serving, KeyboardType.Decimal) { viewModel.setLabel(form.copy(serving = it)) }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = viewModel::scanAgain, modifier = Modifier.weight(1f)) { Text("Annuler") }
        Button(onClick = viewModel::saveLabel, modifier = Modifier.weight(1f)) { Text("Continuer") }
    }
}

@Composable
private fun LabelField(label: String, value: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
