package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RegisteredNozzleDao {
    @Query("SELECT * FROM registered_nozzles")
    suspend fun getAllRegisteredNozzles(): List<RegisteredNozzle>

    @Query("SELECT * FROM registered_nozzles WHERE mobileNumber = :mobileNumber")
    suspend fun getNozzlesByPumpMobile(mobileNumber: String): List<RegisteredNozzle>

    @Query("SELECT * FROM registered_nozzles WHERE mobileNumber = :mobileNumber AND nozzleType = :type")
    suspend fun getNozzlesByPumpMobileAndType(mobileNumber: String, type: String): List<RegisteredNozzle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNozzles(nozzles: List<RegisteredNozzle>)

    @Query("DELETE FROM registered_nozzles WHERE mobileNumber = :mobileNumber")
    suspend fun deleteNozzlesByPumpMobile(mobileNumber: String)

    @Query("DELETE FROM registered_nozzles")
    suspend fun clearAllNozzles()
}
