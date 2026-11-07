package com.aiclicker.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(0xFF100E19.toInt())
        }
        root.addView(TextView(this).apply { text = "AI CLICKER"; textSize = 30f; setTextColor(0xFFB99AFF.toInt()) })
        root.addView(TextView(this).apply { text = "Your keyboard. Your tools. Your control."; textSize = 15f; setTextColor(-1) })
        root.addView(TextView(this).apply { text = "1. Enable AI Clicker in Android keyboard settings.\n2. Select AI Clicker Keyboard.\n3. Switch to it from the keyboard picker in any text field."; textSize = 15f; setTextColor(-1); setPadding(0,24,0,24) })
        root.addView(Button(this).apply { text = "1 • Enable keyboard"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } })
        root.addView(Button(this).apply { text = "2 • Choose AI Clicker"; setOnClickListener { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() } })
        root.addView(TextView(this).apply { text = "Privacy first: typed text is processed locally by this starter app."; textSize = 13f; setTextColor(0xFFB8AEC8.toInt()); setPadding(0,24,0,0) })
        setContentView(root)
    }
}
