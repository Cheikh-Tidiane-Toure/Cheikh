package com.boutique.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

class MainActivity : Activity() {

    private lateinit var info: TextView
    private lateinit var passEdit: EditText
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        if (prefs.getString("pass", "").isNullOrBlank()) {
            prefs.edit().putString("pass", genererMotDePasse()).apply()
        }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(TextView(this).apply {
            text = "📹 Caméra Boutique"
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        })

        root.addView(TextView(this).apply {
            text = "Mot de passe (identifiant : boutique)"
            textSize = 15f
        })
        passEdit = EditText(this).apply {
            setText(prefs.getString("pass", ""))
            setSingleLine()
        }
        root.addView(passEdit)

        btnStart = Button(this).apply { setOnClickListener { basculerService() } }
        root.addView(btnStart)

        root.addView(Button(this).apply {
            text = "🔋 Autoriser en arrière-plan (important)"
            setOnClickListener { demanderBatterie() }
        })

        info = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad, 0, 0)
            setTextIsSelectable(true)
        }
        root.addView(info)
    }

    override fun onResume() {
        super.onResume()
        rafraichir()
    }

    private fun enMarche() = CameraService.instance != null

    private fun basculerService() {
        if (enMarche()) {
            stopService(Intent(this, CameraService::class.java))
            Handler(Looper.getMainLooper()).postDelayed({ rafraichir() }, 500)
            return
        }
        val mdp = passEdit.text.toString().trim()
        if (mdp.length < 6) {
            Toast.makeText(this, "Mot de passe trop court (6 caractères minimum)", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("pass", mdp).apply()

        val manque = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            manque.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            manque.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (manque.isNotEmpty()) {
            requestPermissions(manque.toTypedArray(), 1)
        } else {
            demarrerService()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            demarrerService()
        } else {
            Toast.makeText(this, "Il faut autoriser la caméra", Toast.LENGTH_LONG).show()
        }
    }

    private fun demarrerService() {
        val i = Intent(this, CameraService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        Handler(Looper.getMainLooper()).postDelayed({ rafraichir() }, 800)
    }

    private fun demanderBatterie() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Déjà autorisé ✅", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun rafraichir() {
        val marche = enMarche()
        btnStart.text = if (marche) "⏹ Arrêter la caméra" else "▶️ Démarrer la caméra"
        val port = getSharedPreferences("cfg", MODE_PRIVATE).getInt("port", 8080)
        val liens = adresses().joinToString("\n") { "http://$it:$port" }
        info.text = if (marche) {
            "✅ La caméra est EN LIGNE.\n\nOuvre un de ces liens dans un navigateur (identifiant : boutique) :\n\n" +
                (if (liens.isEmpty()) "(aucune adresse trouvée : connecte le téléphone au Wi-Fi)" else liens) +
                "\n\nTu peux maintenant quitter l'application et éteindre l'écran : la caméra continue."
        } else {
            "⏸ La caméra est arrêtée.\n\nAppuie sur « Démarrer la caméra »."
        }
    }

    private fun adresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni ->
                ni.inetAddresses.toList()
                    .filter { it is Inet4Address && !it.isLoopbackAddress }
                    .mapNotNull { it.hostAddress }
            }
    } catch (e: Exception) {
        emptyList()
    }

    private fun genererMotDePasse(): String {
        val c = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val r = SecureRandom()
        return (1..10).map { c[r.nextInt(c.length)] }.joinToString("")
    }
}
