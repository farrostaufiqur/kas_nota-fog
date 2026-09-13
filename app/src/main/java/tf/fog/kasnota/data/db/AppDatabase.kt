package tf.fog.kasnota.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BukuEntity::class, OpsiEntity::class, TransaksiEntity::class,
        BuktiEntity::class, KelompokEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bukuDao(): BukuDao
    abstract fun opsiDao(): OpsiDao
    abstract fun transaksiDao(): TransaksiDao
    abstract fun buktiDao(): BuktiDao
    abstract fun kelompokDao(): KelompokDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kasnota.db",
                ).build().also { INSTANCE = it }
            }
    }
}
