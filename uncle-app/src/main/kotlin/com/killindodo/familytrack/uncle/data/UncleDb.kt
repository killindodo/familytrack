package com.killindodo.familytrack.uncle.data

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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pairing")
data class PairingEntity(
    @PrimaryKey val id: Int = 1,
    val uncleName: String,
    val watcherNumber: String,
    val code: String,
    val pairedAt: Long,
)

@Entity(tableName = "geofences")
data class GeofenceEntity(
    @PrimaryKey val geofId: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Double,
    val label: String,
)

/** Messages that failed to send (no signal) — retried automatically. */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dest: String,
    val body: String,
    val createdAt: Long,
    val attempts: Int = 0,
)

@Dao
interface UncleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPairing(p: PairingEntity)

    @Query("SELECT * FROM pairing WHERE id = 1")
    suspend fun pairing(): PairingEntity?

    @Query("SELECT * FROM pairing WHERE id = 1")
    fun pairingLive(): Flow<PairingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putGeofence(g: GeofenceEntity)

    @Query("SELECT * FROM geofences")
    suspend fun geofences(): List<GeofenceEntity>

    @Insert
    suspend fun enqueue(o: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    suspend fun outbox(): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun removeOutbox(id: Long)

    @Query("SELECT COUNT(*) FROM outbox")
    fun outboxCount(): Flow<Int>
}

@Database(
    entities = [PairingEntity::class, GeofenceEntity::class, OutboxEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class UncleDb : RoomDatabase() {
    abstract fun dao(): UncleDao

    companion object {
        @Volatile
        private var instance: UncleDb? = null

        fun get(context: Context): UncleDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, UncleDb::class.java, "uncle.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
