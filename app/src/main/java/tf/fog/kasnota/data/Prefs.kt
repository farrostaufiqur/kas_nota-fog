package tf.fog.kasnota.data

import android.content.Context

/** Preferensi ringan aplikasi offline — hanya menyimpan buku mana yang terakhir dibuka
 * pengguna, supaya splash bisa langsung membukanya lagi (lihat SplashScreen). */
object Prefs {

    private const val FILE = "kasnota_prefs"
    private const val KEY_ACTIVE_BUKU = "active_buku_id"

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var activeBukuId: Int?
        get() = prefs().getInt(KEY_ACTIVE_BUKU, -1).takeIf { it > 0 }
        set(value) {
            prefs().edit().apply {
                if (value == null) remove(KEY_ACTIVE_BUKU) else putInt(KEY_ACTIVE_BUKU, value)
            }.apply()
        }
}
