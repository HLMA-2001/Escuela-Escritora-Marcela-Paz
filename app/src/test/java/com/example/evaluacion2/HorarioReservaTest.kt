package com.example.evaluacion2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HorarioReservaTest {
    @Test
    fun formateaLosDigitosAlEscribirOPegarUnaHora() {
        assertEquals("00", formatearEntradaHora("00"))
        assertEquals("00:1", formatearEntradaHora("001"))
        assertEquals("00:15", formatearEntradaHora("0015"))
        assertEquals("12:15", formatearEntradaHora("12:15"))
        assertNull(formatearEntradaHora("00150"))
    }

    @Test
    fun admiteMedianochePeroNoHorasFueraDeRango() {
        assertNull(errorHorarioReserva("00:15", "00:20"))
        assertNull(errorHorarioReserva("12:15", "12:20"))
        assertNotNull(errorHorarioReserva("00:15", "00:15"))
        assertNotNull(errorHorarioReserva("12:20", "12:15"))
        assertNotNull(errorHorarioReserva("23:59", "24:00"))
        assertNotNull(errorHorarioReserva("00:00", "00:60"))
        assertNotNull(errorHorarioReserva("00:1", "00:20"))
    }
}
