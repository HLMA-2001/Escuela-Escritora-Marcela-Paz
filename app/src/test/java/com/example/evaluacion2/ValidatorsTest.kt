package com.example.evaluacion2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// Verifica reglas reales del formulario sin iniciar Android.
class ValidatorsTest {
    @Test
    fun aceptaRutConDigitoVerificadorValido() {
        assertNull(Validators.rut("12.345.678-5"))
    }

    @Test
    fun rechazaRutConDigitoVerificadorErroneo() {
        assertNotNull(Validators.rut("12.345.678-9"))
    }

    @Test
    fun formateaRutSinSeparadores() {
        assertEquals("12.345.678-5", Validators.formatRut("123456785"))
    }

    @Test
    fun rechazaConfirmacionDistinta() {
        assertNotNull(Validators.confirm("ClavePrueba!731", "Distinta!864"))
    }
}

