package com.actuagt.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.actuagt.shared.data.DataGraph
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Receives FCM pushes: report-status changes for citizens, new cases for
// officials. Tapping a notification opens the app.
class ActuaGTMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            DataGraph.reportRepository.refreshPushToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "ActuaGT"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return
        showNotification(title, body, message.data["reportId"])
    }

    private fun showNotification(title: String, body: String, reportId: String?) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Actualizaciones de reportes",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            reportId?.let { putExtra(MainActivity.EXTRA_REPORT_ID, it) }
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(title.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "actuagt_updates"
    }
}
