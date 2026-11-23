package com.bidaboss.healthy.meals.ui

import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.oned.UPCEReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Lit les codes-barres EAN-13, EAN-8, UPC-A et UPC-E dans les images de la caméra, avec ZXing :
 * tout se passe sur le téléphone, sans réseau ni service Google.
 */
internal class BarcodeAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                ),
                // Le code est souvent tenu en travers : on laisse ZXing essayer aussi les images tournées.
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            // Le premier plan d'une image YUV est la luminance (le noir et blanc) : tout ce qu'il faut.
            val plane = image.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            // Un UPC-E (code court) est converti en UPC-A, comme Open Food Facts l'enregistre.
            val text = if (result.barcodeFormat == BarcodeFormat.UPC_E) UPCEReader.convertUPCEtoUPCA(result.text) else result.text
            onCode(text)
        } catch (_: NotFoundException) {
            // Pas de code dans cette image : normal, on attend la suivante.
        } catch (_: Exception) {
            // Une image illisible ne doit jamais arrêter la caméra.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

/** Aperçu de la caméra arrière avec lecture du code. [torch] allume la lampe si le téléphone en a une. */
@Composable
internal fun BarcodeCamera(
    torch: Boolean,
    onCode: (String) -> Unit,
    onTorchAvailable: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnTorchAvailable by rememberUpdatedState(onTorchAvailable)
    var camera by remember { mutableStateOf<Camera?>(null) }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()
            provider = cameraProvider
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, BarcodeAnalyzer { code -> currentOnCode(code) }) }
            runCatching {
                cameraProvider.unbindAll()
                val bound = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                camera = bound
                currentOnTorchAvailable(bound.cameraInfo.hasFlashUnit())
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            camera = null
            executor.shutdown()
        }
    }

    LaunchedEffect(camera, torch) {
        camera?.let { runCatching { it.cameraControl.enableTorch(torch) } }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
