package net.rizkiepratama.hako.engine.tether

data class PtpInitializationStep(
    val opcode: Int,
    val params: List<Int> = emptyList(),
    val expectsData: Boolean
)

fun sonyInitializationSteps(): List<PtpInitializationStep> = listOf(
    PtpInitializationStep(PtpConstants.OC_GET_DEVICE_INFO, expectsData = true),
    PtpInitializationStep(PtpConstants.OC_OPEN_SESSION, params = listOf(1), expectsData = false),
    PtpInitializationStep(PtpConstants.OC_SONY_SDIO_CONNECT, params = listOf(1, 0, 0), expectsData = true),
    PtpInitializationStep(PtpConstants.OC_SONY_SDIO_CONNECT, params = listOf(2, 0, 0), expectsData = true),
    PtpInitializationStep(PtpConstants.OC_SONY_SDIO_GET_EXT_DEVICE_INFO, params = listOf(0xC8), expectsData = true),
    PtpInitializationStep(PtpConstants.OC_SONY_SDIO_CONNECT, params = listOf(3, 0, 0), expectsData = true)
)
