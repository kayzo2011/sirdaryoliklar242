package com.jarvis.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply {
            text = "JARVIS online"
            textSize = 28f
            setTextColor(Color.CYAN)
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        setContentView(tv)
    }
}
