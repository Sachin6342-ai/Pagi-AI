package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "USER", "JARVIS", "SYSTEM"
    val spokenText: String,
    val detailedText: String = "",
    val language: String = "en",
    val actionSummary: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_facts")
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // "PREFERENCE", "PERSONAL_FACT", "FREQUENT_COMMAND"
    val memoryKey: String,
    val memoryValue: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "todo_items")
data class TodoItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val task: String,
    val dueLabel: String = "Today",
    val priority: String = "HIGH", // "HIGH", "NORMAL", "LOW"
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerPhrase: String,
    val stepsJoined: String, // Pipe-delimited steps e.g. "Check calendar|Read pending tasks|Fetch live weather|Generate daily summary"
    val isEnabled: Boolean = true,
    val lastRunAt: Long? = null
)

@Entity(tableName = "action_logs")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val title: String,
    val targetSummary: String,
    val status: String, // "EXECUTED", "CONFIRMED", "CANCELLED", "PERMISSION_REQUIRED"
    val timestamp: Long = System.currentTimeMillis()
)
