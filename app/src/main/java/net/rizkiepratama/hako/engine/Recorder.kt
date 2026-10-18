package net.rizkiepratama.hako.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class Recorder(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var inputSurface: Surface? = null
    private val isRecording = AtomicBoolean(false)
    private var drainThread: Thread? = null
    private var audioThread: Thread? = null
    private var muxerStarted = false
    private var startTimeMs = 0L

    private val pendingSamples = ConcurrentLinkedQueue<SampleData>()

    data class SampleData(
        val trackType: Int, // 0 for video, 1 for audio
        val buffer: ByteBuffer,
        val info: MediaCodec.BufferInfo
    )

    fun start(outputFile: File, settings: RecordingSettings) {
        if (isRecording.get()) return
        try {
            setupVideoEncoder(settings)
            setupAudioEncoder(settings)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            isRecording.set(true)
            muxerStarted = false
            videoTrackIndex = -1
            audioTrackIndex = -1
            startTimeMs = System.currentTimeMillis()
            pendingSamples.clear()

            videoEncoder?.start()
            audioEncoder?.start()

            drainThread = Thread({ 
                try {
                    drain() 
                } catch (e: Exception) {
                    Log.e(TAG, "Error in drain thread", e)
                }
            }, "RecorderDrain")
            drainThread?.start()

            if (audioRecord != null && audioEncoder != null) {
                audioThread = Thread({
                    try {
                        recordAudio()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in audio thread", e)
                    }
                }, "RecorderAudio")
                audioThread?.start()
            }

            Log.d(TAG, "Recording started: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            stop()
        }
    }

    private fun setupVideoEncoder(settings: RecordingSettings) {
        val format = MediaFormat.createVideoFormat(settings.codec, settings.resolution.first, settings.resolution.second)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, settings.bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, settings.fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        videoEncoder = MediaCodec.createEncoderByType(settings.codec)
        videoEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = videoEncoder?.createInputSurface()
    }

    @SuppressLint("MissingPermission")
    private fun setupAudioEncoder(settings: RecordingSettings) {
        if (!settings.recordsAudio) return

        val captureProfile = audioCaptureProfile(settings.audioSource) ?: return
        val preferredDeviceTypes = preferredAudioDeviceTypes(settings.audioSource)
        val preferredDevice = audioManager
            .getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type in preferredDeviceTypes }
        if (preferredDevice == null) {
            Log.w(TAG, "No ${settings.audioSource.label} input is available; recording video without audio")
            return
        }

        val sampleRate = 44100
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, captureProfile.channelConfig, audioFormat)
        
        if (minBufferSize <= 0) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                captureProfile.channelConfig,
                audioFormat,
                minBufferSize * 2
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return
            }
            if (audioRecord?.setPreferredDevice(preferredDevice) != true) {
                Log.w(TAG, "Could not route recording to ${settings.audioSource.label}; recording video without audio")
                audioRecord?.release()
                audioRecord = null
                return
            }

            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                captureProfile.channelCount
            )
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_BIT_RATE, settings.audioBitrate)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize * 2)

            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            Log.e(TAG, "Audio setup failed", e)
            audioRecord?.release()
            audioRecord = null
            audioEncoder = null
        }
    }

    private fun drain() {
        val bufferInfo = MediaCodec.BufferInfo()
        var videoEos = false
        var audioEos = false
        
        while (isRecording.get() || !videoEos || (audioEncoder != null && !audioEos)) {
            if (!videoEos) videoEos = drainEncoder(videoEncoder, bufferInfo, true)
            if (audioEncoder != null && !audioEos) audioEos = drainEncoder(audioEncoder, bufferInfo, false)
            
            if (isRecording.get()) {
                checkAndStartMuxer()
                Thread.sleep(10)
            } else if (videoEos && (audioEncoder == null || audioEos)) {
                break
            }
        }
    }

    private fun drainEncoder(encoder: MediaCodec?, bufferInfo: MediaCodec.BufferInfo, isVideo: Boolean): Boolean {
        if (encoder == null) return true
        var eosReached = false
        while (true) {
            val index = try { encoder.dequeueOutputBuffer(bufferInfo, 0) } catch (e: Exception) { -1 }
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) break
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                if (isVideo) videoTrackIndex = muxer?.addTrack(newFormat) ?: -1
                else audioTrackIndex = muxer?.addTrack(newFormat) ?: -1
                checkAndStartMuxer()
            } else if (index >= 0) {
                val data = encoder.getOutputBuffer(index)
                if (data != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0)) {
                    if (muxerStarted) {
                        val track = if (isVideo) videoTrackIndex else audioTrackIndex
                        if (track != -1) muxer?.writeSampleData(track, data, bufferInfo)
                    } else {
                        // Buffer samples until muxer starts
                        val copy = ByteBuffer.allocateDirect(bufferInfo.size)
                        data.position(bufferInfo.offset)
                        data.limit(bufferInfo.offset + bufferInfo.size)
                        copy.put(data)
                        copy.flip()
                        val infoCopy = MediaCodec.BufferInfo().apply { set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags) }
                        pendingSamples.add(SampleData(if (isVideo) 0 else 1, copy, infoCopy))
                    }
                }
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eosReached = true
                encoder.releaseOutputBuffer(index, false)
            }
        }
        return eosReached
    }

    private fun checkAndStartMuxer(force: Boolean = false) {
        if (muxerStarted) return
        val hasVideo = videoTrackIndex != -1
        val expectsAudio = audioEncoder != null
        val hasAudio = audioTrackIndex != -1
        
        val timeout = System.currentTimeMillis() - startTimeMs > 1500
        if (hasVideo && (!expectsAudio || hasAudio || timeout || force)) {
            try {
                muxer?.start()
                muxerStarted = true
                Log.d(TAG, "Muxer started (audioTrack=$audioTrackIndex)")
                // Write pending samples
                while (pendingSamples.isNotEmpty()) {
                    val sample = pendingSamples.poll() ?: break
                    val track = if (sample.trackType == 0) videoTrackIndex else audioTrackIndex
                    if (track != -1) muxer?.writeSampleData(track, sample.buffer, sample.info)
                }
            } catch (e: Exception) { Log.e(TAG, "Muxer start failed", e) }
        }
    }

    private fun recordAudio() {
        val rec = audioRecord ?: return
        val enc = audioEncoder ?: return
        try {
            rec.startRecording()
            val buffer = ByteArray(4096)
            while (isRecording.get()) {
                val read = rec.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val index = enc.dequeueInputBuffer(1000)
                    if (index >= 0) {
                        enc.getInputBuffer(index)?.put(buffer, 0, read)
                        enc.queueInputBuffer(index, 0, read, System.nanoTime() / 1000, 0)
                    }
                }
            }
            val index = enc.dequeueInputBuffer(1000)
            if (index >= 0) enc.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        } catch (e: Exception) { Log.e(TAG, "Audio thread error", e) }
        finally { try { rec.stop() } catch (e: Exception) {} }
    }

    fun stop() {
        if (!isRecording.get()) return
        isRecording.set(false)
        try {
            videoEncoder?.signalEndOfInputStream()
            drainThread?.join(2000)
            audioThread?.join(2000)
        } catch (e: Exception) {}

        try {
            if (muxerStarted) muxer?.stop()
        } catch (e: Exception) {}
        finally {
            muxer?.release()
            muxer = null
            muxerStarted = false
            releaseEncoders()
        }
    }

    private fun releaseEncoders() {
        videoEncoder?.let { it.stop(); it.release() }; videoEncoder = null
        audioEncoder?.let { it.stop(); it.release() }; audioEncoder = null
        audioRecord?.let { try { it.stop() } catch(e:Exception){}; it.release() }; audioRecord = null
        inputSurface?.release(); inputSurface = null
    }

    fun getInputSurface(): Surface? = inputSurface

    companion object { private const val TAG = "Hako.Recorder" }
}
