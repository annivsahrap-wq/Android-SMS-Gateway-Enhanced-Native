package me.capcom.smsgateway.modules.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.media.RingtoneManager
import androidx.preference.PreferenceManager
import androidx.core.app.NotificationCompat
import me.capcom.smsgateway.MainActivity
import me.capcom.smsgateway.R

class NotificationsService(
    context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    private val icons = mapOf(
        NOTIFICATION_ID_LOCAL_SERVICE to R.drawable.notif_server,
        NOTIFICATION_ID_SEND_WORKER to R.drawable.notif_send,
        NOTIFICATION_ID_WEBHOOK_WORKER to R.drawable.notif_webhook,
        NOTIFICATION_ID_PING_SERVICE to R.drawable.notif_ping,
        NOTIFICATION_ID_SETTINGS_CHANGED to R.drawable.notif_settings,
        NOTIFICATION_ID_SMS_RECEIVED_WEBHOOK to R.drawable.notif_webhook_registered,
        NOTIFICATION_ID_REALTIME_EVENTS to R.drawable.notif_realtime_events,
    )

    private val builders = mapOf<Int, (NotificationCompat.Builder) -> NotificationCompat.Builder>(
        NOTIFICATION_ID_SETTINGS_CHANGED to {
            it.setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
        },
        NOTIFICATION_ID_SMS_RECEIVED_WEBHOOK to {
            it.setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
        },
    )

    private val contentIntentFactories = mapOf(
        NOTIFICATION_ID_SMS_RECEIVED_WEBHOOK to { context: Context ->
            MainActivity.starter(context, MainActivity.TAB_INDEX_SETTINGS)
        }
    )

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.sms_gateway)
            val descriptionText = context.getString(R.string.local_sms_gateway_notifications)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            mChannel.enableVibration(true)
            mChannel.vibrationPattern = longArrayOf(0, 250, 150, 250)
            mChannel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build()
            )
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    fun notify(context: Context, id: Int, contentText: String) {
        notificationManager.notify(id, makeNotification(context, id, contentText))
    }

    fun notifyConnection(context: Context, online: Boolean) {
        if (!prefs(context).getBoolean(KEY_CONNECTION, true)) return
        notify(context, if (online) NOTIFICATION_ID_CONNECTION_ONLINE else NOTIFICATION_ID_CONNECTION_OFFLINE,
            if (online) "Live Server connected" else "Live Server connection lost")
    }

    fun notifyIncoming(context: Context, sender: String?, preview: String?) {
        if (!prefs(context).getBoolean(KEY_INCOMING, true)) return
        notify(context, NOTIFICATION_ID_INCOMING, "Incoming SMS${sender?.let { " from $it" } ?: ""}: ${preview.orEmpty().take(80)}")
    }

    fun notifyOutgoing(context: Context, recipient: String?) {
        if (!prefs(context).getBoolean(KEY_OUTGOING, true)) return
        notify(context, NOTIFICATION_ID_OUTGOING, "SMS sent${recipient?.let { " to $it" } ?: ""}")
    }

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    fun makeNotification(
        context: Context,
        id: Int,
        contentText: String,
        contentIntent: PendingIntent? = null
    ): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getText(R.string.sms_gateway))
            .setContentText(contentText)
            .setSmallIcon(icons[id] ?: R.drawable.ic_sms)
            .setContentIntent(
                contentIntent
                    ?: PendingIntent.getActivity(
                        context,
                        0,
                        contentIntentFactories[id]?.invoke(context)
                            ?: context.packageManager.getLaunchIntentForPackage(context.packageName),
                        PendingIntent.FLAG_IMMUTABLE
                    )
            )
            .apply {
                builders[id]?.invoke(this)
                val p = prefs(context)
                if (!p.getBoolean(KEY_SOUND, true)) setSilent(true)
                if (!p.getBoolean(KEY_VIBRATION, true)) setVibrate(longArrayOf(0L))
            }
            .build()
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "sms-gateway-alerts-v2"

        const val NOTIFICATION_ID_LOCAL_SERVICE = 1
        const val NOTIFICATION_ID_SEND_WORKER = 2
        const val NOTIFICATION_ID_WEBHOOK_WORKER = 3
        const val NOTIFICATION_ID_PING_SERVICE = 4
        const val NOTIFICATION_ID_SETTINGS_CHANGED = 5
        const val NOTIFICATION_ID_SMS_RECEIVED_WEBHOOK = 6
        const val NOTIFICATION_ID_REALTIME_EVENTS = 7
        const val NOTIFICATION_ID_CONNECTION_ONLINE = 8
        const val NOTIFICATION_ID_CONNECTION_OFFLINE = 9
        const val NOTIFICATION_ID_INCOMING = 10
        const val NOTIFICATION_ID_OUTGOING = 11
        const val KEY_CONNECTION = "notifications.connection"
        const val KEY_INCOMING = "notifications.incoming"
        const val KEY_OUTGOING = "notifications.outgoing"
        const val KEY_SOUND = "notifications.sound"
        const val KEY_VIBRATION = "notifications.vibration"
    }
}