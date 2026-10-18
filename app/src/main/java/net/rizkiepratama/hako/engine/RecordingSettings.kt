package net.rizkiepratama.hako.engine

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.MediaCodecList
import android.media.MediaFormat

enum class RecordingAudioSource(val label: String, val description: String) {
    PHONE_MICROPHONE(
        label = "Phone microphone",
        description = "Record from the phone's built-in microphone."
    ),
    HDMI_USB(
        label = "HDMI / USB audio",
        description = "Record audio exposed by the connected capture device."
    ),
    NONE(
        label = "No audio",
        description = "Save a video-only recording."
    )
}

internal fun preferredAudioDeviceTypes(source: RecordingAudioSource): List<Int> = when (source) {
    RecordingAudioSource.PHONE_MICROPHONE -> listOf(AudioDeviceInfo.TYPE_BUILTIN_MIC)
    RecordingAudioSource.HDMI_USB -> listOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)
    RecordingAudioSource.NONE -> emptyList()
}

internal data class AudioCaptureProfile(val channelConfig: Int, val channelCount: Int)

internal fun audioCaptureProfile(source: RecordingAudioSource): AudioCaptureProfile? = when (source) {
    RecordingAudioSource.PHONE_MICROPHONE -> AudioCaptureProfile(AudioFormat.CHANNEL_IN_MONO, 1)
    RecordingAudioSource.HDMI_USB -> AudioCaptureProfile(AudioFormat.CHANNEL_IN_STEREO, 2)
    RecordingAudioSource.NONE -> null
}

data class RecordingSettings(
    val codec: String = MediaFormat.MIMETYPE_VIDEO_AVC,
    val resolution: Pair<Int, Int> = Pair(1280, 720),
    val fps: Int = 30,
    val bitrate: Int = 8_000_000,
    val audioBitrate: Int = 193_000,
    val audioSource: RecordingAudioSource = RecordingAudioSource.PHONE_MICROPHONE
) {
    val recordsAudio: Boolean
        get() = audioSource != RecordingAudioSource.NONE
}

data class CodecInfo(
    val name: String,
    val mimeType: String,
    val isHardwareAccelerated: Boolean
) {
    val friendlyName: String
        get() = when {
            mimeType == MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264 (AVC)"
            mimeType == MediaFormat.MIMETYPE_VIDEO_HEVC -> "H.265 (HEVC)"
            mimeType == MediaFormat.MIMETYPE_VIDEO_VP8 -> "VP8"
            mimeType == MediaFormat.MIMETYPE_VIDEO_VP9 -> "VP9"
            else -> name
        }
}

fun getAvailableEncoders(): List<CodecInfo> {
    val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
    return codecList.codecInfos
        .filter { it.isEncoder }
        .flatMap { info ->
            info.supportedTypes.filter { type ->
                type.startsWith("video/")
            }.map { type ->
                CodecInfo(info.name, type, info.isHardwareAccelerated)
            }
        }
        .sortedWith(compareByDescending<CodecInfo> { it.isHardwareAccelerated }
            .thenBy { it.name.contains("google", ignoreCase = true) } // Prefer vendor over google (usually sw)
        )
        .distinctBy { it.mimeType } // Only one encoder per format
}

fun getResolutionOptions(maxResolution: Pair<Int, Int>?): List<Pair<Int, Int>> {
    val commonResolutions = listOf(
        Pair(3840, 2160),
        Pair(1920, 1080),
        Pair(1280, 720),
        Pair(854, 480),
        Pair(640, 360)
    )
    val maxW = maxResolution?.first ?: 1920
    val maxH = maxResolution?.second ?: 1080
    return commonResolutions.filter { it.first <= maxW && it.second <= maxH } + listOf(Pair(maxW, maxH))
}

val FpsOptions = listOf(24, 30, 60)
val BitrateOptions = listOf(2_000_000, 4_000_000, 8_000_000, 12_000_000, 20_000_000)
