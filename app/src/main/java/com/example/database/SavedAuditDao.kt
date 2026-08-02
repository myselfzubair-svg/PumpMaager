package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedAuditDao {
    @Query("SELECT * FROM saved_audits ORDER BY timestamp DESC")
    fun getAllAudits(): Flow<List<SavedAudit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: SavedAudit)

    @Query("DELETE FROM saved_audits WHERE id = :id")
    suspend fun deleteAuditById(id: Int)

    @Query("SELECT * FROM saved_audits WHERE id = :id")
    suspend fun getAuditById(id: Int): SavedAudit?
    
    @Query("DELETE FROM saved_audits")
    suspend fun clearAllAudits()
}
