package com.nicoshake.shortsautonext

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast

class AutoNextService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val yt = "com.google.android.youtube"
    private val h = Handler(Looper.getMainLooper())

    private var onShorts = false
    private var shortStart = 0L          // quando o Short atual começou
    private var sessionStart = 0L        // início da sessão (limite de minutos)
    private var count = 0                // Shorts pulados na sessão
    private var lastProgress = -1f       // 0..1 lido da barra de progresso
    private var pendingSwipe = false
    private var ignoreScrollUntil = 0L   // ignora o scroll gerado pelo nosso próprio gesto

    private var wm: WindowManager? = null
    private var badge: TextView? = null

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        resetSession()
        createBadge()
        h.post(loop)
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        badge?.let { runCatching { wm?.removeView(it) } }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == "enabled" && Prefs.enabled(this)) resetSession()
        paintBadge()
    }

    private fun resetSession() {
        count = 0
        sessionStart = System.currentTimeMillis()
        newShort()
    }

    private fun newShort() {
        shortStart = System.currentTimeMillis()
        lastProgress = -1f
        pendingSwipe = false
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        // usuário arrastou manualmente para outro Short → reinicia contagem do vídeo
        if (e?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            e.packageName == yt && System.currentTimeMillis() > ignoreScrollUntil
        ) newShort()
    }

    // ---------- loop principal (a cada 400 ms) ----------
    private val loop = object : Runnable {
        override fun run() {
            runCatching { tick() }
            h.postDelayed(this, 400)
        }
    }

    private fun tick() {
        val root = rootInActiveWindow
        val inYt = root?.packageName == yt
        onShorts = inYt && root != null && isShortsScreen(root)
        paintBadge()
        if (!onShorts || !Prefs.enabled(this) || pendingSwipe) return

        val now = System.currentTimeMillis()

        // limite de minutos
        val stopMin = Prefs.stopMinutes(this)
        if (stopMin > 0 && now - sessionStart >= stopMin * 60_000L) {
            stop("Parado: $stopMin min de Shorts")
            return
        }

        val elapsed = (now - shortStart) / 1000f
        val prog = readProgress(root!!)
        var ended = false

        if (prog != null) {
            // chegou ao fim, ou voltou ao início (YouTube repete o Short em loop)
            if (prog >= 0.97f) ended = true
            if (lastProgress > 0.85f && prog < 0.15f) ended = true
            lastProgress = prog
        } else if (elapsed >= Prefs.fallbackSecs(this)) {
            ended = true // não deu pra ler a barra → usa o tempo padrão
        }

        val max = Prefs.maxSecs(this)
        if (max > 0 && elapsed >= max) ended = true

        if (ended) scheduleNext()
    }

    private fun scheduleNext() {
        pendingSwipe = true
        val delayMs = (Prefs.delay(this) * 1000).toLong()
        h.postDelayed({
            if (!Prefs.enabled(this) || !onShorts) { pendingSwipe = false; return@postDelayed }
            count++
            val stopV = Prefs.stopVideos(this)
            if (stopV > 0 && count >= stopV) {
                stop("Parado: $count Shorts assistidos")
                return@postDelayed
            }
            swipeUp()
        }, delayMs)
    }

    private fun stop(msg: String) {
        Prefs.setEnabled(this, false)
        pendingSwipe = false
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        performGlobalAction(GLOBAL_ACTION_HOME) // sai dos Shorts de verdade
    }

    // ---------- gesto de arrastar para cima ----------
    private fun swipeUp() {
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.5f
        val path = Path().apply {
            moveTo(x, dm.heightPixels * 0.72f)
            lineTo(x, dm.heightPixels * 0.28f)
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 220))
            .build()
        ignoreScrollUntil = System.currentTimeMillis() + 1200
        dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { newShort(); paintBadge() }
            override fun onCancelled(d: GestureDescription?) { pendingSwipe = false }
        }, null)
    }

    // ---------- leitura da tela do YouTube ----------
    private fun isShortsScreen(root: AccessibilityNodeInfo): Boolean {
        var found = false
        walk(root, 300) { n ->
            val id = n.viewIdResourceName ?: ""
            if (id.contains("reel_") || id.contains("shorts_")) found = true
            found
        }
        return found
    }

    /** procura uma barra de progresso (SeekBar/ProgressBar) e devolve 0..1 */
    private fun readProgress(root: AccessibilityNodeInfo): Float? {
        var result: Float? = null
        walk(root, 400) { n ->
            val r = n.rangeInfo
            if (r != null && r.max > r.min) {
                val id = n.viewIdResourceName ?: ""
                val cls = n.className?.toString() ?: ""
                if (id.contains("progress") || id.contains("seek") || cls.contains("SeekBar") || cls.contains("ProgressBar")) {
                    result = ((r.current - r.min) / (r.max - r.min)).coerceIn(0f, 1f)
                }
            }
            result != null
        }
        return result
    }

    /** percorre a árvore até `limit` nós; para quando `visit` retorna true */
    private fun walk(root: AccessibilityNodeInfo, limit: Int, visit: (AccessibilityNodeInfo) -> Boolean) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var seen = 0
        while (stack.isNotEmpty() && seen < limit) {
            val n = stack.removeLast()
            seen++
            if (visit(n)) return
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
    }

    // ---------- botão flutuante liga/desliga ----------
    private fun createBadge() {
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(28, 14, 28, 14)
            background = GradientDrawable().apply { cornerRadius = 60f }
            setOnClickListener { Prefs.setEnabled(this@AutoNextService, !Prefs.enabled(this@AutoNextService)) }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24; y = 180
        }
        runCatching { wm?.addView(tv, lp) }
        badge = tv
        paintBadge()
    }

    private fun paintBadge() {
        val b = badge ?: return
        val on = Prefs.enabled(this)
        b.visibility = if (onShorts) android.view.View.VISIBLE else android.view.View.GONE
        (b.background as? GradientDrawable)?.setColor(if (on) 0xE6E62117.toInt() else 0xE6555555.toInt())
        val stopV = Prefs.stopVideos(this)
        b.text = if (on) "⏭ Auto ON · $count" + (if (stopV > 0) "/$stopV" else "") else "⏸ Auto OFF"
    }
}
