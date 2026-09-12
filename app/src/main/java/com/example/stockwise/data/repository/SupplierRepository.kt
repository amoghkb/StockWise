package com.example.stockwise.data.repository

import com.example.stockwise.data.dao.SupplierDao
import com.example.stockwise.data.entities.Supplier
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplierRepository @Inject constructor(
    private val supplierDao: SupplierDao
) {

    suspend fun insertSupplier(
        companyName: String,
        category: String = "General",
        contactPerson: String? = null,
        phoneNumber: String? = null,
        address: String? = null
    ): String {
        // Build initials from company name (max 2 letters)
        val initials = companyName
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "SP" }

        val supplier = Supplier(
            id = UUID.randomUUID().toString(),
            initials = initials,
            companyName = companyName.trim(),
            category = category,
            contactPerson = contactPerson?.trim().orEmpty().ifEmpty { "—" },
            phoneNumber = phoneNumber?.trim().orEmpty().ifEmpty { "—" },
            address = address?.trim().orEmpty().ifEmpty { "Address not provided" },
            createdAt = Date(),
            updatedAt = Date()
        )

        supplierDao.insertSupplier(supplier)
        return supplier.id
    }

    suspend fun updateSupplier(supplier: Supplier) {
        supplierDao.updateSupplier(supplier.copy(updatedAt = Date()))
    }

    suspend fun softDeleteSupplier(supplierId: String) {
        supplierDao.softDeleteSupplier(supplierId, System.currentTimeMillis())
    }

    fun getAllActiveSuppliers(): Flow<List<Supplier>> = supplierDao.getAllActiveSuppliers()

    fun getDeletedSuppliers(): Flow<List<Supplier>> = supplierDao.getDeletedSuppliers()

    suspend fun getSupplierById(supplierId: String): Supplier? =
        supplierDao.getSupplierById(supplierId)

    fun searchActiveSuppliers(query: String): Flow<List<Supplier>> {
        return if (query.isBlank()) {
            supplierDao.getAllActiveSuppliers()
        } else {
            supplierDao.searchActiveSuppliers(query)
        }
    }

    suspend fun getActiveSupplierCount(): Int = supplierDao.getActiveSupplierCount()
}