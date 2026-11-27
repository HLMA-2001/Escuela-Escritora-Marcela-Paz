package com.example.evaluacion2

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class EstadoSalaLocalTest {
    private val fecha = LocalDate.of(2026, 11, 27)
    private val manana = SolicitudSala(salaId = 1, salaNombre = "Biblioteca",
        docenteNombre = "Docente", docenteRut = "", horaInicio = "09:00", horaFin = "10:00",
        fecha = fecha.toString(), aprobada = true)
    private val tarde = manana.copy(horaInicio = "15:00", horaFin = "16:00")

    @Test
    fun marcaElInicioComoOcupadoYElFinComoTerminado() {
        assertEquals(EstadoSalaLocal.DISPONIBLE, estadoSalaLocal(listOf(manana), fecha.atTime(8, 59)))
        assertEquals(EstadoSalaLocal.OCUPADA, estadoSalaLocal(listOf(manana), fecha.atTime(9, 0)))
        assertEquals(EstadoSalaLocal.TERMINADA, estadoSalaLocal(listOf(manana), fecha.atTime(10, 0)))
    }

    @Test
    fun dejaDisponibleEntreBloquesYSinReservas() {
        val ahora = fecha.atTime(12, 0)
        assertEquals(EstadoSalaLocal.DISPONIBLE, estadoSalaLocal(listOf(manana, tarde), ahora))
        assertEquals(EstadoSalaLocal.DISPONIBLE, estadoSalaLocal(emptyList(), ahora))
        assertEquals(EstadoSalaLocal.OCUPADA, estadoSalaLocal(listOf(manana, tarde), fecha.atTime(15, 30)))
    }

    @Test
    fun lasSolicitudesPendientesYDeOtraFechaNoOcupanLaSala() {
        val ahora = fecha.atTime(9, 30)
        assertEquals(EstadoSalaLocal.DISPONIBLE, estadoSalaLocal(listOf(manana.copy(aprobada = false)), ahora))
        assertEquals(EstadoSalaLocal.TERMINADA, estadoSalaLocal(listOf(manana), ahora.plusDays(1)))
    }

    @Test
    fun conservaLaFechaDeSantiagoCuandoEnUtcEsElDiaSiguiente() {
        val instante = horaEscuela(Instant.parse("2026-11-28T02:55:00Z"))
        assertEquals(fecha.atTime(23, 55), instante)
        val nocturna = manana.copy(horaInicio = "23:50", horaFin = "23:59")
        assertEquals(EstadoSalaLocal.OCUPADA, estadoSalaLocal(listOf(nocturna), instante))
    }

    @Test
    fun restablecerMantieneElHistorialYLosBloquesPosteriores() {
        val restablecida = fecha.atTime(12, 0).toString()
        assertEquals(EstadoSalaLocal.DISPONIBLE, estadoSalaLocal(listOf(manana), fecha.atTime(12, 1), restablecida))
        assertEquals(EstadoSalaLocal.OCUPADA, estadoSalaLocal(listOf(manana, tarde), fecha.atTime(15, 30), restablecida))
        assertEquals(EstadoSalaLocal.TERMINADA, estadoSalaLocal(listOf(manana, tarde), fecha.atTime(16, 0), restablecida))
    }
}
