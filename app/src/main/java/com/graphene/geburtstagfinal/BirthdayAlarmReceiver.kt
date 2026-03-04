package com.graphene.geburtstagfinal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BirthdayAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        val req = OneTimeWorkRequestBuilder<BirthdayWorker>().build()
        WorkManager.getInstance(context).enqueue(req)

        // plant direkt den nächsten Alarm für morgen
        BirthdayAlarmScheduler.scheduleNext(context)
    }
}