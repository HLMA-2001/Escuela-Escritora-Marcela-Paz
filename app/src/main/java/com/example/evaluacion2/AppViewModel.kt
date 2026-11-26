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

// Coordina la sesión y las operaciones de los flujos que se guardan en el dispositivo.
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val prefs = app.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    private val _rolRed = MutableStateFlow(RolRed.SIN_ROL)
    internal val rolRed = _rolRed.asStateFlow()

    // Solo el servicio de salas determina qué rol tiene la cuenta en red.
    internal fun actualizarRol(rol: RolRed) {
        _rolRed.value = rol
    }

    private val _userName = MutableStateFlow("")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userRut = MutableStateFlow("")
    val userRut: StateFlow<String> = _userRut.asStateFlow()

    private val _photoPath = MutableStateFlow<String?>(null)
    val photoPath: StateFlow<String?> = _photoPath.asStateFlow()

    // Recupera la cuenta al reiniciar la app. Si ya no existe, vuelve al ingreso.
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

    // Recupera la foto guardada solo si el archivo aún existe.
    private fun loadPhotoPath(): String? {
        val id = prefs.getInt("userId", -1)
        if (id == -1) return null
        return prefs.getString("photo_$id", null)?.takeIf { File(it).exists() }
    }

    // Calcula el SHA-256 usado para comparar contraseñas en la base de cuentas local.
    private fun hash(p: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(p.toByteArray())
            .joinToString("") { "%02x".format(it) }

    // Crea la cuenta local y copia sus adjuntos al almacenamiento interno.
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

    // Compara las credenciales y guarda el identificador de sesión.
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

    // Genera una credencial aleatoria por cuenta local para cancelar reservas propias en red.
    internal fun tokenReservas(): String? {
        val id = prefs.getInt("userId", -1)
        if (id == -1) return null
        val clave = "reservas_token_$id"
        return prefs.getString(clave, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString(clave, it).apply()
        }
    }

    // Limpia la sesión activa y los datos de perfil que se muestran en pantalla.
    fun logout() {
        actualizarRol(RolRed.SIN_ROL)
        prefs.edit().remove("userId").remove("userName").apply()
        _userName.value = ""
        _userRut.value = ""
        _photoPath.value = null
    }

    // Copia la imagen elegida y conserva su ruta para el siguiente ingreso.
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

    // Escucha los cambios de Room y actualiza la lista de salas locales.
    val salas: kotlinx.coroutines.flow.StateFlow<List<Sala>> =
        db.salaDao().observeAll().stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Escucha los cambios de Room y actualiza la lista de solicitudes locales.
    val solicitudes: kotlinx.coroutines.flow.StateFlow<List<SolicitudSala>> =
        db.solicitudDao().observeAll().stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Valida el nombre y la capacidad antes de insertar una sala local.
    fun crearSala(
        nombre: String,
        capacidad: Int,
        ubicacion: String,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            if (_rolRed.value != RolRed.INSPECTOR) {
                onResult("Solo los inspectores pueden crear salas.")
                return@launch
            }
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

    // Borra del catálogo local la sala elegida.
    fun eliminarSala(sala: Sala) {
        viewModelScope.launch {
            if (_rolRed.value == RolRed.INSPECTOR) db.salaDao().delete(sala)
        }
    }

    // Valida el formato del RUT y busca la cuenta registrada en este dispositivo.
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

    // Crea una solicitud local después de comprobar el RUT y los cruces de hora.
    fun crearSolicitud(
        sala: Sala,
        rutInput: String,
        horaInicio: String,
        horaFin: String,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            if (_rolRed.value != RolRed.DOCENTE || Validators.formatRut(rutInput) != _userRut.value) {
                onResult("Solo el docente que inició sesión puede solicitar con su propio RUT.")
                return@launch
            }
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

    // Elimina la solicitud de la base de datos local.
    fun eliminarSolicitud(solicitud: SolicitudSala) {
        viewModelScope.launch {
            if (_rolRed.value == RolRed.DOCENTE && solicitud.docenteRut == _userRut.value) {
                db.solicitudDao().delete(solicitud)
            }
        }
    }

    // Convierte una hora válida a minutos desde medianoche para comparar intervalos.
    private fun horaToMinutes(hora: String): Int? {
        val parts = hora.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }












}


