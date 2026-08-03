package com.eraherm.hermchat.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val agentId: String? = null,
    val updatedAt: Long,
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT * FROM conversations
        WHERE agentId = :agentId
        ORDER BY updatedAt DESC
        """,
    )
    fun observeForAgent(agentId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): ConversationEntity?

    @Query(
        """
        SELECT * FROM conversations
        WHERE agentId = :agentId
        ORDER BY updatedAt DESC
        LIMIT 1
        """,
    )
    suspend fun latestForAgent(agentId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("UPDATE conversations SET agentId = :agentId WHERE agentId IS NULL")
    suspend fun claimOrphans(agentId: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)
}
