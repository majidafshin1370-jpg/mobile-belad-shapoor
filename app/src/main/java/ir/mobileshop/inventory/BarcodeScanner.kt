package ir.mobileshop.inventory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BarcodeScannerScreen(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var permissionAsked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionAsked = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission && !permissionAsked) {
            permissionAsked = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF101114))) {
        if (hasPermission) {
            val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
            val consumed = remember { AtomicBoolean(false) }
            DisposableEffect(lifecycleOwner, previewView) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val executor = Executors.newSingleThreadExecutor()
                var provider: ProcessCameraProvider? = null
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    val scanner = BarcodeScanning.getClient(
                        com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128, Barcode.FORMAT_EAN_13)
                            .build()
                    )
                    analysis.setAnalyzer(executor) { proxy -> processBarcodeFrame(proxy, scanner, consumed, context, onResult) }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    } catch (_: Exception) {
                        analysis.clearAnalyzer()
                    }
                }, ContextCompat.getMainExecutor(context))
                onDispose {
                    try { provider?.unbindAll() } catch (_: Exception) { }
                    executor.shutdown()
                }
            }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 280.dp, height = 170.dp).border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(18.dp)))
            }
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xCC101114)).padding(top = 30.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت", tint = Color.White)
                    }
                    Text("اسکن بارکد / IMEI", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("بارکد را داخل کادر قرار دهید", Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp), color = Color.White, fontSize = 14.sp)
        } else {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (permissionAsked) "برای اسکن، دسترسی دوربین لازم است." else "در حال درخواست دسترسی دوربین…", color = Color.White, fontSize = 16.sp)
                Button(onClick = onClose) { Text("بازگشت") }
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت", tint = Color.White) }
                Text("اسکن بارکد / IMEI", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun processBarcodeFrame(
    proxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    consumed: AtomicBoolean,
    context: Context,
    onResult: (String) -> Unit
) {
    if (consumed.get()) { proxy.close(); return }
    val mediaImage = proxy.image
    if (mediaImage == null) { proxy.close(); return }
    val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstNotNullOfOrNull { it.rawValue }
            if (raw != null && consumed.compareAndSet(false, true)) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                try {
                    if (vibrator?.hasVibrator() == true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                        else @Suppress("DEPRECATION") vibrator.vibrate(100)
                    }
                } catch (_: Exception) { }
                val digits = raw.filter(Char::isDigit).take(15)
                android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(digits) }
            }
        }
        .addOnCompleteListener { proxy.close() }
}
