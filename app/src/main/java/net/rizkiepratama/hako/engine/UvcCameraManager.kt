package net.rizkiepratama.hako.engine

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.opengl.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class UvcCameraManager(
    private val context: Context,
    private val onDeviceAttached: (UsbDevice) -> Unit,
    private val onDeviceDetached: (UsbDevice) -> Unit,
    private val onCameraStateChanged: (Boolean, String) -> Unit,
    private val onPreviewModeChanged: (CaptureMode) -> Unit = {},
    private val onCaptureModesChanged: (List<CaptureMode>) -> Unit = {},
    private val onScopePixels: (ByteArray, Int, Int) -> Unit = { _, _, _ -> }
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val permissionAction = "${context.packageName}.USB_PERMISSION"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraThread = HandlerThread("HakoUvc").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    
    private val glThread = HandlerThread("HakoGL").apply { start() }
    private val glHandler = Handler(glThread.looper)

    private var registered = false
    private var released = false
    private var currentDevice: UsbDevice? = null
    private var pendingDevice: UsbDevice? = null
    private var permissionIntent: PendingIntent? = null
    private var generation = 0
    private var requestedMode: CaptureMode? = null

    private var camera: UVCCamera? = null
    private var controlBlock: USBMonitor.UsbControlBlock? = null
    
    // GL state
    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var eglPbuffer = EGL14.EGL_NO_SURFACE
    
    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var cameraSurface: Surface? = null
    private var glProgram = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var transformHandle = 0
    private var zebraEnabledHandle = 0
    private var peakingEnabledHandle = 0
    private var zebraThresholdHandle = 0
    private var peakingThresholdHandle = 0
    private var peakingColorHandle = 0
    private var texelSizeHandle = 0
    // All render settings and surface operations are serialized on HakoGL.
    private var assistSettings = MonitorAssistSettings()
    private var sourceWidth = 1280
    private var sourceHeight = 720
    private var scopeAnalysisEnabled = false
    private var lastScopeReadbackMs = 0L
    private var scopeFramebuffer = 0
    private var scopeTexture = 0
    private val scopePixels = ByteBuffer.allocateDirect(320 * 180 * 4).order(ByteOrder.nativeOrder())
    
    private var previewEglSurface = EGL14.EGL_NO_SURFACE
    private var captureEglSurface = EGL14.EGL_NO_SURFACE
    
    private val stMatrix = FloatArray(16)
    private val updateSurface = AtomicBoolean(false)

    private val usbMonitor = USBMonitor(context, object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice) = Unit
        override fun onDettach(device: UsbDevice) = Unit
        override fun onConnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock, createNew: Boolean) = Unit
        override fun onDisconnect(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) = Unit
        override fun onCancel(device: UsbDevice) = Unit
    })

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!registered) return
            val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
            Log.d(TAG, "USB Event: ${intent.action} for ${device.deviceName}")
            
            when (intent.action) {
                permissionAction -> {
                    if (device != pendingDevice) return
                    clearPermissionRequest()
                    if (usbManager.hasPermission(device)) openCamera(device)
                    else onCameraStateChanged(false, "USB access denied.")
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    onDeviceAttached(device)
                    requestPermission(device)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (device == currentDevice) closeCamera()
                    onDeviceDetached(device)
                }
            }
        }
    }

    init {
        glHandler.post { initGL() }
    }

    fun register() {
        if (registered || released) return
        val filter = IntentFilter(permissionAction).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // System broadcasts require RECEIVER_EXPORTED on Android 14+
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        registered = true
        Log.i(TAG, "USB receiver registered")
    }

    fun unregister() {
        if (released) return
        if (registered) context.unregisterReceiver(receiver)
        registered = false
        released = true
        generation++
        cameraHandler.post {
            releaseCamera()
            usbMonitor.destroy()
            cameraThread.quitSafely()
        }
        glHandler.post {
            releaseGL()
            glThread.quitSafely()
        }
    }

    private fun initGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
        eglConfig = configs[0]

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

        val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        eglPbuffer = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglPbuffer, eglPbuffer, eglContext)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture?.setOnFrameAvailableListener({
            // Keep at most one render runnable queued if a slow encoder stalls a swap.
            if (updateSurface.compareAndSet(false, true)) glHandler.post { drawFrame() }
        }, glHandler)
        cameraSurface = Surface(surfaceTexture)

        initShaders()
        initScopeFramebuffer()
    }

    private fun initShaders() {
        val vShader = MonitorAssistShader.vertex
        val fShader = MonitorAssistShader.fragment(externalTexture = true)

        val program = GLES20.glCreateProgram()
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vShader)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fShader)
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) Log.e(TAG, "Link error: ${GLES20.glGetProgramInfoLog(program)}")
        
        glProgram = program

        positionHandle = GLES20.glGetAttribLocation(glProgram, "position")
        texCoordHandle = GLES20.glGetAttribLocation(glProgram, "inputTextureCoordinate")
        transformHandle = GLES20.glGetUniformLocation(glProgram, "textureTransform")
        zebraEnabledHandle = GLES20.glGetUniformLocation(glProgram, "zebraEnabled")
        peakingEnabledHandle = GLES20.glGetUniformLocation(glProgram, "peakingEnabled")
        zebraThresholdHandle = GLES20.glGetUniformLocation(glProgram, "zebraThreshold")
        peakingThresholdHandle = GLES20.glGetUniformLocation(glProgram, "peakingThreshold")
        peakingColorHandle = GLES20.glGetUniformLocation(glProgram, "peakingColor")
        texelSizeHandle = GLES20.glGetUniformLocation(glProgram, "texelSize")
    }

    private fun initScopeFramebuffer() {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        scopeTexture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, scopeTexture)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 320, 180, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glGenFramebuffers(1, ids, 0)
        scopeFramebuffer = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, scopeFramebuffer)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, scopeTexture, 0)
        check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun setMonitorAssists(settings: MonitorAssistSettings) {
        if (!released) glHandler.post { assistSettings = settings }
    }

    fun setScopeAnalysisEnabled(enabled: Boolean) {
        if (!released) glHandler.post { scopeAnalysisEnabled = enabled }
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) Log.e(TAG, "Shader error: ${GLES20.glGetShaderInfoLog(shader)}")
        return shader
    }

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).position(0)
    }
    private val texBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)).position(0)
    }

    private fun drawFrame() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return
        EGL14.eglMakeCurrent(eglDisplay, eglPbuffer, eglPbuffer, eglContext)
        if (updateSurface.compareAndSet(true, false)) {
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(stMatrix)
        }
        renderToSurface(previewEglSurface, AssistOutput.PREVIEW)
        renderToSurface(captureEglSurface, AssistOutput.RECORDING)
        if (scopeAnalysisEnabled && SystemClock.elapsedRealtime() - lastScopeReadbackMs >= 160L) {
            lastScopeReadbackMs = SystemClock.elapsedRealtime()
            EGL14.eglMakeCurrent(eglDisplay, eglPbuffer, eglPbuffer, eglContext)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, scopeFramebuffer)
            drawVideo(320, 180, AssistOutput.ANALYSIS)
            scopePixels.clear()
            GLES20.glReadPixels(0, 0, 320, 180, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, scopePixels)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            val rgba = ByteArray(320 * 180 * 4)
            scopePixels.rewind()
            scopePixels.get(rgba)
            // Scope analysis depends on columns and color distributions, not row order.
            onScopePixels(rgba, 320, 180)
        }
    }

    private fun renderToSurface(eglSurface: EGLSurface, output: AssistOutput) {
        if (eglSurface == EGL14.EGL_NO_SURFACE) return
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        
        val width = IntArray(1)
        val height = IntArray(1)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, width, 0)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, height, 0)
        drawVideo(width[0], height[0], output)
        if (output == AssistOutput.RECORDING) {
            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, System.nanoTime())
        }
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun drawVideo(width: Int, height: Int, output: AssistOutput) {
        GLES20.glViewport(0, 0, width, height)
        
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        
        GLES20.glUseProgram(glProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glUniformMatrix4fv(transformHandle, 1, false, stMatrix, 0)
        val effects = assistSettings.effectsFor(output)
        GLES20.glUniform1i(zebraEnabledHandle, if (effects.zebra) 1 else 0)
        GLES20.glUniform1i(peakingEnabledHandle, if (effects.peaking) 1 else 0)
        GLES20.glUniform1f(zebraThresholdHandle, assistSettings.zebraThreshold)
        GLES20.glUniform1f(peakingThresholdHandle, assistSettings.peakingSensitivity.threshold)
        // Use at least one output pixel so peaking remains visible on a reduced preview.
        GLES20.glUniform2f(texelSizeHandle, 1f / minOf(sourceWidth, width).coerceAtLeast(1), 1f / minOf(sourceHeight, height).coerceAtLeast(1))
        val color = assistSettings.peakingColor.rgb
        GLES20.glUniform3f(peakingColorHandle, ((color shr 16) and 255) / 255f, ((color shr 8) and 255) / 255f, (color and 255) / 255f)
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun releaseGL() {
        surfaceTexture?.setOnFrameAvailableListener(null)
        EGL14.eglMakeCurrent(eglDisplay, eglPbuffer, eglPbuffer, eglContext)
        GLES20.glDeleteFramebuffers(1, intArrayOf(scopeFramebuffer), 0)
        GLES20.glDeleteTextures(2, intArrayOf(scopeTexture, textureId), 0)
        GLES20.glDeleteProgram(glProgram)
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (previewEglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, previewEglSurface)
        if (captureEglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, captureEglSurface)
        if (eglPbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglPbuffer)
        cameraSurface?.release(); surfaceTexture?.release()
        EGL14.eglDestroyContext(eglDisplay, eglContext); EGL14.eglTerminate(eglDisplay)
        cameraSurface = null
        surfaceTexture = null
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
    }

    fun requestPermission(device: UsbDevice) {
        if (!registered || currentDevice != null || pendingDevice != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        if (usbManager.hasPermission(device)) {
            openCamera(device)
            return
        }
        pendingDevice = device
        permissionIntent = PendingIntent.getBroadcast(context, device.deviceId,
            Intent(permissionAction).setPackage(context.packageName).putExtra(UsbManager.EXTRA_DEVICE, device),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        usbManager.requestPermission(device, permissionIntent!!)
    }

    fun setPreviewMode(mode: CaptureMode) {
        requestedMode = mode
        val device = currentDevice ?: return
        val session = ++generation
        cameraHandler.post { releaseCamera(); openCameraOnCameraThread(device, session) }
    }

    private fun clearPermissionRequest() { permissionIntent?.cancel(); permissionIntent = null; pendingDevice = null }
    private fun openCamera(device: UsbDevice) { currentDevice = device; val session = ++generation; cameraHandler.post { openCameraOnCameraThread(device, session) } }

    private fun openCameraOnCameraThread(device: UsbDevice, session: Int) {
        try {
            controlBlock = usbMonitor.openDevice(device)
            camera = UVCCamera()
            camera!!.open(controlBlock)
            val supportedModes = supportedCaptureModes(camera!!)
            val selectedMode = requestedMode?.takeIf { r -> supportedModes.any { it.width == r.width && it.height == r.height } }
                ?: supportedModes.firstOrNull { it.width == 1280 && it.height == 720 } ?: supportedModes.firstOrNull()
            selectedMode?.let {
                if (it.frameRate != null) camera!!.setPreviewSize(it.width, it.height, it.frameRate, it.frameRate, it.frameFormat, 0f)
                else camera!!.setPreviewSize(it.width, it.height, it.frameFormat)
                glHandler.post { sourceWidth = it.width; sourceHeight = it.height }
            }
            camera!!.setPreviewDisplay(cameraSurface)
            camera!!.startPreview()
            mainHandler.post {
                if (generation == session) {
                    onCaptureModesChanged(supportedModes)
                    selectedMode?.let { onPreviewModeChanged(it) }
                    onCameraStateChanged(true, "Active")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Open failed", e)
            releaseCamera()
            mainHandler.post { if (generation == session) onCameraStateChanged(false, "Failed: ${e.message}") }
        }
    }

    fun startPreview(surface: Surface) {
        glHandler.post {
            if (previewEglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, previewEglSurface)
            previewEglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, intArrayOf(EGL14.EGL_NONE), 0)
        }
    }

    fun stopPreview() {
        glHandler.post {
            if (previewEglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, previewEglSurface)
            previewEglSurface = EGL14.EGL_NO_SURFACE
        }
    }

    fun startCapture(surface: Surface) {
        glHandler.post {
            if (captureEglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, captureEglSurface)
            captureEglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, intArrayOf(EGL14.EGL_NONE), 0)
        }
    }

    fun stopCapture(surface: Surface) {
        glHandler.post {
            if (captureEglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, captureEglSurface)
            captureEglSurface = EGL14.EGL_NO_SURFACE
        }
    }

    private fun supportedCaptureModes(camera: UVCCamera): List<CaptureMode> {
        val sizes = UVCCamera.getSupportedSize(-1, camera.getSupportedSize())
        return collapseCaptureModes(sizes.flatMap { s ->
            val format = if (s.type == 6) UVCCamera.FRAME_FORMAT_MJPEG else UVCCamera.FRAME_FORMAT_YUYV
            val fps = s.fps?.map { it.roundToInt() }?.filter { it > 0 }?.distinct()?.sortedDescending().orEmpty()
            if (fps.isEmpty()) listOf(CaptureMode(s.width, s.height, null, format))
            else fps.map { CaptureMode(s.width, s.height, it, format) }
        })
    }

    fun closeCamera() { generation++ ; currentDevice = null; cameraHandler.post { releaseCamera() }; onCameraStateChanged(false, "Searching…") }
    private fun releaseCamera() { camera?.destroy(); camera = null; controlBlock?.close(); controlBlock = null }
    
    fun getUsbDevices(): List<UsbDevice> {
        val devices = usbManager.deviceList.values.toList()
        Log.d(TAG, "getUsbDevices found: ${devices.size} total devices")
        return devices.filter(::isVideoDevice)
    }

    private fun isVideoDevice(d: UsbDevice): Boolean {
        val isVideo = d.deviceClass == UsbConstants.USB_CLASS_VIDEO || (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }
        Log.d(TAG, "Device: ${d.deviceName} (id=${d.deviceId}, class=${d.deviceClass}) isVideo=$isVideo")
        return isVideo
    }

    companion object { private const val TAG = "Hako.USB" }
}

