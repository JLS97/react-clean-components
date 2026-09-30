package io.github.jls97.boveda.ui.otp

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors

/**
 * Camera preview that reads QR codes on the phone with ZXing (open source, no Google services).
 * Frames stay in memory, are never stored and are wiped after reading. [onDecoded] runs on the
 * main thread once per different text found.
 */
@Composable
internal fun QrCameraPreview(onDecoded: (String) -> Unit, onError: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDecoded by rememberUpdatedState(onDecoded)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner, previewView) {
        val mainExecutor = context.mainExecutor
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false

        providerFuture.addListener(
            {
                if (disposed) return@addListener
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    val decoder = QrFrameDecoder()
                    var lastText: String? = null // Only touched on the analysis thread.
                    analysis.setAnalyzer(analysisExecutor) { image ->
                        val text = try {
                            decoder.decode(image)
                        } finally {
                            image.close()
                        }
                        if (text != null && text != lastText) {
                            lastText = text
                            mainExecutor.execute { if (!disposed) currentOnDecoded(text) }
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    // No back camera, or it is in use by another app.
                    currentOnError()
                }
            },
            mainExecutor,
        )

        onDispose {
            disposed = true
            if (providerFuture.isDone) {
                try {
                    providerFuture.get().unbindAll()
                } catch (e: Exception) {
                    // The camera never opened: nothing to release.
                }
            }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Reads a QR code from the brightness plane of a camera frame. */
internal class QrFrameDecoder {
    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    fun decode(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val rowStride = plane.rowStride
        val luminance = ByteArray(rowStride * image.height)
        try {
            val buffer = plane.buffer
            buffer.rewind()
            buffer.get(luminance, 0, minOf(buffer.remaining(), luminance.size))
            val source = PlanarYUVLuminanceSource(luminance, rowStride, image.height, 0, 0, image.width, image.height, false)
            // Light-on-dark codes (some dark themes) only read once inverted.
            return read(source) ?: read(source.invert())
        } finally {
            luminance.fill(0)
        }
    }

    private fun read(source: LuminanceSource): String? =
        try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (e: ReaderException) {
            null
        } finally {
            reader.reset()
        }
}
