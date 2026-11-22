package com.ahmedmahmoud.cinemonitor.camera

import android.graphics.RectF

object TouchAfCoordinateMapper {

    data class CanonPoint(val x: Int, val y: Int)

    fun mapScreenToCanon(
        touchX: Float,
        touchY: Float,
        viewWidth: Float,
        viewHeight: Float,
        videoAspectRatio: Float = 16f / 9f,
        evfWidth: Int = 6000,
        evfHeight: Int = 3375,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false
    ): CanonPoint? {
        if (viewWidth <= 0f || viewHeight <= 0f || videoAspectRatio <= 0f) return null

        val viewAspectRatio = viewWidth / viewHeight
        val activeVideoRect = RectF()

        if (viewAspectRatio > videoAspectRatio) {
            val actualVideoWidth = viewHeight * videoAspectRatio
            val leftOffset = (viewWidth - actualVideoWidth) / 2f
            activeVideoRect.set(leftOffset, 0f, leftOffset + actualVideoWidth, viewHeight)
        } else {
            val actualVideoHeight = viewWidth / videoAspectRatio
            val topOffset = (viewHeight - actualVideoHeight) / 2f
            activeVideoRect.set(0f, topOffset, viewWidth, topOffset + actualVideoHeight)
        }

        if (activeVideoRect.width() <= 0f || activeVideoRect.height() <= 0f) return null
        if (!activeVideoRect.contains(touchX, touchY)) return null

        var normalizedX = ((touchX - activeVideoRect.left) / activeVideoRect.width()).coerceIn(0f, 1f)
        var normalizedY = ((touchY - activeVideoRect.top) / activeVideoRect.height()).coerceIn(0f, 1f)

        if (flipHorizontal) normalizedX = 1f - normalizedX
        if (flipVertical) normalizedY = 1f - normalizedY

        val maxX = (evfWidth - 1).coerceAtLeast(0)
        val maxY = (evfHeight - 1).coerceAtLeast(0)
        val canonX = (normalizedX * maxX).toInt().coerceIn(0, maxX)
        val canonY = (normalizedY * maxY).toInt().coerceIn(0, maxY)

        return CanonPoint(canonX, canonY)
    }
}
