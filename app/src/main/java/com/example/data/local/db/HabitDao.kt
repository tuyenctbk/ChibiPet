package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY createdAt ASC")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    fun observeAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    fun observeHabitById(id: Long): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getHabitById(id: Long): HabitEntity?

    @Query("SELECT COUNT(*) FROM habits WHERE isArchived = 0")
    fun observeActiveHabitCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(habits: List<HabitEntity>): List<Long>

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitById(id: Long)

    @Query("UPDATE habits SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchivedStatus(id: Long, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())

    // Completion logs queries
    @Query("SELECT * FROM habit_completion_logs WHERE completionDate = :date")
    fun observeCompletionsForDate(date: String): Flow<List<HabitCompletionLogEntity>>

    @Query("SELECT * FROM habit_completion_logs WHERE habitId = :habitId ORDER BY completionDate DESC")
    fun observeCompletionsForHabit(habitId: Long): Flow<List<HabitCompletionLogEntity>>

    @Query("SELECT * FROM habit_completion_logs WHERE completionDate >= :startDate ORDER BY completionDate ASC")
    fun observeRecentCompletions(startDate: String): Flow<List<HabitCompletionLogEntity>>

    @Query("SELECT completionDate FROM habit_completion_logs WHERE habitId = :habitId ORDER BY completionDate DESC")
    suspend fun getCompletedDatesForHabit(habitId: Long): List<String>

    @Query("SELECT * FROM habit_completion_logs WHERE habitId = :habitId AND completionDate = :date LIMIT 1")
    suspend fun getCompletionForDate(habitId: Long, date: String): HabitCompletionLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletionLog(log: HabitCompletionLogEntity): Long

    @Query("DELETE FROM habit_completion_logs WHERE habitId = :habitId AND completionDate = :date")
    suspend fun deleteCompletionLog(habitId: Long, date: String): Int

    @Query("SELECT COUNT(DISTINCT habitId) FROM habit_completion_logs WHERE completionDate = :date")
    fun observeCompletedCountForDate(date: String): Flow<Int>

    @Query("SELECT SUM(xpEarned) FROM habit_completion_logs WHERE completionDate = :date")
    fun observeTotalXpEarnedForDate(date: String): Flow<Int?>
}
