package com.example.evaluacion2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

@Composable
internal fun CuentasScreen(vm: AppViewModel) {
    val cuentas by remember(vm) { vm.cuentas() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var cuentaEditada by remember { mutableStateOf<User?>(null) }
    var credencialRol by remember { mutableStateOf<String?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button(onClick = { credencialRol = "" }) { Text("Asignar rol por credencial") }
            mensaje?.let { Text(it) }
            Text("Cuentas de este dispositivo", style = MaterialTheme.typography.titleLarge)
        }
        items(cuentas, key = { it.id }) { cuenta ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(cuenta.name, style = MaterialTheme.typography.titleMedium)
                    Text(cuenta.email)
                    Row {
                        TextButton(onClick = { cuentaEditada = cuenta }) { Text("Editar cuenta") }
                        TextButton(onClick = { credencialRol = vm.tokenReservas(cuenta.id) }) { Text("Cambiar rol") }
                    }
                }
            }
        }
    }

    cuentaEditada?.let { cuenta ->
        var nombre by remember(cuenta.id) { mutableStateOf(cuenta.name) }
        var correo by remember(cuenta.id) { mutableStateOf(cuenta.email) }
        EdicionCuentaDialog(
            titulo = "Editar cuenta",
            onDismiss = { cuentaEditada = null },
            guardar = { vm.editarCuenta(cuenta.id, nombre, correo) },
            onSuccess = { mensaje = "Cuenta actualizada"; cuentaEditada = null },
            scope = scope
        ) {
            OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
            OutlinedTextField(correo, { correo = it }, label = { Text("Correo") }, singleLine = true)
        }
    }

    credencialRol?.let { inicial ->
        var entrada by remember(inicial) { mutableStateOf(inicial) }
        var rol by remember(inicial) { mutableStateOf<RolRed?>(null) }
        EdicionCuentaDialog(
            titulo = "Cambiar rol",
            onDismiss = { credencialRol = null },
            guardar = {
                val token = vm.tokenReservas()
                val destino = entrada.trim()
                val nuevoRol = rol
                when {
                    token == null -> "Inicia sesión para asignar roles."
                    !esCredencialValida(destino) -> "Ingresa la credencial de rol de la cuenta."
                    nuevoRol == null -> "Selecciona un rol."
                    else -> try {
                        RedApi.asignarRol(destino, nuevoRol, token)
                        null
                    } catch (error: ApiException) {
                        error.message ?: "No se pudo cambiar el rol."
                    } catch (_: IOException) {
                        "No se pudo conectar con el servicio de salas."
                    }
                }
            },
            onSuccess = { mensaje = "Rol actualizado"; credencialRol = null },
            scope = scope
        ) {
            if (inicial.isEmpty()) {
                OutlinedTextField(entrada, { entrada = it }, label = { Text("Credencial de rol") }, singleLine = true)
            }
            listOf(RolRed.DOCENTE to "Docente", RolRed.INSPECTOR to "Inspector", RolRed.SIN_ROL to "Sin rol").forEach { (opcion, etiqueta) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = rol == opcion, onClick = { rol = opcion })
                    Text(etiqueta)
                }
            }
        }
    }
}

private fun esCredencialValida(credencial: String): Boolean =
    runCatching { UUID.fromString(credencial).toString().equals(credencial, ignoreCase = true) }.getOrDefault(false)

@Composable
private fun EdicionCuentaDialog(
    titulo: String,
    onDismiss: () -> Unit,
    guardar: suspend () -> String?,
    onSuccess: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    contenido: @Composable () -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!guardando) onDismiss() },
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                contenido()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !guardando, onClick = {
                guardando = true
                scope.launch {
                    try {
                        error = guardar()
                        if (error == null) onSuccess()
                    } finally {
                        guardando = false
                    }
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(enabled = !guardando, onClick = onDismiss) { Text("Cancelar") } }
    )
}
