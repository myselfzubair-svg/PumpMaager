package com.example.database

import kotlinx.coroutines.flow.Flow

class SavedAuditRepository(private val dao: SavedAuditDao) {
    val allAudits: Flow<List<SavedAudit>> = dao.getAllAudits()

    suspend fun insert(audit: SavedAudit) {
        dao.insertAudit(audit)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteAuditById(id)
    }

    suspend fun getById(id: Int): SavedAudit? {
        return dao.getAuditById(id)
    }
    
    suspend fun clearAll() {
        dao.clearAllAudits()
    }
}
