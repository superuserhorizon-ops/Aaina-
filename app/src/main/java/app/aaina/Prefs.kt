package app.aaina

import android.content.Context

/** The only things stored on the phone: settings. No usage data. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("aaina", Context.MODE_PRIVATE)

    var limit: Int
        get() = p.getInt("limit", 240)
        set(v) { p.edit().putInt("limit", v).apply() }
    var theme: String
        get() = p.getString("theme", "thermal") ?: "thermal"
        set(v) { p.edit().putString("theme", v).apply() }
    var color: Int
        get() = p.getInt("color", 0xFFD62F74.toInt())
        set(v) { p.edit().putInt("color", v).apply() }
    var hidden: Set<String>
        get() = HashSet(p.getStringSet("hidden", emptySet()) ?: emptySet())
        set(v) { p.edit().putStringSet("hidden", HashSet(v)).apply() }
    var discreet: Boolean
        get() = p.getBoolean("discreet", false)
        set(v) { p.edit().putBoolean("discreet", v).apply() }
    var lockOn: Boolean
        get() = p.getBoolean("lockOn", false)
        set(v) { p.edit().putBoolean("lockOn", v).apply() }
    var nightOn: Boolean
        get() = p.getBoolean("nightOn", false)
        set(v) { p.edit().putBoolean("nightOn", v).apply() }
    var pro: Boolean
        get() = p.getBoolean("pro", false)
        set(v) { p.edit().putBoolean("pro", v).apply() }
    var install: Long
        get() {
            val v = p.getLong("install", 0L)
            if (v != 0L) return v
            val now = System.currentTimeMillis()
            p.edit().putLong("install", now).apply()
            return now
        }
        set(v) { p.edit().putLong("install", v).apply() }

    val daysLeft: Int get() = (30 - (System.currentTimeMillis() - install) / 86_400_000L).toInt()
    fun locked(t: String): Boolean = t != "thermal" && !pro && daysLeft <= 0
    fun effTheme(): String = if (locked(theme)) "thermal" else theme
}
