package ch.heuscher.safe_home_button.service.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import ch.heuscher.safe_home_button.BackHomeAccessibilityService
import ch.heuscher.safe_home_button.MainActivity
import ch.heuscher.safe_home_button.R
import ch.heuscher.safe_home_button.di.ServiceLocator
import ch.heuscher.safe_home_button.domain.model.DotPosition
import ch.heuscher.safe_home_button.domain.model.Gesture
import ch.heuscher.safe_home_button.domain.repository.SettingsRepository
import ch.heuscher.safe_home_button.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Refactored OverlayService with Ghost Anchor pattern.
 * - anchorPosition: The "Desired" position (set by user or rotation), invariant to keyboard/temporary shifts.
 * - currentPosition: The "Actual" position on screen.
 * - Watchdog: Periodically ensures currentPosition == anchorPosition if no valid displacement exists.
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        private const val ORIENTATION_CHANGE_INITIAL_DELAY_MS = 16L
        private const val ORIENTATION_CHANGE_RETRY_DELAY_MS = 16L
        private const val ORIENTATION_CHANGE_MAX_ATTEMPTS = 20

        // Notification constants
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "overlay_service_channel"
        private const val CHANNEL_NAME = "Assistive Tap Service"
    }

    // Core dependencies
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewManager: OverlayViewManager
    private lateinit var gestureDetector: GestureDetector

    // Specialized components
    private lateinit var keyboardManager: KeyboardManager
    private lateinit var positionAnimator: PositionAnimator
    private lateinit var orientationHandler: OrientationHandler
    private lateinit var tooltipManager: TooltipManager
    private lateinit var tetherOverlayManager: TetherOverlayManager

    // Service scope for coroutines
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // State tracking
    private var isUserDragging = false
    private var isOrientationChanging = false
    
    // Ghost Anchor State
    private var anchorPosition: DotPosition? = null

    // Handler for delayed updates
    private val updateHandler = Handler(Looper.getMainLooper())

    // Broadcast receiver for settings changes
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConstants.ACTION_UPDATE_SETTINGS) {
                serviceScope.launch {
                    updateOverlayAppearance()
                }
            }
        }
    }

    // Broadcast receiver for keyboard changes
    private val keyboardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConstants.ACTION_UPDATE_KEYBOARD) {
                val visible = intent.getBooleanExtra("keyboard_visible", false)
                val height = intent.getIntExtra("keyboard_height", 0)
                handleKeyboardBroadcast(visible, height)
            }
        }
    }

    // Broadcast receiver for configuration changes (e.g., orientation)
    private val configurationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                handleOrientationChange()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)

        settingsRepository = ServiceLocator.settingsRepository
        viewManager = ServiceLocator.overlayViewManager
        gestureDetector = ServiceLocator.gestureDetector
        orientationHandler = ServiceLocator.orientationHandler
        tetherOverlayManager = ServiceLocator.tetherOverlayManager

        // Create specialized components
        keyboardManager = ServiceLocator.createKeyboardManager(
            context = this,
            onAdjustPosition = { position -> animateToPosition(position) },
            getCurrentPosition = { viewManager.getCurrentPosition() },
            getCurrentRotation = { orientationHandler.getCurrentRotation() },
            getUsableScreenSize = { orientationHandler.getUsableScreenSize() },
            getSettings = { settingsRepository.getAllSettings().first() },
            isUserDragging = { isUserDragging },
            getStatusBarHeight = { viewManager.getStatusBarHeight() }
        )

        positionAnimator = ServiceLocator.createPositionAnimator(
            onPositionUpdate = { position ->
                val (constrainedX, constrainedY) = viewManager.constrainPositionToBounds(position.x, position.y)
                val currentPos = DotPosition(constrainedX, constrainedY)
                viewManager.updatePosition(currentPos)
                
                // Update tether while animating if applicable
                anchorPosition?.let { anchor ->
                   updateTetherVisualization(anchor, currentPos)
                }
            },
            onAnimationComplete = { position -> onAnimationComplete(position) }
        )

        tooltipManager = ServiceLocator.createTooltipManager(
            context = this,
            getCurrentPosition = { viewManager.getCurrentPosition() },
            getScreenSize = { orientationHandler.getUsableScreenSize() },
            onBringButtonToFront = { viewManager.bringToFront() }
        )

        viewManager.createOverlayView()
        setupGestureCallbacks()
        registerBroadcastReceivers()
        observeSettings()
        initializeScreenDimensions()
        keyboardManager.startMonitoring()
        startForegroundService()
        
        // Start the Watchdog
        startPositionWatchdog()
    }

    private fun startPositionWatchdog() {
        serviceScope.launch {
            while (isActive) {
                delay(AppConstants.POSITION_WATCHDOG_INTERVAL_MS)
                checkAndCorrectPosition()
            }
        }
    }

    private fun checkAndCorrectPosition() {
        if (isUserDragging || isOrientationChanging || positionAnimator.isAnimating()) {
            return
        }

        // Do not correct if keyboard is visible (displacement is expected)
        if (keyboardManager.keyboardVisible) {
            return
        }

        val current = viewManager.getCurrentPosition()
        val anchor = anchorPosition

        if (current != null && anchor != null) {
            val dist = hypot((current.x - anchor.x).toFloat(), (current.y - anchor.y).toFloat())
            val threshold = AppConstants.ANCHOR_DRIFT_THRESHOLD_DP * resources.displayMetrics.density
            
            if (dist > threshold) {
                Log.d(TAG, "Watchdog: Drift detected! Dist=$dist, Threshold=$threshold. Snapping back to anchor.")
                // Snap back to anchor
                animateToPosition(anchor)
            }
        }
    }

    private fun updateTetherVisualization(anchor: DotPosition, current: DotPosition) {
         val dist = hypot((current.x - anchor.x).toFloat(), (current.y - anchor.y).toFloat())
         // Threshold to show tether: slightly larger than drift threshold to avoid flickering
         val threshold = (AppConstants.ANCHOR_DRIFT_THRESHOLD_DP + 5) * resources.displayMetrics.density
         
         if (dist > threshold) {
             // Calculate centers
             val buttonSizePx = (AppConstants.DOT_SIZE_DP * resources.displayMetrics.density).toInt()
             val half = buttonSizePx / 2
             
             val anchorCenter = Point(anchor.x + half, anchor.y + half)
             val currentCenter = Point(current.x + half, current.y + half)
             
             tetherOverlayManager.setTether(anchorCenter, currentCenter)
         } else {
             tetherOverlayManager.hideTether()
         }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        return START_STICKY
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.accessibility_service_description)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.apply {
            setContentTitle(getString(R.string.app_name))
            setContentText(getString(R.string.shows_the_assistipoint))
            setSmallIcon(R.mipmap.ic_launcher)
            setContentIntent(pendingIntent)
            setOngoing(true)
        }.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        tetherOverlayManager.removeTetherOverlay()
        keyboardManager.stopMonitoring()
        positionAnimator.cancel()
        tooltipManager.cleanup()
        serviceScope.cancel()
        updateHandler.removeCallbacksAndMessages(null)

        unregisterReceiver(settingsReceiver)
        unregisterReceiver(keyboardReceiver)
        unregisterReceiver(configurationReceiver)

        viewManager.removeOverlayView()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupGestureCallbacks() {
        gestureDetector.onGesture = { gesture -> handleGesture(gesture) }
        gestureDetector.onPositionChanged = { deltaX, deltaY -> handlePositionChange(deltaX, deltaY) }
        gestureDetector.onDragModeChanged = { enabled -> viewManager.setDragMode(enabled) }
        
        gestureDetector.onTouchDown = {
            serviceScope.launch {
                val settings = settingsRepository.getAllSettings().first()
                if (settings.isHapticFeedbackEnabled) vibrate()
                if (settings.isTooltipEnabled) tooltipManager.showTooltip(Gesture.TAP, settings.tapBehavior)
            }
        }

        val listener = View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                serviceScope.launch {
                    val settings = settingsRepository.getAllSettings().first()
                    if (settings.isHapticFeedbackEnabled) vibrate()
                }
            }
            gestureDetector.onTouch(event)
        }
        viewManager.setTouchListener(listener)
    }

    private fun registerBroadcastReceivers() {
        val settingsFilter = IntentFilter(AppConstants.ACTION_UPDATE_SETTINGS)
        val keyboardFilter = IntentFilter(AppConstants.ACTION_UPDATE_KEYBOARD)
        val configFilter = IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsReceiver, settingsFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(keyboardReceiver, keyboardFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(configurationReceiver, configFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(settingsReceiver, settingsFilter)
            @Suppress("DEPRECATION")
            registerReceiver(keyboardReceiver, keyboardFilter)
            @Suppress("DEPRECATION")
            registerReceiver(configurationReceiver, configFilter)
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            var isFirstEmission = true
            settingsRepository.getAllSettings().collectLatest { settings ->
                if (isFirstEmission) {
                    isFirstEmission = false
                    anchorPosition = settings.position // Initialize Anchor
                    updateOverlayAppearance()
                    updateGestureMode(settings.isLongPressToMoveEnabled)
                    tooltipManager.initialize(settings.tapBehavior)
                } else {
                    updateOverlayAppearance()
                    updateGestureMode(settings.isLongPressToMoveEnabled)
                    // Do not jump to saved position on settings update, maintain current
                    // (Watchdog will correct if needed, or visual update will handle it)
                }
            }
        }
    }

    private fun updateGestureMode(requiresLongPress: Boolean) {
        gestureDetector.setRequiresLongPressToDrag(requiresLongPress)
    }

    private fun initializeScreenDimensions() {
        serviceScope.launch {
            val size = orientationHandler.getUsableScreenSize()
            val rotation = orientationHandler.getCurrentRotation()
            settingsRepository.setScreenWidth(size.x)
            settingsRepository.setScreenHeight(size.y)
            settingsRepository.setRotation(rotation)
        }
    }

    private suspend fun updateOverlayAppearance() {
        val settings = settingsRepository.getAllSettings().first()
        viewManager.updateAppearance(settings)
        
        // Ensure we have a valid anchor position
        if (anchorPosition == null) {
            anchorPosition = settings.position
        }
        
        // On initial load or explicit reset, we might want to enforce anchor
        // But generally, let the layout/watchdog handle positioning
        if (viewManager.getCurrentPosition() == null) {
            anchorPosition?.let { anchor ->
                val (constrainedX, constrainedY) = viewManager.constrainPositionToBounds(anchor.x, anchor.y)
                viewManager.updatePosition(DotPosition(constrainedX, constrainedY))
            }
        }
    }

    private fun handleGesture(gesture: Gesture) {
        when (gesture) {
            Gesture.DRAG_START -> {
                serviceScope.launch {
                    val settings = settingsRepository.getAllSettings().first()
                    if (settings.isPositionLocked) return@launch
                    positionAnimator.cancel()
                    isUserDragging = true
                    tetherOverlayManager.hideTether() // Hide tether while dragging
                }
                return
            }
            Gesture.DRAG_MOVE -> {
                serviceScope.launch {
                    val settings = settingsRepository.getAllSettings().first()
                    if (settings.isPositionLocked) return@launch
                    if (!isUserDragging) {
                        positionAnimator.cancel()
                        isUserDragging = true
                    }
                }
                return
            }
            Gesture.DRAG_END -> {
                serviceScope.launch {
                    val settings = settingsRepository.getAllSettings().first()
                    if (settings.isPositionLocked) return@launch
                    
                    positionAnimator.cancel()
                    isUserDragging = false
                    onDragEnd() // Update Anchor on drag end
                }
                return
            }
            else -> { /* continue */ }
        }

        serviceScope.launch {
            when (gesture) {
                Gesture.TAP -> handleTap()
                Gesture.DOUBLE_TAP -> handleDoubleTap()
                Gesture.TRIPLE_TAP -> handleTripleTap()
                Gesture.QUADRUPLE_TAP -> handleQuadrupleTap()
                Gesture.LONG_PRESS -> handleLongPress()
                else -> { /* No-op */ }
            }
        }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(50)
        }
    }

    private fun handleTap() {
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            if (settings.isTooltipEnabled) tooltipManager.showTooltip(Gesture.TAP, settings.tapBehavior)
            when (settings.tapBehavior) {
                "STANDARD", "SAFE_HOME" -> BackHomeAccessibilityService.instance?.performHomeAction()
                "NAVI" -> BackHomeAccessibilityService.instance?.performBackAction()
                else -> BackHomeAccessibilityService.instance?.performBackAction()
            }
        }
    }

    private fun handleDoubleTap() {
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            if (settings.isTooltipEnabled) tooltipManager.showTooltip(Gesture.DOUBLE_TAP, settings.tapBehavior)
            when (settings.tapBehavior) {
                "STANDARD" -> BackHomeAccessibilityService.instance?.performBackAction()
                "NAVI", "SAFE_HOME" -> BackHomeAccessibilityService.instance?.performRecentsAction()
                else -> BackHomeAccessibilityService.instance?.performRecentsAction()
            }
        }
    }

    private fun handleTripleTap() {
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            if (settings.isTooltipEnabled) tooltipManager.showTooltip(Gesture.TRIPLE_TAP, settings.tapBehavior)
            if (settings.tapBehavior == "SAFE_HOME") {
                BackHomeAccessibilityService.instance?.performHomeAction() // Fallback/Alternative?
            } else {
                BackHomeAccessibilityService.instance?.performRecentsOverviewAction()
            }
        }
    }

    private fun handleQuadrupleTap() {
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            if (settings.isTooltipEnabled) tooltipManager.showTooltip(Gesture.QUADRUPLE_TAP, settings.tapBehavior)
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun handleLongPress() {
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            if (settings.isHapticFeedbackEnabled) vibrate()
            if (settings.isTooltipEnabled) tooltipManager.showTooltip(Gesture.LONG_PRESS, settings.tapBehavior)
            if (settings.tapBehavior == "SAFE_HOME") {
                if (settings.isPositionLocked) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this@OverlayService, getString(R.string.position_locked_message), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                BackHomeAccessibilityService.instance?.performHomeAction()
            }
        }
    }

    private fun handlePositionChange(deltaX: Int, deltaY: Int) {
        serviceScope.launch {
            val settings = settingsRepository.getAllSettings().first()
            if (settings.isPositionLocked) return@launch

            val currentPos = viewManager.getCurrentPosition() ?: return@launch
            val newX = currentPos.x + deltaX
            val newY = currentPos.y + deltaY
            val (constrainedX, constrainedY) = viewManager.constrainPositionToBounds(newX, newY)

            // During drag, we update the view immediately
            // We do NOT update the Anchor here
            viewManager.updatePosition(DotPosition(constrainedX, constrainedY))
        }
    }

    private fun onDragEnd() {
        serviceScope.launch {
            viewManager.getCurrentPosition()?.let { finalPos ->
                // Dragging explicitly updates the Anchor
                updateAnchor(finalPos)
                Log.d(TAG, "onDragEnd: Anchor updated to (${finalPos.x}, ${finalPos.y})")
            }
        }
    }

    private fun updateAnchor(position: DotPosition) {
        anchorPosition = position
        savePosition(position)
    }

    private fun savePosition(position: DotPosition) {
        serviceScope.launch {
            val screenSize = orientationHandler.getUsableScreenSize()
            val rotation = orientationHandler.getCurrentRotation()
            val positionWithScreen = DotPosition(position.x, position.y, screenSize.x, screenSize.y)
            settingsRepository.setPosition(positionWithScreen)
            settingsRepository.setScreenWidth(screenSize.x)
            settingsRepository.setScreenHeight(screenSize.y)
            settingsRepository.setRotation(rotation)
        }
    }

    private fun handleKeyboardBroadcast(visible: Boolean, height: Int) {
        serviceScope.launch {
            // If keyboard hidden, restore to Anchor
            if (!visible) {
                anchorPosition?.let { anchor ->
                    Log.d(TAG, "Keyboard hidden: Restoring to anchor (${anchor.x}, ${anchor.y})")
                    animateToPosition(anchor)
                }
                tetherOverlayManager.hideTether()
            } else {
                // If keyboard visible, move Current (keep Anchor)
                val currentAnchor = anchorPosition ?: viewManager.getCurrentPosition() ?: return@launch
                
                // Use keyboardManager to calculate avoided position
                // We trick it by passing the Anchor as the current position to check against
                val (constrainedX, constrainedY) = keyboardManager.constrainPositionWithKeyboard(
                    currentAnchor.x, currentAnchor.y, currentAnchor.x, currentAnchor.y
                )
                
                // If position changed, animate to it
                if (constrainedX != currentAnchor.x || constrainedY != currentAnchor.y) {
                    val newPos = DotPosition(constrainedX, constrainedY)
                    Log.d(TAG, "Keyboard visible: Moving to avoidance pos (${newPos.x}, ${newPos.y})")
                    animateToPosition(newPos)
                    updateTetherVisualization(currentAnchor, newPos)
                }
            }
            
            // Notify manager for internal state
             val settings = settingsRepository.getAllSettings().first()
             keyboardManager.handleKeyboardChange(visible, height, settings)
        }
    }

    private fun handleOrientationChange() {
        Log.d(TAG, "Configuration changed, handling orientation")
        isOrientationChanging = true
        viewManager.setVisibility(View.INVISIBLE)
        tetherOverlayManager.hideTether()

        serviceScope.launch {
            val oldSettings = settingsRepository.getAllSettings().first()
            waitForOrientationComplete(oldSettings.rotation, oldSettings.screenWidth, oldSettings.screenHeight, oldSettings.position, 0)
        }
    }

    private fun waitForOrientationComplete(oldRotation: Int, oldWidth: Int, oldHeight: Int, oldPosition: DotPosition, attempt: Int) {
        if (attempt >= ORIENTATION_CHANGE_MAX_ATTEMPTS) {
            isOrientationChanging = false
            viewManager.fadeIn(200L)
            return
        }

        val delay = if (attempt == 0) ORIENTATION_CHANGE_INITIAL_DELAY_MS else ORIENTATION_CHANGE_RETRY_DELAY_MS

        updateHandler.postDelayed({
            serviceScope.launch {
                val newSize = orientationHandler.getUsableScreenSize()
                val newRotation = orientationHandler.getCurrentRotation()
                
                if ((newSize.x != oldWidth || newSize.y != oldHeight) || (newRotation != oldRotation)) {
                    applyOrientationTransformation(oldRotation, oldWidth, oldHeight, oldPosition, newRotation, newSize)
                } else {
                    waitForOrientationComplete(oldRotation, oldWidth, oldHeight, oldPosition, attempt + 1)
                }
            }
        }, delay)
    }

    private suspend fun applyOrientationTransformation(
        oldRotation: Int, oldWidth: Int, oldHeight: Int, oldPosition: DotPosition,
        newRotation: Int, newSize: Point
    ) {
        // Transform the ANCHOR position
        val currentAnchor = anchorPosition ?: oldPosition
        
        // Use existing physical transformation logic
        val buttonSizePx = (AppConstants.DOT_SIZE_DP * resources.displayMetrics.density).toInt()
        val half = buttonSizePx / 2
        val centerPosition = DotPosition(currentAnchor.x + half, currentAnchor.y + half, oldWidth, oldHeight, oldRotation)
        
        val transformedCenter = orientationHandler.transformPosition(centerPosition, oldWidth, oldHeight, oldRotation, newRotation)
        
        val newTopLeftX = transformedCenter.x - half
        val newTopLeftY = transformedCenter.y - half
        
        val (constrainedX, constrainedY) = viewManager.constrainPositionToBounds(newTopLeftX, newTopLeftY)
        val newAnchor = DotPosition(constrainedX, constrainedY, newSize.x, newSize.y, newRotation)
        
        // Update Anchor
        updateAnchor(newAnchor)
        
        // Move view to new Anchor immediately
        viewManager.updatePosition(newAnchor)
        
        isOrientationChanging = false
        viewManager.fadeIn(200L)
    }

    private fun animateToPosition(targetPosition: DotPosition) {
        val current = viewManager.getCurrentPosition() ?: return
        positionAnimator.animateToPosition(current, targetPosition)
    }

    private fun onAnimationComplete(position: DotPosition) {
        // Update tether at end of animation
        anchorPosition?.let { anchor ->
            updateTetherVisualization(anchor, position)
        }
    }
}


