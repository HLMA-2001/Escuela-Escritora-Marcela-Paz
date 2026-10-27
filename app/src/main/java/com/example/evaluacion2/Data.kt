package com.example.evaluacion2

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["rut"], unique = true)
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val rut: String,
    val email: String,
    val passwordHash: String
)

@Entity(tableName = "files")
data class StoredFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ownerId: Int,
    val displayName: String,
    val path: String,
    val mimeType: String,
    val sizeBytes: Long
)

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User): Long

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE rut = :rut LIMIT 1")
    suspend fun findByRut(rut: String): User?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: Int): User?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int
}

@Dao
interface FileDao {
    @Insert
    suspend fun insert(file: StoredFile)

    @Query("SELECT * FROM files WHERE ownerId = :ownerId ORDER BY id DESC")
    suspend fun byOwner(ownerId: Int): List<StoredFile>
}

@Database(entities = [User::class, StoredFile::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun fileDao(): FileDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                ctx.applicationContext,
                AppDatabase::class.java,
                "app.db"
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}