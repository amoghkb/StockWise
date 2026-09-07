package com.example.stockwise

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.stockwise.commons.DateConverters
import com.example.stockwise.data.dao.CategoryDao
import com.example.stockwise.data.dao.ItemDao
import com.example.stockwise.data.dao.VehicleDao
import com.example.stockwise.data.entities.Category
import com.example.stockwise.data.entities.Item
import com.example.stockwise.data.entities.Vehicle

@Database(
    entities = [Category::class, Item::class, Vehicle::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun itemDao(): ItemDao

    abstract fun vehicleDao(): VehicleDao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inventory_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}