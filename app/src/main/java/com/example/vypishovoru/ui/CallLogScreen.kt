package com.example.vypishovoru.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.vypishovoru.model.CallLogItem
import com.example.vypishovoru.model.buildCallLogContentDescription
import com.example.vypishovoru.model.displayName
import com.example.vypishovoru.model.organizationSubtitle
import com.example.vypishovoru.viewmodel.CallLogViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(
    viewModel: CallLogViewModel,
    themeMode: AppThemeMode,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onExportClick: () -> Unit,
    isContactsGranted: Boolean = true,
    isContactsPermanentlyDenied: Boolean = false,
    onRequestContacts: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val callLogs by viewModel.callLogs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSortedDesc by viewModel.isSortedDesc.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historie hovorů") },
                actions = {
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier.semantics { role = Role.Button }
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Exportovat do CSV") },
                            modifier = Modifier.semantics { role = Role.Button },
                            onClick = {
                                showMenu = false
                                onExportClick()
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text(if (isSortedDesc) "Seřadit: Od nejstarších" else "Seřadit: Od nejnovějších") },
                            modifier = Modifier.semantics { role = Role.Button },
                            onClick = {
                                showMenu = false
                                viewModel.toggleSortOrder()
                                
                                val accessibilityManager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
                                val announcement = if (!isSortedDesc) "Seřazeno od nejnovějších" else "Seřazeno od nejstarších"
                                val event = android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT)
                                event.text.add(announcement)
                                accessibilityManager?.sendAccessibilityEvent(event)
                            }
                        )
                        Divider()
                        Text(
                            text = "Motiv",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                modifier = Modifier.semantics { role = Role.RadioButton },
                                leadingIcon = {
                                    RadioButton(
                                        selected = themeMode == mode,
                                        onClick = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onThemeModeSelected(mode)
                                }
                            )
                        }
                        Divider()
                        DropdownMenuItem(
                            text = { Text("O aplikaci") },
                            modifier = Modifier.semantics { role = Role.Button },
                            onClick = {
                                showMenu = false
                                showAboutDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showAboutDialog) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .semantics { 
                        liveRegion = LiveRegionMode.Assertive
                    },
                title = { Text("O aplikaci", modifier = Modifier.semantics { heading() }) },
                text = {
                    Text("Tato aplikace je optimalizována pro přístupnost a plně kompatibilní se čtečkou obrazovky TalkBack. V nabídce (tři tečky vpravo nahoře) můžete nyní hovory řadit podle data od nejnovějších nebo nejstarších. Nastavení usnadnění najdete v Nastavení telefonu.")
                },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.semantics { role = Role.Button },
                        onClick = { showAboutDialog = false }
                    ) {
                        Text("Zavřít")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!isContactsGranted) {
                ContactsPermissionBanner(
                    isPermanentlyDenied = isContactsPermanentlyDenied,
                    onRequestContacts = onRequestContacts,
                    onOpenSettings = onOpenSettings
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (callLogs.isEmpty()) {
                    Text(
                        text = "Žádné hovory nebyly nalezeny.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                collectionInfo = CollectionInfo(
                                    rowCount = callLogs.size,
                                    columnCount = 1
                                )
                            },
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(callLogs) { index, log ->
                            CallLogListItem(
                                log = log,
                                index = index
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactsPermissionBanner(
    isPermanentlyDenied: Boolean,
    onRequestContacts: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "Informační panel: Pro zobrazení firem povolte kontakty"
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Pro zobrazení firem povolte kontakty",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            if (isPermanentlyDenied) {
                TextButton(
                    modifier = Modifier.semantics {
                        contentDescription = "Otevřít nastavení pro povolení kontaktů"
                        role = Role.Button
                    },
                    onClick = onOpenSettings
                ) {
                    Text("Otevřít nastavení")
                }
            } else {
                TextButton(
                    modifier = Modifier.semantics {
                        contentDescription = "Povolit přístup ke kontaktům pro zobrazení firem"
                        role = Role.Button
                    },
                    onClick = onRequestContacts
                ) {
                    Text("Povolit")
                }
            }
        }
    }
}

@Composable
fun CallLogListItem(
    log: CallLogItem,
    index: Int
) {
    val formattedDate = remember(log.date) {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(log.date))
    }
    val accessiblePhoneNumber = remember(log.number) {
        formatPhoneNumberForAccessibility(log.number)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = buildCallLogContentDescription(
                    log = log,
                    accessiblePhoneNumber = accessiblePhoneNumber,
                    formattedDate = formattedDate
                )
                collectionItemInfo = CollectionItemInfo(
                    rowIndex = index,
                    rowSpan = 1,
                    columnIndex = 0,
                    columnSpan = 1
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            Text(
                text = log.displayName,
                style = MaterialTheme.typography.titleMedium
            )
            log.organizationSubtitle?.let { subtitle ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Číslo: ${log.number}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Datum: $formattedDate",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Délka hovoru: ${log.accessibleDuration}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatPhoneNumberForAccessibility(number: String): String {
    if (number == "Neznámé číslo") {
        return number
    }

    return number
        .mapNotNull { character ->
            when {
                character == '+' -> "plus"
                character.isDigit() -> character.toString()
                character.isWhitespace() || character == '-' || character == '.' -> null
                else -> character.toString()
            }
        }
        .joinToString(" ")
}
