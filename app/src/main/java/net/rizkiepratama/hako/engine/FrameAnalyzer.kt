package net.rizkiepratama.hako.engine

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/** Analyzes clean, reduced GPU readbacks without queuing work behind the live frame stream. */
class FrameAnalyzer(private val onFrame: (ScopeFrame) -> Unit) {
    private val thread = HandlerThread("HakoScopes").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private val released = AtomicBoolean(false)

    fun analyzeRgba(rgba: ByteArray, width: Int, height: Int) {
        // Drop a snapshot if the previous density raster is still being processed.
        if (released.get() || !busy.compareAndSet(false, true)) return
        handler.post {
            try {
                val analysis = analyzeRgbaFrame(rgba, width, height)
                mainHandler.post { if (!released.get()) onFrame(analysis) }
            } finally {
                busy.set(false)
            }
        }
    }

    fun release() {
        released.set(true)
        thread.quitSafely()
    }
}
