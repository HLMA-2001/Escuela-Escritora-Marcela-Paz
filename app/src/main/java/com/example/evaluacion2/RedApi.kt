package com.example.evaluacion2

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Representa una sala obtenida del catálogo Spring Boot.
internal data class SalaRemota(val id: String, val nombre: String, val capacidad: Int, val ubicacion: String)
// Representa un bloque remoto y si pertenece a la cuenta actual.
internal data class ReservaRemota(val id: String, val salaId: String, val sala: String, val fecha: String, val inicio: String, val fin: String, val propia: Boolean, val aprobada: Boolean, val rechazada: Boolean)

// Centraliza las URL y convierte las respuestas JSON en datos para Compose.
internal enum class RolRed { DOCENTE, INSPECTOR, SIN_ROL }

internal object RedApi {
    // Consulta el rol que el servidor asignó a esta cuenta.
    suspend fun rol(token: String): RolRed = RolRed.valueOf(
        request(BuildConfig.SALAS_URL + "/api/salas/rol", token = token).trim().removeSurrounding("\"")
    )

    suspend fun registrarDocente(token: String): RolRed = RolRed.valueOf(
        request(BuildConfig.SALAS_URL + "/api/salas/rol/docente", method = "POST", token = token)
            .trim().removeSurrounding("\"")
    )

    suspend fun asignarRol(credencial: String, rol: RolRed, token: String) {
        request(BuildConfig.SALAS_URL + "/api/salas/rol", "PUT",
            JSONObject().put("credencial", credencial).put("rol", rol.name), token)
    }

    // Distingue las direcciones de demostración local de un servidor HTTPS público.
    val usaRedLocal = listOf(BuildConfig.SALAS_URL, BuildConfig.RESERVAS_URL).any { url ->
        val host = URI(url).host.orEmpty()
        host == "localhost" || host.startsWith("127.") || host.startsWith("10.") ||
            host.startsWith("192.168.") || host.endsWith(".local") ||
            (host.startsWith("172.") && (host.split('.').getOrNull(1)?.toIntOrNull() ?: -1) in 16..31)
    }

    // Consulta el catálogo del microservicio de salas.
    suspend fun salas(): List<SalaRemota> = array(BuildConfig.SALAS_URL + "/api/salas").map {
        SalaRemota(it.getString("id"), it.getString("nombre"), it.getInt("capacidad"), it.getString("ubicacion"))
    }

    // Envía el token opcional para distinguir las reservas de la cuenta actual.
    suspend fun reservas(token: String?): List<ReservaRemota> = array(BuildConfig.RESERVAS_URL + "/api/reservas", token).map {
        ReservaRemota(it.getString("id"), it.getString("salaId"), it.getString("sala"), it.getString("fecha"),
            it.getString("inicio"), it.getString("fin"), it.getBoolean("propia"),
            it.optBoolean("aprobada", true), it.optBoolean("rechazada"))
    }

    // Envía el alta o la edición con la credencial del inspector.
    suspend fun guardarSala(id: String?, nombre: String, capacidad: Int, ubicacion: String, token: String) {
        val ruta = if (id == null) "/api/salas" else "/api/salas/$id"
        request(BuildConfig.SALAS_URL + ruta, if (id == null) "POST" else "PUT",
            JSONObject().put("nombre", nombre).put("capacidad", capacidad).put("ubicacion", ubicacion), token)
    }
    // Envía el identificador de sala, la fecha y el bloque al servicio de reservas.
    suspend fun reservar(salaId: String, fecha: String, inicio: String, fin: String, token: String, id: String? = null) {
        val ruta = if (id == null) "/api/reservas" else "/api/reservas/$id"
        request(BuildConfig.RESERVAS_URL + ruta, if (id == null) "POST" else "PUT",
            JSONObject().put("salaId", salaId).put("fecha", fecha).put("inicio", inicio).put("fin", fin), token)
    }

    suspend fun resolverReserva(id: String, aprobar: Boolean, token: String) {
        val accion = if (aprobar) "aprobar" else "rechazar"
        request(BuildConfig.RESERVAS_URL + "/api/reservas/$id/$accion", "PUT", token = token)
    }

    // Solicita la cancelación con la credencial de quien creó la reserva.
    suspend fun cancelar(id: String, token: String) {
        request(BuildConfig.RESERVAS_URL + "/api/reservas/$id", "DELETE", token = token)
    }

    // Convierte la respuesta del servidor en una lista de objetos JSON.
    private suspend fun array(url: String, token: String? = null): List<JSONObject> {
        val result = JSONArray(request(url, token = token))
        return List(result.length()) { result.getJSONObject(it) }
    }

    // Ejecuta HTTP en segundo plano y traduce los errores HTTP a mensajes para la interfaz.
    private suspend fun request(url: String, method: String = "GET", body: JSONObject? = null, token: String? = null): String =
        withContext(Dispatchers.IO) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 5000
                connection.readTimeout = 6000
                connection.setRequestProperty("Accept", "application/json")
                token?.let { connection.setRequestProperty("X-Reserva-Token", it) }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val message = runCatching { JSONObject(error).optString("message") }.getOrNull()
                    throw ApiException(message?.takeIf { it.isNotBlank() } ?: "Error del servicio (HTTP $status)")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
}

internal class ApiException(message: String) : IOException(message)





