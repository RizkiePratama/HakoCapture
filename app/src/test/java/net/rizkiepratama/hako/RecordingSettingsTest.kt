package net.rizkiepratama.hako

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import net.rizkiepratama.hako.engine.RecordingAudioSource
import net.rizkiepratama.hako.engine.RecordingSettings
import net.rizkiepratama.hako.engine.audioCaptureProfile
import net.rizkiepratama.hako.engine.preferredAudioDeviceTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSettingsTest {
    @Test fun recordingsUseThePhoneMicrophoneByDefault() {
        val settings = RecordingSettings()

        assertEquals(RecordingAudioSource.PHONE_MICROPHONE, settings.audioSource)
        assertTrue(settings.recordsAudio)
    }

    @Test fun phoneMicrophoneOnlyTargetsBuiltInAudioInputs() {
        assertEquals(
            listOf(AudioDeviceInfo.TYPE_BUILTIN_MIC),
            preferredAudioDeviceTypes(RecordingAudioSource.PHONE_MICROPHONE)
        )
        assertEquals(AudioFormat.CHANNEL_IN_MONO, audioCaptureProfile(RecordingAudioSource.PHONE_MICROPHONE)?.channelConfig)
        assertEquals(1, audioCaptureProfile(RecordingAudioSource.PHONE_MICROPHONE)?.channelCount)
    }

    @Test fun hdmiAudioTargetsUsbCaptureInputs() {
        assertEquals(
            listOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET),
            preferredAudioDeviceTypes(RecordingAudioSource.HDMI_USB)
        )
        assertEquals(AudioFormat.CHANNEL_IN_STEREO, audioCaptureProfile(RecordingAudioSource.HDMI_USB)?.channelConfig)
        assertEquals(2, audioCaptureProfile(RecordingAudioSource.HDMI_USB)?.channelCount)
    }

    @Test fun noAudioSkipsAudioRecordingAndDeviceRouting() {
        val settings = RecordingSettings(audioSource = RecordingAudioSource.NONE)

        assertFalse(settings.recordsAudio)
        assertTrue(preferredAudioDeviceTypes(settings.audioSource).isEmpty())
        assertEquals(null, audioCaptureProfile(settings.audioSource))
    }
}
