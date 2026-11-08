package com.nicoshake.shortsautonext

import android.content.Context

object Prefs {
    private const val FILE = "cfg"

    fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    /** atraso entre vídeos (s) */
    fun delay(c: Context) = sp(c).getFloat("delay", 0f)
    /** pular Short após X s (0 = inteiro) */
    fun maxSecs(c: Context) = sp(c).getInt("maxSecs", 0)
    /** parar após N vídeos (0 = sem limite) */
    fun stopVideos(c: Context) = sp(c).getInt("stopVideos", 0)
    /** parar após N minutos (0 = sem limite) */
    fun stopMinutes(c: Context) = sp(c).getInt("stopMinutes", 0)
    /** se não conseguir ler a barra de progresso, avança após X s */
    fun fallbackSecs(c: Context) = sp(c).getInt("fallbackSecs", 30)
}
