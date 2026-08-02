package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PumpInfoDao {
    @Query("SELECT * FROM pump_info")
    suspend fun getAllPumpInfo(): List<PumpInfo>

    @Query("SELECT * FROM pump_info WHERE mobileNumber = :mobileNumber LIMIT 1")
    suspend fun getPumpInfoByMobile(mobileNumber: String): PumpInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPumpInfo(pumpInfo: PumpInfo)

    @Query("DELETE FROM pump_info WHERE mobileNumber = :mobileNumber")
    suspend fun deletePumpInfoByMobile(mobileNumber: String)

    @Query("DELETE FROM pump_info")
    suspend fun clearAllPumpInfo()
}
