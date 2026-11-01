package com.yuzuki.futurekb

import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// adb reverse tcp:5555 tcp:5555 経由でPCのserver.pyに繋ぐ
object Net {
    @Volatile var connected = false
    private val q = LinkedBlockingQueue<String>()
    private var started = false

    fun send(msg: String) {
        if (connected) q.offer(msg)
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        thread(isDaemon = true) {
            while (true) {
                try {
                    Socket("127.0.0.1", 5555).use { s ->
                        s.tcpNoDelay = true
                        q.clear()
                        connected = true
                        val out = s.getOutputStream()
                        while (true) {
                            val m = q.poll(1, TimeUnit.SECONDS) ?: "P"
                            out.write((m + "\n").toByteArray())
                            out.flush()
                        }
                    }
                } catch (e: Exception) {
                    connected = false
                    Thread.sleep(700)
                }
            }
        }
    }
}
