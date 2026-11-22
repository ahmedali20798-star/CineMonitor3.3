package com.ahmedmahmoud.cinemonitor.ui

import android.net.Uri
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ahmedmahmoud.cinemonitor.camera.CameraLiveState
import com.ahmedmahmoud.cinemonitor.camera.CanonExposureCodes
import com.ahmedmahmoud.cinemonitor.camera.CanonPtpController
import com.ahmedmahmoud.cinemonitor.gl.CinemaRenderer
import com.ahmedmahmoud.cinemonitor.gl.CubeLutParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val HudSurface = Color(0xCC111111)
private val HudBorder = Color(0x33FFFFFF)
private val AccentGold = Color(0xFFD4AF37)
private val RecordRed = Color(0xFFE53935)
private val SafeGreen = Color(0xFF00E676)
private val WarnAmber = Color(0xFFFFB300)

@Composable
fun CinemaMonitorScreen(
    ptpController: CanonPtpController,
    renderer: CinemaRenderer,
    isCameraConnected: Boolean,
    isVideoConnected: Boolean,
    videoAspectRatio: Float,
    onManualRescanUsb: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cameraState by ptpController.cameraState.collectAsState()

    var toolsState by remember { mutableStateOf(MonitorToolsState()) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, glSurfaceViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceViewRef?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(cameraState.isRecording) {
        if (cameraState.isRecording) {
            recordingSeconds = 0
            while (cameraState.isRecording) {
                delay(1000)
                recordingSeconds++
            }
        } else {
            recordingSeconds = 0
        }
    }

    val lutFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                        val parsedLut = CubeLutParser.parse(stream)
                        withContext(Dispatchers.Main) {
                            val currentIntensity = toolsState.lutIntensity
                            glSurfaceViewRef?.queueEvent {
                                renderer.updateLutTexture(parsedLut)
                                renderer.setLutIntensity(currentIntensity)
                            }
                            toolsState = toolsState.copy(
                                isLutEnabled = true,
                                activeLutName = parsedLut.title
                            )
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    glSurfaceViewRef = this
                }
            }
        )

        CinemaGuidesOverlay(
            frameGuide = toolsState.frameGuide,
            showRuleOfThirds = toolsState.showRuleOfThirds,
            showCenterCrosshair = toolsState.showCenterCrosshair,
            videoAspectRatio = videoAspectRatio,
            anamorphicFactor = toolsState.anamorphic.factor,
            modifier = Modifier.fillMaxSize()
        )

        TouchAfOverlay(
            ptpController = ptpController,
            cameraState = cameraState,
            isScreenLocked = toolsState.isScreenLocked,
            videoAspectRatio = videoAspectRatio,
            anamorphicFactor = toolsState.anamorphic.factor,
            flipHorizontal = toolsState.flipHorizontal,
            flipVertical = toolsState.flipVertical,
            modifier = Modifier.fillMaxSize()
        )

        TopTelemetryBar(
            cameraState = cameraState,
            isConnected = isCameraConnected,
            isVideoConnected = isVideoConnected,
            recordingSeconds = recordingSeconds,
            isScreenLocked = toolsState.isScreenLocked,
            isHudHidden = toolsState.isHudHidden,
            onManualRescanUsb = onManualRescanUsb,
            onWbClick = {
                if (!toolsState.isScreenLocked) {
                    toolsState = toolsState.copy(
                        activeDrawer = if (toolsState.activeDrawer == ActiveControlDrawer.WB_KELVIN)
                            ActiveControlDrawer.NONE else ActiveControlDrawer.WB_KELVIN
                    )
                }
            },
            onToggleLock = {
                toolsState = toolsState.copy(
                    isScreenLocked = !toolsState.isScreenLocked,
                    activeDrawer = ActiveControlDrawer.NONE
                )
            },
            onToggleHud = {
                toolsState = toolsState.copy(
                    isHudHidden = !toolsState.isHudHidden,
                    activeDrawer = ActiveControlDrawer.NONE
                )
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (!toolsState.isHudHidden) {
            RecordTriggerButton(
                isRecording = cameraState.isRecording,
                enabled = isCameraConnected && !toolsState.isScreenLocked,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        ptpController.toggleVideoRecording(!cameraState.isRecording)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
            )
        }

        AnimatedVisibility(
            visible = !toolsState.isHudHidden,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!toolsState.isScreenLocked && toolsState.activeDrawer != ActiveControlDrawer.NONE) {
                    ControlValueDrawer(
                        drawer = toolsState.activeDrawer,
                        cameraState = cameraState,
                        toolsState = toolsState,
                        onSelectShutter = { hexCode ->
                            scope.launch(Dispatchers.IO) {
                                ptpController.setCameraProperty(CanonPtpController.PROP_SHUTTER_SPEED, hexCode)
                            }
                        },
                        onSelectAperture = { hexCode ->
                            scope.launch(Dispatchers.IO) {
                                ptpController.setCameraProperty(CanonPtpController.PROP_APERTURE, hexCode)
                            }
                        },
                        onSelectIso = { hexCode ->
                            scope.launch(Dispatchers.IO) {
                                ptpController.setCameraProperty(CanonPtpController.PROP_ISO, hexCode)
                            }
                        },
                        onSelectKelvin = { kelvin ->
                            scope.launch(Dispatchers.IO) {
                                ptpController.setWhiteBalanceKelvin(kelvin)
                            }
                        },
                        onLutIntensityChange = { newIntensity ->
                            val lutActive = toolsState.isLutEnabled
                            toolsState = toolsState.copy(lutIntensity = newIntensity)
                            if (lutActive) {
                                glSurfaceViewRef?.queueEvent {
                                    renderer.setLutIntensity(newIntensity)
                                }
                            }
                        },
                        onImportLutClick = {
                            lutFilePicker.launch(arrayOf("*/*"))
                        },
                        onUpdateToolsState = { updated ->
                            toolsState = updated
                            glSurfaceViewRef?.queueEvent {
                                renderer.setMonochromeEnabled(updated.isMonochromeEnabled)
                                renderer.setFlip(updated.flipHorizontal, updated.flipVertical)
                                renderer.setAnamorphicDesqueeze(updated.anamorphic.factor)
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(HudSurface)
                            .border(1.dp, HudBorder, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ExposurePill(
                            label = "SHUTTER",
                            value = cameraState.shutterSpeed,
                            isSelected = toolsState.activeDrawer == ActiveControlDrawer.SHUTTER,
                            enabled = !toolsState.isScreenLocked,
                            onClick = {
                                toolsState = toolsState.copy(
                                    activeDrawer = if (toolsState.activeDrawer == ActiveControlDrawer.SHUTTER)
                                        ActiveControlDrawer.NONE else ActiveControlDrawer.SHUTTER
                                )
                            }
                        )
                        ExposurePill(
                            label = "IRIS",
                            value = cameraState.aperture,
                            isSelected = toolsState.activeDrawer == ActiveControlDrawer.APERTURE,
                            enabled = !toolsState.isScreenLocked,
                            onClick = {
                                toolsState = toolsState.copy(
                                    activeDrawer = if (toolsState.activeDrawer == ActiveControlDrawer.APERTURE)
                                        ActiveControlDrawer.NONE else ActiveControlDrawer.APERTURE
                                )
                            }
                        )
                        ExposurePill(
                            label = "ISO",
                            value = cameraState.iso,
                            isSelected = toolsState.activeDrawer == ActiveControlDrawer.ISO,
                            enabled = !toolsState.isScreenLocked,
                            onClick = {
                                toolsState = toolsState.copy(
                                    activeDrawer = if (toolsState.activeDrawer == ActiveControlDrawer.ISO)
                                        ActiveControlDrawer.NONE else ActiveControlDrawer.ISO
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(HudSurface)
                            .border(1.dp, HudBorder, RoundedCornerShape(12.dp))
                            .padding(4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MonitorToolChip(
                            title = "LUT",
                            isActive = toolsState.isLutEnabled,
                            enabled = !toolsState.isScreenLocked,
                            onClick = {
                                val next = !toolsState.isLutEnabled
                                val intensity = toolsState.lutIntensity
                                toolsState = toolsState.copy(
                                    isLutEnabled = next,
                                    activeDrawer = if (next) ActiveControlDrawer.LUT_SETTINGS else ActiveControlDrawer.NONE
                                )
                                glSurfaceViewRef?.queueEvent {
                                    renderer.setLutIntensity(if (next) intensity else 0f)
                                }
                            }
                        )
                        MonitorToolChip(
                            title = "FALSE COLOR",
                            isActive = toolsState.isFalseColorEnabled,
                            enabled = !toolsState.isScreenLocked,
                            onClick = {
                                val next = !toolsState.isFalseColorEnabled
                                toolsState = toolsState.copy(isFalseColorEnabled = next)
                                glSurfaceViewRef?.queueEvent {
                                    renderer.setFalseColorEnabled(next)
                                }
                            }
                        )
                        MonitorToolChip(
                            title = "PEAKING",
                            isActive = toolsState.isPeakingEnabled,
                            enabled = !toolsState.isScreenLocked,
                            onClick = {
                                val next = !toolsState.isPeakingEnabled
                                toolsState = toolsState.copy(isPeakingEnabled = next)
                                glSurfaceViewRef?.queueEvent {
                                    renderer.setPeakingEnabled(next)
                                }
                            }
                        )
                        MonitorToolChip(
                            title = "ZEBRA",
                            isActive = toolsState.isZebraEnabled,
                            enabled = !toolsState.isScreenLocked,
                            onClick = {
                                val next = !toolsState.isZebraEnabled
                                toolsState = toolsState.copy(isZebraEnabled = next)
                                glSurfaceViewRef?.queueEvent {
                                    renderer.setZebraEnabled(next)
                                }
                            }
                        )
                        MonitorToolChip(
                            title = "GUIDES / RIG",
                            isActive = toolsState.activeDrawer == ActiveControlDrawer.GUIDES_AND_RIG ||
                                    toolsState.frameGuide != FrameGuideRatio.OFF ||
                                    toolsState.anamorphic != AnamorphicFactor.X1_0 ||
                                    toolsState.isMonochromeEnabled ||
                                    toolsState.flipHorizontal ||
                                    toolsState.flipVertical,
                            enabled = !toolsState.isScreenLocked,
                            onClick = {
                                toolsState = toolsState.copy(
                                    activeDrawer = if (toolsState.activeDrawer == ActiveControlDrawer.GUIDES_AND_RIG)
                                        ActiveControlDrawer.NONE else ActiveControlDrawer.GUIDES_AND_RIG
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTelemetryBar(
    cameraState: CameraLiveState,
    isConnected: Boolean,
    isVideoConnected: Boolean,
    recordingSeconds: Int,
    isScreenLocked: Boolean,
    isHudHidden: Boolean,
    onManualRescanUsb: () -> Unit,
    onWbClick: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(HudSurface)
                .border(1.dp, HudBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isVideoConnected) SafeGreen else WarnAmber)
                )
                Text(
                    text = if (isVideoConnected) "USB LIVE ${cameraState.liveViewFps}FPS" else "TEST SIGNAL",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { onManualRescanUsb() }
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) SafeGreen else RecordRed)
                )
                Text(
                    text = if (isConnected) "CANON USB" else "TAP TO CONNECT USB",
                    color = if (isConnected) Color.White else AccentGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isHudHidden) {
                Text(
                    text = "${cameraState.whiteBalanceKelvin}K",
                    color = AccentGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { onWbClick() }
                )

                val battText = when {
                    cameraState.isAcPowered -> "AC POWER"
                    cameraState.batteryPercent >= 0 -> "BAT ${cameraState.batteryPercent}%"
                    else -> "BAT --%"
                }
                val battColor = if (cameraState.batteryPercent in 0..20 && !cameraState.isAcPowered) RecordRed else SafeGreen
                Text(
                    text = battText,
                    color = battColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (cameraState.isRecording) {
            val hrs = recordingSeconds / 3600
            val mins = (recordingSeconds % 3600) / 60
            val secs = recordingSeconds % 60
            val timecode = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(RecordRed.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                Text(
                    text = "REC $timecode",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onManualRescanUsb,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HudSurface)
                    .border(1.dp, HudBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Rescan USB",
                    tint = AccentGold,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onToggleHud,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HudSurface)
                    .border(1.dp, HudBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = if (isHudHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle Clean View",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isScreenLocked) AccentGold else HudSurface)
                    .border(1.dp, HudBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Lock Screen",
                    tint = if (isScreenLocked) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ExposurePill(
    label: String,
    value: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentGold else Color.Transparent)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else Color(0xFF9E9E9E)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isSelected) Color.Black else Color.White
        )
    }
}

@Composable
private fun MonitorToolChip(
    title: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) AccentGold.copy(alpha = 0.22f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isActive) AccentGold else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) AccentGold else Color(0xFFE0E0E0)
        )
    }
}

@Composable
private fun ControlValueDrawer(
    drawer: ActiveControlDrawer,
    cameraState: CameraLiveState,
    toolsState: MonitorToolsState,
    onSelectShutter: (Int) -> Unit,
    onSelectAperture: (Int) -> Unit,
    onSelectIso: (Int) -> Unit,
    onSelectKelvin: (Int) -> Unit,
    onLutIntensityChange: (Float) -> Unit,
    onImportLutClick: () -> Unit,
    onUpdateToolsState: (MonitorToolsState) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HudSurface)
            .border(1.dp, HudBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        when (drawer) {
            ActiveControlDrawer.SHUTTER -> {
                val itemsList = CanonExposureCodes.shutterMap.entries.toList()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(itemsList) { (label, hexCode) ->
                        ValueChoiceChip(label = label, isActive = cameraState.shutterSpeed == label) {
                            onSelectShutter(hexCode)
                        }
                    }
                }
            }
            ActiveControlDrawer.APERTURE -> {
                val itemsList = CanonExposureCodes.apertureMap.entries.filter { (_, hexCode) ->
                    cameraState.supportedApertures.isEmpty() || cameraState.supportedApertures.contains(hexCode)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(itemsList) { (label, hexCode) ->
                        ValueChoiceChip(label = label, isActive = cameraState.aperture == label) {
                            onSelectAperture(hexCode)
                        }
                    }
                }
            }
            ActiveControlDrawer.ISO -> {
                val itemsList = CanonExposureCodes.isoMap.entries.toList()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(itemsList) { (label, hexCode) ->
                        ValueChoiceChip(label = label, isActive = cameraState.iso == label) {
                            onSelectIso(hexCode)
                        }
                    }
                }
            }
            ActiveControlDrawer.WB_KELVIN -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CanonExposureCodes.kelvinPresets) { kelvin ->
                        ValueChoiceChip(label = "${kelvin}K", isActive = cameraState.whiteBalanceKelvin == kelvin) {
                            onSelectKelvin(kelvin)
                        }
                    }
                }
            }
            ActiveControlDrawer.LUT_SETTINGS -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportLutClick,
                        border = BorderStroke(1.dp, AccentGold),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import .cube", color = AccentGold, fontSize = 12.sp)
                    }

                    Text(
                        text = toolsState.activeLutName,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${(toolsState.lutIntensity * 100).toInt()}%",
                        color = AccentGold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Slider(
                        value = toolsState.lutIntensity,
                        onValueChange = onLutIntensityChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentGold,
                            activeTrackColor = AccentGold
                        ),
                        modifier = Modifier.width(140.dp)
                    )
                }
            }
            ActiveControlDrawer.GUIDES_AND_RIG -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FrameGuideRatio.entries.forEach { guide ->
                        ValueChoiceChip(
                            label = "FRAME ${guide.label}",
                            isActive = toolsState.frameGuide == guide
                        ) {
                            onUpdateToolsState(toolsState.copy(frameGuide = guide))
                        }
                    }

                    ValueChoiceChip(
                        label = "3x3 GRID",
                        isActive = toolsState.showRuleOfThirds
                    ) {
                        onUpdateToolsState(toolsState.copy(showRuleOfThirds = !toolsState.showRuleOfThirds))
                    }

                    ValueChoiceChip(
                        label = "CENTER +",
                        isActive = toolsState.showCenterCrosshair
                    ) {
                        onUpdateToolsState(toolsState.copy(showCenterCrosshair = !toolsState.showCenterCrosshair))
                    }

                    AnamorphicFactor.entries.forEach { factor ->
                        ValueChoiceChip(
                            label = "DESQ ${factor.label}",
                            isActive = toolsState.anamorphic == factor
                        ) {
                            onUpdateToolsState(toolsState.copy(anamorphic = factor))
                        }
                    }

                    ValueChoiceChip(
                        label = "B&W",
                        isActive = toolsState.isMonochromeEnabled
                    ) {
                        onUpdateToolsState(toolsState.copy(isMonochromeEnabled = !toolsState.isMonochromeEnabled))
                    }

                    ValueChoiceChip(
                        label = "H-FLIP",
                        isActive = toolsState.flipHorizontal
                    ) {
                        onUpdateToolsState(toolsState.copy(flipHorizontal = !toolsState.flipHorizontal))
                    }

                    ValueChoiceChip(
                        label = "180° RIG",
                        isActive = toolsState.flipHorizontal && toolsState.flipVertical
                    ) {
                        val next = !(toolsState.flipHorizontal && toolsState.flipVertical)
                        onUpdateToolsState(toolsState.copy(flipHorizontal = next, flipVertical = next))
                    }
                }
            }
            ActiveControlDrawer.NONE -> Unit
        }
    }
}

@Composable
private fun ValueChoiceChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) AccentGold else Color(0xFF222222))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RecordTriggerButton(
    isRecording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(HudSurface)
            .border(3.dp, if (isRecording) RecordRed else Color.White, CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (isRecording) 24.dp else 46.dp)
                .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                .background(RecordRed)
        )
    }
}
