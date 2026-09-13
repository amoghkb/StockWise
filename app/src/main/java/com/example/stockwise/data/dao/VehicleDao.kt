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

    // ==================== NEW VEHICLE-ITEM RELATIONSHIP METHODS ====================

    @Query("""
        SELECT DISTINCT vehicleId FROM item_vehicle_relations
    """)
    suspend fun getAllAssignedVehicleIds(): List<String>

    @Query("""
        SELECT * FROM vehicles 
        WHERE isDeleted = 0 AND id IN (
            SELECT DISTINCT vehicleId FROM item_vehicle_relations
        )
        ORDER BY name ASC
    """)
    fun getAssignedVehicles(): Flow<List<Vehicle>>

    @Query("""
        SELECT * FROM vehicles 
        WHERE isDeleted = 0 AND id NOT IN (
            SELECT DISTINCT vehicleId FROM item_vehicle_relations
        )
        ORDER BY name ASC
    """)
    fun getUnassignedVehicles(): Flow<List<Vehicle>>

    @Query("""
        SELECT * FROM vehicles 
        WHERE isDeleted = 0 
        AND id IN (
            SELECT DISTINCT vehicleId FROM item_vehicle_relations
        )
        AND (name LIKE '%' || :query || '%' 
        OR company LIKE '%' || :query || '%'
        OR model LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun searchAssignedVehicles(query: String): Flow<List<Vehicle>>

    @Query("""
        SELECT * FROM vehicles 
        WHERE isDeleted = 0 
        AND id NOT IN (
            SELECT DISTINCT vehicleId FROM item_vehicle_relations
        )
        AND (name LIKE '%' || :query || '%' 
        OR company LIKE '%' || :query || '%'
        OR model LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun searchUnassignedVehicles(query: String): Flow<List<Vehicle>>

    @Query("""
        SELECT COUNT(*) FROM vehicles 
        WHERE isDeleted = 0 AND id IN (
            SELECT DISTINCT vehicleId FROM item_vehicle_relations
        )
    """)
    suspend fun getAssignedVehicleCount(): Int

    @Query("""
        SELECT COUNT(*) FROM vehicles 
        WHERE isDeleted = 0 AND id NOT IN (
            SELECT DISTINCT vehicleId FROM item_vehicle_relations
        )
    """)
    suspend fun getUnassignedVehicleCount(): Int

    @Query("""
        SELECT v.* FROM vehicles v
        INNER JOIN item_vehicle_relations ivr ON v.id = ivr.vehicleId
        WHERE ivr.itemId = :itemId AND v.isDeleted = 0
        ORDER BY v.name ASC
    """)
    suspend fun getVehiclesByItemId(itemId: String): List<Vehicle>

    @Query("""
        SELECT COUNT(*) FROM vehicles v
        INNER JOIN item_vehicle_relations ivr ON v.id = ivr.vehicleId
        WHERE ivr.itemId = :itemId AND v.isDeleted = 0
    """)
    suspend fun getVehicleCountByItemId(itemId: String): Int

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM item_vehicle_relations 
            WHERE itemId = :itemId AND vehicleId = :vehicleId
        )
    """)
    suspend fun isVehicleAssignedToItem(itemId: String, vehicleId: String): Boolean
}