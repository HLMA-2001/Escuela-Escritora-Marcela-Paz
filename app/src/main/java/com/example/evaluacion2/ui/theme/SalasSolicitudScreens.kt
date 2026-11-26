package com.example.evaluacion2

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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

// Muestra las salas de Room y permite abrir el formulario de creación local.
@Composable
fun SalasScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val rol by vm.rolRed.collectAsStateWithLifecycle()
    val salas by vm.salas.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (rol == RolRed.INSPECTOR) Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Crear sala")
        }

        Spacer(Modifier.height(16.dp))

        if (salas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No hay salas registradas.",
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
                items(salas, key = { it.id }) { sala ->
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
                                    "Capacidad: ${sala.capacidad} personas",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Ubicación: ${sala.ubicacion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
        CrearSalaDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { nombre, capacidad, ubicacion, onError ->
                vm.crearSala(nombre, capacidad, ubicacion) { err ->
                    if (err == null) {
                        Toast.makeText(context, "Sala creada correctamente", Toast.LENGTH_SHORT).show()
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
    onDismiss: () -> Unit,
    onConfirm: (nombre: String, capacidad: Int, ubicacion: String, onError: (String) -> Unit) -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf("") }
    var capacidadText by rememberSaveable { mutableStateOf("") }
    var ubicacion by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva sala") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Button(onClick = {
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
                items(solicitudes, key = { it.id }) { sol ->
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
                                        "Horario: ${sol.horaInicio} - ${sol.horaFin}",
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
                            }
                            if (rol == RolRed.DOCENTE && sol.docenteRut == currentUserRut) IconButton(onClick = {
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
                    }
                }
            }
        }
    }

    if (rol == RolRed.DOCENTE && showRequestDialog) {
        SolicitarSalaDialog(
            vm = vm,
            salas = salas,
            defaultRut = Validators.cleanRut(currentUserRut),
            onDismiss = { showRequestDialog = false },
            onSuccess = {
                Toast.makeText(context, "Solicitud registrada con éxito", Toast.LENGTH_SHORT).show()
                showRequestDialog = false
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
    defaultRut: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var selectedSala by remember { mutableStateOf(salas.first()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var horaInicio by rememberSaveable { mutableStateOf("08:30") }
    var horaFin by rememberSaveable { mutableStateOf("10:00") }
    var pickingInicio by remember { mutableStateOf(false) }
    var pickingFin by remember { mutableStateOf(false) }

    var rut by rememberSaveable { mutableStateOf(defaultRut) }
    var docenteVerificado by remember { mutableStateOf<User?>(null) }
    var rutMensajeError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitar sala") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. Selector de Sala
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = "${selectedSala.nombre} (Cap: ${selectedSala.capacidad})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sala") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable { dropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        salas.forEach { sala ->
                            DropdownMenuItem(
                                text = { Text("${sala.nombre} (${sala.ubicacion})") },
                                onClick = {
                                    selectedSala = sala
                                    dropdownExpanded = false
                                    formError = null
                                }
                            )
                        }
                    }
                }

                // 2. Hora de inicio y Hora final
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { pickingInicio = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Inicio: $horaInicio")
                    }
                    OutlinedButton(
                        onClick = { pickingFin = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Fin: $horaFin")
                    }
                }

                // 3. RUT del docente y botón de verificación
                OutlinedTextField(
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

                FilledTonalButton(
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
            Button(onClick = {
                vm.crearSolicitud(
                    sala = selectedSala,
                    rutInput = rut,
                    horaInicio = horaInicio,
                    horaFin = horaFin
                ) { err ->
                    if (err == null) {
                        onSuccess()
                    } else {
                        formError = err
                    }
                }
            }) {
                Text("Confirmar solicitud")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )

    if (pickingInicio) {
        TimeSelectorDialog(
            initialTime = horaInicio,
            title = "Hora de inicio",
            onDismiss = { pickingInicio = false },
            onConfirm = { selected ->
                horaInicio = selected
                pickingInicio = false
                formError = null
            }
        )
    }

    if (pickingFin) {
        TimeSelectorDialog(
            initialTime = horaFin,
            title = "Hora final",
            onDismiss = { pickingFin = false },
            onConfirm = { selected ->
                horaFin = selected
                pickingFin = false
                formError = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// Devuelve una hora elegida en el formato que usa la base local.
@Composable
private fun TimeSelectorDialog(
    initialTime: String,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val parts = initialTime.split(":")
    val initH = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val initM = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val state = rememberTimePickerState(
        initialHour = initH,
        initialMinute = initM,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val formatted = "%02d:%02d".format(state.hour, state.minute)
                onConfirm(formatted)
            }) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
