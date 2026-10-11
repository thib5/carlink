package com.carlink.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

private data class CamInfo(
    val id: String,
    val label: String,
)

/** Every camera Android exposes to a normal app (GM may expose none — they're behind EVS). */
private fun listCameras(ctx: Context): List<CamInfo> =
    runCatching {
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return@runCatching emptyList()
        cm.cameraIdList.map { id ->
            val facing =
                when (cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "arrière"
                    CameraCharacteristics.LENS_FACING_FRONT -> "intérieure"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "externe"
                    else -> "?"
                }
            CamInfo(id, "Caméra $id ($facing)")
        }
    }.getOrDefault(emptyList())

@Composable
fun CameraApp() {
    val ctx = LocalContext.current
    val cameras = remember { listCameras(ctx) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    if (cameras.isEmpty()) {
        AppMessage(
            Icons.Default.NoPhotography,
            "Caméras de l'auto non accessibles",
            "GM réserve les caméras à ses propres apps : Android n'en montre aucune aux apps comme Carlink.",
        )
        return
    }
    if (!granted) {
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.CAMERA) }
        AppMessage(Icons.Default.Videocam, "Permission caméra", "Accepte la demande à l'écran pour voir les caméras.")
        return
    }

    var index by remember { mutableIntStateOf(0) }
    val cam = cameras[index % cameras.size]
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var error by remember { mutableStateOf<String?>(null) }
    val tState =
        rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 8f)
            offset = if (scale <= 1f) Offset.Zero else offset + panChange
        }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .transformable(tState),
        ) {
            CameraPreview(
                cameraId = cam.id,
                reportError = { error = it },
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Text(
                "${cam.label}  ·  ×${"%.1f".format(scale)}",
                color = Color.White,
                fontSize = 17.sp,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .appTile(RoundedCornerShape(percent = 50))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            error?.let {
                Text(
                    it,
                    color = AppColors.Pink,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            AppRoundButton(Icons.Default.ZoomOut, "Dézoomer", {
                scale = (scale / 1.5f).coerceAtLeast(1f)
                if (scale <= 1f) offset = Offset.Zero
            })
            AppRoundButton(Icons.Default.ZoomIn, "Zoomer", { scale = (scale * 1.5f).coerceAtMost(8f) })
            if (cameras.size > 1) {
                AppRoundButton(Icons.Default.Cameraswitch, "Changer de caméra", {
                    index++
                    scale = 1f
                    offset = Offset.Zero
                    error = null
                })
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun CameraPreview(
    cameraId: String,
    reportError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var texture by remember(cameraId) { mutableStateOf<SurfaceTexture?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { c ->
            TextureView(c).apply {
                surfaceTextureListener =
                    object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            texture = st
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            texture = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
            }
        },
    )

    DisposableEffect(cameraId, texture) {
        val st = texture
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        if (st != null) {
            val cm = ctx.getSystemService(CameraManager::class.java)
            try {
                cm.openCamera(
                    cameraId,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(d: CameraDevice) {
                            device = d
                            val surface = Surface(st)
                            d.createCaptureSession(
                                listOf(surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(s: CameraCaptureSession) {
                                        session = s
                                        runCatching {
                                            val req = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
                                            s.setRepeatingRequest(req.build(), null, handler)
                                        }.onFailure { reportError("Image indisponible : ${it.message}") }
                                    }

                                    override fun onConfigureFailed(s: CameraCaptureSession) {
                                        reportError("La caméra a refusé de démarrer.")
                                    }
                                },
                                handler,
                            )
                        }

                        override fun onDisconnected(d: CameraDevice) {
                            d.close()
                        }

                        override fun onError(
                            d: CameraDevice,
                            code: Int,
                        ) {
                            d.close()
                            reportError("Caméra occupée ou bloquée (code $code).")
                        }
                    },
                    handler,
                )
            } catch (e: Exception) {
                reportError("Accès refusé : ${e.message}")
            }
        }
        onDispose {
            runCatching { session?.close() }
            runCatching { device?.close() }
        }
    }
}
