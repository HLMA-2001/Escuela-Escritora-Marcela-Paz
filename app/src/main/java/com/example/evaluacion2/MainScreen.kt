package com.example.evaluacion2

import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private data class Tab(val route: String, val label: String, val icon: ImageVector)

// Define las pestañas de reservas en red, gestión local y perfil.
private val tabs = listOf(
    Tab("reservas", "Reservas", Icons.Default.Edit),
    Tab("salas", "Salas locales", Icons.Default.TableRestaurant),
    Tab("perfil", "Perfil", Icons.Default.Person)
)

@OptIn(ExperimentalMaterial3Api::class)
// Conecta la navegación inferior con las pantallas de la sesión.
@Composable
fun MainScreen(vm: AppViewModel, onLogout: () -> Unit) {
    val nav = rememberNavController()
    val rol by vm.rolRed.collectAsStateWithLifecycle()
    val catalogoLocal by vm.catalogoLocal.collectAsStateWithLifecycle()
    val rut by vm.userRut.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(catalogoLocal, rut) {
        if (!catalogoLocal || rol != RolRed.SIN_ROL) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 37 && RedApi.usaRedLocal &&
            ContextCompat.checkSelfPermission(context, permisoRedLocal) != PackageManager.PERMISSION_GRANTED
        ) return@LaunchedEffect
        val token = vm.tokenReservas() ?: return@LaunchedEffect
        val confirmado = try {
            vm.consultarRol(token)
        } catch (_: IOException) {
            return@LaunchedEffect
        } catch (_: IllegalArgumentException) {
            return@LaunchedEffect
        }
        if (token == vm.tokenReservas()) vm.actualizarRol(confirmado)
    }
    val pestañas = if (rol == RolRed.INSPECTOR) tabs + Tab("cuentas", "Cuentas", Icons.Default.Person) else tabs
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val title = pestañas.firstOrNull { it.route == route }?.label ?: ""

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(title) })
        },
        bottomBar = {
            BoxWithConstraints {
                val compacto = maxWidth < 400.dp
                NavigationBar {
                pestañas.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, tab.label) },
                        label = {
                            Text(if (compacto) when (tab.route) {
                                "salas" -> "Salas"
                                else -> tab.label
                            } else tab.label, maxLines = 1)
                        }
                    )
                }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "reservas",
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            composable("reservas") { ReservasScreen(vm) }
            composable("salas") { CatalogoSalasScreen(vm) }
            composable("perfil") { ProfileScreen(vm, onLogout) }
            composable("cuentas") {
                if (rol == RolRed.INSPECTOR) CuentasScreen(vm)
            }
        }
    }
}

// Lee la foto interna con un tamaño acotado para mostrarla en el perfil.
private fun loadBitmap(path: String): ImageBitmap? {
    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
    val bmp = BitmapFactory.decodeFile(path, opts) ?: return null

    val degrees = when (
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val result = if (degrees == 0f) bmp else Bitmap.createBitmap(
        bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true
    )
    return result.asImageBitmap()
}

// Muestra los datos de la cuenta y permite elegir una imagen con el selector de Android.
@Composable
private fun ProfileScreen(vm: AppViewModel, onLogout: () -> Unit) {
    val context = LocalContext.current
    val name by vm.userName.collectAsStateWithLifecycle()
    val rut by vm.userRut.collectAsStateWithLifecycle()
    val photoPath by vm.photoPath.collectAsStateWithLifecycle()
    val rol by vm.rolRed.collectAsStateWithLifecycle()
    var verCredencial by remember { mutableStateOf(false) }
    var estadoConsulta by remember(rut) { mutableStateOf<String?>(null) }
    LaunchedEffect(rut) {
        val token = vm.tokenReservas() ?: return@LaunchedEffect
        estadoConsulta = "Verificando rol…"
        try {
            val confirmado = vm.consultarRol(token)
            if (token == vm.tokenReservas()) vm.actualizarRol(confirmado)
            estadoConsulta = null
        } catch (_: IOException) {
            estadoConsulta = "No se pudo consultar el rol. Revisa la conexión en «Reservas»."
        } catch (_: IllegalArgumentException) {
            estadoConsulta = "El servicio devolvió un rol desconocido."
        }
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val bitmap by produceState<ImageBitmap?>(null, photoPath) {
        value = photoPath?.let { path ->
            withContext(Dispatchers.IO) { loadBitmap(path) }
        }
    }

    val preview by produceState<ImageBitmap?>(null, pendingUri) {
        value = pendingUri?.let { uri ->
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(
                        stream, null, BitmapFactory.Options().apply { inSampleSize = 4 }
                    )?.asImageBitmap()
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) pendingUri = uri }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it / 4 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val img = bitmap
                    if (img != null) {
                        Image(
                            bitmap = img,
                            contentDescription = "Foto de perfil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Person, null, Modifier.size(72.dp))
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera, null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Toca la foto para cambiarla", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(estadoConsulta ?: when (rol) {
                            RolRed.DOCENTE -> "Docente"
                            RolRed.INSPECTOR -> "Inspector"
                            RolRed.SIN_ROL -> "Sin rol asignado"
                        }, style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { verCredencial = true }) { Text("Ver credencial de rol") }
                        Spacer(Modifier.height(8.dp))
                        Text("RUT: $rut", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Filled.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Cerrar sesión")
        }
    }

    // La credencial solo se comparte con quien administra los roles en el servidor.
    if (verCredencial) vm.tokenReservas()?.let { token ->
        AlertDialog(
            onDismissRequest = { verCredencial = false },
            title = { Text("Credencial de rol") },
            text = {
                Column {
                    Text("Compártela solo con quien administra los permisos de la escuela.")
                    SelectionContainer { Text(token) }
                }
            },
            confirmButton = { TextButton(onClick = { verCredencial = false }) { Text("Cerrar") } }
        )
    }

    pendingUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text("¿Usar esta foto?") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val p = preview
                    if (p != null) {
                        Image(
                            bitmap = p,
                            contentDescription = "Vista previa",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(200.dp).clip(CircleShape)
                        )
                    } else {
                        Text("Cargando vista previa...")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setProfilePhoto(uri)
                    pendingUri = null
                    Toast.makeText(context, "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUri = null }) { Text("Cancelar") }
            }
        )
    }
}

