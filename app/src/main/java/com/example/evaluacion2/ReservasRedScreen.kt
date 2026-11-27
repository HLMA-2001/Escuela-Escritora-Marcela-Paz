package com.example.evaluacion2

import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import androidx.compose.ui.text.input.KeyboardType
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val permisoRedLocal = "android.permission.ACCESS_LOCAL_NETWORK"
private val formatoHora = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun CamposHoraReserva(
    inicio: String,
    fin: String,
    cambiarInicio: (String) -> Unit,
    cambiarFin: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CampoHoraReserva("Inicio", inicio, cambiarInicio, Modifier.weight(1f))
        CampoHoraReserva("Término", fin, cambiarFin, Modifier.weight(1f))
    }
}

@Composable
private fun CampoHoraReserva(etiqueta: String, hora: String, cambiar: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = hora,
        onValueChange = { texto -> formatearEntradaHora(texto)?.let(cambiar) },
        label = { Text(etiqueta) },
        placeholder = { Text("HH:mm") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
internal fun BotonesDecisionReserva(
    modifier: Modifier = Modifier,
    habilitados: Boolean = true,
    decidir: (Boolean) -> Unit
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(modifier = Modifier.weight(1f), enabled = habilitados, onClick = { decidir(true) }) {
            Text("Aprobar")
        }
        Button(
            modifier = Modifier.weight(1f),
            enabled = habilitados,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            onClick = { decidir(false) }
        ) { Text("Rechazar") }
    }
}

@Composable
internal fun ReservasScreen(vm: AppViewModel) {
    val locales by vm.catalogoLocal.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        SelectorCatalogo(locales, vm::seleccionarCatalogo)
        if (locales) SolicitudesScreen(vm) else ReservasRedScreen(vm)
    }
}

@Composable
internal fun CatalogoSalasScreen(vm: AppViewModel) {
    val locales by vm.catalogoLocal.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        SelectorCatalogo(locales, vm::seleccionarCatalogo)
        if (locales) SalasScreen(vm) else ReservasRedScreen(vm, soloSalas = true)
    }
}

@Composable
private fun SelectorCatalogo(locales: Boolean, seleccionar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = !locales, onClick = { seleccionar(false) }, label = { Text("En red") })
        FilterChip(selected = locales, onClick = { seleccionar(true) }, label = { Text("En este dispositivo") })
    }
}

