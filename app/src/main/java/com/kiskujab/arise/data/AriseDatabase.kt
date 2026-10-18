package com.kiskujab.arise.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AriseDao {
    // Player
    @Query("SELECT * FROM player WHERE id = 0")
    fun playerFlow(): Flow<PlayerEntity?>

    @Query("SELECT * FROM player WHERE id = 0")
    suspend fun player(): PlayerEntity?

    @Upsert
    suspend fun upsertPlayer(p: PlayerEntity)

    // Days
    @Query("SELECT * FROM days WHERE epochDay = :day")
    suspend fun day(day: Long): DayEntity?

    @Query("SELECT * FROM days WHERE epochDay = :day")
    fun dayFlow(day: Long): Flow<DayEntity?>

    @Query("SELECT * FROM days WHERE finalized = 1 ORDER BY epochDay DESC")
    fun finalizedDaysFlow(): Flow<List<DayEntity>>

    @Upsert
    suspend fun upsertDay(d: DayEntity)

    // Goals
    @Query("SELECT * FROM goals WHERE effectiveFrom <= :day ORDER BY effectiveFrom DESC LIMIT 1")
    suspend fun goalsFor(day: Long): GoalsEntity?

    @Query("SELECT * FROM goals ORDER BY effectiveFrom DESC")
    fun goalsFlow(): Flow<List<GoalsEntity>>

    @Upsert
    suspend fun upsertGoals(g: GoalsEntity)

    // Vacations
    @Query("SELECT * FROM vacations ORDER BY startDay")
    suspend fun vacations(): List<VacationEntity>

    @Query("SELECT * FROM vacations ORDER BY startDay")
    fun vacationsFlow(): Flow<List<VacationEntity>>

    @Insert
    suspend fun insertVacation(v: VacationEntity): Long

    @Upsert
    suspend fun upsertVacation(v: VacationEntity)

    @Query("DELETE FROM vacations WHERE id = :id AND startDay > :today")
    suspend fun deleteFutureVacation(id: Long, today: Long)

    // Custom criteria
    @Query("SELECT * FROM custom_criteria ORDER BY id")
    suspend fun customCriteria(): List<CustomCriterionEntity>

    @Query("SELECT * FROM custom_criteria ORDER BY id")
    fun customCriteriaFlow(): Flow<List<CustomCriterionEntity>>

    @Insert
    suspend fun insertCustom(c: CustomCriterionEntity): Long

    @Upsert
    suspend fun upsertCustom(c: CustomCriterionEntity)

    @Query("DELETE FROM custom_criteria WHERE id = :id AND createdDay > :today")
    suspend fun deleteFutureCustom(id: Long, today: Long)

    // Events
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(e: EventEntity): Long

    @Query("SELECT * FROM events WHERE seen = 0 ORDER BY id")
    fun unseenEventsFlow(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY id DESC LIMIT 300")
    fun eventLogFlow(): Flow<List<EventEntity>>

    @Query("UPDATE events SET seen = 1 WHERE id = :id")
    suspend fun markSeen(id: Long)

    @Query("UPDATE events SET seen = 1")
    suspend fun markAllSeen()
}

@Database(
    entities = [
        PlayerEntity::class, DayEntity::class, GoalsEntity::class, VacationEntity::class,
        CustomCriterionEntity::class, EventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AriseDatabase : RoomDatabase() {
    abstract fun dao(): AriseDao

    companion object {
        fun create(context: Context): AriseDatabase =
            Room.databaseBuilder(context, AriseDatabase::class.java, "arise.db")
                // Never fall back to a destructive migration: the System does not forget.
                .build()
    }
}
