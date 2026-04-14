package uk.orth.push

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.messaging.RemoteMessage
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import uk.orth.push.serialization.PushHostApi

/**
 * This class is used when the application was terminated when the push notification is received. It
 * launches the Flutter application and sends it a RemoteMessage. See
 * [PushHostHandlers] to see where push notifications being handled whilst the app is
 * in the background or the foreground. Use this class when no existing Flutter Activity is running.
 *
 * This class can be generalized to launch the app manually for any purpose, but currently it is
 * narrowly scoped for push notifications.
 *
 * Creates a Flutter engine, launches the Flutter application inside that Flutter engine, and
 * creates a MethodChannel to communicate with the Flutter application.
 *
 * @param context
 * @param intent An intent containing a RemoteMessage passed from MessagingService
 * @param onComplete Callback to invoke when processing is complete
 */
class BackgroundFlutterAppLauncher(
    private val context: Context,
    private val intent: Intent,
    private val onComplete: (() -> Unit)? = null,
) {
    private val remoteMessage: RemoteMessage = RemoteMessage((intent.extras)!!)
    private var flutterEngine: FlutterEngine? = null
    private var pushHostHandlers: PushHostHandlers? = null

    init {
        // FlutterEngine initialization must happen on the main thread.
        // This class may be called from Firebase-Messaging-Intent-Handle background thread,
        // so we defer the initialization to the main thread using Handler.
        Handler(Looper.getMainLooper()).post {
            initializeFlutterEngine()
        }
    }

    private fun initializeFlutterEngine() {
        try {
            flutterEngine = FlutterEngine(context, null)
            pushHostHandlers = PushHostHandlers(context, flutterEngine!!.dartExecutor.binaryMessenger)

            // Setup listener for background processing
            pushHostHandlers!!.setupForBackgroundNotificationProcessing(remoteMessage) { finish() }
            // Setup listener
            PushHostApi.setUp(flutterEngine!!.dartExecutor.binaryMessenger, pushHostHandlers)
            // Launch the users app isolate manually
            flutterEngine!!.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Flutter engine", e)
            onComplete?.invoke()
        }
    }

    private fun finish() {
        Log.i(
            TAG,
            "Manually launched Flutter application has finished processing message. " +
                "Destroying FlutterEngine and finishing asynchronous MessagingService",
        )
        flutterEngine?.destroy()
        flutterEngine = null
        pushHostHandlers = null
        onComplete?.invoke()
    }

    companion object {
        private val TAG = BackgroundFlutterAppLauncher::class.qualifiedName
    }
}
