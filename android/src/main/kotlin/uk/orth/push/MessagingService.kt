package uk.orth.push

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "onMessage: $remoteMessage")
        if (remoteMessage.data.isEmpty()) {
            return
        }

        handleMessage(applicationContext, remoteMessage)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New token: $token")
    }

    private fun handleMessage(context: Context, remoteMessage: RemoteMessage) {
        val data = remoteMessage.data

        when {
            isApplicationInForeground(context) -> {
                PushHostHandlers.sendMessageToFlutterApp(context, data)
            }

            PushPlugin.isMainActivityRunning -> {
                PushHostHandlers.sendBackgroundMessageToFlutterApp(context, data)
            }

            else -> {
                BackgroundFlutterAppLauncher(context, data)
            }
        }
    }

    private fun isApplicationInForeground(context: Context): Boolean {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val processes = activityManager.runningAppProcesses ?: return false

        for (process in processes) {
            if (process.importance ==
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            ) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}