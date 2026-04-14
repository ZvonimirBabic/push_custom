package uk.orth.push

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService() {
    override fun onNewToken(registrationToken: String) {
        super.onNewToken(registrationToken)
        PushPlugin.onNewToken(this, registrationToken)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Create an intent from the RemoteMessage to maintain compatibility with existing message handling
        val intent = Intent()
        remoteMessage.data.forEach { (key, value) ->
            intent.putExtra(key, value)
        }
        remoteMessage.notification?.let { notification ->
            intent.putExtra("notification_title", notification.title)
            intent.putExtra("notification_body", notification.body)
        }
        intent.putExtras(remoteMessage.toIntent().extras ?: android.os.Bundle())

        sendMessageToFlutterApplication(intent)
    }


    private fun sendMessageToFlutterApplication(intent: Intent) {
        val isApplicationInForeground = isApplicationInForeground()
        when {
            isApplicationInForeground -> {
                PushHostHandlers.sendMessageToFlutterApp(this, intent)
            }
            PushPlugin.isMainActivityRunning -> {
                PushHostHandlers.sendBackgroundMessageToFlutterApp(this, intent)
            }
            else -> {
                BackgroundFlutterAppLauncher(this, intent)
            }
        }
    }

    private fun isApplicationInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        for (process in appProcesses) {
            if (process.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true
            }
        }
        return false
    }


    companion object {
        private val TAG = MessagingService::class.qualifiedName
    }
}

private fun RemoteMessage.toIntent(): Intent {
    val intent = Intent()
    data.forEach { (key, value) ->
        intent.putExtra(key, value)
    }
    return intent
}
