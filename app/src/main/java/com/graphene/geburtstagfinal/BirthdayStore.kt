package com.graphene.geburtstagfinal

import androidx.datastore.preferences.core.intPreferencesKey

import androidx.datastore.preferences.core.booleanPreferencesKey


import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

val Context.dataStore by preferencesDataStore(name = "birthday_store")

object BirthdayStore {
    private val KEY_NOTIFY_HOUR = intPreferencesKey("notify_hour")
    private val KEY_NOTIFY_MIN = intPreferencesKey("notify_min")

    private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")

    private val KEY_NOTIFY_DAILY = booleanPreferencesKey("notify_daily")
    private val KEY_NOTIFY_7DAYS = booleanPreferencesKey("notify_7days")
    private val KEY_NOTIFY_MONTH_START = booleanPreferencesKey("notify_month_start")

    suspend fun loadNotifyTime(context: Context): Pair<Int, Int> {
        val prefs = context.dataStore.data.first()
        val hour = prefs[KEY_NOTIFY_HOUR] ?: 8
        val min = prefs[KEY_NOTIFY_MIN] ?: 0
        return hour to min
    }

    suspend fun saveNotifyTime(context: Context, hour: Int, min: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFY_HOUR] = hour
            prefs[KEY_NOTIFY_MIN] = min
        }
    }

    suspend fun loadNotifyDaily(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_NOTIFY_DAILY] ?: false
    }

    suspend fun saveNotifyDaily(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFY_DAILY] = enabled
        }
    }

    suspend fun loadNotify7Days(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_NOTIFY_7DAYS] ?: false
    }

    suspend fun saveNotify7Days(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFY_7DAYS] = enabled
        }
    }

    suspend fun loadNotifyMonthStart(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_NOTIFY_MONTH_START] ?: false
    }

    suspend fun saveNotifyMonthStart(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFY_MONTH_START] = enabled
        }
    }
    private val KEY_BIRTHDAYS_JSON = stringPreferencesKey("birthdays_json")

    // Sehr simples JSON (ohne extra Lib): wir bauen/lesen es selbst.
    // Format pro Zeile: id|name|yyyy-MM-dd
    // Das ist noch simpler als "echtes JSON" und reicht völlig für deine App.

    suspend fun load(context: Context): List<BirthdayEntry> {
        val raw = context.dataStore.data.first()[KEY_BIRTHDAYS_JSON].orEmpty()
        if (raw.isBlank()) return emptyList()

        return raw.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size != 3) return@mapNotNull null
                val id = parts[0].toLongOrNull() ?: return@mapNotNull null
                val name = parts[1]
                val date = runCatching { LocalDate.parse(parts[2]) }.getOrNull() ?: return@mapNotNull null
                BirthdayEntry(id = id, fullName = name, birthDate = date)
            }
    }

    suspend fun save(context: Context, entries: List<BirthdayEntry>) {
        val raw = entries.joinToString("\n") { e ->
            "${e.id}|${e.fullName}|${e.birthDate}"
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_BIRTHDAYS_JSON] = raw
        }
    }
    suspend fun loadDarkMode(context: Context): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_DARK_MODE] ?: false
    }
    suspend fun saveDarkMode(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = enabled
        }
    }

}
