package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MsNozzleReadingDao {
    @Query("SELECT * FROM ms_nozzle_readings ORDER BY timestamp DESC")
    fun getAllMsNozzleReadings(): Flow<List<MsNozzleReading>>

    @Query("SELECT * FROM ms_nozzle_readings WHERE phone = :phone ORDER BY timestamp DESC")
    fun getMsNozzleReadingsByPhone(phone: String): Flow<List<MsNozzleReading>>

    @Query("SELECT * FROM ms_nozzle_readings WHERE date = :date ORDER BY timestamp DESC")
    fun getMsNozzleReadingsByDate(date: String): Flow<List<MsNozzleReading>>

    @Query("SELECT * FROM ms_nozzle_readings WHERE nozzleLabel = :nozzleLabel AND phone = :phone ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReadingForNozzle(nozzleLabel: String, phone: String): MsNozzleReading?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMsNozzleReading(reading: MsNozzleReading)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMsNozzleReadings(readings: List<MsNozzleReading>)

    @Query("DELETE FROM ms_nozzle_readings WHERE id = :id")
    suspend fun deleteMsNozzleReadingById(id: Long)

    @Query("DELETE FROM ms_nozzle_readings WHERE date = :date AND caName = :caName")
    suspend fun deleteMsNozzleReadingsByDateAndCa(date: String, caName: String)

    @Query("DELETE FROM ms_nozzle_readings")
    suspend fun clearAllMsNozzleReadings()
}
