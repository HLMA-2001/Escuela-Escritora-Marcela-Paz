package com.example.evaluacion2

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay


@Composable
fun LoginScreen(vm: AppViewModel, onSuccess: () -> Unit, onRegister: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val emailErr = if (submitted) Validators.email(email) else null
    val passErr = if (submitted && pass.isBlank()) "Ingresa tu contraseña" else null

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Iniciar sesión", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Correo") }, singleLine = true,
            isError = emailErr != null, supportingText = { emailErr?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(
            value = pass,
            onValueChange = { pass = it },
            label = "Contraseña",
            error = passErr
        )

        AnimatedVisibility(error != null) {
            Text(
                error ?: "", color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                submitted = true
                if (Validators.email(email) == null && pass.isNotBlank()) {
                    vm.login(email, pass) { err -> if (err == null) onSuccess() else error = err }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Entrar") }
        TextButton(onClick = onRegister) { Text("¿No tienes cuenta? Regístrate") }
    }
}


data class PickedDoc(val uri: Uri, val name: String, val size: Long)

private val allowedExtensions = listOf("pdf", "doc", "docx")
private const val MAX_DOCS = 5
private const val MAX_SIZE_BYTES = 10L * 1024 * 1024

private fun readDoc(context: Context, uri: Uri): PickedDoc {
    var name = "documento"
    var size = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val s = c.getColumnIndex(OpenableColumns.SIZE)
            if (n >= 0) name = c.getString(n)
            if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
        }
    }
    return PickedDoc(uri, name, size)
}

@Composable
fun RegisterScreen(vm: AppViewModel, onDone: () -> Unit) {
    val context = LocalContext.current

    var name by rememberSaveable { mutableStateOf("") }
    var rut by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val docs = remember { mutableStateListOf<PickedDoc>() }

    val docPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            val doc = readDoc(context, uri)
            val ext = doc.name.substringAfterLast('.', "").lowercase()
            if (ext !in allowedExtensions) {
                Toast.makeText(context, "Solo se aceptan archivos PDF o Word", Toast.LENGTH_SHORT).show()
                return@forEach
            }
            if (doc.size > MAX_SIZE_BYTES) {
                Toast.makeText(context, "\"${doc.name}\" supera los 10 MB", Toast.LENGTH_SHORT).show()
                return@forEach
            }
            if (docs.size >= MAX_DOCS) {
                Toast.makeText(context, "Máximo $MAX_DOCS archivos", Toast.LENGTH_SHORT).show()
                return@forEach
            }
            if (docs.none { it.uri == uri }) docs.add(doc)
        }
    }

    val nameErr = if (submitted) Validators.name(name) else null
    val rutErr = if (submitted) Validators.rut(rut) else null
    val emailErr = if (submitted) Validators.email(email) else null
    val passErr = if (submitted) Validators.password(pass) else null
    val confErr = if (submitted) Validators.confirm(pass, confirm) else null
    val docErr = if (submitted && docs.isEmpty())
        "Sube al menos un documento que acredite que eres docente" else null

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Crear cuenta", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Nombre") }, singleLine = true,
            isError = nameErr != null, supportingText = { nameErr?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = rut,
            onValueChange = { v ->
                val c = Validators.cleanRut(v)
                if (c.length <= 9) rut = c
            },
            label = { Text("RUT") }, singleLine = true,
            placeholder = { Text("12.345.678-5") },
            visualTransformation = RutTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            isError = rutErr != null, supportingText = { rutErr?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Correo") }, singleLine = true,
            isError = emailErr != null, supportingText = { emailErr?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        PasswordField(
            value = pass,
            onValueChange = { pass = it },
            label = "Contraseña",
            error = passErr
        )
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Repetir contraseña",
            error = confErr
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "Documentos que acrediten que eres docente",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            "PDF o Word, hasta $MAX_DOCS archivos de 10 MB (título, credencial, contrato...)",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                docPicker.launch(
                    arrayOf(
                        "application/pdf",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.UploadFile, null)
            Spacer(Modifier.width(8.dp))
            Text("Subir archivos")
        }

        docs.forEach { doc ->
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (doc.name.lowercase().endsWith(".pdf")) Icons.Default.PictureAsPdf
                    else Icons.Default.Description,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("${doc.size / 1024} KB", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { docs.remove(doc) }) {
                    Icon(Icons.Default.Delete, "Quitar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (docErr != null) {
            Text(
                docErr, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        AnimatedVisibility(error != null) {
            Text(
                error ?: "", color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                submitted = true
                val ok = listOf(
                    Validators.name(name), Validators.rut(rut), Validators.email(email),
                    Validators.password(pass), Validators.confirm(pass, confirm)
                ).all { it == null } && docs.isNotEmpty()
                if (ok) {
                    vm.register(name, rut, email, pass, docs.toList()) { err ->
                        if (err == null) onDone() else error = err
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Registrarme") }
        TextButton(onClick = onDone) { Text("Ya tengo cuenta") }
    }
}

@Composable
fun WelcomeScreen(vm: AppViewModel, onFinish: () -> Unit) {
    val name by vm.userName.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(2500)
        onFinish()
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible, enter = fadeIn() + scaleIn()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("¡Bienvenido!", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None
        else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña"
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

class RutTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = Validators.formatRut(text.text)

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                var count = 0
                for (i in formatted.indices) {
                    if (formatted[i].isDigit() || formatted[i] == 'K') {
                        count++
                        if (count == offset) return i + 1
                    }
                }
                return formatted.length
            }

            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset).count { it.isDigit() || it == 'K' }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}