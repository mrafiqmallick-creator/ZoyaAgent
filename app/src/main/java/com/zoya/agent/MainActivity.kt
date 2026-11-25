package com.zoya.agent

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var liveStatusText: TextView
    private lateinit var chatInput: EditText
    private lateinit var sphereView: GoldenSphereView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#04091A")
        window.navigationBarColor = Color.parseColor("#04091A")
        tts = TextToSpeech(this, this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#071330"), Color.parseColor("#04091A"), Color.parseColor("#020510"))
            )
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(44, 36, 44, 12)
        }
        val menuIcon = TextView(this).apply {
            text = "☰   Zoya"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val keyChip = TextView(this).apply {
            text = "🔑 API Key"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(28, 14, 28, 14)
            background = roundedBox("#11224D", "#1E3A8A", 40f)
            setOnClickListener { showApiKeyDialog() }
        }
        topBar.addView(menuIcon)
        topBar.addView(keyChip)

        val greetLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 8)
        }
        greetLayout.addView(TextView(this).apply { text = "Good evening,"; textSize = 18f; setTextColor(Color.parseColor("#CBD5E1")) })
        greetLayout.addView(TextView(this).apply { text = "BOSS"; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        liveStatusText = TextView(this).apply {
            text = "Sun rahi hoon... Screen share hai aapki"
            textSize = 13f
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(0, 6, 0, 0)
        }
        greetLayout.addView(liveStatusText)

        val orbWrapper = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        sphereView = GoldenSphereView(this).apply {
            layoutParams = FrameLayout.LayoutParams(540, 540, Gravity.CENTER)
            setOnClickListener { startVoiceListening() }
        }
        val zoyaLabel = TextView(this).apply {
            text = "Z  O  Y  A"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FEF9C3"))
            setShadowLayer(18f, 0f, 0f, Color.parseColor("#FDE047"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        orbWrapper.addView(sphereView)
        orbWrapper.addView(zoyaLabel)

        val pillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(36, 8, 36, 18)
        }
        pillRow.addView(createPill("🎵 Gaane") { runCommand("YouTube music kholo") })
        pillRow.addView(createPill("⚡ Overlay") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                ZoyaAccessibilityService.instance?.showFloatingHud("Zoya Active")
                Toast.makeText(this, "Floating Golden Orb Active!", Toast.LENGTH_SHORT).show()
            }
        })
        pillRow.addView(createPill("🤖 Control") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })

        val cardRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(36, 0, 36, 20)
        }
        val dayStr = SimpleDateFormat("dd", Locale.getDefault()).format(Date())
        val monStr = SimpleDateFormat("EEE, MMM", Locale.getDefault()).format(Date())
        cardRow.addView(createInfoCard("☁️ Mausam", "23°", "Clear • 72%"))
        cardRow.addView(createInfoCard("📅 Aaj", dayStr, monStr))
        cardRow.addView(createInfoCard("❤️ Mood", "Ready", "Boss Mode"))

        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 12, 24, 12)
            background = roundedBox("#0E1B3D", "#1D4ED8", 60f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = 36; rightMargin = 36; bottomMargin = 20
            }
        }
        val micBtn = TextView(this).apply { text = "🎙️"; textSize = 20f; setPadding(12, 8, 16, 8); setOnClickListener { startVoiceListening() } }
        chatInput = EditText(this).apply {
            hint = "Zoya se kuch bhi poocho..."
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            textSize = 14f
            background = null
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sendBtn = TextView(this).apply {
            text = "➤"; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = roundedBox("#2563EB", "#60A5FA", 50f)
            layoutParams = LinearLayout.LayoutParams(92, 92)
            setOnClickListener {
                val cmd = chatInput.text.toString().trim()
                if (cmd.isNotEmpty()) { chatInput.setText(""); runCommand(cmd) } else startVoiceListening()
            }
        }
        inputBar.addView(micBtn); inputBar.addView(chatInput); inputBar.addView(sendBtn)

        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#060D24")); setPadding(20, 14, 20, 18)
        }
        navBar.addView(createNavText("🏠\nHome")); navBar.addView(createNavText("⛶\nScan"))
        navBar.addView(TextView(this).apply {
            text = "◉"; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = roundedBox("#2563EB", "#93C5FD", 60f)
            layoutParams = LinearLayout.LayoutParams(110, 110)
            setOnClickListener { startVoiceListening() }
        })
        navBar.addView(createNavText("⚙️\nSystem")); navBar.addView(createNavText("💬\nChat"))

        root.addView(topBar); root.addView(greetLayout); root.addView(orbWrapper)
        root.addView(pillRow); root.addView(cardRow); root.addView(inputBar); root.addView(navBar)
        setContentView(root)
    }

    private fun roundedBox(fillHex: String, strokeHex: String, radius: Float) = GradientDrawable().apply {
        setColor(Color.parseColor(fillHex)); setStroke(2, Color.parseColor(strokeHex)); cornerRadius = radius
    }

    private fun createPill(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 12f; setTextColor(Color.WHITE); setPadding(30, 16, 30, 16)
        background = roundedBox("#0F1D42", "#1E3A8A", 45f)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = 16 }
        setOnClickListener { onClick() }
    }

    private fun createInfoCard(header: String, bigVal: String, subVal: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 20)
        background = roundedBox("#0B1636", "#172554", 28f)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 12 }
        addView(TextView(context).apply { text = header; textSize = 11f; setTextColor(Color.parseColor("#93C5FD")) })
        addView(TextView(context).apply { text = bigVal; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0, 6, 0, 4) })
        addView(TextView(context).apply { text = subVal; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")) })
    }

    private fun createNavText(label: String) = TextView(this).apply {
        text = label; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun showApiKeyDialog() {
        val prefs = getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE)
        val input = EditText(this).apply { hint = "Paste Gemini API Key (AIzaSy...)"; setText(prefs.getString("gemini_key", "")) }
        AlertDialog.Builder(this).setTitle("Gemini API Key (AIzaSy...)").setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("gemini_key", input.text.toString().trim()).apply()
                speakHindi("API key save ho gayi hai Boss.")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun startVoiceListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { liveStatusText.text = "Sun rahi hoon Boss... Boliye!"; liveStatusText.setTextColor(Color.parseColor("#FDE047")) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { val s = 1.0f + (rmsdB.coerceIn(0f, 10f) / 30f); sphereView.scaleX = s; sphereView.scaleY = s }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { liveStatusText.text = "Samajh rahi hoon..." }
            override fun onError(error: Int) { liveStatusText.text = "Orb par tap karke dobara bolein" }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                liveStatusText.text = "Aapne kaha: \"$spoken\""
                runCommand(spoken)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!p.isNullOrEmpty()) liveStatusText.text = p
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun runCommand(command: String) {
        val lower = command.lowercase()
        liveStatusText.text = "Kaam shuru: $command"
        if (lower.contains("instagram") || lower.contains("reel")) {
            speakHindi("Ji Boss, main Instagram khol kar reel post kar rahi hoon.")
            ZoyaAccessibilityService.pendingTask = command
            ZoyaAccessibilityService.instance?.showFloatingHud(command)
            packageManager.getLaunchIntentForPackage("com.instagram.android")?.let { startActivity(it) }
        } else {
            speakHindi("Ji Boss, $command par kaam kar rahi hoon.")
            ZoyaAccessibilityService.pendingTask = command
            ZoyaAccessibilityService.instance?.showFloatingHud(command)
        }
    }

    private fun speakHindi(msg: String) { tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "ZOYA_MSG") }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) { tts?.language = Locale("hi", "IN"); tts?.setPitch(1.06f) } }
    override fun onDestroy() { tts?.shutdown(); speechRecognizer?.destroy(); super.onDestroy() }
}

class GoldenSphereView(context: Context) : View(context) {
    private var angle = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 6000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { angle = it.animatedValue as Float; invalidate() }; start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f; val r = (minOf(width, height) / 2f) * 0.85f
        paint.shader = RadialGradient(cx, cy, r * 1.15f, intArrayOf(Color.parseColor("#AAFEF08A"), Color.parseColor("#66EAB308"), Color.parseColor("#332563EB"), Color.TRANSPARENT), floatArrayOf(0.2f, 0.55f, 0.82f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.15f, paint)
        canvas.save(); canvas.rotate(angle, cx, cy); paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
        paint.color = Color.parseColor("#99FEF08A"); canvas.drawOval(RectF(cx - r * 0.78f, cy - r * 0.52f, cx + r * 0.78f, cy + r * 0.52f), paint)
        canvas.rotate(60f, cx, cy); paint
