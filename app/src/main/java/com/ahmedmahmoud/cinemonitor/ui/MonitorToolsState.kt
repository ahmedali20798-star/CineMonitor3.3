package com.ahmedmahmoud.cinemonitor.ui

enum class ActiveControlDrawer {
    NONE, SHUTTER, APERTURE, ISO, WB_KELVIN, LUT_SETTINGS, GUIDES_AND_RIG
}

enum class FrameGuideRatio(val label: String, val ratio: Float?) {
    OFF("OFF", null),
    SCOPE_239("2.39:1", 2.39f),
    FLAT_185("1.85:1", 1.85f),
    REELS_916("9:16", 9f / 16f),
    SOCIAL_45("4:5", 4f / 5f)
}

enum class AnamorphicFactor(val label: String, val factor: Float) {
    X1_0("1.0x", 1.0f),
    X1_33("1.33x", 1.33f),
    X1_5("1.5x", 1.5f),
    X1_8("1.8x", 1.8f),
    X2_0("2.0x", 2.0f)
}

data class MonitorToolsState(
    val isLutEnabled: Boolean = false,
    val activeLutName: String = "CineFilm Warm Rec.709",
    val lutIntensity: Float = 1.0f,
    val isFalseColorEnabled: Boolean = false,
    val isPeakingEnabled: Boolean = false,
    val isZebraEnabled: Boolean = false,
    val isMonochromeEnabled: Boolean = false,
    val frameGuide: FrameGuideRatio = FrameGuideRatio.OFF,
    val showRuleOfThirds: Boolean = false,
    val showCenterCrosshair: Boolean = false,
    val anamorphic: AnamorphicFactor = AnamorphicFactor.X1_0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val isScreenLocked: Boolean = false,
    val isHudHidden: Boolean = false,
    val activeDrawer: ActiveControlDrawer = ActiveControlDrawer.NONE
)
