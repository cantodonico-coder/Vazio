package com.nicoshake.shortsautonext

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var accStatus: TextView
    private lateinit var notifStatus: TextView
    private lateinit var srcStatus: TextView

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val sp = Prefs.sp(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            setBackgroundColor(Color.parseColor("#181818"))
        }

        fun label(t: String, size: Float = 15f) = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.parseColor("#EEEEEE")); setPadding(0, 20, 0, 6)
        }

        col.addView(label("⏭ Shorts Auto Next", 22f))

        accStatus = label("", 14f); col.addView(accStatus)
        col.addView(Button(this).apply {
            text = "1. Ativar acessibilidade"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        notifStatus = label("", 14f); col.addView(notifStatus)
        col.addView(Button(this).apply {
            text = "2. Permitir acesso a notificações"
            setOnClickListener { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        })
        col.addView(TextView(this).apply {
            text = "Necessário para saber quando o Short termina. Nenhuma notificação é lida ou guardada."
            textSize = 12f; setTextColor(Color.GRAY)
        })

        srcStatus = label("", 14f); col.addView(srcStatus)

        col.addView(Switch(this).apply {
            text = "Auto-next ligado"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 32, 0, 16)
            isChecked = Prefs.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setEnabled(this@MainActivity, v) }
        })

        fun numField(key: String, title: String, hint: String, def: Number, decimal: Boolean = false) {
            col.addView(label(title))
            col.addView(TextView(this).apply { text = hint; textSize = 12f; setTextColor(Color.GRAY) })
            col.addView(EditText(this).apply {
                setTextColor(Color.WHITE)
                inputType = InputType.TYPE_CLASS_NUMBER or (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
                val cur: Number = if (decimal) sp.getFloat(key, def.toFloat()) else sp.getInt(key, def.toInt())
                setText(cur.toString())
                setOnFocusChangeListener { _, has ->
                    if (!has) {
                        val e = sp.edit()
                        if (decimal) e.putFloat(key, text.toString().toFloatOrNull()?.coerceAtLeast(0f) ?: 0f)
                        else e.putInt(key, text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0)
                        e.apply()
                    }
                }
            })
        }

        numField("delay", "Atraso entre vídeos (s)", "0 = passa assim que termina", 0f, decimal = true)
        numField("maxSecs", "Pular Shorts longos após (s)", "Opcional · 0 = sempre assistir até o fim", 0)
        numField("stopVideos", "Parar após N vídeos", "0 = sem limite · ao parar, volta para a tela inicial", 0)
        numField("stopMinutes", "Parar após N minutos", "0 = sem limite", 0)

        col.addView(Button(this).apply {
            text = "Salvar"
            setOnClickListener { currentFocus?.clearFocus(); finish() }
        })

        col.addView(label("Liga/desliga rápido: puxe a barra de notificações → editar blocos → arraste \"Shorts Auto\".", 12f))

        setContentView(ScrollView(this).apply {
            addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    override fun onResume() {
        super.onResume()
        val acc = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains(packageName)
        val notif = (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "")
            .contains(packageName)

        fun paint(tv: TextView, ok: Boolean, okTxt: String, badTxt: String) {
            tv.text = if (ok) okTxt else badTxt
            tv.setTextColor(Color.parseColor(if (ok) "#4CAF50" else "#FFB300"))
        }
        paint(accStatus, acc, "✅ Acessibilidade ativa", "⚠️ Acessibilidade desativada")
        paint(notifStatus, notif, "✅ Acesso a notificações liberado", "⚠️ Acesso a notificações pendente")
        srcStatus.text = "Detecção do fim do Short: ${Prefs.source(this)}"
        srcStatus.setTextColor(Color.parseColor("#BBBBBB"))
    }
}
