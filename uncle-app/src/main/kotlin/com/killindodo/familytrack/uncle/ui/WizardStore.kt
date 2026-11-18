package com.killindodo.familytrack.uncle.ui

import android.content.Context

/** Wizard progress survives app relaunches — the uncle must not lose his place. */
object WizardStore {
    private const val FILE = "wizard"

    data class Saved(val name: String, val code: String, val number: String, val step: Int)

    fun load(ctx: Context): Saved? {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val name = sp.getString("name", null) ?: return null
        return Saved(
            name = name,
            code = sp.getString("code", "").orEmpty(),
            number = sp.getString("number", "").orEmpty(),
            step = sp.getInt("step", 0),
        )
    }

    fun put(ctx: Context, name: String, code: String, number: String, step: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("name", name)
            .putString("code", code)
            .putString("number", number)
            .putInt("step", step)
            .apply()
    }

    fun done(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("done", false)

    fun markDone(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("done", true).apply()
    }
}
