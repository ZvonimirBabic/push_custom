package uk.orth.push

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.RemoteMessage
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import uk.orth.push.serialization.PushHostApi

class BackgroundFlutterAppLauncher(
    private val context: Context,
    private val remoteMessage: RemoteMessage,
) {

    private val flutterEngine: FlutterEngine = FlutterEngine(context)
    private val pushHostHandlers: PushHostHandlers =
        PushHostHandlers(context, flutterEngine.dartExecutor.binaryMessenger)

    init {
        Log.d(TAG, "Starting headless Flutter engine")

        // Setup background processing callback
        pushHostHandlers.setupForBackgroundNotificationProcessing(remoteMessage) {
            finish()
        }

        // Setup platform channel
        PushHostApi.setUp(
            flutterEngine.dartExecutor.binaryMessenger,
            pushHostHandlers
        )

        // Start Flutter (headless)
        flutterEngine.dartExecutor.executeDartEntrypoint(
            DartExecutor.DartEntrypoint.createDefault()
        )
    }

    private fun finish() {
        Log.i(TAG, "Flutter finished processing. Destroying engine.")
        flutterEngine.destroy()
    }

    companion object {
        private val TAG = BackgroundFlutterAppLauncher::class.qualifiedName
    }
}