package com.ngratzi.lumina.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ngratzi.lumina.MainActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Re-schedule alarms after reboot via WorkManager
                DailyAlarmWorker.schedule(context)
            }
            AlarmScheduler.ACTION_SOLAR,
            AlarmScheduler.ACTION_TIDE -> {
                val label = intent.getStringExtra(AlarmScheduler.EXTRA_LABEL) ?: return
                val body  = intent.getStringExtra(AlarmScheduler.EXTRA_BODY)  ?: ""
                val event = intent.getStringExtra(AlarmScheduler.EXTRA_EVENT) ?: return
                showNotification(context, event.hashCode(), label, body)
            }
        }
    }

    private fun showNotification(context: Context, id: Int, title: String, body: String) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AlarmScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // replace with custom icon
            .setContentTitle(title)
            .setContentText(body.ifBlank { title })
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, notification)
    }
}
