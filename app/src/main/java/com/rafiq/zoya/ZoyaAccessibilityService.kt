package com.rafiq.zoya

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ZoyaAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {
    companion object { var instance: ZoyaAccessibilityService? = null }
    private var wm: WindowManager? = null
    private var glow: View? = null
    private var tts: TextToSpeech? = null
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var running = false
    private var goal = ""

    override fun onServiceConnected() {
        super.onServiceConnected(); instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale("hi", "IN") }
    private fun speak(t: String) { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "ZoyaTTS") }

    fun startTask(cmd: String) {
        goal = cmd; running = true; showGlow()
        speak("Theek hai, main $cmd shuru kar rahi hoon.")
        if (cmd.lowercase().contains("instagram") || cmd.lowercase().contains("reel")) {
            packageManager.getLaunchIntentForPackage("com.instagram.android")?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
            }
        }
        scope.launch { delay(3000); loop() }
    }

    private suspend fun loop() {
        var step = 0
        while (running && step < 15) {
            step++
            val res = askGemini(goal, dump())
            if (res != null) {
                val act = res.optString("action")
                val msg = res.optString("speak")
                if (msg.isNotEmpty()) speak(msg)
                when (act) {
                    "CLICK_TEXT" -> clickText(res.optString("target"))
                    "TYPE_TEXT" -> typeText(res.optString("text"))
                    "TAP_XY" -> tap(res.optDouble("x", 500.0).toFloat(), res.optDouble("y", 500.0).toFloat())
                    "ASK_CONFIRMATION", "DONE" -> { running = false; hideGlow() }
                }
            }
            delay(3000)
        }
        hideGlow()
    }

    private fun dump(): String {
        val root = rootInActiveWindow ?: return "[]"
        val arr = JSONArray()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            val t = n.text?.toString() ?: ""; val d = n.contentDescription?.toString() ?: ""
            if (t.isNotEmpty() || d.isNotEmpty() || n.isClickable || n.isEditable) {
                val r = Rect(); n.getBoundsInScreen(r)
                arr.put(JSONObject().apply {
                    put("text", t); put("desc", d); put("clickable", n.isClickable)
                    put("editable", n.isEditable); put("x", r.centerX()); put("y", r.centerY())
                })
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return arr.toString()
    }

    private suspend fun askGemini(g: String, ui: String): JSONObject? = withContext(Dispatchers.IO) {
        val key = getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE).getString("GEMINI_API_KEY", "") ?: ""
        if (key.isEmpty()) return@withContext null
        val prompt = "You are Zoya AI automating an Android phone in Hindi. Goal: \"$g\". Screen UI: $ui. Decide next step for Instagram Reel upload (+, video, Next, audio/song, caption+hashtags, STOP before Share to ask confirmation). Reply ONLY JSON: {\"action\":\"CLICK_TEXT\"|\"TYPE_TEXT\"|\"TAP_XY\"|\"ASK_CONFIRMATION\"|\"DONE\",\"target\":\"\",\"text\":\"\",\"x\":540,\"y\":1200,\"speak\":\"Hindi update\"}"
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        try {
            val raw = JSONObject(client.newCall(req).execute().body?.string() ?: "").getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            JSONObject(raw.replace("```json", "").replace("```", "").trim())
        } catch (e: Exception) { null }
    }

    private fun clickText(t: String) {
        rootInActiveWindow?.findAccessibilityNodeInfosByText(t)?.forEach {
            if (it.isClickable) { it.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }
            if (it.parent?.isClickable == true) { it.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }
        }
    }

    private fun typeText(t: String) {
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t) }
        )
    }

    private fun tap(x: Float, y: Float) {
        val p = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 80)).build(), null, null)
    }

    private fun showGlow() {
        if (!Settings.canDrawOverlays(this) || glow != null) return
        Handler(Looper.getMainLooper()).post {
            glow = object : View(this) {
                val p = Paint().apply {
                    style = Paint.Style.STROKE; strokeWidth = 22f
                    shader = LinearGradient(0f, 0f, 1080f, 2400f, intArrayOf(Color.CYAN, Color.MAGENTA, Color.BLUE, Color.CYAN), null, Shader.TileMode.CLAMP)
                    maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
                }
                override fun onDraw(c: Canvas) { super.onDraw(c); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p) }
            }
            wm?.addView(glow, WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT))
        }
    }

    private fun hideGlow() {
        Handler(Looper.getMainLooper()).post { glow?.let { wm?.removeView(it); glow = null } }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() { running = false; hideGlow() }
}
