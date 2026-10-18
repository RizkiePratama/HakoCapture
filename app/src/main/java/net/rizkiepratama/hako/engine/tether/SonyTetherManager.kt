package net.rizkiepratama.hako.engine.tether

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class SonyTetherManager(
    private val context: Context,
    private val onDeviceAttached: (UsbDevice) -> Unit = {},
    private val onDeviceDetached: (UsbDevice) -> Unit = {},
    private val onConnectionStateChanged: (Boolean, String, String?) -> Unit = { _, _, _ -> },
    private val onNewImageDownloaded: (TetheredImage) -> Unit = {},
    private val onDownloadProgress: (Int, Long, Long) -> Unit = { _, _, _ -> }
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val permissionAction = "${context.packageName}.SONY_TETHER_USB_PERMISSION"
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val workerThread = HandlerThread("SonyTetherWorker").apply { start() }
    private val workerHandler = Handler(workerThread.looper)

    private var registered = false
    private var released = false
    private var currentDevice: UsbDevice? = null
    private var pendingDevice: UsbDevice? = null
    private var permissionIntent: PendingIntent? = null

    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var intInterface: UsbInterface? = null
    private var bulkInEndpoint: UsbEndpoint? = null
    private var bulkOutEndpoint: UsbEndpoint? = null
    private var interruptInEndpoint: UsbEndpoint? = null

    private var transactionId = 1
    private val isConnected = AtomicBoolean(false)
    private val knownHandles = mutableSetOf<Int>()
    private val downloadedImages = mutableListOf<TetheredImage>()
    private var downloadSequence = 0L
    private var lastEventTimeMs = 0L
    private var nextObjectMemoryPollMs = 0L
    private var lastDownloadCompletedMs = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!registered) return
            val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
            Log.d(TAG, "USB Tether Event: ${intent.action} for ${device.deviceName}")

            when (intent.action) {
                permissionAction -> {
                    if (device != pendingDevice) return
                    clearPermissionRequest()
                    if (usbManager.hasPermission(device)) openCameraSession(device)
                    else onConnectionStateChanged(false, "USB access denied.", null)
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (isSonyOrPtpDevice(device)) {
                        onDeviceAttached(device)
                        requestPermission(device)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (device == currentDevice) {
                        closeCameraSession()
                        onDeviceDetached(device)
                    }
                }
            }
        }
    }

    fun register() {
        if (registered || released) return
        val filter = IntentFilter(permissionAction).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        registered = true
        Log.i(TAG, "Sony Tether USB receiver registered")
    }

    fun unregister() {
        if (released) return
        if (registered) context.unregisterReceiver(receiver)
        registered = false
        released = true
        workerHandler.post {
            releaseSession()
            workerThread.quitSafely()
        }
    }

    fun getSonyDevices(): List<UsbDevice> {
        val devices = usbManager.deviceList.values.toList()
        return devices.filter(::isSonyOrPtpDevice)
    }

    fun isSonyOrPtpDevice(device: UsbDevice): Boolean {
        val isPtpClass = device.deviceClass == UsbConstants.USB_CLASS_STILL_IMAGE ||
                device.deviceClass == 255 || device.deviceClass == 0
        val hasPtpInterface = (0 until device.interfaceCount).any { i ->
            val iface = device.getInterface(i)
            iface.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE || iface.interfaceClass == 255
        }
        return isPtpClass || hasPtpInterface
    }

    fun requestPermission(device: UsbDevice) {
        if (!registered || currentDevice != null || pendingDevice != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        if (usbManager.hasPermission(device)) {
            openCameraSession(device)
            return
        }
        pendingDevice = device
        permissionIntent = PendingIntent.getBroadcast(
            context, device.deviceId,
            Intent(permissionAction).setPackage(context.packageName).putExtra(UsbManager.EXTRA_DEVICE, device),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, permissionIntent!!)
    }

    private fun clearPermissionRequest() {
        permissionIntent?.cancel()
        permissionIntent = null
        pendingDevice = null
    }

    private fun openCameraSession(device: UsbDevice) {
        currentDevice = device
        workerHandler.post {
            connectAndInitialize(device)
        }
    }

    private fun connectAndInitialize(device: UsbDevice) {
        try {
            var selectedIface: UsbInterface? = null
            var selectedIntIface: UsbInterface? = null
            var bIn: UsbEndpoint? = null
            var bOut: UsbEndpoint? = null
            var iIn: UsbEndpoint? = null

            Log.d(TAG, "Scanning device ${device.deviceName} (${device.interfaceCount} interfaces)")

            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                Log.d(TAG, "Interface $i: class=${iface.interfaceClass}, subclass=${iface.interfaceSubclass}, protocol=${iface.interfaceProtocol}, endpoints=${iface.endpointCount}")
                var localBIn: UsbEndpoint? = null
                var localBOut: UsbEndpoint? = null
                var localIIn: UsbEndpoint? = null

                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    Log.d(TAG, "  Endpoint $e: dir=${ep.direction}, type=${ep.type}, addr=${ep.address}")
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) localBIn = ep
                        else localBOut = ep
                    } else if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) localIIn = ep
                    }
                }

                if (localBIn != null && localBOut != null && bIn == null) {
                    selectedIface = iface
                    bIn = localBIn
                    bOut = localBOut
                    Log.i(TAG, "Selected PTP Bulk Interface $i (bIn addr=${bIn.address}, bOut addr=${bOut.address})")
                    if (localIIn != null) {
                        iIn = localIIn
                        selectedIntIface = iface
                    }
                } else if (localIIn != null && iIn == null) {
                    iIn = localIIn
                    selectedIntIface = iface
                    Log.i(TAG, "Selected Interrupt Interface $i (iIn addr=${iIn.address})")
                }
            }

            if (selectedIface == null || bIn == null || bOut == null) {
                postState(false, "Could not find PTP endpoints on USB device.", device.productName ?: device.deviceName)
                return
            }

            val connection = usbManager.openDevice(device) ?: run {
                postState(false, "Failed to open USB connection.", device.productName ?: device.deviceName)
                return
            }

            if (!connection.claimInterface(selectedIface, true)) {
                connection.close()
                postState(false, "Failed to claim PTP USB interface.", device.productName ?: device.deviceName)
                return
            }

            if (selectedIntIface != null && selectedIntIface != selectedIface) {
                connection.claimInterface(selectedIntIface, true)
            }

            usbConnection = connection
            usbInterface = selectedIface
            intInterface = selectedIntIface
            bulkInEndpoint = bIn
            bulkOutEndpoint = bOut
            interruptInEndpoint = iIn

            transactionId = 0
            sonyInitializationSteps().forEach { step ->
                val result = executeTransaction(step.opcode, step.params, step.expectsData)
                    ?: throw IOException("No PTP response for opcode 0x${step.opcode.toString(16)}")
                val accepted = result.response.code == PtpConstants.RC_OK ||
                    (step.opcode == PtpConstants.OC_OPEN_SESSION && result.response.code == 0x201E)
                if (!accepted) {
                    throw IOException(
                        "PTP opcode 0x${step.opcode.toString(16)} failed with 0x${result.response.code.toString(16)}"
                    )
                }
            }

            isConnected.set(true)
            val devName = device.productName ?: "Sony Camera"
            postState(true, "Connected: $devName", devName)

            // Start polling loop
            nextObjectMemoryPollMs = System.currentTimeMillis() + OBJECT_MEMORY_POLL_INTERVAL_MS
            startEventPolling()

        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            releaseSession()
            postState(false, "Connection error: ${e.message}", null)
        }
    }

    fun manualSync() {
        if (!isConnected.get()) return
        workerHandler.post {
            // This A7R II announces the safe-to-read RAM handle through C201.
            // Sending 0x9203 while capture is active wedges its bulk endpoint.
            Log.i(TAG, "Manual sync requested; waiting for Sony ObjectAdded event")
            val devName = currentDevice?.productName ?: "Sony Camera"
            postState(true, "Waiting for the next camera image…", devName)
        }
    }

    private fun syncObjectHandles() {
        if (!isConnected.get()) return
        try {
            // GetObjectHandles (0x1008)
            var handlesPkt = PtpPacket.createCommand(PtpConstants.OC_GET_OBJECT_HANDLES, transactionId++, 0x00000000, 0x00000000, 0x00000000)
            sendPacket(handlesPkt)
            var dataPkt = readDataPacket()
            readResponsePacket()

            if (dataPkt == null || dataPkt.payload.isEmpty()) {
                handlesPkt = PtpPacket.createCommand(PtpConstants.OC_GET_OBJECT_HANDLES, transactionId++, 0xFFFFFFFF.toInt(), 0x00000000, 0x00000000)
                sendPacket(handlesPkt)
                dataPkt = readDataPacket()
                readResponsePacket()
            }

            if (dataPkt != null && dataPkt.payload.isNotEmpty()) {
                val handles = parseObjectHandles(dataPkt.payload)
                Log.d(TAG, "Found ${handles.size} object handles on camera")
                val newHandles = handles.filter { !knownHandles.contains(it) }
                for (h in newHandles) {
                    knownHandles.add(h)
                }
                // Automatically download the latest object if new
                if (newHandles.isNotEmpty()) {
                    val latestHandle = newHandles.last()
                    downloadObject(latestHandle)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync object handles failed", e)
        }
    }

    private fun parseObjectHandles(payload: ByteArray): List<Int> {
        if (payload.size < 4) return emptyList()
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val count = buffer.int
        val result = mutableListOf<Int>()
        repeat(count) {
            if (buffer.remaining() >= 4) {
                result.add(buffer.int)
            }
        }
        return result
    }

    private fun downloadObject(handle: Int) {
        val downloadedHandles = downloadedImages.mapTo(mutableSetOf()) { it.objectHandle }
        if (!shouldDownloadSonyObject(handle, downloadedHandles)) {
            Log.d(TAG, "Object handle $handle already downloaded")
            return
        }
        knownHandles.add(handle)

        try {
            Log.i(TAG, "Downloading object handle=$handle...")
            val infoResult = executeTransaction(
                PtpConstants.OC_GET_OBJECT_INFO,
                listOf(handle),
                expectsData = true
            ) ?: throw IOException("No GetObjectInfo response for handle=$handle")
            if (infoResult.response.code != PtpConstants.RC_OK || infoResult.data == null) {
                throw IOException("GetObjectInfo failed with 0x${infoResult.response.code.toString(16)}")
            }
            val infoData = infoResult.data

            var format = PtpConstants.FMT_EXIF_JPEG
            var size = 0L
            val imageSequence = downloadSequence++

            if (infoData.payload.size >= 12) {
                val buf = ByteBuffer.wrap(infoData.payload).order(ByteOrder.LITTLE_ENDIAN)
                buf.int // Storage ID
                format = buf.short.toInt() and 0xFFFF
                buf.short // Protection status
                size = buf.int.toLong() and 0xFFFFFFFFL
            }

            // GetObject (0x1009)
            val getObjCmd = PtpPacket.createCommand(PtpConstants.OC_GET_OBJECT, transactionId++, handle)
            if (!sendPacket(getObjCmd)) {
                throw IOException("Failed to send GetObject for handle=$handle")
            }

            val rawData = readLargeDataPayload(handle, size)
            if (rawData == null) {
                throw IOException("Incomplete object transfer for handle=$handle")
            }
            val objectResponse = readResponsePacket(5000)
                ?: throw IOException("Missing GetObject response for handle=$handle")
            if (objectResponse.type != PtpConstants.TYPE_RESPONSE || objectResponse.code != PtpConstants.RC_OK) {
                throw IOException("GetObject failed with 0x${objectResponse.code.toString(16)}")
            }

            if (rawData.isNotEmpty()) {
                Log.i(TAG, "Successfully downloaded object handle=$handle, size=${rawData.size} bytes, format=0x${format.toString(16)}")
                val filename = tetherFilename(handle, imageSequence, format)
                val fileUri = try {
                    saveTetheredImage(context, filename, format, rawData).also {
                        Log.i(TAG, "Saved tethered image to $it")
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to save $filename to phone storage", error)
                    null
                }
                val bitmap = RawImageDecoder.decodeOrExtractJpeg(rawData)
                saveTetherThumbnail(context, filename, bitmap)
                val image = TetheredImage(
                    id = "img_${handle}_$imageSequence",
                    objectHandle = handle,
                    filename = filename,
                    format = format,
                    fileSizeBytes = rawData.size.toLong(),
                    bitmap = bitmap,
                    // The tether screen only needs the decoded preview. Retaining an
                    // 80+ MB ARW for every shot quickly exhausts phone memory in burst mode.
                    rawBytes = null,
                    fileUri = fileUri
                )
                downloadedImages.add(image)
                lastDownloadCompletedMs = System.currentTimeMillis()
                mainHandler.post {
                    onNewImageDownloaded(image)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error downloading object handle=$handle", e)
            mainHandler.post { onDownloadProgress(handle, 0, 0) }
            // Do not continue issuing commands after a partial/failed GetObject.
            // Sony's reusable RAM handle leaves the session out of sync, and
            // subsequent traffic can lock older Alpha firmware completely.
            releaseSession()
            postState(false, "USB transfer failed. Reconnect the camera before shooting again.", null)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing downloaded object handle=$handle", e)
            mainHandler.post { onDownloadProgress(handle, 0, 0) }
        }
    }

    private data class TransactionResult(val data: PtpPacket?, val response: PtpPacket)

    private fun executeTransaction(
        opcode: Int,
        params: List<Int>,
        expectsData: Boolean
    ): TransactionResult? {
        val txId = transactionId++
        val command = PtpPacket.createCommand(opcode, txId, *params.toIntArray())
        if (!sendPacket(command)) return null

        var data: PtpPacket? = null
        repeat(if (expectsData) 3 else 2) {
            val packet = readResponsePacket(3000) ?: return null
            if (packet.transactionId != txId) {
                Log.w(TAG, "Ignoring stale PTP packet tx=${packet.transactionId}, expected=$txId")
                return@repeat
            }
            when (packet.type) {
                PtpConstants.TYPE_DATA -> {
                    data = packet
                    if (opcode == PtpConstants.OC_SONY_SDIO_GET_EXT_DEVICE_INFO) {
                        Log.i(
                            TAG,
                            "Sony capability payload (${packet.payload.size} bytes): ${packet.payload.toHexPreview(512)}"
                        )
                    }
                }
                PtpConstants.TYPE_RESPONSE -> {
                    if (expectsData && data == null) {
                        Log.w(TAG, "PTP opcode 0x${opcode.toString(16)} returned no data phase")
                    }
                    Log.d(TAG, "PTP opcode 0x${opcode.toString(16)} response=0x${packet.code.toString(16)}")
                    return TransactionResult(data, packet)
                }
            }
        }
        return null
    }

    private fun sendPacket(packet: PtpPacket): Boolean {
        val conn = usbConnection ?: return false
        val epOut = bulkOutEndpoint ?: return false
        val bytes = packet.encode()
        val transferred = conn.bulkTransfer(epOut, bytes, bytes.size, 1000)
        if (transferred != bytes.size) {
            Log.e(TAG, "bulkTransfer OUT failed for code 0x${packet.code.toString(16)}, transferred=$transferred/${bytes.size}, epAddr=${epOut.address}")
            return false
        }
        return true
    }

    private fun readResponsePacket(timeoutMs: Int = 1000): PtpPacket? {
        val conn = usbConnection ?: return null
        val epIn = bulkInEndpoint ?: return null
        val buffer = ByteArray(65536)
        val read = conn.bulkTransfer(epIn, buffer, buffer.size, timeoutMs)
        if (read <= 0) {
            return null
        }
        return PtpPacket.decode(buffer, 0, read)
    }

    private fun readDataPacket(): PtpPacket? {
        return readResponsePacket()
    }

    private fun readLargeDataPayload(handle: Int, expectedSize: Long): ByteArray? {
        val conn = usbConnection ?: return null
        val epIn = bulkInEndpoint ?: return null
        var activeReadSize = usbObjectReadBufferSize(Build.VERSION.SDK_INT)
        val buffer = ByteArray(activeReadSize)

        Log.d(TAG, "Reading object handle=$handle with initial USB chunkSize=$activeReadSize")

        fun readChunk(timeoutMs: Int): Int {
            while (true) {
                val read = conn.bulkTransfer(epIn, buffer, activeReadSize, timeoutMs)
                if (read > 0) return read
                val smallerSize = smallerUsbObjectReadSize(activeReadSize) ?: return read
                Log.w(TAG, "USB read size $activeReadSize rejected; retrying pending data with $smallerSize bytes")
                activeReadSize = smallerSize
            }
        }

        val read = readChunk(5000)
        if (read < PtpConstants.HEADER_SIZE) {
            Log.e(TAG, "Initial object read failed with read=$read at chunkSize=$activeReadSize")
            return null
        }
        Log.i(TAG, "USB object read negotiated chunkSize=$activeReadSize")

        val bufHeader = ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.LITTLE_ENDIAN)
        val containerLen = bufHeader.int
        val containerType = bufHeader.short.toInt() and 0xFFFF

        if (containerType != PtpConstants.TYPE_DATA) {
            Log.w(TAG, "Expected DATA container type 2, got $containerType")
            return null
        }

        val totalPayloadBytes = containerLen - PtpConstants.HEADER_SIZE
        val outputStream = ByteArrayOutputStream(if (totalPayloadBytes > 0) totalPayloadBytes else 1024 * 1024)

        val initialPayloadLen = read - PtpConstants.HEADER_SIZE
        if (initialPayloadLen > 0) {
            outputStream.write(buffer, PtpConstants.HEADER_SIZE, initialPayloadLen)
        }

        var downloaded = initialPayloadLen.toLong()
        val targetSize = if (totalPayloadBytes > 0) totalPayloadBytes.toLong() else expectedSize

        var consecutiveFailures = 0
        while (isConnected.get() && !isCompleteLargeObjectRead(downloaded, targetSize)) {
            val chunkRead = readChunk(5000)
            if (chunkRead <= 0) {
                consecutiveFailures++
                Log.w(TAG, "Chunk read failure $consecutiveFailures at downloaded=$downloaded / $targetSize")
                if (shouldRetryLargeObjectRead(consecutiveFailures)) continue
                Log.e(TAG, "Aborting incomplete object transfer at downloaded=$downloaded / $targetSize")
                return null
            }
            consecutiveFailures = 0
            outputStream.write(buffer, 0, chunkRead)
            downloaded += chunkRead
            if (targetSize > 0) {
                mainHandler.post { onDownloadProgress(handle, downloaded, targetSize) }
            }
        }

        if (!isCompleteLargeObjectRead(downloaded, targetSize)) {
            Log.e(TAG, "Object transfer ended incomplete at downloaded=$downloaded / $targetSize")
            return null
        }
        return outputStream.toByteArray()
    }

    private fun startEventPolling() {
        workerHandler.post(object : Runnable {
            override fun run() {
                if (!isConnected.get() || released) return

                try {
                    val epInt = interruptInEndpoint
                    val conn = usbConnection
                    if (epInt != null && conn != null) {
                        val intBuf = ByteArray(64)
                        val read = conn.bulkTransfer(epInt, intBuf, intBuf.size, 300)
                        if (read >= PtpConstants.HEADER_SIZE) {
                            Log.d(TAG, "Interrupt raw ($read bytes): ${intBuf.copyOf(read).toHexPreview(64)}")
                            val evt = PtpPacket.decode(intBuf, 0, read)
                            if (evt != null) {
                                lastEventTimeMs = System.currentTimeMillis()
                                Log.d(
                                    TAG,
                                    "Event caught: type=${evt.type}, code=0x${evt.code.toString(16)}, tx=${evt.transactionId.toUInt()}, params=${evt.params.map { "0x${it.toUInt().toString(16)}" }}"
                                )

                                when (val action = actionForSonyTetherEvent(evt.code, evt.params)) {
                                    is SonyTetherEventAction.DownloadObject -> {
                                        Log.i(TAG, "Object-added event detected handle=${action.handle}")
                                        downloadObject(action.handle)
                                    }
                                    SonyTetherEventAction.Ignore -> Unit
                                }
                            }
                        }
                    }

                    pollSonyObjectMemoryIfDue()
                } catch (e: Exception) {
                    Log.d(TAG, "Interrupt poll error: ${e.message}")
                }

                if (isConnected.get() && !released) {
                    workerHandler.postDelayed(this, 1000L)
                }
            }
        })
    }

    private fun pollSonyObjectMemoryIfDue(nowMs: Long = System.currentTimeMillis()) {
        if (nowMs < nextObjectMemoryPollMs) return
        nextObjectMemoryPollMs = nowMs + OBJECT_MEMORY_POLL_INTERVAL_MS

        val result = executeTransaction(
            PtpConstants.OC_SONY_GET_ALL_EXT_DEVICE_PROP_INFO,
            emptyList(),
            expectsData = true
        ) ?: return
        if (result.response.code != PtpConstants.RC_OK) {
            Log.w(TAG, "Sony all-properties poll failed with 0x${result.response.code.toString(16)}")
            return
        }
        val value = result.data?.payload?.let(::findSonyObjectInMemoryValue) ?: return
        Log.d(TAG, "Sony 0x9209 ObjectInMemory=0x${value.toString(16)}")
        if (value >= 0x8000 && nowMs - lastDownloadCompletedMs >= OBJECT_MEMORY_SETTLE_MS) {
            Log.i(TAG, "Sony RAM contains an object without C201; downloading fallback handle")
            downloadObject(SONY_IN_MEMORY_OBJECT_HANDLE)
        }
    }

    fun closeCameraSession() {
        isConnected.set(false)
        workerHandler.post {
            releaseSession()
        }
        onConnectionStateChanged(false, "Disconnected", null)
    }

    private fun releaseSession() {
        try {
            if (usbConnection != null) {
                val conn = usbConnection
                val iface = usbInterface
                val iIface = intInterface
                if (conn != null) {
                    if (iface != null) conn.releaseInterface(iface)
                    if (iIface != null && iIface != iface) conn.releaseInterface(iIface)
                    conn.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing USB session", e)
        } finally {
            usbConnection = null
            usbInterface = null
            intInterface = null
            bulkInEndpoint = null
            bulkOutEndpoint = null
            interruptInEndpoint = null
            nextObjectMemoryPollMs = 0L
            lastDownloadCompletedMs = 0L
            isConnected.set(false)
            currentDevice = null
        }
    }

    private fun postState(connected: Boolean, msg: String, deviceName: String?) {
        mainHandler.post {
            onConnectionStateChanged(connected, msg, deviceName)
        }
    }

    companion object {
        private const val TAG = "Hako.SonyTether"
        private const val SONY_IN_MEMORY_OBJECT_HANDLE = 0xFFFFC001.toInt()
        private const val OBJECT_MEMORY_POLL_INTERVAL_MS = 3_000L
        private const val OBJECT_MEMORY_SETTLE_MS = 4_000L
    }
}

private fun ByteArray.toHexPreview(limit: Int): String =
    take(limit).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) } +
        if (size > limit) " …" else ""
