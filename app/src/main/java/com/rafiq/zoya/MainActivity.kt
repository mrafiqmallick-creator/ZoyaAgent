package com.rafiq.zoya

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F19"))
            setPadding(48, 80, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(TextView(this).apply {
            text = "ZOYA AI AGENT"
            textSize = 28f
            setTextColor(Color.parseColor("#00F0FF"))
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Hands-Free Instagram Reel & Screen Automation"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 8, 0, 40)
        })
        apiKeyInput = EditText(this).apply {
            hint = "Paste Gemini API Key here..."
            setText(prefs.getString("GEMINI_API_KEY", ""))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(32, 28, 32, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#38BDF8"))
            }
        }
        root.addView(apiKeyInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 })
        root.addView(btn("1. Save API Key", "#2563EB") {
            prefs.edit().putString("GEMINI_API_KEY", apiKeyInput.text.toString().trim()).apply()
            Toast.makeText(this, "API Key Saved!", Toast.LENGTH_SHORT).show()
        })
        root.addView(btn("2. Enable Glowing Border (Overlay)", "#7C3AED") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else Toast.makeText(this, "Overlay Already Enabled!", Toast.LENGTH_SHORT).show()
        })
        root.addView(btn("3. Enable Screen Control (Accessibility)", "#059669") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        statusText = TextView(this).apply {
            text = "Ready! Mic dabakar bolein:\n\"Instagram par reel post karo\""
            textSize = 15f
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 32)
        }
        root.addView(statusText)
        root.addView(btn("🎙️ ZOYA SE BAAT KAREIN", "#00D4FF", Color.BLACK) { listen() })
        setContentView(root)
        setupSpeech()
    }

    private fun btn(label: String, hex: String, txtCol: Int = Color.WHITE, act: () -> Unit) = Button(this).apply {
        text = label; setTextColor(txtCol); typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { setColor(Color.parseColor(hex)); cornerRadius = 28f }
        setOnClickListener { act() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 135).apply { bottomMargin = 22 }
    }

    private fun listen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101); return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        statusText.text = "Sun rahi hoon... Boliye..."
        speechRecognizer.startListening(intent)
    }

    private fun setupSpeech() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(e: Int, p: Bundle?) {}
            override fun onError(e: Int) { statusText.text = "Awaaz samajh nahi aayi. Dobara try karein." }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                statusText.text = "Aapne kaha: \"$spoken\""
                ZoyaAccessibilityService.instance?.startTask(spoken)
                    ?: Toast.makeText(this@MainActivity, "Pehle Step 3 ON karein!", Toast.LENGTH_LONG).show()
            }
        })
    }
    override fun onDestroy() { super.onDestroy(); speechRecognizer.destroy() }
}
