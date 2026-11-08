package com.nicoshake.shortsautonext

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.SharedPreferences
import android.graphics.Path
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Passa para o próximo Short quando o atual TERMINA.
 * Fonte 1: MediaSession do YouTube (posição e duração reais do vídeo).
 * Fonte 2: barra de progresso do Short, quando ela está acessível.
 * Sem tempo fixo: se nenhuma fonte estiver disponível, não avança.
 */
class AutoNextService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val yt = "com.google.android.youtube"
    private val h = Handler(Looper.getMainLooper())

    private var onShorts = false
    private var shortStart = 0L
    private var sessionStart = 0L
    private var count = 0
    private var lastPosMs = -1L          // última posição lida (MediaSession)
    private var lastFrac = -1f           // última fração lida (barra)
    private var pendingSwipe = false
    private var ignoreScrollUntil = 0L
    private var settleUntil = 0L         // depois de trocar de Short, espera o novo carregar
    private var titleAtSwipe: String? = null

    override fun onServiceConnected() {
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        resetSession()
        h.post(loop)
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == "enabled" && Prefs.enabled(this)) resetSession()
    }

    private fun resetSession() {
        count = 0
        sessionStart = System.currentTimeMillis()
        newShort()
    }

    private fun newShort() {
        shortStart = System.currentTimeMillis()
        lastPosMs = -1L
        lastFrac = -1f
        pendingSwipe = false
        settleUntil = SystemClock.elapsedRealtime() + 1500
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        // usuário arrastou manualmente → novo Short
        if (e?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            e.packageName == yt && System.currentTimeMillis() > ignoreScrollUntil
        ) {
            titleAtSwipe = null
            newShort()
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            runCatching { tick() }
            h.postDelayed(this, 250)
        }
    }

    private fun tick() {
        val root = rootInActiveWindow
        onShorts = root != null && root.packageName == yt && isShortsScreen(root)
        if (!onShorts || !Prefs.enabled(this) || pendingSwipe) return

        val now = System.currentTimeMillis()
        val stopMin = Prefs.stopMinutes(this)
        if (stopMin > 0 && now - sessionStart >= stopMin * 60_000L) {
            stop("Parado: $stopMin min de Shorts")
            return
        }

        // opcional: pular Shorts longos
        val max = Prefs.maxSecs(this)
        if (max > 0 && (now - shortStart) / 1000 >= max) { scheduleNext(); return }

        if (SystemClock.elapsedRealtime() < settleUntil) return

        // ---- Fonte 1: MediaSession ----
        val media = readMedia()
        if (media != null) {
            val (pos, dur, title) = media
            // ainda é o Short antigo? espera o novo carregar (até 4 s)
            if (titleAtSwipe != null && title == titleAtSwipe &&
                SystemClock.elapsedRealtime() < settleUntil + 2500) return
            titleAtSwipe = null
            Prefs.setSource(this, "MediaSession ✅")

            val nearEnd = pos >= dur - 300
            val looped = lastPosMs > dur - 1500 && pos < 1500 // YouTube repete em loop
            lastPosMs = pos
            if (nearEnd || looped) scheduleNext()
            return
        }

        // ---- Fonte 2: barra de progresso ----
        val frac = readProgressBar(root!!)
        if (frac != null) {
            Prefs.setSource(this, "Barra de progresso ✅")
            val ended = frac >= 0.98f || (lastFrac > 0.85f && frac < 0.15f)
            lastFrac = frac
            if (ended) scheduleNext()
            return
        }

        Prefs.setSource(this, "Nenhuma ❌ — permita o acesso a notificações")
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
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun swipeUp() {
        titleAtSwipe = readMedia()?.third
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
            override fun onCompleted(d: GestureDescription?) { newShort() }
            override fun onCancelled(d: GestureDescription?) { pendingSwipe = false }
        }, null)
    }

    // ---------- leitura do vídeo ----------

    /** posição (ms), duração (ms) e título do que o YouTube está tocando */
    private fun readMedia(): Triple<Long, Long, String?>? {
        val msm = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = try {
            msm.getActiveSessions(ComponentName(this, MediaListener::class.java))
        } catch (e: SecurityException) {
            return null // sem acesso a notificações
        }
        val c = sessions.firstOrNull { it.packageName == yt } ?: return null
        val st = c.playbackState ?: return null
        val meta = c.metadata ?: return null
        val dur = meta.getLong(MediaMetadata.METADATA_KEY_DURATION)
        if (dur <= 0) return null
        var pos = st.position
        if (st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        return Triple(pos.coerceIn(0, dur), dur, meta.getString(MediaMetadata.METADATA_KEY_TITLE))
    }

    private fun isShortsScreen(root: AccessibilityNodeInfo): Boolean {
        var found = false
        walk(root, 300) { n ->
            val id = n.viewIdResourceName ?: ""
            if (id.contains("reel_") || id.contains("shorts_")) found = true
            found
        }
        return found
    }

    private fun readProgressBar(root: AccessibilityNodeInfo): Float? {
        var result: Float? = null
        walk(root, 400) { n ->
            val r = n.rangeInfo
            if (r != null && r.max > r.min) {
                val id = n.viewIdResourceName ?: ""
                val cls = n.className?.toString() ?: ""
                if (id.contains("progress") || id.contains("seek") || id.contains("time_bar") ||
                    cls.contains("SeekBar") || cls.contains("ProgressBar")
                ) result = ((r.current - r.min) / (r.max - r.min)).coerceIn(0f, 1f)
            }
            result != null
        }
        return result
    }

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
}
