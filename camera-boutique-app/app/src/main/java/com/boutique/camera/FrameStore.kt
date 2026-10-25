package com.boutique.camera

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Garde la dernière image JPEG et réveille les spectateurs quand une nouvelle arrive. */
object FrameStore {

    class Frame(val seq: Long, val data: ByteArray)

    private val lock = ReentrantLock()
    private val cond = lock.newCondition()
    private var seq = 0L
    private var last: ByteArray? = null

    @Volatile
    var running = false
        private set

    /** Nombre de personnes qui regardent en ce moment (pour économiser la batterie sinon). */
    val viewers = AtomicInteger(0)

    fun start() = lock.withLock {
        running = true
        last = null
    }

    fun stop() = lock.withLock {
        running = false
        cond.signalAll()
    }

    fun push(jpeg: ByteArray) = lock.withLock {
        seq++
        last = jpeg
        cond.signalAll()
    }

    fun latest(): ByteArray? = lock.withLock { last }

    /** Attend une image plus récente que [lastSeq]. Renvoie null quand le service s'arrête. */
    fun waitNext(lastSeq: Long): Frame? = lock.withLock {
        while (running) {
            var nanos = TimeUnit.SECONDS.toNanos(5)
            while (running && seq == lastSeq && nanos > 0) {
                nanos = cond.awaitNanos(nanos)
            }
            val d = last
            if (running && d != null) return@withLock Frame(seq, d)
        }
        null
    }
}
