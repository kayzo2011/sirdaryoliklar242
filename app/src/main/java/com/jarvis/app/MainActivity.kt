package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply {
            text = "JARVIS"
            textSize = 30f
            setTextColor(Color.CYAN)
            gravity = Gravity.CENTER
        }
        val start = Button(this).apply {
            text = "Ishga tushirish"
            setOnClickListener { proceed() }
        }
        val stop = Button(this).apply {
            text = "To'xtatish"
            setOnClickListener { stopService(Intent(this@MainActivity, JarvisService::class.java)) }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(48, 48, 48, 48)
            addView(title)
            addView(start)
            addView(stop)
        }
        setContentView(root)
    }

    private fun proceed() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay ruxsatini bering, so'ng orqaga qaytib yana bosing", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1
            )
            return
        }
        startForegroundService(Intent(this, JarvisService::class.java))
        Toast.makeText(this, "Jarvis tinglayapti", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        Toast.makeText(this, "Endi \"Ishga tushirish\"ni yana bosing", Toast.LENGTH_SHORT).show()
    }
}
