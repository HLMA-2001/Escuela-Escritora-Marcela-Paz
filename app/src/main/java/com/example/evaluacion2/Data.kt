package com.example.evaluacion2

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["rut"], unique = true)
    ]
)
// Representa una cuenta local con un correo y un RUT únicos.
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val rut: String,
    val email: String,
    val passwordHash: String
)

// Guarda los metadatos y la ruta interna de un adjunto.
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
// Representa una sala guardada en este dispositivo.
data class Sala(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nombre: String,
    val capacidad: Int,
    val ubicacion: String,
    val restablecidaEn: String? = null
)

// Guarda el bloque local y una copia del nombre de la sala y del solicitante.
@Entity(tableName = "solicitudes")
data class SolicitudSala(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val salaId: Int,
    val salaNombre: String,
    val docenteNombre: String,
    val docenteRut: String,
    val horaInicio: String, // Formato "HH:mm"
    val horaFin: String,    // Formato "HH:mm"
    @ColumnInfo(defaultValue = "''") val fecha: String = horaEscuela().toLocalDate().toString(),
    @ColumnInfo(defaultValue = "0") val aprobada: Boolean = false,
    @ColumnInfo(defaultValue = "0") val rechazada: Boolean = false
)

// Agrupa las consultas de cuentas usadas por el registro y la sesión.
@Dao
interface UserDao {
    @Update
    suspend fun update(user: User): Int

    @Query("SELECT * FROM users ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<User>>
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

// Relaciona los archivos adjuntos con la cuenta que los agregó.
@Dao
interface FileDao {
    @Insert
    suspend fun insert(file: StoredFile)

    @Query("SELECT * FROM files WHERE ownerId = :ownerId ORDER BY id DESC")
    suspend fun byOwner(ownerId: Int): List<StoredFile>
}

// Expone inserciones, eliminaciones y cambios del catálogo local.
@Dao
interface SalaDao {
    @Query("SELECT * FROM salas WHERE id = :id")
    suspend fun findById(id: Int): Sala?

    @Update
    suspend fun update(sala: Sala): Int

    @Insert
    suspend fun insert(sala: Sala): Long

    @Delete
    suspend fun delete(sala: Sala)

    @Query("SELECT * FROM salas ORDER BY nombre ASC")
    fun observeAll(): Flow<List<Sala>>

    @Query("SELECT * FROM salas WHERE LOWER(nombre) = LOWER(:nombre) LIMIT 1")
    suspend fun findByNombre(nombre: String): Sala?
}

// Consulta las solicitudes locales y los horarios de una sala.
@Dao
interface SolicitudDao {
    @Query("SELECT * FROM solicitudes WHERE id = :id")
    suspend fun findById(id: Int): SolicitudSala?

    @Query("SELECT * FROM solicitudes WHERE salaId = :salaId AND aprobada = 1")
    suspend fun aprobadasPorSala(salaId: Int): List<SolicitudSala>

    @Query("""
        SELECT EXISTS(SELECT 1 FROM solicitudes
        WHERE salaId = :salaId AND fecha = :fecha AND aprobada = 1 AND id != :excluirId
            AND horaInicio < :fin AND horaFin > :inicio)
    """)
    suspend fun hayCruceAprobado(salaId: Int, fecha: String, inicio: String, fin: String, excluirId: Int): Boolean

    @Update
    suspend fun update(solicitud: SolicitudSala): Int

    @Insert
    suspend fun insert(solicitud: SolicitudSala): Long

    @Delete
    suspend fun delete(solicitud: SolicitudSala)

    @Query("SELECT * FROM solicitudes ORDER BY id DESC")
    fun observeAll(): Flow<List<SolicitudSala>>

}

@Database(
    entities = [User::class, StoredFile::class, Sala::class, SolicitudSala::class],
    version = 8,
    exportSchema = false
)
// Declara las entidades Room y comparte una instancia de la base local.
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun fileDao(): FileDao
    abstract fun salaDao(): SalaDao
    abstract fun solicitudDao(): SolicitudDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val migracionSalasPredefinidas = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                insertarSalasPredefinidas(db)
            }
        }

        private val migracionAprobaciones = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN fecha TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN aprobada INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE solicitudes SET fecha = ?, aprobada = 1",
                    arrayOf(horaEscuela().toLocalDate().toString()))
                for (numero in 13..28) {
                    db.execSQL(
                        """
                        DELETE FROM salas WHERE nombre = ? AND capacidad = 0 AND ubicacion = ?
                        AND NOT EXISTS (SELECT 1 FROM solicitudes WHERE salaId = salas.id)
                        """.trimIndent(),
                        arrayOf("Sala de clases $numero", "Escuela Marcela Paz, San Bernardo")
                    )
                }
            }
        }

        private val migracionRechazos = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE solicitudes ADD COLUMN rechazada INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val migracionRestablecimiento = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE salas ADD COLUMN restablecidaEn TEXT")
            }
        }

        private val crearSalasPredefinidas = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                insertarSalasPredefinidas(db)
            }
        }

        private fun insertarSalasPredefinidas(db: SupportSQLiteDatabase) {
            // PEI de la Escuela Escritora Marcela Paz, San Bernardo (RBD 10571).
            // La numeración es de la app. El PEI no indica los cupos individuales.
            val nombres = (1..12).map { "Sala de clases ${it.toString().padStart(2, '0')}" } +
                listOf("Sala de computación 1", "Sala de computación 2", "Laboratorio de Ciencias", "Biblioteca CRA")
            for (nombre in nombres) {
                db.execSQL(
                    """
                    INSERT INTO salas (nombre, capacidad, ubicacion)
                    SELECT ?, 0, ?
                    WHERE NOT EXISTS (SELECT 1 FROM salas WHERE LOWER(nombre) = LOWER(?))
                    """.trimIndent(),
                    arrayOf(nombre, "Escuela Marcela Paz, San Bernardo", nombre)
                )
            }
        }

        fun get(ctx: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                ctx.applicationContext,
                AppDatabase::class.java,
                "app.db"
            )
                .addMigrations(migracionSalasPredefinidas, migracionAprobaciones, migracionRechazos, migracionRestablecimiento)
                .addCallback(crearSalasPredefinidas)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
