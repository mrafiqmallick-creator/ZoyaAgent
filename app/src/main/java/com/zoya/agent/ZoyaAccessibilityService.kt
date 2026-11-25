package com.zoya.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ZoyaAccessibilityService : AccessibilityService() {
    companion object {
        var instance: ZoyaAccessibilityService? = null
        var pendingTask: String? = null
    }
    private var wm: WindowManager? = null
    private var hudOverlay: MayaHudOverlayView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stepStage = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun showFloatingHud(taskTitle: String) {
        if (!Settings.canDrawOverlays(this)) return
        handler.post {
            if (hudOverlay == null) {
                hudOverlay = MayaHudOverlayView(this)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.TOP or Gravity.START }
                wm?.addView(hudOverlay, params)
            }
            hudOverlay?.visibility = View.VISIBLE
            stepStage = 0
        }
    }

    fun hideFloatingHud() { handler.post { hudOverlay?.visibility = View.GONE } }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val task = pendingTask ?: return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != "com.instagram.android") return
        val root = rootInActiveWindow ?: return

        when {
            findAndClick(root, listOf("Create", "New post", "Plus")) -> {}
            findAndClick(root, listOf("REEL", "Reel")) -> {}
            stepStage == 0 && clickFirstGalleryVideo(root) -> { stepStage = 1 }
            findAndClick(root, listOf("Next", "Done", "Continue")) -> {}
            fillCaptionIfVisible(root, "Trending Vibes ✨🔥 #reels #viral #trending") -> {}
            findAndClick(root, listOf("Share")) -> {
                pendingTask = null
                handler.postDelayed({ hideFloatingHud() }, 3500)
            }
        }
    }

    private fun findAndClick(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        for (kw in keywords) {
            for (node in root.findAccessibilityNodeInfosByText(kw)) {
                val rect = Rect(); node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    hudOverlay?.setTarget(rect.exactCenterX(), rect.exactCenterY())
                    if (node.isClickable) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
                    var p = node.parent
                    while (p != null) { if (p.isClickable) { p.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }; p = p.parent }
                    performTap(rect.exactCenterX(), rect.exactCenterY()); return true
                }
            }
        }
        return false
    }

    private fun clickFirstGalleryVideo(root: AccessibilityNodeInfo): Boolean {
        val list = ArrayList<AccessibilityNodeInfo>(); collectNodes(root, list)
        for (n in list) {
            val desc = n.contentDescription?.toString()?.lowercase() ?: ""
            if (desc.contains("video") || desc.contains("reel") || desc.contains("0:")) {
                val rect = Rect(); n.getBoundsInScreen(rect)
                if (rect.width() > 80 && rect.height() > 80) {
                    hudOverlay?.setTarget(rect.exactCenterX(), rect.exactCenterY())
                    performTap(rect.exactCenterX(), rect.exactCenterY()); return true
                }
            }
        }
        return false
    }

    private fun fillCaptionIfVisible(root: AccessibilityNodeInfo, captionText: String): Boolean {
        val list = ArrayList<AccessibilityNodeInfo>(); collectNodes(root, list)
        for (n in list) {
            if (n.className?.toString()?.contains("EditText") == true) {
                val existing = n.text?.toString() ?: ""
                if (existing.isEmpty() || existing.contains("Write a caption")) {
                    val rect = Rect(); n.getBoundsInScreen(rect)
                    hudOverlay?.setTarget(rect.exactCenterX(), rect.exactCenterY())
                    val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, captionText) }
                    n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args); return true
                }
            }
        }
        return false
    }

    private fun collectNodes(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        out.add(node)
        for (i in 0 until node.childCount) collectNodes(node.getChild(i), out)
    }

    private fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build(), null, null)
    }

    override fun onInterrupt() {}
}

class MayaHudOverlayView(context: Context) : View(context) {
    private var targetX = -1f; private var targetY = -1f; private var phase = 0f
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 14f }
    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f }

    init {
        post(object : Runnable { override fun run() { phase = (phase + 0.04f) % 1f; invalidate(); postDelayed(this, 30) } })
    }

    fun setTarget(x: Float, y: Float) { targetX = x; targetY = y; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val matrix = Matrix().apply { setRotate(phase * 360f, w / 2f, h / 2f) }
        val sweep = SweepGradient(w / 2f, h / 2f, intArrayOf(Color.parseColor("#FF007F"), Color.parseColor("#38BDF8"), Color.parseColor("#A855F7"), Color.parseColor("#FACC15"), Color.parseColor("#FF007F")), null)
        sweep.setLocalMatrix(matrix); borderPaint.shader = sweep
        canvas.drawRoundRect(RectF(7f, 7f, w - 7f, h - 7f), 54f, 54f, borderPaint)

        val ox = w * 0.34f; val oy = h * 0.22f; val or = 115f
        orbPaint.shader = RadialGradient(ox, oy, or, intArrayOf(Color.parseColor("#EEFEF08A"), Color.parseColor("#AAEAB308"), Color.parseColor("#552563EB"), Color.TRANSPARENT), floatArrayOf(0.2f, 0.6f, 0.85f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(ox, oy, or, orbPaint)

        if (targetX > 0 && targetY > 0) {
            targetPaint.color = Color.parseColor("#FF1744"); canvas.drawCircle(targetX, targetY, 46f, targetPaint)
            targetPaint.color = Color.parseColor("#00E5FF"); canvas.drawCircle(targetX, targetY, 24f, targetPaint)
            canvas.drawLine(targetX - 60f, targetY, targetX + 60f, targetY, targetPaint)
            canvas.drawLine(targetX, targetY - 60f, targetX, targetY + 60f, targetPaint)
        }
    }
}
