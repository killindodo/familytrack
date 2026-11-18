package com.killindodo.familytrack.watcher.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "uncles")
data class UncleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val number: String,
    val colorIndex: Int,
    val pairedAt: Long,
    // latest known state (updated on every received fix)
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastAcc: Double? = null,
    val lastSpd: Double? = null,
    val lastBat: Int? = null,
    val lastSeenAt: Long? = null,
    val lastSource: String? = null,
)

@Entity(tableName = "fixes")
data class FixEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uncleId: Long,
    val lat: Double,
    val lng: Double,
    val acc: Double,
    val spd: Double,
    val bat: Int,
    val receivedAt: Long,
    val source: String, // CHECK | SOS | GEOF
)

/** One-time pairing codes shown as QR/6-digit in the Add-uncle screen. */
@Entity(tableName = "pair_codes")
data class PairCodeEntity(
    @PrimaryKey val code: String,
    val createdAt: Long,
    val usedAt: Long? = null,
)

/** A "Check location" we sent — powers the 5-minute did-not-respond alarm. */
@Entity(tableName = "checks")
data class CheckEntity(
    @PrimaryKey val msgId: Long,
    val uncleId: Long,
    val sentAt: Long,
    val answeredAt: Long? = null,
)

@Entity(tableName = "geofences")
data class GeofEntity(
    @PrimaryKey val geofId: String,
    val uncleId: Long,
    val lat: Double,
    val lng: Double,
    val radiusM: Double,
    val label: String,
    val createdAt: Long,
)

@Dao
interface WatcherDao {
    @Insert
    suspend fun insertUncle(u: UncleEntity): Long

    @Query("SELECT * FROM uncles ORDER BY pairedAt ASC")
    fun unclesLive(): Flow<List<UncleEntity>>

    @Query("SELECT * FROM uncles WHERE id = :id")
    suspend fun uncle(id: Long): UncleEntity?

    @Query("SELECT * FROM uncles WHERE number = :number LIMIT 1")
    suspend fun uncleByNumber(number: String): UncleEntity?

    @Update
    suspend fun updateUncle(u: UncleEntity)

    @Query("SELECT COUNT(*) FROM uncles")
    suspend fun uncleCount(): Int

    @Insert
    suspend fun insertFix(f: FixEntity): Long

    @Query("SELECT * FROM fixes WHERE uncleId = :uncleId AND receivedAt BETWEEN :from AND :to ORDER BY receivedAt ASC")
    suspend fun fixesBetween(uncleId: Long, from: Long, to: Long): List<FixEntity>

    @Query("DELETE FROM fixes WHERE receivedAt < :before")
    suspend fun deleteOldFixes(before: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPairCode(c: PairCodeEntity)

    @Query("SELECT * FROM pair_codes WHERE code = :code LIMIT 1")
    suspend fun pairCode(code: String): PairCodeEntity?

    @Query("UPDATE pair_codes SET usedAt = :usedAt WHERE code = :code")
    suspend fun markPairCodeUsed(code: String, usedAt: Long)

    @Insert
    suspend fun insertCheck(c: CheckEntity)

    @Query("SELECT * FROM checks WHERE msgId = :msgId LIMIT 1")
    suspend fun check(msgId: Long): CheckEntity?

    @Query("UPDATE checks SET answeredAt = :at WHERE msgId = :msgId")
    suspend fun markCheckAnswered(msgId: Long, at: Long)

    @Query("SELECT * FROM checks WHERE answeredAt IS NULL")
    suspend fun unansweredChecks(): List<CheckEntity>

    @Query("DELETE FROM checks WHERE sentAt < :before")
    suspend fun deleteOldChecks(before: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putGeof(g: GeofEntity)

    @Query("SELECT * FROM geofences WHERE uncleId = :uncleId")
    suspend fun geofencesFor(uncleId: Long): List<GeofEntity>

    @Query("SELECT * FROM geofences")
    suspend fun geofences(): List<GeofEntity>
}

@Database(
    entities = [UncleEntity::class, FixEntity::class, PairCodeEntity::class, CheckEntity::class, GeofEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WatcherDb : RoomDatabase() {
    abstract fun dao(): WatcherDao

    companion object {
        @Volatile
        private var instance: WatcherDb? = null

        fun get(context: Context): WatcherDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, WatcherDb::class.java, "watcher.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
