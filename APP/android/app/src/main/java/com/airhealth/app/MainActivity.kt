package com.airhealth.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "AirHealth"
            textSize = 24f
            setPadding(32, 32, 32, 32)
        }

        setContentView(textView)
    }
}
