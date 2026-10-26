package com.example.evaluacion2

import android.util.Patterns

object Validators {
    fun name(v: String): String? =
        if (v.trim().length < 3) "Mínimo 3 caracteres" else null

    fun email(v: String): String? =
        if (!Patterns.EMAIL_ADDRESS.matcher(v.trim()).matches()) "Correo inválido" else null

    fun password(v: String): String? = when {
        v.length < 6 -> "Mínimo 6 caracteres"
        v.none { it.isDigit() } -> "Debe incluir al menos un número"
        else -> null
    }

    fun confirm(p: String, c: String): String? =
        if (p != c) "Las contraseñas no coinciden" else null

    // ---------- RUT chileno ----------

    /** Deja solo dígitos y la K: "12.345.678-5" -> "123456785" */
    fun cleanRut(v: String): String =
        v.filter { it.isDigit() || it == 'k' || it == 'K' }.uppercase()

    /** Da formato mientras se escribe: "123456785" -> "12.345.678-5" */
    fun formatRut(v: String): String {
        val c = cleanRut(v)
        if (c.length < 2) return c
        val body = c.dropLast(1)
        val dots = body.reversed().chunked(3).joinToString(".").reversed()
        return "$dots-${c.last()}"
    }

    fun rut(v: String): String? {
        val c = cleanRut(v)
        if (c.isEmpty()) return "Ingresa tu RUT"
        if (c.length < 8) return "RUT incompleto"

        val body = c.dropLast(1)
        if (!body.all { it.isDigit() }) return "La K solo puede ir al final"

        // Algoritmo módulo 11
        var sum = 0
        var factor = 2
        for (d in body.reversed()) {
            sum += d.digitToInt() * factor
            factor = if (factor == 7) 2 else factor + 1
        }
        val r = 11 - sum % 11
        val expected = when (r) {
            11 -> '0'
            10 -> 'K'
            else -> r.digitToChar()
        }
        return if (c.last() == expected) null else "RUT inválido (dígito verificador incorrecto)"
    }
}