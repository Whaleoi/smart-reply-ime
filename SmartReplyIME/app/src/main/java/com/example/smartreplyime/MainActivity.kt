package com.example.smartreplyime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import com.example.smartreplyime.engine.RuleStore
import com.example.smartreplyime.template.TemplateActivity

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuleStore.load(applicationContext)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnEnableIme).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnPickIme).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        findViewById<Button>(R.id.btnEnableA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnTemplates).setOnClickListener {
            startActivity(Intent(this, TemplateActivity::class.java))
        }
    }
}
