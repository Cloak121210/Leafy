package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LeafyDao {
    // Conversations
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    // Message Nodes (Branching Tree)
    @Query("SELECT * FROM message_nodes WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    fun observeMessagesForConversation(conversationId: Long): Flow<List<MessageNodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessageNode(node: MessageNodeEntity): Long

    @Update
    suspend fun updateMessageNode(node: MessageNodeEntity)

    @Query("DELETE FROM message_nodes WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Long)

    // Local Models
    @Query("SELECT * FROM local_models ORDER BY isInstalled DESC, sizeMb ASC")
    fun observeLocalModels(): Flow<List<LocalModelEntity>>

    @Query("SELECT COUNT(*) FROM local_models")
    suspend fun countLocalModels(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalModels(models: List<LocalModelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalModel(model: LocalModelEntity)

    @Update
    suspend fun updateLocalModel(model: LocalModelEntity)

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteLocalModel(id: String)

    @Query("DELETE FROM local_models WHERE isInstalled = 0 OR localFilePath = ''")
    suspend fun deleteUninstalledModels()

    // RAG Documents
    @Query("SELECT * FROM rag_documents ORDER BY isPinnedToChat DESC, updatedAt DESC")
    fun observeRagDocuments(): Flow<List<RagDocumentEntity>>

    @Query("SELECT COUNT(*) FROM rag_documents")
    suspend fun countRagDocuments(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRagDocument(doc: RagDocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRagDocuments(docs: List<RagDocumentEntity>)

    @Update
    suspend fun updateRagDocument(doc: RagDocumentEntity)

    @Query("DELETE FROM rag_documents WHERE id = :id")
    suspend fun deleteRagDocument(id: Long)

    // Personas
    @Query("SELECT * FROM persona_presets ORDER BY isBuiltIn DESC, id ASC")
    fun observePersonas(): Flow<List<PersonaPresetEntity>>

    @Query("SELECT COUNT(*) FROM persona_presets")
    suspend fun countPersonas(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonas(personas: List<PersonaPresetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersona(persona: PersonaPresetEntity): Long

    @Update
    suspend fun updatePersona(persona: PersonaPresetEntity)

    @Query("DELETE FROM persona_presets WHERE id = :id")
    suspend fun deletePersona(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun wipeAllConversations()

    @Query("DELETE FROM message_nodes")
    suspend fun wipeAllMessages()
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageNodeEntity::class,
        LocalModelEntity::class,
        RagDocumentEntity::class,
        PersonaPresetEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LeafyDatabase : RoomDatabase() {
    abstract fun leafyDao(): LeafyDao

    companion object {
        @Volatile
        private var INSTANCE: LeafyDatabase? = null

        fun getInstance(context: Context): LeafyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LeafyDatabase::class.java,
                    "leafy_offline_ai.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
