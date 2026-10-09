package com.example.vypishovoru.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vypishovoru.model.CallLogItem
import com.example.vypishovoru.model.buildCallLogCsv
import com.example.vypishovoru.model.pickFirstNonBlank
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
        // První řádek `sep=,` je direktiva pro desktopový Excel (viz CsvUtils),
        // BOM se přidává až při zápisu souboru v MainActivity.
        return buildCallLogCsv(_callLogs.value) { timestamp ->
            sdf.format(Date(timestamp))
        }
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

            var contactId: Long? = null
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.CONTACT_ID),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ContactsContract.PhoneLookup.CONTACT_ID)
                    if (index != -1) contactId = cursor.getLong(index)
                }
            }

            val id = contactId ?: return null

            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Organization.COMPANY),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(id.toString(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
                // Deterministické pořadí při více Organization řádcích: první abecedně.
                "${ContactsContract.CommonDataKinds.Organization.COMPANY} COLLATE NOCASE ASC"
            )?.use { orgCursor ->
                val index = orgCursor.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY)
                if (index != -1) {
                    val companies = mutableListOf<String?>()
                    while (orgCursor.moveToNext()) {
                        companies.add(orgCursor.getString(index))
                    }
                    // Prázdný/whitespace COMPANY se nesmí vrátit jako "".
                    return pickFirstNonBlank(companies)
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
