package uk.orth.push

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import uk.orth.push.PushHostHandlers.Companion.ON_BACKGROUND_MESSAGE_PROCESSING_COMPLETE

class MessagingService : FirebaseMessagingService() {
    private var asyncProcessingPendingResult: BroadcastReceiver.PendingResult? = null
    private var flutterBackgroundMessageProcessingCompleteReceiver: FlutterBackgroundMessageProcessingCompleteReceiver? = null

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

        listenForFlutterApplicationToFinishProcessingMessage()
        sendMessageToFlutterApplication(intent)
    }

    /**
     * Launches a broadcast receiver ([FlutterBackgroundMessageProcessingCompleteReceiver]) so that
     * this service can be informed when the Flutter application completes processing.
     *
     * goAsync() also increases the execution time from 10s/20s (depending on API level) to 30s
     */
    private fun listenForFlutterApplicationToFinishProcessingMessage() {
        if (flutterBackgroundMessageProcessingCompleteReceiver == null) {
            flutterBackgroundMessageProcessingCompleteReceiver =
                FlutterBackgroundMessageProcessingCompleteReceiver(this)
            asyncProcessingPendingResult = goAsync()
        }
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

    /**
     * A dynamic broadcast receiver registered to listen to a
     * `PUSH_ON_BACKGROUND_MESSAGE_PROCESSING_COMPLETE`
     */
    private inner class FlutterBackgroundMessageProcessingCompleteReceiver(
        context: Context,
    ) : BroadcastReceiver() {
        init {
            val filter = IntentFilter()
            filter.addAction(ON_BACKGROUND_MESSAGE_PROCESSING_COMPLETE)
            LocalBroadcastManager.getInstance(context).registerReceiver(this, filter)
        }

        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            flutterBackgroundMessageProcessingCompleteReceiver?.let {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
                flutterBackgroundMessageProcessingCompleteReceiver = null
            }
            if (intent.action == ON_BACKGROUND_MESSAGE_PROCESSING_COMPLETE) {
                finish()
            } else {
                Log.e(TAG, String.format("Received unknown intent action: %s", intent.action))
            }
        }
    }

    private fun finish() {
        asyncProcessingPendingResult?.finish()
        asyncProcessingPendingResult = null
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
