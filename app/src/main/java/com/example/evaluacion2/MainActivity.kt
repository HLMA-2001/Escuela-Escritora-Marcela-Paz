package com.example.evaluacion2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.evaluacion2.ui.theme.Evaluacion2Theme

// Punto de entrada de Android. Crea la interfaz Compose con el ViewModel compartido.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Evaluacion2Theme {
                Surface(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize().safeDrawingPadding().imePadding()
                    ) {
                        val vm: AppViewModel = viewModel()
                        App(vm)
                    }
                }
            }
        }
    }
}
