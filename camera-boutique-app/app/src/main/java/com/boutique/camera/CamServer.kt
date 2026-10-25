package com.boutique.camera

import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * Petit serveur web protégé par mot de passe :
 *   /              page avec la vidéo en direct
 *   /video         flux vidéo (MJPEG)
 *   /snapshot.jpg  une photo
 *   /switch        change de caméra (avant/arrière)
 */
class CamServer(
    port: Int,
    private val user: String,
    private val pass: String,
    private val onSwitch: () -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (!autorise(session)) {
            val r = newFixedLengthResponse(Status.UNAUTHORIZED, "text/plain", "Mot de passe requis")
            r.addHeader("WWW-Authenticate", "Basic realm=\"Camera Boutique\"")
            return r
        }
        return when (session.uri) {
            "/video" -> {
                val r = newChunkedResponse(
                    Status.OK, "multipart/x-mixed-replace; boundary=frame", MjpegStream()
                )
                r.addHeader("Cache-Control", "no-cache, no-store")
                r
            }
            "/snapshot.jpg" -> {
                val jpg = FrameStore.latest()
                if (jpg == null) {
                    newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Pas encore d'image, réessaie dans 2 secondes")
                } else {
                    val r = newFixedLengthResponse(
                        Status.OK, "image/jpeg", ByteArrayInputStream(jpg), jpg.size.toLong()
                    )
                    r.addHeader("Content-Disposition", "attachment; filename=\"boutique-${System.currentTimeMillis()}.jpg\"")
                    r
                }
            }
            "/switch" -> {
                onSwitch()
                newFixedLengthResponse(Status.OK, "text/html; charset=utf-8", page("Caméra changée. L'image revient dans 2 secondes…"))
            }
            else -> newFixedLengthResponse(Status.OK, "text/html; charset=utf-8", page(""))
        }
    }

    private fun autorise(session: IHTTPSession): Boolean {
        val h = session.headers["authorization"] ?: return false
        if (!h.startsWith("Basic ", ignoreCase = true)) return false
        return try {
            val decode = String(Base64.decode(h.substring(6).trim(), Base64.DEFAULT), Charsets.UTF_8)
            MessageDigest.isEqual(
                decode.toByteArray(Charsets.UTF_8),
                "$user:$pass".toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            false
        }
    }

    private fun page(message: String): String = """
        <!doctype html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Caméra Boutique</title>
        <style>
          body{font-family:system-ui,sans-serif;background:#111;color:#eee;margin:0;padding:12px;text-align:center}
          img{width:100%;max-width:900px;border-radius:10px;background:#000}
          a{display:inline-block;margin:8px;padding:12px 18px;background:#2d7ff9;color:#fff;border-radius:10px;text-decoration:none;font-size:17px}
          a.sec{background:#444}
        </style></head><body>
        <h2>📹 Caméra Boutique</h2>
        <p>$message</p>
        <img src="/video" alt="Vidéo en direct"><br>
        <a class="sec" href="/switch">🔄 Changer de caméra</a>
        <a class="sec" href="/snapshot.jpg">📸 Photo</a>
        <a href="/">↻ Recharger</a>
        </body></html>
    """.trimIndent()
}

/** Fabrique le flux MJPEG : une image JPEG après l'autre, tant que quelqu'un regarde. */
private class MjpegStream : InputStream() {
    private var buf = ByteArray(0)
    private var pos = 0
    private var lastSeq = -1L
    private var closed = false

    init {
        FrameStore.viewers.incrementAndGet()
    }

    private fun remplir(): Boolean {
        if (pos < buf.size) return true
        if (closed) return false
        val f = FrameStore.waitNext(lastSeq) ?: return false
        lastSeq = f.seq
        val head = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${f.data.size}\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
        buf = head + f.data + "\r\n".toByteArray(Charsets.US_ASCII)
        pos = 0
        return true
    }

    override fun read(): Int {
        if (!remplir()) return -1
        return buf[pos++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!remplir()) return -1
        val n = minOf(len, buf.size - pos)
        System.arraycopy(buf, pos, b, off, n)
        pos += n
        return n
    }

    override fun close() {
        if (!closed) {
            closed = true
            FrameStore.viewers.decrementAndGet()
        }
        super.close()
    }
}
