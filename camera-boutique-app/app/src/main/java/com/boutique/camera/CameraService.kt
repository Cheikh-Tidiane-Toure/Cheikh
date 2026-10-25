package com.boutique.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleService
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Service au premier plan : il garde la caméra et le serveur actifs
 * même quand l'application est fermée ou que l'écran est éteint.
 */
class CameraService : LifecycleService() {

    companion object {
        const val CHANNEL = "camera_boutique"
        const val NOTIF_ID = 1

        @Volatile
        var instance: CameraService? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor = java.util.concurrent.Executor { main.post(it) }

    private var server: CamServer? = null
    private var provider: ProcessCameraProvider? = null
    private var useBack = true
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private lateinit var executor: ExecutorService
    private var lastFrameTime = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        executor = Executors.newSingleThreadExecutor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        demarrerPremierPlan()
        prendreVerrous()

        if (server == null) {
            val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
            val pass = prefs.getString("pass", "") ?: ""
            val port = prefs.getInt("port", 8080)
            FrameStore.start()
            try {
                val s = CamServer(port, "boutique", pass) { basculer() }
                s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                server = s
            } catch (e: Exception) {
                stopSelf()
                return START_NOT_STICKY
            }
            lancerCamera()
        }
        return START_STICKY
    }

    private fun demarrerPremierPlan() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Caméra Boutique", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
        else Notification.Builder(this)
        val notif = builder
            .setContentTitle("Caméra Boutique active")
            .setContentText("La caméra est en ligne")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    @Suppress("DEPRECATION")
    private fun prendreVerrous() {
        if (wakeLock == null) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "boutique:camera").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(WifiManager::class.java)
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "boutique:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun lancerCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                provider = future.get()
                lier()
            } catch (e: Exception) {
                main.postDelayed({ lancerCamera() }, 5000)
            }
        }, mainExecutor)
    }

    @Suppress("DEPRECATION")
    private fun lier() {
        val p = provider ?: return
        p.unbindAll()
        val selector = if (useBack) CameraSelector.DEFAULT_BACK_CAMERA
        else CameraSelector.DEFAULT_FRONT_CAMERA
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(executor) { image -> traiter(image) }
        try {
            p.bindToLifecycle(this, selector, analysis)
        } catch (e: Exception) {
            // Caméra occupée ou introuvable : on réessaie dans 5 secondes
            main.postDelayed({ if (server != null) lier() }, 5000)
        }
    }

    private fun traiter(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            // Avec spectateur : ~10 images/s. Sans spectateur : 1 image toutes les 2 s (économie de batterie).
            val intervalle = if (FrameStore.viewers.get() > 0) 100L else 2000L
            if (now - lastFrameTime < intervalle) return
            lastFrameTime = now

            var bmp = image.toBitmap()
            val rot = image.imageInfo.rotationDegrees
            if (rot != 0) {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
            FrameStore.push(out.toByteArray())
        } catch (e: Exception) {
            // on ignore l'image défaillante
        } finally {
            image.close()
        }
    }

    private fun basculer() {
        useBack = !useBack
        main.post { lier() }
    }

    override fun onDestroy() {
        instance = null
        server?.stop()
        server = null
        FrameStore.stop()
        main.removeCallbacksAndMessages(null)
        try { provider?.unbindAll() } catch (e: Exception) {}
        executor.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
