package com.ahmedmahmoud.cinemonitor.camera

object CanonExposureCodes {

    val isoMap: Map<String, Int> = linkedMapOf(
        "Auto" to 0x00,
        "100" to 0x48, "125" to 0x4B, "160" to 0x4D,
        "200" to 0x50, "250" to 0x53, "320" to 0x55,
        "400" to 0x58, "500" to 0x5B, "640" to 0x5D,
        "800" to 0x60, "1000" to 0x63, "1250" to 0x65,
        "1600" to 0x68, "2000" to 0x6B, "2500" to 0x6D,
        "3200" to 0x70, "4000" to 0x73, "5000" to 0x75,
        "6400" to 0x78, "8000" to 0x7B, "10000" to 0x7D,
        "12800" to 0x80, "25600" to 0x88, "51200" to 0x90
    )

    val shutterMap: Map<String, Int> = linkedMapOf(
        "1/25" to 0x5D, "1/30" to 0x60, "1/40" to 0x63,
        "1/50" to 0x65, "1/60" to 0x68, "1/80" to 0x6B,
        "1/100" to 0x6D, "1/125" to 0x70, "1/160" to 0x73,
        "1/200" to 0x75, "1/250" to 0x78, "1/320" to 0x7B,
        "1/400" to 0x7D, "1/500" to 0x80, "1/640" to 0x83,
        "1/800" to 0x85, "1/1000" to 0x88, "1/1250" to 0x8B,
        "1/1600" to 0x8D, "1/2000" to 0x90, "1/2500" to 0x93,
        "1/3200" to 0x95, "1/4000" to 0x98, "1/8000" to 0xA0
    )

    val apertureMap: Map<String, Int> = linkedMapOf(
        "f/1.2" to 0x0D, "f/1.4" to 0x10, "f/1.6" to 0x13,
        "f/1.8" to 0x15, "f/2.0" to 0x18, "f/2.2" to 0x1B,
        "f/2.5" to 0x1D, "f/2.8" to 0x20, "f/3.2" to 0x23,
        "f/3.5" to 0x25, "f/4.0" to 0x28, "f/4.5" to 0x2B,
        "f/5.0" to 0x2D, "f/5.6" to 0x30, "f/6.3" to 0x33,
        "f/7.1" to 0x35, "f/8.0" to 0x38, "f/9.0" to 0x3B,
        "f/10" to 0x3D, "f/11" to 0x40, "f/13" to 0x43,
        "f/14" to 0x45, "f/16" to 0x48, "f/22" to 0x50
    )

    val kelvinPresets: List<Int> = listOf(
        2800, 3200, 3600, 4000, 4300, 4600, 5000, 5200, 5600, 6000, 6500, 7500
    )

    private val reverseIso = isoMap.entries.associate { (k, v) -> v to k }
    private val reverseShutter = shutterMap.entries.associate { (k, v) -> v to k }
    private val reverseAperture = apertureMap.entries.associate { (k, v) -> v to k }

    fun getIsoLabel(code: Int): String = reverseIso[code] ?: "ISO ?"
    fun getShutterLabel(code: Int): String = reverseShutter[code] ?: "1/?"
    fun getApertureLabel(code: Int): String = reverseAperture[code] ?: "f/?"
}
