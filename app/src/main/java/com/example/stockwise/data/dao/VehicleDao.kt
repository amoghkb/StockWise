package com.example.stockwise.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.stockwise.data.entities.Vehicle
import com.example.stockwise.data.entities.VehicleType
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Query("UPDATE vehicles SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :vehicleId")
    suspend fun softDeleteVehicle(vehicleId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM vehicles WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllActiveVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedVehicles(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId AND isDeleted = 0")
    suspend fun getActiveVehicleById(vehicleId: String): Vehicle?

    @Query("SELECT * FROM vehicles WHERE type = :type AND isDeleted = 0 ORDER BY name ASC")
    fun getActiveVehiclesByType(type: VehicleType): Flow<List<Vehicle>>

    @Query("""
        SELECT * FROM vehicles 
        WHERE isDeleted = 0 
        AND (name LIKE '%' || :query || '%' 
        OR company LIKE '%' || :query || '%'
        OR model LIKE '%' || :query || '%'
        OR description LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun searchActiveVehicles(query: String): Flow<List<Vehicle>>

    @Query("SELECT COUNT(*) FROM vehicles WHERE isDeleted = 0")
    suspend fun getActiveVehicleCount(): Int

    @Query("SELECT COUNT(*) FROM vehicles WHERE type = :type AND isDeleted = 0")
    suspend fun getActiveVehicleCountByType(type: VehicleType): Int
}