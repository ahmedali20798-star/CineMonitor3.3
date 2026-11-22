package com.ahmedmahmoud.cinemonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun CinemaGuidesOverlay(
    frameGuide: FrameGuideRatio,
    showRuleOfThirds: Boolean,
    showCenterCrosshair: Boolean,
    videoAspectRatio: Float,
    anamorphicFactor: Float,
    modifier: Modifier = Modifier
) {
    if (frameGuide == FrameGuideRatio.OFF && !showRuleOfThirds && !showCenterCrosshair) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val baseAspect = videoAspectRatio.coerceAtLeast(0.1f) * anamorphicFactor
        val screenAspect = size.width / size.height.coerceAtLeast(1f)

        val videoLeft: Float
        val videoTop: Float
        val videoWidth: Float
        val videoHeight: Float

        if (screenAspect > baseAspect) {
            videoHeight = size.height
            videoWidth = videoHeight * baseAspect
            videoLeft = (size.width - videoWidth) / 2f
            videoTop = 0f
        } else {
            videoWidth = size.width
            videoHeight = videoWidth / baseAspect
            videoLeft = 0f
            videoTop = (size.height - videoHeight) / 2f
        }

        frameGuide.ratio?.let { targetRatio ->
            val currentVideoAspect = videoWidth / videoHeight
            val guideW: Float
            val guideH: Float
            if (currentVideoAspect > targetRatio) {
                guideH = videoHeight
                guideW = guideH * targetRatio
            } else {
                guideW = videoWidth
                guideH = guideW / targetRatio
            }
            val guideLeft = videoLeft + (videoWidth - guideW) / 2f
            val guideTop = videoTop + (videoHeight - guideH) / 2f

            val matteColor = Color.Black.copy(alpha = 0.55f)
            if (guideTop > videoTop) {
                drawRect(matteColor, Offset(videoLeft, videoTop), Size(videoWidth, guideTop - videoTop))
                drawRect(matteColor, Offset(videoLeft, guideTop + guideH), Size(videoWidth, (videoTop + videoHeight) - (guideTop + guideH)))
            }
            if (guideLeft > videoLeft) {
                drawRect(matteColor, Offset(videoLeft, guideTop), Size(guideLeft - videoLeft, guideH))
                drawRect(matteColor, Offset(guideLeft + guideW, guideTop), Size((videoLeft + videoWidth) - (guideLeft + guideW), guideH))
            }

            drawRect(
                color = Color(0xFFD4AF37).copy(alpha = 0.85f),
                topLeft = Offset(guideLeft, guideTop),
                size = Size(guideW, guideH),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        if (showRuleOfThirds) {
            val gridColor = Color.White.copy(alpha = 0.35f)
            val stroke = 1.dp.toPx()
            for (i in 1..2) {
                val x = videoLeft + (videoWidth * i / 3f)
                val y = videoTop + (videoHeight * i / 3f)
                drawLine(gridColor, Offset(x, videoTop), Offset(x, videoTop + videoHeight), stroke)
                drawLine(gridColor, Offset(videoLeft, y), Offset(videoLeft + videoWidth, y), stroke)
            }
        }

        if (showCenterCrosshair) {
            val cx = videoLeft + videoWidth / 2f
            val cy = videoTop + videoHeight / 2f
            val arm = 12.dp.toPx()
            val crossColor = Color(0xFFD4AF37)
            val stroke = 1.5.dp.toPx()
            drawLine(crossColor, Offset(cx - arm, cy), Offset(cx + arm, cy), stroke)
            drawLine(crossColor, Offset(cx, cy - arm), Offset(cx, cy + arm), stroke)
        }
    }
}
