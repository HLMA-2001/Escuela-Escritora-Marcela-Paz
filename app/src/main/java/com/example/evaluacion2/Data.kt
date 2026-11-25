package com.example.evaluacion2

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

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

@Entity(
    tableName = "salas",
    indices = [Index(value = ["nombre"], unique = true)]
)
data class Sala(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val capacidad: Int,
    val ubicacion: String
)

@Entity(tableName = "solicitudes")
data class SolicitudSala(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val salaId: Int,
    val salaNombre: String,
    val docenteNombre: String,
    val docenteRut: String,
    val horaInicio: String, // Formato "HH:mm"
    val horaFin: String     // Formato "HH:mm"
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

@Dao
interface SalaDao {
    @Insert
    suspend fun insert(sala: Sala): Long

    @Delete
    suspend fun delete(sala: Sala)

    @Query("SELECT * FROM salas ORDER BY nombre ASC")
    fun observeAll(): Flow<List<Sala>>

    @Query("SELECT * FROM salas WHERE LOWER(nombre) = LOWER(:nombre) LIMIT 1")
    suspend fun findByNombre(nombre: String): Sala?
}

@Dao
interface SolicitudDao {
    @Insert
    suspend fun insert(solicitud: SolicitudSala): Long

    @Delete
    suspend fun delete(solicitud: SolicitudSala)

    @Query("SELECT * FROM solicitudes ORDER BY id DESC")
    fun observeAll(): Flow<List<SolicitudSala>>

    @Query("SELECT * FROM solicitudes WHERE salaId = :salaId")
    suspend fun findBySala(salaId: Int): List<SolicitudSala>
}

@Database(
    entities = [User::class, StoredFile::class, Sala::class, SolicitudSala::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun fileDao(): FileDao
    abstract fun salaDao(): SalaDao
    abstract fun solicitudDao(): SolicitudDao

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