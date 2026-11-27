package com.example.evaluacion2

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.security.MessageDigest

// Coordina la sesión y las operaciones de los flujos que se guardan en el dispositivo.
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val prefs = app.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    private val _rolRed = MutableStateFlow(RolRed.SIN_ROL)
    internal val rolRed = _rolRed.asStateFlow()
    private val _catalogoLocal = MutableStateFlow(false)
    internal val catalogoLocal = _catalogoLocal.asStateFlow()

    internal fun seleccionarCatalogo(local: Boolean) {
        _catalogoLocal.value = local
    }

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
            prefs.edit().putBoolean("docente_pendiente_$userId", true).apply()

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
                return@launch
            }

            actualizarRol(RolRed.SIN_ROL)
            prefs.edit().putInt("userId", u.id).putString("userName", u.name).apply()
            _userName.value = u.name
            _userRut.value = u.rut
            _photoPath.value = loadPhotoPath()
            onResult(null)
        }
    }

    // Genera una credencial aleatoria por cuenta local para cancelar reservas propias en red.
    internal fun tokenReservas(): String? {
        val id = prefs.getInt("userId", -1)
        if (id == -1) return null
        return tokenReservas(id)
    }

    internal fun tokenReservas(id: Int): String {
        val clave = "reservas_token_$id"
        return prefs.getString(clave, null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString(clave, it).apply()
        }
    }

    internal suspend fun consultarRol(token: String): RolRed {
        val id = prefs.getInt("userId", -1)
        val pendiente = "docente_pendiente_$id"
        if (id == -1 || token != tokenReservas(id) || !prefs.getBoolean(pendiente, false)) {
            return RedApi.rol(token)
        }
        val rol = RedApi.registrarDocente(token)
        prefs.edit().remove(pendiente).apply()
        return rol
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

    internal fun cuentas() = db.userDao().observeAll()

    internal suspend fun editarCuenta(id: Int, nombre: String, correo: String): String? {
        if (_rolRed.value != RolRed.INSPECTOR) return "Esta acción requiere el rol Inspector."
        val nombreLimpio = nombre.trim()
        val correoLimpio = correo.trim().lowercase()
        if (nombreLimpio.isBlank()) return "Ingresa el nombre de la cuenta."
        Validators.email(correoLimpio)?.let { return it }
        return db.withTransaction {
            val dao = db.userDao()
            val cuenta = dao.findById(id) ?: return@withTransaction "La cuenta ya no existe."
            val otra = dao.findByEmail(correoLimpio)
            if (otra != null && otra.id != id) return@withTransaction "Ese correo ya está registrado."
            dao.update(cuenta.copy(name = nombreLimpio, email = correoLimpio))
            if (prefs.getInt("userId", -1) == id) {
                prefs.edit().putString("userName", nombreLimpio).apply()
                _userName.value = nombreLimpio
            }
            null
        }
    }

    // Valida el nombre y la capacidad antes de insertar una sala local.
    fun guardarSala(
        sala: Sala?,
        nombre: String,
        capacidad: Int,
        ubicacion: String,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            if (_rolRed.value != RolRed.INSPECTOR) {
                onResult("Solo los inspectores pueden gestionar salas.")
                return@launch
            }
            val cleanNombre = nombre.trim()
            if (cleanNombre.isEmpty()) {
                onResult("Ingresa el nombre de la sala")
                return@launch
            }
            if (capacidad <= 0 && !(capacidad == 0 && sala?.capacidad == 0)) {
                onResult("La capacidad debe ser mayor a 0")
                return@launch
            }
            val error = db.withTransaction {
                val dao = db.salaDao()
                val existente = dao.findByNombre(cleanNombre)
                if (existente != null && existente.id != sala?.id) {
                    return@withTransaction "Ya existe una sala con ese nombre"
                }
                val ubicacionLimpia = ubicacion.trim().ifEmpty { "Sin ubicación especificada" }
                if (sala == null) {
                    dao.insert(Sala(nombre = cleanNombre, capacidad = capacidad, ubicacion = ubicacionLimpia))
                } else {
                    val actual = dao.findById(sala.id) ?: return@withTransaction "La sala ya no existe."
                    dao.update(actual.copy(nombre = cleanNombre, capacidad = capacidad, ubicacion = ubicacionLimpia))
                }
                null
            }
            onResult(error)
        }
    }

    fun restablecerSala(id: Int, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            if (_rolRed.value != RolRed.INSPECTOR) {
                onResult("Solo los inspectores pueden restablecer una sala.")
                return@launch
            }
            val error = db.withTransaction {
                val dao = db.salaDao()
                val sala = dao.findById(id) ?: return@withTransaction "La sala ya no existe."
                val ahora = horaEscuela()
                val aprobadas = db.solicitudDao().aprobadasPorSala(id)
                if (estadoSalaLocal(aprobadas, ahora, sala.restablecidaEn) != EstadoSalaLocal.TERMINADA) {
                    return@withTransaction "Solo se puede restablecer una sala terminada."
                }
                dao.update(sala.copy(restablecidaEn = ahora.toString()))
                null
            }
            onResult(error)
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
        fecha: LocalDate,
        horaInicio: String,
        horaFin: String,
        solicitud: SolicitudSala? = null,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val puedeGuardar = if (solicitud == null) {
                _rolRed.value == RolRed.DOCENTE && Validators.formatRut(rutInput) == _userRut.value
            } else {
                _rolRed.value == RolRed.INSPECTOR
            }
            if (!puedeGuardar) {
                onResult("Tu cuenta no tiene permiso para guardar esta solicitud.")
                return@launch
            }
            // 1. Verificar RUT del docente
            val docente = if (solicitud == null) {
                Validators.rut(rutInput)?.let {
                    onResult(it)
                    return@launch
                }
                db.userDao().findByRut(Validators.formatRut(rutInput)) ?: run {
                    onResult("RUT no verificado: el docente no está registrado en el sistema")
                    return@launch
                }
            } else {
                null
            }

            errorHorarioReserva(horaInicio, horaFin)?.let {
                onResult(it)
                return@launch
            }

            val error = db.withTransaction {
                val dao = db.solicitudDao()
                val salaActual = db.salaDao().findById(sala.id)
                    ?: return@withTransaction "La sala ya no existe."
                val anterior = solicitud?.let { dao.findById(it.id) }
                if (solicitud != null && anterior == null) return@withTransaction "La solicitud ya no existe."
                if (anterior?.rechazada == true) return@withTransaction "La solicitud ya fue rechazada."
                if (dao.hayCruceAprobado(sala.id, fecha.toString(), horaInicio, horaFin, anterior?.id ?: 0)) {
                    return@withTransaction "La sala ya está reservada en ese bloque horario."
                }

                val nueva = SolicitudSala(
                    id = anterior?.id ?: 0,
                    salaId = salaActual.id,
                    salaNombre = salaActual.nombre,
                    docenteNombre = anterior?.docenteNombre ?: checkNotNull(docente).name,
                    docenteRut = anterior?.docenteRut ?: checkNotNull(docente).rut,
                    horaInicio = horaInicio,
                    horaFin = horaFin,
                    fecha = fecha.toString(),
                    aprobada = anterior?.aprobada ?: false
                )
                if (solicitud == null) dao.insert(nueva)
                else if (dao.update(nueva) == 0) return@withTransaction "La solicitud ya no existe."
                null
            }
            onResult(error)
        }
    }

    fun resolverSolicitud(id: Int, aprobar: Boolean, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            if (_rolRed.value != RolRed.INSPECTOR) {
                onResult("Solo un Inspector puede resolver solicitudes.")
                return@launch
            }
            val error = db.withTransaction {
                val dao = db.solicitudDao()
                val solicitud = dao.findById(id) ?: return@withTransaction "La solicitud ya no existe."
                if (solicitud.aprobada || solicitud.rechazada) return@withTransaction "La solicitud ya fue resuelta."
                if (aprobar) {
                    if (db.salaDao().findById(solicitud.salaId) == null) return@withTransaction "La sala ya no existe."
                    if (dao.hayCruceAprobado(solicitud.salaId, solicitud.fecha, solicitud.horaInicio, solicitud.horaFin, id)) {
                        return@withTransaction "Ya hay una reserva aprobada para esta sala en ese horario."
                    }
                }
                dao.update(solicitud.copy(aprobada = aprobar, rechazada = !aprobar))
                null
            }
            onResult(error)
        }
    }

    // Elimina la solicitud de la base de datos local.
    fun eliminarSolicitud(solicitud: SolicitudSala) {
        viewModelScope.launch {
            if (_rolRed.value == RolRed.INSPECTOR ||
                (_rolRed.value == RolRed.DOCENTE && solicitud.docenteRut == _userRut.value)
            ) {
                db.solicitudDao().delete(solicitud)
            }
        }
    }













}


