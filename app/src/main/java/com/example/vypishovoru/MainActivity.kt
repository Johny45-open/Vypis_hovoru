package com.example.vypishovoru

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import androidx.core.app.ActivityCompat
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
    private var isContactsPermanentlyDenied by mutableStateOf(false)
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
        // null v mapě znamená "nežádáno v tomto kole" (už uděleno) → ověř realitu.
        val contactsRequested = grants.containsKey(Manifest.permission.READ_CONTACTS)
        val contactsNow = grants[Manifest.permission.READ_CONTACTS] ?: hasContactsPermission()
        if (contactsRequested && !contactsNow) {
            markContactsAsked()
        }
        isCallLogGranted = callLogNow
        isContactsGranted = contactsNow
        updateContactsDenialState()
        if (callLogNow) {
            // I při odmítnutém READ_CONTACTS pokračuj: ViewModel bezpečně vrátí organization=null.
            viewModel.fetchCallLogs()
        }
    }

    private val requestContactsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (!granted) {
            markContactsAsked()
        }
        isContactsGranted = granted || hasContactsPermission()
        updateContactsDenialState()
        if (isContactsGranted && isCallLogGranted) {
            viewModel.fetchCallLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeMode = loadThemeMode()

        isCallLogGranted = hasCallLogPermission()
        isContactsGranted = hasContactsPermission()
        updateContactsDenialState()
        if (isCallLogGranted) {
            // READ_CALL_LOG už je: načti hovory okamžitě a READ_CONTACTS nech na banneru.
            // Nikdy automaticky nežádej kontaktové oprávnění při startu — uživatel má volbu.
            viewModel.fetchCallLogs()
        } else {
            // Chybí READ_CALL_LOG: požádej pouze o chybějící (obě jen při prvním startu).
            requestPermissionsLauncher.launch(missingPermissions())
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
                            onExportClick = { exportCsvLauncher.launch("hovory.csv") },
                            isContactsGranted = isContactsGranted,
                            isContactsPermanentlyDenied = isContactsPermanentlyDenied,
                            onRequestContacts = ::onRequestContacts,
                            onOpenSettings = ::openAppSettings
                        )
                    } else {
                        PermissionDeniedScreen {
                            requestPermissionsLauncher.launch(missingPermissions())
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
        updateContactsDenialState()
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
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            viewModel.fetchCallLogs()
        } else {
            requestPermissionsLauncher.launch(missing)
        }
    }

    /**
     * READ_CALL_LOG a READ_CONTACTS jako dvě nezávislá oprávnění.
     * Nikdy nežádej o již udělené: chybí-li jen jedno, žádá se pouze ono.
     */
    private fun missingPermissions(): Array<String> {
        val missing = mutableListOf<String>()
        if (!hasCallLogPermission()) missing.add(Manifest.permission.READ_CALL_LOG)
        if (!hasContactsPermission()) missing.add(Manifest.permission.READ_CONTACTS)
        return missing.toTypedArray()
    }

    private fun onRequestContacts() {
        if (hasContactsPermission()) {
            isContactsGranted = true
            updateContactsDenialState()
            if (isCallLogGranted) viewModel.fetchCallLogs()
            return
        }
        if (isContactsPermanentlyDenied) {
            openAppSettings()
        } else {
            requestContactsLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            // Nastavení nelze otevřít — ignoruj, banner zůstane k dispozici.
        }
    }

    /**
     * Samotné shouldShowRequestPermissionRationale() == false NENÍ permanent denial
     * (na první žádost vrací false také). Proto sledujeme, zda už byla žádost
     * o READ_CONTACTS dříve skutečně zobrazena (SharedPreferences).
     * Permanent = dříve žádáno + systém už nedoporučuje běžný dialog.
     */
    private fun updateContactsDenialState() {
        if (hasContactsPermission()) {
            isContactsPermanentlyDenied = false
            return
        }
        val askedBefore = getPreferences(MODE_PRIVATE)
            .getBoolean(KEY_CONTACTS_ASKED, false)
        val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.READ_CONTACTS
        )
        isContactsPermanentlyDenied = askedBefore && !showRationale
    }

    private fun markContactsAsked() {
        getPreferences(MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CONTACTS_ASKED, true)
            .apply()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_CONTACTS_ASKED = "contacts_asked_before"
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
