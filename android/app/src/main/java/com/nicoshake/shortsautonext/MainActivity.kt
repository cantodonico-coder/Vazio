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

    private lateinit var status: TextView

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val sp = Prefs.sp(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            setBackgroundColor(Color.parseColor("#181818"))
        }

        fun label(t: String, size: Float = 15f) = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.parseColor("#EEEEEE")); setPadding(0, 24, 0, 6)
        }

        col.addView(label("⏭ Shorts Auto Next", 22f))

        status = label("", 14f)
        col.addView(status)

        col.addView(Button(this).apply {
            text = "1. Ativar acessibilidade"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

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

        numField("delay", "Atraso entre vídeos (s)", "0 = imediato", 0f, decimal = true)
        numField("maxSecs", "Pular Shorts após (s)", "0 = assistir inteiro", 0)
        numField("stopVideos", "Parar após N vídeos", "0 = sem limite · ao parar, volta para a tela inicial", 0)
        numField("stopMinutes", "Parar após N minutos", "0 = sem limite", 0)
        numField("fallbackSecs", "Tempo padrão (s)", "Usado se o app não conseguir ler a barra de progresso do Short", 30)

        col.addView(Button(this).apply {
            text = "Salvar"
            setOnClickListener { currentFocus?.clearFocus(); finish() }
        })

        col.addView(label("Botão flutuante vermelho aparece nos Shorts: toque para ligar/desligar.", 12f))

        setContentView(ScrollView(this).apply {
            addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    override fun onResume() {
        super.onResume()
        val on = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains(packageName)
        status.text = if (on) "✅ Serviço ativo — abra os Shorts no YouTube" else "⚠️ Serviço desativado — toque no botão abaixo"
        status.setTextColor(if (on) Color.parseColor("#4CAF50") else Color.parseColor("#FFB300"))
    }
}
