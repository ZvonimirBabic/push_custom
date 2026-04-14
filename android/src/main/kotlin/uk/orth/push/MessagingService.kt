class BackgroundFlutterAppLauncher(
    private val context: Context,
    private val data: Map<String, String>,
) {

    private val flutterEngine = FlutterEngine(context)

    private val pushHostHandlers =
        PushHostHandlers(context, flutterEngine.dartExecutor.binaryMessenger)

    init {
        Log.e("FCM_DEBUG", "🚀 Starting Flutter engine (terminated state)")

        pushHostHandlers.setupForBackgroundNotificationProcessing(data) {
            finish()
        }

        PushHostApi.setUp(
            flutterEngine.dartExecutor.binaryMessenger,
            pushHostHandlers
        )

        flutterEngine.dartExecutor.executeDartEntrypoint(
            DartExecutor.DartEntrypoint.createDefault()
        )
    }

    private fun finish() {
        Log.e("FCM_DEBUG", "🛑 Flutter finished, destroying engine")
        flutterEngine.destroy()
    }
}