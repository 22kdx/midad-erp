package com.example.midad.ui.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.midad.ui.theme.LocalMizan
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * مسح الباركود بالكاميرا — يعمل بلا إنترنت (نموذج ML Kit مضمَّن في التطبيق).
 *
 * القاعدة: الكاميرا تُغذّي حقل البحث فقط، ولا تُنشئ أي حركة مخزون أو قيد.
 * عند رفض الإذن يبقى الإدخال اليدوي متاحاً — المسح تحسين لا شرط.
 */
@Composable
fun BarcodeScannerDialog(
    onDetected: (String) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalMizan.current
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var lastCode by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }

    val permission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { ok -> granted = ok }

    LaunchedEffect(granted) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("مسح باركود", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (granted) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(Color.Black, RoundedCornerShape(12.dp)),
                    ) {
                        CameraPreview(
                            onCode = { code ->
                                if (code.isNotBlank() && code != lastCode) {
                                    lastCode = code
                                    onDetected(code)
                                }
                            },
                        )
                    }
                    Text(
                        if (lastCode.isBlank()) "وجّه الكاميرا نحو الباركود…" else "آخر قراءة: $lastCode",
                        fontSize = 11.sp,
                        color = c.text3,
                    )
                } else {
                    Text(
                        "لم يُمنح إذن الكاميرا. يمكنك كتابة الباركود يدوياً — كل شيء يعمل بلا كاميرا.",
                        fontSize = 12.sp,
                        color = c.text2,
                    )
                    OutlinedTextField(
                        manual, { manual = it },
                        label = { Text("الباركود أو رمز الصنف") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                            Text("السماح بالكاميرا", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { if (manual.isNotBlank()) onDetected(manual.trim()) },
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("بحث", fontSize = 11.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("إغلاق") }
        },
    )
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val options = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_ITF,
            )
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(options) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewCtx ->
            val previewView = PreviewView(viewCtx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(viewCtx)
            providerFuture.addListener({
                runCatching {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy: ImageProxy ->
                        val media = proxy.image
                        if (media == null) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { codes ->
                                codes.firstOrNull { !it.rawValue.isNullOrBlank() }
                                    ?.rawValue
                                    ?.let(onCode)
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }
            }, ContextCompat.getMainExecutor(viewCtx))
            previewView
        },
    )
}
