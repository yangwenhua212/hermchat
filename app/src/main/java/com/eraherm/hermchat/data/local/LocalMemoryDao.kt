package com.eraherm.hermchat.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "local_memories")
data class LocalMemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface LocalMemoryDao {
    @Query("SELECT * FROM local_memories ORDER BY pinned DESC, updatedAt DESC")
    suspend fun listAll(): List<LocalMemoryEntity>

    @Query("SELECT * FROM local_memories WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): LocalMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalMemoryEntity)

    @Query("SELECT COUNT(*) FROM local_memories")
    suspend fun count(): Int

    @Query(
        """
        DELETE FROM local_memories WHERE id IN (
            SELECT id FROM local_memories
            WHERE pinned = 0
            ORDER BY updatedAt ASC
            LIMIT :n
        )
        """,
    )
    suspend fun deleteOldestUnpinned(n: Int)

    @Query("DELETE FROM local_memories")
    suspend fun clearAll()
}
