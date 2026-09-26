package com.example.vypishovoru

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
    // READ_CALL_LOG je povinné pro výpis; READ_CONTACTS je volitelné obohacení (firma).
    // Odmítnutí READ_CONTACTS nikdy neblokuje hlavní funkci.
    private var isCallLogGranted by mutableStateOf(false)
    private var isContactsGranted by mutableStateOf(false)
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

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants: Map<String, Boolean> ->
        val callLogNow = grants[Manifest.permission.READ_CALL_LOG] ?: hasCallLogPermission()
        val contactsNow = grants[Manifest.permission.READ_CONTACTS] ?: hasContactsPermission()
        isCallLogGranted = callLogNow
        isContactsGranted = contactsNow
        if (callLogNow) {
            // I při odmítnutém READ_CONTACTS pokračuj: ViewModel bezpečně vrátí organization=null.
            viewModel.fetchCallLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeMode = loadThemeMode()
        
        isCallLogGranted = hasCallLogPermission()
        isContactsGranted = hasContactsPermission()
        if (isCallLogGranted) {
            viewModel.fetchCallLogs()
        } else {
            requestPermissionsLauncher.launch(
                arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
            )
        }

        setContent {
            VypisHovoruTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isCallLogGranted) {
                        CallLogScreen(
                            viewModel = viewModel,
                            themeMode = themeMode,
                            onThemeModeSelected = ::saveThemeMode,
                            onExportClick = { exportCsvLauncher.launch("hovory.csv") }
                        )
                    } else {
                        PermissionDeniedScreen {
                            requestPermissionsLauncher.launch(
                                arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check po návratu ze systémového nastavení (odebrání/udělení).
        // Nikdy zde automaticky nespouštíme request — jen synchronizujeme stav a případně reload.
        // Tím je vyloučen nekonečný cyklus žádostí při každém onResume.
        val callLogNow = hasCallLogPermission()
        val contactsNow = hasContactsPermission()
        val changed = (callLogNow != isCallLogGranted) || (contactsNow != isContactsGranted)
        isCallLogGranted = callLogNow
        isContactsGranted = contactsNow
        if (changed && callLogNow) {
            viewModel.fetchCallLogs()
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

    private fun hasContactsPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    private fun checkAndRequestPermission() {
        when {
            hasCallLogPermission() -> {
                viewModel.fetchCallLogs()
            }
            else -> {
                requestPermissionsLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
                )
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