// Gestiona el formulario por fecha y mantiene sincronizadas las listas en red.
@Composable
internal fun ReservasRedScreen(vm: AppViewModel, soloSalas: Boolean = false) {
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
    var fechaTexto by rememberSaveable { mutableStateOf(horaEscuela().toLocalDate().toString()) }
    val fecha = LocalDate.parse(fechaTexto)
    val instante by observarHoraReservas()
    val fechaActual = instante.toLocalDate().toString()
    val horaActual = instante.toLocalTime().format(formatoHora)
    var inicio by rememberSaveable { mutableStateOf("09:00") }
    var fin by rememberSaveable { mutableStateOf("10:00") }
    var mostrarSala by remember { mutableStateOf(false) }
    var salaEditada by remember { mutableStateOf<SalaRemota?>(null) }
    var reservaEditada by remember { mutableStateOf<ReservaRemota?>(null) }
    var reservaPorCancelar by remember { mutableStateOf<ReservaRemota?>(null) }
    val lista = rememberLazyListState()
    var menu by remember { mutableStateOf(false) }

    // Carga los dos catálogos y conserva la sala seleccionada si sigue disponible.
    suspend fun actualizar() {
        val rolActual = token?.let { vm.consultarRol(it) } ?: RolRed.SIN_ROL
        if (token == vm.tokenReservas()) vm.actualizarRol(rolActual)
        val nuevasSalas = RedApi.salas()
        val nuevasReservas = RedApi.reservas(token)
        salas = nuevasSalas
        reservas = nuevasReservas
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
        if (!accesoLocal) return@LaunchedEffect
        ejecutar { actualizar() }
        while (true) {
            delay(30_000)
            if (!cargando) ejecutar { actualizar() }
        }
    }

    fun estadoSala(id: String): String =
        if (reservas.any { it.aprobada && it.salaId == id && it.fecha == fechaActual &&
                horaActual >= it.inicio && horaActual < it.fin }) "Ocupada ahora"
        else "Disponible ahora"

    if (soloSalas) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(instante.etiquetaHoraEscuela(), style = MaterialTheme.typography.labelLarge)
                if (!accesoLocal) {
                    Button(onClick = { pedirAcceso.launch(permisoRedLocal) }) { Text("Permitir red local") }
                } else {
                    OutlinedButton(enabled = !cargando, onClick = { scope.launch { ejecutar { actualizar() } } }) {
                        Text("Actualizar")
                    }
                }
                if (cargando) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (accesoLocal && salas.isEmpty() && !cargando && error == null) Text("No hay salas en red.")
            }
            items(salas, key = { it.id }) { sala ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(sala.nombre, style = MaterialTheme.typography.titleMedium)
                        Text(if (error == null) estadoSala(sala.id) else "Estado sin verificar")
                        Text("${sala.capacidad} personas")
                        if (sala.ubicacion.isNotBlank()) Text(sala.ubicacion)
                        reservas.asSequence()
                            .filter { it.aprobada && it.salaId == sala.id &&
                                (it.fecha > fechaActual || (it.fecha == fechaActual && it.inicio > horaActual)) }
                            .minWithOrNull(compareBy(ReservaRemota::fecha, ReservaRemota::inicio))
                            ?.let { Text("Próxima reserva · ${it.fecha} · ${it.inicio} a ${it.fin}") }
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        state = lista,
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
            val editando = inspector && reservaEditada != null
            val eligeHorario = docente || editando
            val errorHorario = if (eligeHorario) errorHorarioReserva(inicio, fin) else null
            val horarioValido = errorHorario == null
            val salasElegibles = if (eligeHorario && horarioValido) {
                salas.filter { sala ->
                    reservas.none {
                        it.aprobada && it.id != reservaEditada?.id && it.salaId == sala.id && it.fecha == fechaTexto &&
                            inicio < it.fin && fin > it.inicio
                    }
                }
            } else salas
            val seleccionada = salasElegibles.firstOrNull { it.id == salaId }
            Text(
                when {
                    editando -> "Editar reserva"
                    docente -> "Solicitar clase"
                    else -> "Consultar salas"
                },
                style = MaterialTheme.typography.titleLarge
            )
            Box {
                OutlinedButton(enabled = salasElegibles.isNotEmpty() && !cargando, onClick = { menu = true }) {
                    Text(seleccionada?.nombre ?: if (salasElegibles.isEmpty()) {
                        "Sin salas disponibles"
                    } else {
                        "Elige una sala disponible"
                    })
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    salasElegibles.forEach { sala ->
                        DropdownMenuItem(
                            text = { Text("${sala.nombre} · ${estadoSala(sala.id)}") },
                            onClick = {
                                salaId = sala.id
                                menu = false
                            }
                        )
                    }
                }
            }
            if (seleccionada != null && accesoLocal && !cargando && error == null) {
                Text(estadoSala(seleccionada.id), style = MaterialTheme.typography.labelLarge)
            }
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
            Text(instante.etiquetaHoraEscuela(), style = MaterialTheme.typography.labelMedium)
            if (eligeHorario) {
                CamposHoraReserva(inicio, fin, { inicio = it }, { fin = it })
                if (errorHorario != null) Text(errorHorario, color = MaterialTheme.colorScheme.error)
            }
            if (eligeHorario) Button(enabled = accesoLocal && token != null && seleccionada != null && horarioValido && !cargando, onClick = {
                scope.launch {
                    ejecutar {
                        val id = reservaEditada?.id?.takeIf { editando }
                        RedApi.reservar(checkNotNull(seleccionada).id, fechaTexto, inicio, fin, checkNotNull(token), id)
                        reservaEditada = null
                        actualizar()
                    }
                }
            }) { Text(if (editando) "Guardar cambios" else "Enviar solicitud") }
            if (editando) TextButton(enabled = !cargando, onClick = { reservaEditada = null }) {
                Text("Descartar cambios")
            }
        }
        item { Text("Reservas registradas", style = MaterialTheme.typography.titleLarge) }
        if (inspector && accesoLocal && error == null) item {
            val pendientes = reservas.count { !it.aprobada && !it.rechazada }
            Text(if (pendientes == 0) "No hay solicitudes pendientes de aprobación en red."
                else "Solicitudes pendientes de aprobación: $pendientes")
        }
        if (accesoLocal && reservas.isEmpty() && !cargando && error == null) item { Text("Todavía no hay reservas.") }
        // Expone la cancelación solo cuando el servidor identifica la reserva como propia.
        items(reservas.sortedBy { inspector && (it.aprobada || it.rechazada) }, key = { it.id }) { reserva ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(reserva.sala, style = MaterialTheme.typography.titleMedium)
                    Text("${reserva.fecha} · ${reserva.inicio} a ${reserva.fin}")
                    Text(etiquetaSolicitud(reserva.aprobada, reserva.rechazada))
                    if (inspector && !reserva.aprobada && !reserva.rechazada && token != null) {
                        BotonesDecisionReserva(habilitados = !cargando) { aprobar ->
                            scope.launch {
                                ejecutar {
                                    RedApi.resolverReserva(reserva.id, aprobar, token)
                                    if (!aprobar && reservaEditada?.id == reserva.id) reservaEditada = null
                                    actualizar()
                                }
                            }
                        }
                    }
                    if (inspector && !reserva.rechazada) TextButton(enabled = !cargando, onClick = {
                        reservaEditada = reserva
                        salaId = reserva.salaId
                        fechaTexto = reserva.fecha
                        inicio = reserva.inicio
                        fin = reserva.fin
                        scope.launch { lista.animateScrollToItem(1) }
                    }) { Text("Editar reserva") }
                    if (!reserva.rechazada && token != null && (inspector || (docente && reserva.propia))) {
                        TextButton(enabled = !cargando, onClick = { reservaPorCancelar = reserva }) {
                            Text("Cancelar reserva")
                        }
                    }
                }
            }
        }
    }

    reservaPorCancelar?.let { reserva ->
        if (token != null && (inspector || (docente && reserva.propia))) {
            AlertDialog(
                onDismissRequest = { reservaPorCancelar = null },
                title = { Text("Cancelar reserva") },
                text = { Text("${reserva.sala} · ${reserva.fecha} · ${reserva.inicio} a ${reserva.fin}") },
                confirmButton = {
                    Button(onClick = {
                        reservaPorCancelar = null
                        scope.launch {
                            ejecutar {
                                RedApi.cancelar(reserva.id, token)
                                if (reservaEditada?.id == reserva.id) reservaEditada = null
                                actualizar()
                            }
                        }
                    }) { Text("Cancelar reserva") }
                },
                dismissButton = { TextButton(onClick = { reservaPorCancelar = null }) { Text("Volver") } }
            )
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




