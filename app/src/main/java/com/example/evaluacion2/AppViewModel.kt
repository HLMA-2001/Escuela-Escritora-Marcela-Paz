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
import kotlinx.coroutines.launch
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
                _startRoute.value = if (db.userDao().count() == 0) "register" else "login"
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
            db.userDao().insert(
                User(name = name.trim(), rut = cleanRut, email = mail, passwordHash = hash(pass))
            )
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
        // Solo borramos la sesión; la foto de cada usuario se conserva
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
}