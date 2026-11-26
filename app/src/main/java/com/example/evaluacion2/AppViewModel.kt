package com.example.evaluacion2

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val prefs = app.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userRut = MutableStateFlow("")
    val userRut: StateFlow<String> = _userRut.asStateFlow()

    private val _photoPath = MutableStateFlow<String?>(null)
    val photoPath: StateFlow<String?> = _photoPath.asStateFlow()

    init {
        viewModelScope.launch {
            val id = prefs.getInt("userId", -1)
            val user = if (id != -1) db.userDao().findById(id) else null

            if (user != null) {
                _userName.value = user.name
                _userRut.value = user.rut
                _photoPath.value = loadPhotoPath()
                _startRoute.value = "home"
            } else {
                prefs.edit().remove("userId").remove("userName").apply()
                _startRoute.value = "login"
            }
        }
    }

    private fun loadPhotoPath(): String? {
        val id = prefs.getInt("userId", -1)
        if (id == -1) return null
        return prefs.getString("photo_$id", null)?.takeIf { File(it).exists() }
    }

    private fun hash(p: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(p.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun register(
        name: String,
        rut: String,
        email: String,
        pass: String,
        docs: List<PickedDoc>,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val mail = email.trim().lowercase()
            val cleanRut = Validators.formatRut(rut)

            if (db.userDao().findByRut(cleanRut) != null) {
                onResult("Ese RUT ya está registrado")
                return@launch
            }
            if (db.userDao().findByEmail(mail) != null) {
                onResult("Ese correo ya está registrado")
                return@launch
            }

            val userId = db.userDao().insert(
                User(name = name.trim(), rut = cleanRut, email = mail, passwordHash = hash(pass))
            ).toInt()

            withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                val dir = File(ctx.filesDir, "docs/$userId").apply { mkdirs() }
                docs.forEach { doc ->
                    try {
                        val dest = File(dir, "${System.currentTimeMillis()}_${doc.name}")
                        ctx.contentResolver.openInputStream(doc.uri)?.use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                        val mime = ctx.contentResolver.getType(doc.uri) ?: "application/octet-stream"
                        db.fileDao().insert(
                            StoredFile(
                                ownerId = userId,
                                displayName = doc.name,
                                path = dest.absolutePath,
                                mimeType = mime,
                                sizeBytes = dest.length()
                            )
                        )
                    } catch (e: Exception) {
                    }
                }
            }
            onResult(null)
        }
    }

    fun login(email: String, pass: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val u = db.userDao().findByEmail(email.trim().lowercase())
            if (u == null || u.passwordHash != hash(pass)) {
                onResult("Correo o contraseña incorrectos")
            } else {
                prefs.edit().putInt("userId", u.id).putString("userName", u.name).apply()
                _userName.value = u.name
                _userRut.value = u.rut
                _photoPath.value = loadPhotoPath()
                onResult(null)
            }
        }
    }

    fun logout() {
        prefs.edit().remove("userId").remove("userName").apply()
        _userName.value = ""
        _userRut.value = ""
        _photoPath.value = null
    }

    fun setProfilePhoto(uri: Uri) {
        val ctx = getApplication<Application>()
        val id = prefs.getInt("userId", -1)
        if (id == -1) return
        viewModelScope.launch(Dispatchers.IO) {
            val dest = File(ctx.filesDir, "profile_${id}_${System.currentTimeMillis()}.jpg")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@launch

            prefs.getString("photo_$id", null)?.let { File(it).delete() }

            prefs.edit().putString("photo_$id", dest.absolutePath).apply()
            _photoPath.value = dest.absolutePath
        }
    }
    // --- GESTIÓN DE SALAS Y SOLICITUDES ---

    val salas: kotlinx.coroutines.flow.StateFlow<List<Sala>> =
        db.salaDao().observeAll().stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val solicitudes: kotlinx.coroutines.flow.StateFlow<List<SolicitudSala>> =
        db.solicitudDao().observeAll().stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun crearSala(
        nombre: String,
        capacidad: Int,
        ubicacion: String,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val cleanNombre = nombre.trim()
            if (cleanNombre.isEmpty()) {
                onResult("Ingresa el nombre de la sala")
                return@launch
            }
            if (capacidad <= 0) {
                onResult("La capacidad debe ser mayor a 0")
                return@launch
            }
            if (db.salaDao().findByNombre(cleanNombre) != null) {
                onResult("Ya existe una sala con ese nombre")
                return@launch
            }
            db.salaDao().insert(
                Sala(
                    nombre = cleanNombre,
                    capacidad = capacidad,
                    ubicacion = ubicacion.trim().ifEmpty { "Sin ubicación especificada" }
                )
            )
            onResult(null)
        }
    }

    fun eliminarSala(sala: Sala) {
        viewModelScope.launch {
            db.salaDao().delete(sala)
        }
    }

    fun verificarDocentePorRut(
        rutInput: String,
        onResult: (docente: User?, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            val rutError = Validators.rut(rutInput)
            if (rutError != null) {
                onResult(null, rutError)
                return@launch
            }
            val formattedRut = Validators.formatRut(rutInput)
            val docente = db.userDao().findByRut(formattedRut)
            if (docente == null) {
                onResult(null, "El RUT $formattedRut no está registrado como docente")
            } else {
                onResult(docente, null)
            }
        }
    }

    fun crearSolicitud(
        sala: Sala,
        rutInput: String,
        horaInicio: String,
        horaFin: String,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            // 1. Verificar RUT del docente
            val rutError = Validators.rut(rutInput)
            if (rutError != null) {
                onResult(rutError)
                return@launch
            }
            val formattedRut = Validators.formatRut(rutInput)
            val docente = db.userDao().findByRut(formattedRut)
            if (docente == null) {
                onResult("RUT no verificado: el docente no está registrado en el sistema")
                return@launch
            }

            // 2. Validar horas (inicio < fin)
            val inicioMin = horaToMinutes(horaInicio)
            val finMin = horaToMinutes(horaFin)
            if (inicioMin == null || finMin == null) {
                onResult("Selecciona hora de inicio y término")
                return@launch
            }
            if (finMin <= inicioMin) {
                onResult("La hora final debe ser posterior a la hora de inicio")
                return@launch
            }

            // 3. Validar que no exista tope horario en la misma sala
            val existentes = db.solicitudDao().findBySala(sala.id)
            val hayTope = existentes.any { sol ->
                val sIni = horaToMinutes(sol.horaInicio) ?: 0
                val sFin = horaToMinutes(sol.horaFin) ?: 0
                inicioMin < sFin && finMin > sIni
            }
            if (hayTope) {
                onResult("La sala ya está reservada en ese bloque horario")
                return@launch
            }

            // 4. Guardar solicitud
            db.solicitudDao().insert(
                SolicitudSala(
                    salaId = sala.id,
                    salaNombre = sala.nombre,
                    docenteNombre = docente.name,
                    docenteRut = docente.rut,
                    horaInicio = horaInicio,
                    horaFin = horaFin
                )
            )
            onResult(null)
        }
    }

    fun eliminarSolicitud(solicitud: SolicitudSala) {
        viewModelScope.launch {
            db.solicitudDao().delete(solicitud)
        }
    }

    private fun horaToMinutes(hora: String): Int? {
        val parts = hora.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }












}
