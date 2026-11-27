package com.example.evaluacion2

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

// Muestra las salas de Room y permite abrir el formulario de creación local.
@Composable
fun SalasScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val rol by vm.rolRed.collectAsStateWithLifecycle()
    val salas by vm.salas.collectAsStateWithLifecycle()
    val solicitudes by vm.solicitudes.collectAsStateWithLifecycle()
    var filtro by rememberSaveable { mutableStateOf<EstadoSalaLocal?>(null) }
    val ahora by observarHoraReservas()
    val fechaActual = ahora.toLocalDate().toString()
    val horaActual = ahora.toLocalTime().toString().take(5)
    val solicitudesPorSala = remember(solicitudes) { solicitudes.groupBy { it.salaId } }
    val salasVisibles = remember(salas, solicitudesPorSala, ahora, filtro) {
        salas.map { sala -> sala to estadoSalaLocal(solicitudesPorSala[sala.id].orEmpty(), ahora, sala.restablecidaEn) }
            .filter { (_, estado) -> filtro == null || filtro == estado }
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    var salaEditada by remember { mutableStateOf<Sala?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (rol == RolRed.INSPECTOR) Button(
            onClick = { salaEditada = null; showCreateDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Crear sala")
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = filtro == null, onClick = { filtro = null }, label = { Text("Todas") })
            EstadoSalaLocal.entries.forEach { estado ->
                FilterChip(selected = filtro == estado, onClick = { filtro = estado }, label = { Text(estado.filtro) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(ahora.etiquetaHoraEscuela(), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))

        if (salasVisibles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (salas.isEmpty()) "No hay salas registradas." else "No hay salas con este estado.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(salasVisibles, key = { (sala, _) -> sala.id }) { (sala, estado) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MeetingRoom,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sala.nombre,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    estado.etiqueta,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (estado == EstadoSalaLocal.OCUPADA) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                                solicitudesPorSala[sala.id].orEmpty().asSequence()
                                    .filter {
                                        it.aprobada && (it.fecha > fechaActual ||
                                            (it.fecha == fechaActual && it.horaInicio > horaActual))
                                    }
                                    .minWithOrNull(compareBy(SolicitudSala::fecha, SolicitudSala::horaInicio))
                                    ?.let {
                                        Text("Próxima reserva · ${it.fecha} · ${it.horaInicio} a ${it.horaFin}",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                Text(
                                    if (sala.capacidad > 0) "Capacidad: ${sala.capacidad} personas"
                                    else "Capacidad por confirmar",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Ubicación: ${sala.ubicacion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (rol == RolRed.INSPECTOR) IconButton(onClick = {
                                salaEditada = sala
                                showCreateDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar sala")
                            }
                            if (rol == RolRed.INSPECTOR) IconButton(onClick = {
                                vm.eliminarSala(sala)
                                Toast.makeText(context, "Sala eliminada", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar sala",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog && rol == RolRed.INSPECTOR) {
        val editada = salas.firstOrNull { it.id == salaEditada?.id }
        val terminada = editada != null && estadoSalaLocal(
            solicitudesPorSala[editada.id].orEmpty(), ahora, editada.restablecidaEn
        ) == EstadoSalaLocal.TERMINADA
        CrearSalaDialog(
            sala = salaEditada,
            onDismiss = { showCreateDialog = false },
            onRestablecer = if (editada != null && terminada) { responder ->
                vm.restablecerSala(editada.id) { error ->
                    if (error == null) Toast.makeText(context, "Sala disponible", Toast.LENGTH_SHORT).show()
                    responder(error)
                }
            } else null,
            onConfirm = { nombre, capacidad, ubicacion, onError ->
                vm.guardarSala(salaEditada, nombre, capacidad, ubicacion) { err ->
                    if (err == null) {
                        Toast.makeText(context, "Sala guardada", Toast.LENGTH_SHORT).show()
                        showCreateDialog = false
                    } else {
                        onError(err)
                    }
                }
            }
        )
    }
}

// Captura el nombre, la capacidad y la ubicación de una sala local.
@Composable
private fun CrearSalaDialog(
    sala: Sala?,
    onDismiss: () -> Unit,
    onRestablecer: (((String?) -> Unit) -> Unit)?,
    onConfirm: (nombre: String, capacidad: Int, ubicacion: String, onError: (String) -> Unit) -> Unit
) {
    var nombre by rememberSaveable(sala?.id) { mutableStateOf(sala?.nombre.orEmpty()) }
    var capacidadText by rememberSaveable(sala?.id) {
        mutableStateOf(sala?.capacidad?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var ubicacion by rememberSaveable(sala?.id) { mutableStateOf(sala?.ubicacion.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var restableciendo by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (sala == null) "Nueva sala" else "Editar sala") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it; error = null },
                    label = { Text("Nombre de la sala") },
                    placeholder = { Text("Ej: Sala 101 / Laboratorio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = capacidadText,
                    onValueChange = { v ->
                        if (v.all { it.isDigit() } && v.length <= 4) {
                            capacidadText = v
                            error = null
                        }
                    },
                    label = { Text("Capacidad (alumnos)") },
                    placeholder = { Text("Ej: 35") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ubicacion,
                    onValueChange = { ubicacion = it },
                    label = { Text("Ubicación / Piso") },
                    placeholder = { Text("Ej: Primer piso, Pabellón A") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (onRestablecer != null) {
                    OutlinedButton(
                        enabled = !restableciendo,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            restableciendo = true
                            onRestablecer { mensaje ->
                                restableciendo = false
                                error = mensaje
                            }
                        }
                    ) { Text("Restablecer disponibilidad") }
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = !restableciendo, onClick = {
                val cap = capacidadText.toIntOrNull() ?: 0
                onConfirm(nombre, cap, ubicacion) { msg -> error = msg }
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// Muestra y cancela solicitudes guardadas en este dispositivo.
@Composable
fun SolicitudesScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val rol by vm.rolRed.collectAsStateWithLifecycle()
    val solicitudes by vm.solicitudes.collectAsStateWithLifecycle()
    val salas by vm.salas.collectAsStateWithLifecycle()
    val currentUserRut by vm.userRut.collectAsStateWithLifecycle()
    var showRequestDialog by remember { mutableStateOf(false) }
    var solicitudEditada by remember { mutableStateOf<SolicitudSala?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (rol == RolRed.DOCENTE) Button(
            onClick = {
                if (salas.isEmpty()) {
                    Toast.makeText(
                        context,
                        "Primero debes crear al menos una sala en la pestaña Salas",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    showRequestDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Solicitar sala")
        }

        if (rol == RolRed.INSPECTOR) {
            val pendientes = solicitudes.count { !it.aprobada && !it.rechazada }
            Text(if (pendientes == 0) "No hay solicitudes pendientes de aprobación en este dispositivo."
                else "Solicitudes pendientes de aprobación: $pendientes")
        }
        Spacer(Modifier.height(16.dp))

        if (solicitudes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No hay solicitudes de salas registradas.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(solicitudes.sortedBy { rol == RolRed.INSPECTOR && (it.aprobada || it.rechazada) }, key = { it.id }) { sol ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    sol.salaNombre,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${sol.fecha} · ${sol.horaInicio} a ${sol.horaFin}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Docente: ${sol.docenteNombre}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text(
                                    "RUT verificado: ${sol.docenteRut}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(etiquetaSolicitud(sol.aprobada, sol.rechazada))
                            }
                            if (rol == RolRed.INSPECTOR && !sol.rechazada) IconButton(enabled = salas.isNotEmpty(), onClick = {
                                solicitudEditada = sol
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar solicitud")
                            }
                            if (!sol.rechazada && (rol == RolRed.INSPECTOR ||
                                (rol == RolRed.DOCENTE && sol.docenteRut == currentUserRut))
                            ) IconButton(onClick = {
                                vm.eliminarSolicitud(sol)
                                Toast.makeText(context, "Solicitud cancelada", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Cancelar solicitud",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (rol == RolRed.INSPECTOR && !sol.aprobada && !sol.rechazada) {
                            BotonesDecisionReserva(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) { aprobar ->
                                vm.resolverSolicitud(sol.id, aprobar) { error ->
                                    val mensaje = if (aprobar) "Reserva aprobada" else "Reserva rechazada"
                                    Toast.makeText(context, error ?: mensaje, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val editada = solicitudEditada
    if (salas.isNotEmpty() && ((rol == RolRed.DOCENTE && showRequestDialog) ||
        (rol == RolRed.INSPECTOR && editada != null))
    ) {
        SolicitarSalaDialog(
            vm = vm,
            salas = salas,
            solicitudes = solicitudes,
            defaultRut = Validators.cleanRut(editada?.docenteRut ?: currentUserRut),
            solicitud = editada,
            onDismiss = { showRequestDialog = false; solicitudEditada = null },
            onSuccess = {
                val mensaje = if (editada == null) "Solicitud enviada para aprobación" else "Solicitud guardada"
                Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
                showRequestDialog = false
                solicitudEditada = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// Selecciona sala y horas y verifica el RUT antes de registrar una solicitud local.
@Composable
private fun SolicitarSalaDialog(
    vm: AppViewModel,
    salas: List<Sala>,
    solicitudes: List<SolicitudSala>,
    defaultRut: String,
    solicitud: SolicitudSala?,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var salaElegidaId by rememberSaveable { mutableStateOf(solicitud?.salaId) }
    val ahora by observarHoraReservas()
    var fechaTexto by rememberSaveable { mutableStateOf(solicitud?.fecha ?: ahora.toLocalDate().toString()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var horaInicio by rememberSaveable { mutableStateOf(solicitud?.horaInicio ?: "08:30") }
    var horaFin by rememberSaveable { mutableStateOf(solicitud?.horaFin ?: "10:00") }

    var rut by rememberSaveable { mutableStateOf(defaultRut) }
    var docenteVerificado by remember { mutableStateOf<User?>(null) }
    var rutMensajeError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    val errorHorario = errorHorarioReserva(horaInicio, horaFin)
    val salasDisponibles = if (errorHorario == null) {
        salas.filter { sala ->
            solicitudes.none {
                it.aprobada && it.fecha == fechaTexto && it.id != solicitud?.id && it.salaId == sala.id &&
                    horaInicio < it.horaFin && horaFin > it.horaInicio
            }
        }
    } else emptyList()
    val selectedSala = if (salaElegidaId == null) salasDisponibles.firstOrNull()
        else salasDisponibles.firstOrNull { it.id == salaElegidaId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (solicitud == null) "Solicitar sala" else "Editar solicitud") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(ahora.etiquetaHoraEscuela(), style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = {
                    val fecha = LocalDate.parse(fechaTexto)
                    DatePickerDialog(context, { _, anio, mes, dia ->
                        fechaTexto = LocalDate.of(anio, mes + 1, dia).toString()
                        formError = null
                    }, fecha.year, fecha.monthValue - 1, fecha.dayOfMonth).show()
                }) { Text("Fecha · $fechaTexto") }
                // 1. Selector de Sala
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedSala?.let {
                            if (it.capacidad > 0) "${it.nombre} (Cap: ${it.capacidad})" else it.nombre
                        } ?: when {
                            errorHorario != null -> "Revisa el horario"
                            salasDisponibles.isEmpty() -> "Sin salas disponibles"
                            else -> "Elige una sala disponible"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sala") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(enabled = salasDisponibles.isNotEmpty()) { dropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        salasDisponibles.forEach { sala ->
                            DropdownMenuItem(
                                text = { Text("${sala.nombre} (${sala.ubicacion})") },
                                onClick = {
                                    salaElegidaId = sala.id
                                    dropdownExpanded = false
                                    formError = null
                                }
                            )
                        }
                    }
                }

                CamposHoraReserva(
                    horaInicio, horaFin,
                    { horaInicio = it; formError = null },
                    { horaFin = it; formError = null }
                )
                if (errorHorario != null) {
                    Text(errorHorario, color = MaterialTheme.colorScheme.error)
                } else if (salaElegidaId != null && selectedSala == null) {
                    Text("La sala elegida no está disponible para ese bloque.", color = MaterialTheme.colorScheme.error)
                }

                // 3. RUT del docente y botón de verificación
                if (solicitud == null) OutlinedTextField(
                    value = rut,
                    onValueChange = { v ->
                        val c = Validators.cleanRut(v)
                        if (c.length <= 9) {
                            rut = c
                            docenteVerificado = null
                            rutMensajeError = null
                            formError = null
                        }
                    },
                    readOnly = true,
                    label = { Text("RUT del docente solicitante") },
                    placeholder = { Text("12.345.678-5") },
                    singleLine = true,
                    visualTransformation = RutTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    isError = rutMensajeError != null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (solicitud == null) FilledTonalButton(
                    onClick = {
                        vm.verificarDocentePorRut(rut) { user, err ->
                            docenteVerificado = user
                            rutMensajeError = err
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VerifiedUser, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Verificar RUT docente")
                }

                docenteVerificado?.let { doc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Docente verificado: ${doc.name}",
                            color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                rutMensajeError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                formError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = selectedSala != null, onClick = {
                vm.crearSolicitud(
                    sala = checkNotNull(selectedSala),
                    rutInput = rut,
                    fecha = LocalDate.parse(fechaTexto),
                    horaInicio = horaInicio,
                    horaFin = horaFin,
                    solicitud = solicitud
                ) { err ->
                    if (err == null) {
                        onSuccess()
                    } else {
                        formError = err
                    }
                }
            }) {
                Text(if (solicitud == null) "Enviar solicitud" else "Guardar cambios")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )

}
