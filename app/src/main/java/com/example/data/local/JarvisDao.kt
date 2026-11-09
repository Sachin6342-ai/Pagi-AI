package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JarvisDao {
    // Chat History
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    // Personal Memory Facts
    @Query("SELECT * FROM memory_facts ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_facts ORDER BY updatedAt DESC")
    suspend fun getMemoriesSnapshot(): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE LOWER(memoryKey) = LOWER(:key) LIMIT 1")
    suspend fun findMemoryByKey(key: String): MemoryFactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryFactEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryFactEntity)

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    // Saved Notes
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getNotesSnapshot(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    // To-Do & Reminders
    @Query("SELECT * FROM todo_items ORDER BY isCompleted ASC, createdAt DESC")
    fun getAllTodos(): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE isCompleted = 0 ORDER BY createdAt DESC")
    suspend fun getPendingTodosSnapshot(): List<TodoItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoItemEntity): Long

    @Update
    suspend fun updateTodo(todo: TodoItemEntity)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteTodoById(id: Long)

    // User-Defined Routines
    @Query("SELECT * FROM routines ORDER BY id ASC")
    fun getAllRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY id ASC")
    suspend fun getRoutinesSnapshot(): List<RoutineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutineById(id: Long)

    // Task Execution History
    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllActionLogs(): Flow<List<ActionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(log: ActionLogEntity): Long

    @Query("DELETE FROM action_logs")
    suspend fun clearActionLogs()
}
