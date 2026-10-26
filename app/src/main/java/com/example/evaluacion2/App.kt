package com.example.evaluacion2

import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun App(vm: AppViewModel) {
    val start by vm.startRoute.collectAsStateWithLifecycle()
    val startRoute = start

    if (startRoute == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        AppNavigation(vm, startRoute)
    }
}

@Composable
private fun AppNavigation(vm: AppViewModel, startRoute: String) {
    val nav = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = nav,
        startDestination = startRoute,
        enterTransition = { fadeIn() + slideInHorizontally { it / 4 } },
        exitTransition = { fadeOut() }
    ) {
        composable("login") {
            LoginScreen(
                vm,
                onSuccess = {
                    Toast.makeText(context, "Sesión iniciada correctamente", Toast.LENGTH_SHORT).show()
                    nav.navigate("welcome") { popUpTo(0) }
                },
                onRegister = { nav.navigate("register") }
            )
        }
        composable("register") {
            RegisterScreen(vm, onDone = {
                nav.navigate("login") { popUpTo(0) }
            })
        }
        composable("welcome") {
            WelcomeScreen(vm, onFinish = {
                nav.navigate("home") { popUpTo(0) }
            })
        }
        composable("home") {
            MainScreen(vm, onLogout = {
                vm.logout()
                Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                nav.navigate("login") { popUpTo(0) }
            })
        }
    }
}