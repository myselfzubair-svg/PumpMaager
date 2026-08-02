package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HsdNozzleReadingDao {
    @Query("SELECT * FROM hsd_nozzle_readings ORDER BY timestamp DESC")
    fun getAllHsdNozzleReadings(): Flow<List<HsdNozzleReading>>

    @Query("SELECT * FROM hsd_nozzle_readings WHERE phone = :phone ORDER BY timestamp DESC")
    fun getHsdNozzleReadingsByPhone(phone: String): Flow<List<HsdNozzleReading>>

    @Query("SELECT * FROM hsd_nozzle_readings WHERE date = :date ORDER BY timestamp DESC")
    fun getHsdNozzleReadingsByDate(date: String): Flow<List<HsdNozzleReading>>

    @Query("SELECT * FROM hsd_nozzle_readings WHERE nozzleLabel = :nozzleLabel AND phone = :phone ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReadingForNozzle(nozzleLabel: String, phone: String): HsdNozzleReading?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHsdNozzleReading(reading: HsdNozzleReading)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHsdNozzleReadings(readings: List<HsdNozzleReading>)

    @Query("DELETE FROM hsd_nozzle_readings WHERE id = :id")
    suspend fun deleteHsdNozzleReadingById(id: Long)

    @Query("DELETE FROM hsd_nozzle_readings WHERE date = :date AND caName = :caName")
    suspend fun deleteHsdNozzleReadingsByDateAndCa(date: String, caName: String)

    @Query("DELETE FROM hsd_nozzle_readings")
    suspend fun clearAllHsdNozzleReadings()
}
