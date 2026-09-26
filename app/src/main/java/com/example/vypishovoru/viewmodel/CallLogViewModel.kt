package com.example.vypishovoru.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vypishovoru.model.CallLogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogViewModel(application: Application) : AndroidViewModel(application) {

    private val _callLogs = MutableStateFlow<List<CallLogItem>>(emptyList())
    val callLogs: StateFlow<List<CallLogItem>> = _callLogs

    private val _isSortedDesc = MutableStateFlow(true)
    val isSortedDesc: StateFlow<Boolean> = _isSortedDesc

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun fetchCallLogs() {
        viewModelScope.launch {
            _isLoading.value = true
            val logs = withContext(Dispatchers.IO) {
                loadCallLogsFromSystem()
            }
            _callLogs.value = if (_isSortedDesc.value) logs else logs.reversed()
            _isLoading.value = false
        }
    }

    fun generateCsvData(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val csvHeader = "Jméno,Organizace,Číslo,Trvání (s),Datum\n"
        val csvRows = _callLogs.value.joinToString("\n") {
            val name = it.name?.replace(",", " ") ?: ""
            val org = it.organization?.replace(",", " ") ?: ""
            // Apostrof před číslem zajistí, že Excel bude hodnotu brát jako text a ne jako vzorec
            val formattedNumber = "'${it.number}"
            val formattedDate = sdf.format(Date(it.date))
            "${name},${org},\"${formattedNumber}\",${it.duration},${formattedDate}"
        }
        return csvHeader + csvRows
    }

    fun toggleSortOrder() {
        _isSortedDesc.value = !_isSortedDesc.value
        _callLogs.value = _callLogs.value.reversed()
    }

    private fun loadCallLogsFromSystem(): List<CallLogItem> {
        val callList = mutableListOf<CallLogItem>()
        val context = getApplication<Application>().applicationContext
        // Jednoduchá cache: stejné číslo -> jeden organization lookup.
        // null je validní výsledek ("nenalezeno"), proto se testuje containsKey, ne == null.
        val orgCache = mutableMapOf<String, String?>()
        
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)

                while (it.moveToNext()) {
                    val name = if (nameIndex != -1) it.getString(nameIndex) else null
                    val number = if (numberIndex != -1) it.getString(numberIndex) ?: "Neznámé číslo" else "Neznámé číslo"
                    val durationSeconds = if (durationIndex != -1) it.getLong(durationIndex) else 0L
                    val date = if (dateIndex != -1) it.getLong(dateIndex) else 0L

                    val organization = if (orgCache.containsKey(number)) {
                        orgCache[number]
                    } else {
                        val org = getOrganizationByNumber(number)
                        orgCache[number] = org
                        org
                    }

                    callList.add(
                        CallLogItem(
                            name = name,
                            organization = organization,
                            number = number,
                            duration = durationSeconds,
                            date = date,
                            accessibleDuration = formatDurationForAccessibility(durationSeconds)
                        )
                    )
                }
            }
            callList
        } catch (e: Exception) {
            android.util.Log.e("CallLogViewModel", "Chyba při načítání hovorů: ${e.message}")
            emptyList()
        }
    }

    private fun getOrganizationByNumber(phoneNumber: String): String? {
        // Neznámé/blank číslo nemá smysl posílat do Contacts Provideru.
        if (phoneNumber.isBlank() || phoneNumber == UNKNOWN_NUMBER_FALLBACK) return null
        return try {
            val context = getApplication<Application>().applicationContext
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            
            var lookupKey: String? = null
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.LOOKUP_KEY),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                    if (index != -1) lookupKey = cursor.getString(index)
                }
            }

            if (lookupKey == null) return null

            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Organization.COMPANY),
                "${ContactsContract.Data.LOOKUP_KEY} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(lookupKey, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
                // Deterministické pořadí při více Organization řádcích: první abecedně.
                "${ContactsContract.CommonDataKinds.Organization.COMPANY} COLLATE NOCASE ASC"
            )?.use { orgCursor ->
                val index = orgCursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY)
                if (index != -1) {
                    while (orgCursor.moveToNext()) {
                        // Prázdný/whitespace COMPANY se nesmí vrátit jako "".
                        val company = orgCursor.getString(index)?.takeIf { it.isNotBlank() }
                        if (company != null) return company
                    }
                }
            }
            null
        } catch (e: SecurityException) {
            // Chybějící/odmítnuté READ_CONTACTS: bezpečně pokračuj bez firmy,
            // nikdy neshazuj celý výpis hovorů.
            android.util.Log.e("CallLogViewModel", "Chybí READ_CONTACTS, organizace nedostupná: ${e.message}")
            null
        } catch (e: Exception) {
            android.util.Log.e("CallLogViewModel", "Chyba při získávání organizace: ${e.message}")
            null
        }
    }

    private fun formatDurationForAccessibility(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        
        return buildString {
            if (minutes > 0) {
                append("$minutes minut ")
            }
            append("$remainingSeconds sekund")
        }.trim()
    }

    companion object {
        const val UNKNOWN_NUMBER_FALLBACK = "Neznámé číslo"
    }
}
