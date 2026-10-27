package com.example.evaluacion2

import android.util.Patterns

object Validators {
    fun name(v: String): String? {
        val n = v.trim()
        return when {
            n.isEmpty() -> "Ingresa tu nombre"
            n.length < 3 -> "Mínimo 3 caracteres"
            n.length > 40 -> "Máximo 40 caracteres"
            !n.all { it.isLetter() || it == ' ' } -> "Solo letras y espacios"
            "  " in n -> "No uses espacios dobles"
            REPEATED_REGEX.containsMatchIn(n.lowercase()) -> "No repitas la misma letra 3 veces seguidas"
            n.split(" ").any { it.length < 2 } -> "Cada palabra debe tener al menos 2 letras"
            !n.contains(" ") -> "Ingresa nombre y apellido"
            else -> null
        }
    }
    private val EMAIL_REGEX = Regex("^[A-Za-z]+([._%+-][A-Za-z0-9]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")
    private val ALLOWED_DOMAINS = listOf(
        "gmail.com", "duoc.cl", "outlook.com", "hotmail.com", "duocuc.cl"
    )
    private val REPEATED_REGEX = Regex("(.)\\1{2,}")

    fun email(v: String): String? = when {
        v.isBlank() -> "Ingresa tu correo"
        v.any { it.isWhitespace() } -> "No puede tener espacios"
        !v.first().isLetter() -> "Debe comenzar con una letra"
        v.substringBefore("@").length < 3 -> "Mínimo 3 caracteres antes del @"
        REPEATED_REGEX.containsMatchIn(v.substringBefore("@")) -> "No repitas el mismo carácter 3 veces seguidas"
        !EMAIL_REGEX.matches(v) -> "Correo inválido"
        v.substringAfter("@").lowercase() !in ALLOWED_DOMAINS -> "Dominio no permitido"
        else -> null
    }
    private val COMMON_PASSWORDS = listOf("password", "contraseña", "qwerty", "admin", "12345678")
    private val SEQUENCES = listOf("0123456789", "abcdefghijklmnopqrstuvwxyz", "qwertyuiop")

    private fun hasSequence(v: String): Boolean {
        val s = v.lowercase()
        return SEQUENCES.any { seq -> seq.windowed(4).any { it in s } }
    }

    fun password(v: String): String? = when {
        v.length < 8 -> "Mínimo 8 caracteres"
        v.length > 32 -> "Máximo 32 caracteres"
        v.any { it.isWhitespace() } -> "No puede tener espacios"
        v.none { it.isUpperCase() } -> "Debe incluir una mayúscula"
        v.none { it.isLowerCase() } -> "Debe incluir una minúscula"
        v.none { it.isDigit() } -> "Debe incluir al menos un número"
        v.all { it.isLetterOrDigit() } -> "Debe incluir un símbolo (ej: ! @ # $)"
        REPEATED_REGEX.containsMatchIn(v) -> "No repitas el mismo carácter 3 veces seguidas"
        hasSequence(v) -> "No uses secuencias como 1234 o abcd"
        COMMON_PASSWORDS.any { it in v.lowercase() } -> "Contraseña demasiado común"
        else -> null
    }
    fun confirm(p: String, c: String): String? =
        if (p != c) "Las contraseñas no coinciden" else null

    fun cleanRut(v: String): String =
        v.filter { it.isDigit() || it == 'k' || it == 'K' }.uppercase()
    
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
