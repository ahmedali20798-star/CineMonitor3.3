package com.ahmedmahmoud.cinemonitor.camera

data class CameraLiveState(
    val iso: String = "---",
    val shutterSpeed: String = "---",
    val aperture: String = "---",
    val whiteBalanceKelvin: Int = 5600,
    val batteryPercent: Int = -1,
    val isAcPowered: Boolean = false,
    val isRecording: Boolean = false,
    val availableShots: Int = 0,
    val supportedApertures: List<Int> = emptyList(),
    val supportedShutterSpeeds: List<Int> = emptyList(),
    val supportedIsos: List<Int> = emptyList(),
    val evfGridWidth: Int = 6000,
    val evfGridHeight: Int = 3375,
    val afFrameNormX: Float = 0.5f,
    val afFrameNormY: Float = 0.5f,
    val liveViewFps: Int = 0
)
