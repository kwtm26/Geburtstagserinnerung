package com.graphene.geburtstagfinal

import java.time.format.TextStyle
import java.util.Locale

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.first

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate

private val KEY_NOTIFY_TODAY = booleanPreferencesKey("notify_daily")
private val KEY_NOTIFY_WEEK  = booleanPreferencesKey("notify_7days")
private val KEY_NOTIFY_MONTH = booleanPreferencesKey("notify_month_start")
class BirthdayWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.dataStore.data.first()

        val notifyToday = prefs[KEY_NOTIFY_TODAY] ?: false
        val notifyWeek  = prefs[KEY_NOTIFY_WEEK] ?: false
        val notifyMonth = prefs[KEY_NOTIFY_MONTH] ?: false

        val entries = BirthdayStore.load(applicationContext)

        val today = LocalDate.now()

        // Hilfsfunktion: nächster Geburtstag (dieses oder nächstes Jahr)
        fun nextOccurrence(b: LocalDate, ref: LocalDate): LocalDate {
            val thisYear = LocalDate.of(ref.year, b.monthValue, b.dayOfMonth)
            return if (!thisYear.isBefore(ref)) thisYear else thisYear.plusYears(1)
        }

// 1) Heute
        val todayNames = entries
            .filter { it.birthDate.monthValue == today.monthValue && it.birthDate.dayOfMonth == today.dayOfMonth }
            .map { it.fullName }

        if (notifyToday && todayNames.isNotEmpty()) {

            val titleToday = if (todayNames.size == 1) {
                "Heute hat Geburtstag:"
            } else {
                "Heute haben Geburtstag:"
            }

            BirthdayNotificationHelper.sendCustomNotification(
                context = applicationContext,
                title = titleToday,
                text = todayNames.joinToString("\n"),
                notificationId = 1001
            )
        }

// 2) 7 Tage vorher
        val in7DaysNames = entries
            .filter { e ->
                val next = nextOccurrence(e.birthDate, today)
                java.time.temporal.ChronoUnit.DAYS.between(today, next) == 7L
            }
            .map { it.fullName }

        if (notifyWeek && in7DaysNames.isNotEmpty()) {

            val title7Days = if (in7DaysNames.size == 1) {
                "In 7 Tagen hat Geburtstag:"
            } else {
                "In 7 Tagen haben Geburtstag:"
            }

            BirthdayNotificationHelper.sendCustomNotification(
                context = applicationContext,
                title = title7Days,
                text = in7DaysNames.joinToString("\n"),
                notificationId = 1002
            )
        }

// 3) Monatsbeginn
        if (today.dayOfMonth == 1) {
            val monthNames = entries
                .filter { it.birthDate.monthValue == today.monthValue }
                .map { it.fullName }

            if (notifyMonth && monthNames.isNotEmpty()) {

                val monthName = today.month.getDisplayName(
                    TextStyle.FULL,
                    Locale.GERMAN
                )

                val titleMonth = if (monthNames.size == 1) {
                    "Im $monthName hat Geburtstag:"
                } else {
                    "Im $monthName haben Geburtstag:"
                }

                BirthdayNotificationHelper.sendCustomNotification(
                    context = applicationContext,
                    title = titleMonth,
                    text = monthNames.joinToString("\n"),
                    notificationId = 1003
                )
            }
        }

        return Result.success()
    }
}
