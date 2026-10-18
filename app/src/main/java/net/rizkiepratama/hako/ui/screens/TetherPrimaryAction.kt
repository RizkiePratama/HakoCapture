package net.rizkiepratama.hako.ui.screens

enum class TetherPrimaryAction {
    REQUEST_PERMISSIONS,
    CONNECT,
    SYNC,
    UNAVAILABLE
}

fun tetherPrimaryAction(
    permissionsGranted: Boolean,
    isConnected: Boolean,
    hasDevice: Boolean
): TetherPrimaryAction = when {
    !permissionsGranted -> TetherPrimaryAction.REQUEST_PERMISSIONS
    isConnected -> TetherPrimaryAction.SYNC
    hasDevice -> TetherPrimaryAction.CONNECT
    else -> TetherPrimaryAction.UNAVAILABLE
}
