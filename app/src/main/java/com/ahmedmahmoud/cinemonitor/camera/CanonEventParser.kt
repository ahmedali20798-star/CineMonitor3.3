package com.ahmedmahmoud.cinemonitor.camera

import java.nio.ByteBuffer
import java.nio.ByteOrder

object CanonEventParser {

    private const val EVENT_END_OF_LIST = 0x00000000
    private const val EVENT_PROP_VALUE_CHANGED = 0x0000C189
    private const val EVENT_PROP_DESC_CHANGED = 0x0000C18A
    private const val EVENT_EVF_AF_FRAME = 0x0000C191
    private const val EVENT_EVF_COORD_SYSTEM = 0x0000C1A2

    private const val PROP_BATTERY_LEVEL = 0xD111
    private const val PROP_AVAILABLE_SHOTS = 0xD11B

    fun parseEventPacket(rawBytes: ByteArray, currentState: CameraLiveState): CameraLiveState {
        if (rawBytes.size <= 12) return currentState

        val buffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
        val containerLength = buffer.int
        val containerType = buffer.short.toInt() and 0xFFFF

        if (containerType != 0x0002) return currentState

        var updatedState = currentState
        var offset = 12
        val limit = minOf(containerLength, rawBytes.size)

        while (offset + 8 <= limit) {
            val recordSize = buffer.getInt(offset)
            val eventType = buffer.getInt(offset + 4)

            if (eventType == EVENT_END_OF_LIST || recordSize < 8) break
            if (offset + recordSize > limit) break

            when (eventType) {
                EVENT_PROP_VALUE_CHANGED -> {
                    if (recordSize >= 16) {
                        val propCode = buffer.getInt(offset + 8)
                        val propValue = buffer.getInt(offset + 12)
                        updatedState = applyPropertyChange(updatedState, propCode, propValue)
                    }
                }
                EVENT_PROP_DESC_CHANGED -> {
                    if (recordSize >= 20) {
                        val propCode = buffer.getInt(offset + 8)
                        val numValues = buffer.getInt(offset + 16)
                        if (numValues in 0..256) {
                            val values = extractAllowedValues(buffer, offset + 20, numValues, offset + recordSize)
                            updatedState = applyAllowedValuesChange(updatedState, propCode, values)
                        }
                    }
                }
                EVENT_EVF_COORD_SYSTEM -> {
                    if (recordSize >= 16) {
                        val width = buffer.getInt(offset + 8)
                        val height = buffer.getInt(offset + 12)
                        if (width > 0 && height > 0) {
                            updatedState = updatedState.copy(evfGridWidth = width, evfGridHeight = height)
                        }
                    }
                }
                EVENT_EVF_AF_FRAME -> {
                    if (recordSize >= 20) {
                        val posX = buffer.getInt(offset + 12)
                        val posY = buffer.getInt(offset + 16)
                        val gridW = updatedState.evfGridWidth.coerceAtLeast(1)
                        val gridH = updatedState.evfGridHeight.coerceAtLeast(1)
                        val normX = (posX.toFloat() / gridW).coerceIn(0f, 1f)
                        val normY = (posY.toFloat() / gridH).coerceIn(0f, 1f)
                        updatedState = updatedState.copy(afFrameNormX = normX, afFrameNormY = normY)
                    }
                }
            }

            offset += recordSize
        }

        return updatedState
    }

    private fun applyPropertyChange(
        state: CameraLiveState,
        propCode: Int,
        value: Int
    ): CameraLiveState {
        return when (propCode) {
            CanonPtpController.PROP_ISO -> state.copy(iso = CanonExposureCodes.getIsoLabel(value))
            CanonPtpController.PROP_SHUTTER_SPEED -> state.copy(shutterSpeed = CanonExposureCodes.getShutterLabel(value))
            CanonPtpController.PROP_APERTURE -> state.copy(aperture = CanonExposureCodes.getApertureLabel(value))
            CanonPtpController.PROP_COLOR_TEMP -> if (value in 2000..10000) state.copy(whiteBalanceKelvin = value) else state
            CanonPtpController.PROP_RECORDING_STATE -> state.copy(isRecording = (value == 4))
            PROP_BATTERY_LEVEL -> parseBatteryLevel(state, value)
            PROP_AVAILABLE_SHOTS -> state.copy(availableShots = value)
            else -> state
        }
    }

    private fun parseBatteryLevel(state: CameraLiveState, rawBattery: Int): CameraLiveState {
        if (rawBattery == -1 || rawBattery == 0x04) {
            return state.copy(batteryPercent = 100, isAcPowered = true)
        }
        val percent = when (rawBattery) {
            0 -> 10
            1 -> 35
            2 -> 70
            3 -> 100
            in 5..100 -> rawBattery
            else -> state.batteryPercent
        }
        return state.copy(batteryPercent = percent, isAcPowered = false)
    }

    private fun extractAllowedValues(
        buffer: ByteBuffer,
        startOffset: Int,
        count: Int,
        recordEnd: Int
    ): List<Int> {
        val list = ArrayList<Int>(count)
        var pos = startOffset
        for (i in 0 until count) {
            if (pos + 4 > recordEnd) break
            list.add(buffer.getInt(pos))
            pos += 4
        }
        return list
    }

    private fun applyAllowedValuesChange(
        state: CameraLiveState,
        propCode: Int,
        values: List<Int>
    ): CameraLiveState {
        return when (propCode) {
            CanonPtpController.PROP_APERTURE -> state.copy(supportedApertures = values)
            CanonPtpController.PROP_SHUTTER_SPEED -> state.copy(supportedShutterSpeeds = values)
            CanonPtpController.PROP_ISO -> state.copy(supportedIsos = values)
            else -> state
        }
    }
}
