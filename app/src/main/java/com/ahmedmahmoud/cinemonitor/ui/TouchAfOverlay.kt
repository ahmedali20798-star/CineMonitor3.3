package com.ahmedmahmoud.cinemonitor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ahmedmahmoud.cinemonitor.camera.CameraLiveState
import com.ahmedmahmoud.cinemonitor.camera.CanonPtpController
import com.ahmedmahmoud.cinemonitor.camera.TouchAfCoordinateMapper
import kotlinx.coroutines.delay

@Composable
fun TouchAfOverlay(
    ptpController: CanonPtpController,
    cameraState: CameraLiveState,
    isScreenLocked: Boolean,
    videoAspectRatio: Float,
    anamorphicFactor: Float,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var tapOffset by remember { mutableStateOf<Offset?>(null) }
    var isFocusing by remember { mutableStateOf(false) }

    val boxScale by animateFloatAsState(
        targetValue = if (isFocusing) 1.0f else 1.25f,
        animationSpec = tween(durationMillis = 180),
        label = "afBoxScale"
    )

    LaunchedEffect(tapOffset) {
        if (tapOffset != null) {
            isFocusing = true
            delay(350)
            isFocusing = false
            delay(1800)
            tapOffset = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .pointerInput(
                isScreenLocked,
                cameraState.evfGridWidth,
                cameraState.evfGridHeight,
                videoAspectRatio,
                anamorphicFactor,
                flipHorizontal,
                flipVertical
            ) {
                detectTapGestures { offset ->
                    if (isScreenLocked || viewSize.width <= 0 || viewSize.height <= 0) return@detectTapGestures

                    val canonPoint = TouchAfCoordinateMapper.mapScreenToCanon(
                        touchX = offset.x,
                        touchY = offset.y,
                        viewWidth = viewSize.width.toFloat(),
                        viewHeight = viewSize.height.toFloat(),
                        videoAspectRatio = videoAspectRatio * anamorphicFactor,
                        evfWidth = cameraState.evfGridWidth,
                        evfHeight = cameraState.evfGridHeight,
                        flipHorizontal = flipHorizontal,
                        flipVertical = flipVertical
                    )

                    if (canonPoint != null) {
                        tapOffset = offset
                        ptpController.triggerTouchAf(
                            canonX = canonPoint.x,
                            canonY = canonPoint.y,
                            scope = scope
                        )
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val currentTap = tapOffset ?: return@Canvas
            val baseSize = 68.dp.toPx() * boxScale
            val half = baseSize / 2f
            val cornerLen = 14.dp.toPx()
            val strokeWidth = 2.dp.toPx()
            val retColor = if (isFocusing) Color(0xFFFFD600) else Color(0xFF00E676)

            val left = currentTap.x - half
            val top = currentTap.y - half
            val right = currentTap.x + half
            val bottom = currentTap.y + half

            drawLine(retColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)
            drawLine(retColor, Offset(left, top), Offset(left, top + cornerLen), strokeWidth)
            drawLine(retColor, Offset(right, top), Offset(right - cornerLen, top), strokeWidth)
            drawLine(retColor, Offset(right, top), Offset(right, top + cornerLen), strokeWidth)
            drawLine(retColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeWidth)
            drawLine(retColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeWidth)
            drawLine(retColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeWidth)
            drawLine(retColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeWidth)

            drawRect(
                color = retColor.copy(alpha = 0.7f),
                topLeft = Offset(currentTap.x - 2.dp.toPx(), currentTap.y - 2.dp.toPx()),
                size = Size(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
