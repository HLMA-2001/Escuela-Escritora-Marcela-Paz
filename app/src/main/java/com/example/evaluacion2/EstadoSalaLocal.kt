package com.example.evaluacion2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val zonaEscuela = ZoneId.of("America/Santiago")
private val formatoHoraEscuela = DateTimeFormatter.ofPattern("dd/MM · HH:mm")
private val formatoHoraReserva = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")

internal fun formatearEntradaHora(texto: String): String? {
    val digitos = texto.filter { it in '0'..'9' }
    if (digitos.length > 4) return null
    return if (digitos.length > 2) digitos.take(2) + ":" + digitos.drop(2) else digitos
}

internal fun errorHorarioReserva(inicio: String, fin: String): String? = when {
    !formatoHoraReserva.matches(inicio) || !formatoHoraReserva.matches(fin) -> "Escribe ambas horas en formato HH:mm."
    inicio >= fin -> "La hora de término debe ser posterior a la de inicio."
    else -> null
}

internal fun horaEscuela(instante: Instant = Instant.now()): LocalDateTime =
    LocalDateTime.ofInstant(instante, zonaEscuela)

internal fun LocalDateTime.etiquetaHoraEscuela(): String =
    "Hora de la escuela · ${format(formatoHoraEscuela)}"

internal enum class EstadoSalaLocal(val filtro: String, val etiqueta: String) {
    DISPONIBLE("Disponibles", "Disponible"),
    OCUPADA("Ocupadas", "Ocupada"),
    TERMINADA("Terminadas", "Disponible · último bloque terminado")
}

internal fun etiquetaSolicitud(aprobada: Boolean, rechazada: Boolean): String = when {
    aprobada -> "Aprobada"
    rechazada -> "Rechazada"
    else -> "Pendiente de aprobación"
}

@Composable
internal fun observarHoraReservas(): State<LocalDateTime> = produceState(horaEscuela()) {
    while (true) {
        val instante = horaEscuela()
        value = instante
        delay(60_000L - instante.second * 1_000L - instante.nano / 1_000_000)
    }
}

internal fun estadoSalaLocal(
    solicitudes: List<SolicitudSala>,
    instante: LocalDateTime,
    restablecidaEn: String? = null
): EstadoSalaLocal {
    val ultimoRestablecimiento = restablecidaEn?.let(LocalDateTime::parse)
    val bloques = solicitudes.filter { it.aprobada }.mapNotNull { solicitud ->
        runCatching {
            val fecha = LocalDate.parse(solicitud.fecha)
            val inicio = fecha.atTime(LocalTime.parse(solicitud.horaInicio))
            val fin = fecha.atTime(LocalTime.parse(solicitud.horaFin))
            if (inicio < fin) inicio to fin else null
        }.getOrNull()
    }
    return when {
        bloques.any { (inicio, fin) -> instante >= inicio && instante < fin } -> EstadoSalaLocal.OCUPADA
        bloques.all { (_, fin) -> instante >= fin } &&
            bloques.any { (_, fin) -> ultimoRestablecimiento == null || fin > ultimoRestablecimiento } -> EstadoSalaLocal.TERMINADA
        else -> EstadoSalaLocal.DISPONIBLE
    }
}
