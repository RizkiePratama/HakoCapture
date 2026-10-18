package net.rizkiepratama.hako.engine.tether

sealed interface SonyTetherEventAction {
    data class DownloadObject(val handle: Int) : SonyTetherEventAction
    data object Ignore : SonyTetherEventAction
}

private const val SONY_IN_MEMORY_OBJECT_HANDLE = 0xFFFFC001.toInt()

fun shouldDownloadSonyObject(handle: Int, downloadedHandles: Set<Int>): Boolean =
    handle == SONY_IN_MEMORY_OBJECT_HANDLE || handle !in downloadedHandles

fun actionForSonyTetherEvent(code: Int, params: List<Int>): SonyTetherEventAction = when (code) {
    PtpConstants.EC_OBJECT_ADDED -> {
        val handle = params.firstOrNull()
        if (handle != null && handle != 0) SonyTetherEventAction.DownloadObject(handle)
        else SonyTetherEventAction.Ignore
    }

    PtpConstants.EC_SONY_OBJECT_ADDED1,
    PtpConstants.EC_SONY_OBJECT_ADDED2 -> {
        val handle = params.firstOrNull()
        if (handle != null && handle != 0) SonyTetherEventAction.DownloadObject(handle)
        else SonyTetherEventAction.Ignore
    }

    // Sony emits this after the current in-memory object changes or is consumed.
    // It does not announce an additional RAW/JPEG file.
    PtpConstants.EC_SONY_OBJECT_INFO_CHANGED -> SonyTetherEventAction.Ignore

    // The parameter of this event is a device-property code (for example 0xD21D),
    // not a PTP object handle.
    PtpConstants.EC_SONY_PROP_CHANGED -> SonyTetherEventAction.Ignore

    else -> SonyTetherEventAction.Ignore
}
