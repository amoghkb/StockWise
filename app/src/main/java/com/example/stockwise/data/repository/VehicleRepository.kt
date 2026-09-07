package com.example.stockwise.data.repository

import com.example.stockwise.data.dao.VehicleDao
import com.example.stockwise.data.entities.Vehicle
import com.example.stockwise.data.entities.VehicleType
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    private val vehicleDao: VehicleDao
) {

    suspend fun insertVehicle(vehicle: Vehicle): String {
        vehicleDao.insertVehicle(vehicle)
        return vehicle.id
    }

    suspend fun insertVehicle(
        name: String,
        company: String? = null,
        type: VehicleType? = null,
        model: String? = null,
        description: String? = null
    ): String {
        val vehicle = Vehicle(
            id = UUID.randomUUID().toString(),
            name = name,
            company = company,
            type = type,
            model = model,
            description = description,
            createdAt = Date(),
            updatedAt = Date()
        )
        vehicleDao.insertVehicle(vehicle)
        return vehicle.id
    }

    suspend fun updateVehicle(vehicle: Vehicle) {
        val updatedVehicle = vehicle.copy(updatedAt = Date())
        vehicleDao.updateVehicle(updatedVehicle)
    }

    suspend fun softDeleteVehicle(vehicleId: String) {
        vehicleDao.softDeleteVehicle(vehicleId, System.currentTimeMillis())
    }

    fun getAllActiveVehicles(): Flow<List<Vehicle>> {
        return vehicleDao.getAllActiveVehicles()
    }

    fun getDeletedVehicles(): Flow<List<Vehicle>> {
        return vehicleDao.getDeletedVehicles()
    }

    suspend fun getActiveVehicleById(vehicleId: String): Vehicle? {
        return vehicleDao.getActiveVehicleById(vehicleId)
    }

    fun getActiveVehiclesByType(type: VehicleType): Flow<List<Vehicle>> {
        return vehicleDao.getActiveVehiclesByType(type)
    }

    fun searchActiveVehicles(query: String): Flow<List<Vehicle>> {
        return if (query.isBlank()) {
            vehicleDao.getAllActiveVehicles()
        } else {
            vehicleDao.searchActiveVehicles(query)
        }
    }

    suspend fun getActiveVehicleCount(): Int {
        return vehicleDao.getActiveVehicleCount()
    }

    suspend fun getActiveVehicleCountByType(type: VehicleType): Int {
        return vehicleDao.getActiveVehicleCountByType(type)
    }

    // Helper method to check if vehicle can be deleted (not assigned to any item)
    suspend fun canDeleteVehicle(vehicleId: String): Boolean {
        // This will be implemented after we add ItemVehicle junction table
        // For now, return true
        return true
    }
}