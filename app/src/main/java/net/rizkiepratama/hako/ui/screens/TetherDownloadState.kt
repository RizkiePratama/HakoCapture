package net.rizkiepratama.hako.ui.screens

fun isActiveTetherDownload(downloadedBytes: Long, totalBytes: Long): Boolean =
    totalBytes > 0 && downloadedBytes >= 0
