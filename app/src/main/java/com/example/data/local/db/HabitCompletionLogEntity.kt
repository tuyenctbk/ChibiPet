package com.example.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_completion_logs",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["habitId"]),
        Index(value = ["habitId", "completionDate"], unique = true),
        Index(value = ["completionDate"])
    ]
)
data class HabitCompletionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val habitId: Long,
    val completionDate: String, // ISO date "YYYY-MM-DD"
    val completedAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val isProofVerified: Boolean = false,
    val xpEarned: Int = 25
)
