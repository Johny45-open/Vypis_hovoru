package com.example.vypishovoru

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.vypishovoru.ui.AppThemeMode
import com.example.vypishovoru.ui.CallLogScreen
import com.example.vypishovoru.ui.VypisHovoruTheme
import com.example.vypishovoru.viewmodel.CallLogViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CallLogViewModel by viewModels()
    private var isPermissionGranted by mutableStateOf(false)
    private var themeMode by mutableStateOf(AppThemeMode.SYSTEM)

    private val exportCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                val csvData = viewModel.generateCsvData()
                val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
                outputStream.write(bom)
                outputStream.write(csvData.toByteArray(Charsets.UTF_8))
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        isPermissionGranted = isGranted
        if (isGranted) {
            viewModel.fetchCallLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeMode = loadThemeMode()
        
        isPermissionGranted = hasCallLogPermission()
        if (isPermissionGranted) {
            viewModel.fetchCallLogs()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }

        setContent {
            VypisHovoruTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isPermissionGranted) {
                        CallLogScreen(
                            viewModel = viewModel,
                            themeMode = themeMode,
                            onThemeModeSelected = ::saveThemeMode,
                            onExportClick = { exportCsvLauncher.launch("hovory.csv") }
                        )
                    } else {
                        PermissionDeniedScreen {
                            requestPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                        }
                    }
                }
            }
        }
    }

    private fun loadThemeMode(): AppThemeMode {
        val savedValue = getPreferences(MODE_PRIVATE).getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return AppThemeMode.entries.firstOrNull { it.name == savedValue } ?: AppThemeMode.SYSTEM
    }

    private fun saveThemeMode(mode: AppThemeMode) {
        themeMode = mode
        getPreferences(MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }

    private fun hasCallLogPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.READ_CALL_LOG
    ) == PackageManager.PERMISSION_GRANTED

    private fun checkAndRequestPermission() {
        when {
            hasCallLogPermission() -> {
                viewModel.fetchCallLogs()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
        }
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}

@Composable
fun PermissionDeniedScreen(onRequestPermission: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Aplikace vyžaduje oprávnění k přístupu k historii hovorů.")
        androidx.compose.material3.Button(onClick = onRequestPermission) {
            Text("Udělit oprávnění")
        }
    }
}
