package com.example.evaluacion2

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val permisoRedLocal = "android.permission.ACCESS_LOCAL_NETWORK"
private val formatoHora = DateTimeFormatter.ofPattern("HH:mm")

// Gestiona el formulario por fecha y mantiene sincronizadas las listas en red.
@Composable
internal fun ReservasRedScreen(vm: AppViewModel) {
    val rut by vm.userRut.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Android 17 exige autorización para acceder a los servicios locales del emulador.
    val requierePermiso = Build.VERSION.SDK_INT >= 37 && RedApi.usaRedLocal
    var accesoLocal by remember {
        mutableStateOf(!requierePermiso ||
            ContextCompat.checkSelfPermission(context, permisoRedLocal) == PackageManager.PERMISSION_GRANTED)
    }
    val pedirAcceso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        accesoLocal = it
    }
    // Consulta el rol del servidor antes de habilitar operaciones de escritura.
    val rol by vm.rolRed.collectAsStateWithLifecycle()
    val docente = rol == RolRed.DOCENTE
    val inspector = rol == RolRed.INSPECTOR
    val token = remember(rut) { vm.tokenReservas() }
    var salas by remember { mutableStateOf(emptyList<SalaRemota>()) }
    var reservas by remember { mutableStateOf(emptyList<ReservaRemota>()) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var salaId by rememberSaveable { mutableStateOf("") }
    var fechaTexto by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val fecha = LocalDate.parse(fechaTexto)
    var inicio by rememberSaveable { mutableStateOf("09:00") }
    var fin by rememberSaveable { mutableStateOf("10:00") }
    var feriado by remember { mutableStateOf("") }
    var mostrarSala by remember { mutableStateOf(false) }
    var salaEditada by remember { mutableStateOf<SalaRemota?>(null) }
    var menu by remember { mutableStateOf(false) }

    // Carga los dos catálogos y conserva la sala seleccionada si sigue disponible.
    suspend fun actualizar() {
        vm.actualizarRol(RolRed.SIN_ROL)
        val rolActual = token?.let { RedApi.rol(it) } ?: RolRed.SIN_ROL
        val nuevasSalas = RedApi.salas()
        val nuevasReservas = RedApi.reservas(token)
        salas = nuevasSalas
        reservas = nuevasReservas
        vm.actualizarRol(rolActual)
        if (salas.none { it.id == salaId }) salaId = salas.firstOrNull()?.id.orEmpty()
    }

    // Controla la espera y muestra los mensajes sin interrumpir la navegación.
    suspend fun ejecutar(accion: suspend () -> Unit) {
        cargando = true
        error = null
        try {
            accion()
        } catch (cancelado: CancellationException) {
            throw cancelado
        } catch (fallo: ApiException) {
            error = fallo.message
        } catch (_: Exception) {
            error = "No fue posible comunicarse con los servicios. Verifica que ambos estén activos."
        } finally {
            cargando = false
        }
    }

    LaunchedEffect(accesoLocal) {
        if (accesoLocal) ejecutar { actualizar() }
    }
    // Actualiza el aviso de feriado solo al cambiar la fecha seleccionada.
    LaunchedEffect(fecha) {
        feriado = "Consultando feriados de Chile…"
        feriado = try {
            RedApi.feriado(fecha)?.let { "Feriado nacional · $it" } ?: "Sin feriado nacional en esta fecha"
        } catch (cancelado: CancellationException) {
            throw cancelado
        } catch (_: Exception) {
            "Consulta de feriados no disponible"
        }
    }

    // Abre el selector nativo en formato de 24 horas.
    fun seleccionarHora(entrada: String, guardar: (String) -> Unit) {
        val hora = LocalTime.parse(entrada)
        TimePickerDialog(context, { _, h, m -> guardar(LocalTime.of(h, m).format(formatoHora)) },
            hora.hour, hora.minute, true).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = accesoLocal && !cargando, onClick = { scope.launch { ejecutar { actualizar() } } }) {
                    Text("Actualizar")
                }
                if (inspector) Button(enabled = accesoLocal && !cargando, onClick = {
                    salaEditada = null
                    mostrarSala = true
                }) {
                    Text("Agregar sala")
                }
            }
            if (!accesoLocal) {
                Text("Permite el acceso a la red local para conectar con los servicios de la escuela.")
                TextButton(onClick = { pedirAcceso.launch(permisoRedLocal) }) { Text("Permitir red local") }
            }
            if (token == null) Text("Inicia sesión para trabajar con tu rol.")
            else if (rol == RolRed.SIN_ROL && !cargando) Text("Tu cuenta necesita un rol asignado. Revisa la credencial en Perfil.")
            if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Text(if (docente) "Solicitar clase" else "Consultar salas", style = MaterialTheme.typography.titleLarge)
            Box {
                OutlinedButton(enabled = salas.isNotEmpty() && !cargando, onClick = { menu = true }) {
                    Text(salas.firstOrNull { it.id == salaId }?.nombre ?: "Sin salas disponibles")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    salas.forEach { sala ->
                        DropdownMenuItem(
                            text = { Text("${sala.nombre} · ${sala.capacidad} personas ${sala.ubicacion}") },
                            onClick = {
                                salaId = sala.id
                                menu = false
                            }
                        )
                    }
                }
            }
            val seleccionada = salas.firstOrNull { it.id == salaId }
            if (inspector && seleccionada != null) {
                TextButton(enabled = !cargando, onClick = {
                    salaEditada = seleccionada
                    mostrarSala = true
                }) { Text("Modificar sala") }
            }
            OutlinedButton(onClick = {
                DatePickerDialog(context, { _, y, m, d -> fechaTexto = LocalDate.of(y, m + 1, d).toString() },
                    fecha.year, fecha.monthValue - 1, fecha.dayOfMonth).show()
            }) { Text("Fecha · $fechaTexto") }
            Text(feriado, style = MaterialTheme.typography.bodyMedium)
            if (docente) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { seleccionarHora(inicio) { inicio = it } }) { Text("Desde $inicio") }
                OutlinedButton(onClick = { seleccionarHora(fin) { fin = it } }) { Text("Hasta $fin") }
            }
            if (docente) Button(enabled = accesoLocal && token != null && salaId.isNotEmpty() && !cargando, onClick = {
                if (!LocalTime.parse(inicio).isBefore(LocalTime.parse(fin))) {
                    error = "La hora de término debe ser posterior a la hora de inicio."
                } else {
                    scope.launch {
                        ejecutar {
                            RedApi.reservar(salaId, fechaTexto, inicio, fin, checkNotNull(token))
                            actualizar()
                        }
                    }
                }
            }) { Text("Solicitar clase") }
        }
        item { Text("Reservas registradas", style = MaterialTheme.typography.titleLarge) }
        if (accesoLocal && reservas.isEmpty() && !cargando && error == null) item { Text("Todavía no hay reservas.") }
        // Expone la cancelación solo cuando el servidor identifica la reserva como propia.
        items(reservas, key = { it.id }) { reserva ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(reserva.sala, style = MaterialTheme.typography.titleMedium)
                    Text("${reserva.fecha} · ${reserva.inicio} a ${reserva.fin}")
                    if (docente && token != null && reserva.propia) TextButton(enabled = !cargando, onClick = {
                        scope.launch {
                            ejecutar {
                                RedApi.cancelar(reserva.id, token)
                                actualizar()
                            }
                        }
                    }) { Text("Cancelar reserva propia") }
                }
            }
        }
    }

    if (mostrarSala && inspector && token != null) {
        NuevaSalaRedDialog(salaEditada, onDismiss = { mostrarSala = false }, onSubmit = { nombre, capacidad, ubicacion ->
            mostrarSala = false
            scope.launch {
                ejecutar {
                    RedApi.guardarSala(salaEditada?.id, nombre, capacidad, ubicacion, token)
                    actualizar()
                }
            }
        })
    }
}

// Pide los datos de la sala antes de enviarlos al microservicio.
@Composable
private fun NuevaSalaRedDialog(sala: SalaRemota?, onDismiss: () -> Unit, onSubmit: (String, Int, String) -> Unit) {
    var nombre by rememberSaveable(sala?.id) { mutableStateOf(sala?.nombre.orEmpty()) }
    var capacidad by rememberSaveable(sala?.id) { mutableStateOf(sala?.capacidad?.toString().orEmpty()) }
    var ubicacion by rememberSaveable(sala?.id) { mutableStateOf(sala?.ubicacion.orEmpty()) }
    val cupos = capacidad.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (sala == null) "Agregar sala en red" else "Modificar sala") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(capacidad, { capacidad = it }, label = { Text("Capacidad") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                OutlinedTextField(ubicacion, { ubicacion = it }, label = { Text("Ubicación") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = nombre.isNotBlank() && cupos != null && cupos > 0, onClick = {
                onSubmit(nombre, checkNotNull(cupos), ubicacion)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}




