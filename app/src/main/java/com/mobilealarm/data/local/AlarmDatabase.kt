package com.mobilealarm.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val hour: Int = 7,
    val minute: Int = 30,
    val enabled: Boolean = true,
    val dayMode: String = "WORKDAY",
    /** 逗号分隔的 ISO 星期（1=周一）。 */
    val customDays: String = "1,2,3,4,5",
    /** 仅“只响一次”使用，ISO yyyy-MM-dd。 */
    val oneTimeDate: String? = null,
    val observeHolidays: Boolean = true,
    /** 序列化后的 WeatherRule 列表。 */
    val weatherRulesJson: String = "[]",
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    val ringtone: String? = null,
)

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour * 60 + minute ASC, id ASC")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun findById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms")
    suspend fun getAll(): List<AlarmEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AlarmEntity): Long

    @Update
    suspend fun update(entity: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Delete
    suspend fun delete(entity: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [AlarmEntity::class], version = 2, exportSchema = false)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var instance: AlarmDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN oneTimeDate TEXT")
            }
        }

        fun get(context: Context): AlarmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "mobilealarm.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}