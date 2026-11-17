package dev.kolib.app.ui.link

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.kolib.app.log.AppLog
import dev.kolib.app.tr
import dev.kolib.app.link.LinkQr
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** QR codes drawn and read (by ZXing). */
object QrCodes {
    /** [text] as a QR code: its squares, with a quiet border two squares wide. */
    fun matrix(text: String): BitMatrix? = runCatching {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"))
    }.getOrNull()

    /** The QR code in a picture [width] by [height], given as its brightness alone, row after row; null if there's none to read. */
    fun read(luminance: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        } catch (e: ReaderException) {
            null
        }
    }

    /** The QR code the camera sees in [image], if it sees one. */
    fun read(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val w = image.width
        val h = image.height
        val stride = plane.rowStride
        val buffer = plane.buffer
        val data = ByteArray(w * h)
        if (stride == w) {
            buffer.rewind()
            buffer.get(data, 0, minOf(buffer.remaining(), data.size))
        } else {
            for (row in 0 until h) {
                val at = row * stride
                if (at + w > buffer.limit()) break
                buffer.position(at)
                buffer.get(data, row * w, w)
            }
        }
        return read(data, w, h)
    }
}

/** A QR code saying [text], black on white. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QrCodes.matrix(text) }
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        val m = matrix ?: return@Canvas
        val cell = size.minDimension / m.width
        val left = (size.width - cell * m.width) / 2
        val top = (size.height - cell * m.height) / 2
        for (y in 0 until m.height) {
            var x = 0
            while (x < m.width) {
                if (!m.get(x, y)) { x++; continue }
                var end = x
                while (end + 1 < m.width && m.get(end + 1, y)) end++
                // A hair over the square's size, so no light shows between neighbours.
                drawRect(Color.Black, Offset(left + x * cell, top + y * cell), Size((end - x + 1) * cell + 0.5f, cell + 0.5f))
                x = end + 1
            }
        }
    }
}

/** This device's pairing code, for the other device to scan. [text] is null when this device can't be reached. */
@Composable
fun ShowQrDialog(text: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Pair with a QR code")) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (text == null) {
                    Text(tr("Join a Wi-Fi network first."), style = MaterialTheme.typography.bodyLarge)
                } else {
                    QrCode(text, Modifier.size(232.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        tr("On your other device, open Devices and tap Scan QR code."),
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done")) } },
    )
}

/** The camera, looking for the other device's pairing code. [onRead] says whether what it read was one. */
@Composable
fun ScanQrDialog(onRead: (String) -> Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var refused by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var broken by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { yes ->
        allowed = yes
        refused = !yes
    }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Scan QR code")) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(248.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                    if (allowed && !broken) {
                        QrCamera(
                            onRead = { text -> onRead(text).also { ok -> wrong = !ok } },
                            onBroken = { broken = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        broken -> tr("The camera couldn't be opened.")
                        !allowed && refused -> tr("KoLib needs the camera to scan the code.")
                        wrong -> tr("That isn't a KoLib pairing code.")
                        else -> tr("Point the camera at the code on your other device.")
                    },
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = if (wrong || broken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = {
            if (!allowed && refused) TextButton(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text(tr("Allow camera")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}

/** The back camera's view, with every QR code it sees handed to [onRead] until one is taken (it answers true). */
@Composable
private fun QrCamera(onRead: (String) -> Boolean, onBroken: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val read by rememberUpdatedState(onRead)
    val brokenNow by rememberUpdatedState(onBroken)
    val worker = remember { Executors.newSingleThreadExecutor() }
    val taken = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            worker.shutdown()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                // Drawn as part of the window, so it sits inside the dialog like anything else.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val main = ContextCompat.getMainExecutor(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    var last = ""
                    analysis.setAnalyzer(worker) { image ->
                        try {
                            val text = if (taken.get()) null else QrCodes.read(image)
                            // The same wrong code isn't handed over again and again.
                            if (text != null && text != last) {
                                last = text
                                main.execute { if (!taken.get() && read(text)) taken.set(true) }
                            }
                        } catch (e: Exception) {
                            AppLog.w("Link", "A camera picture couldn't be looked at", e)
                        } finally {
                            image.close()
                        }
                    }
                    val camera = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, camera, preview, analysis)
                } catch (e: Exception) {
                    AppLog.w("Link", "The camera couldn't be opened", e)
                    brokenNow()
                }
            }, main)
            view
        },
    )
}

/** Whether [text] is one of KoLib's pairing codes. */
fun isPairingCode(text: String): Boolean = LinkQr.parse(text) != null
