package ch.heuscher.safe_home_button.di

import android.content.Context
import android.graphics.Point
import android.view.ViewConfiguration
import ch.heuscher.safe_home_button.data.local.SettingsDataSource
import ch.heuscher.safe_home_button.data.local.SharedPreferencesDataSource
import ch.heuscher.safe_home_button.data.repository.SettingsRepositoryImpl
import ch.heuscher.safe_home_button.domain.model.DotPosition
import ch.heuscher.safe_home_button.domain.repository.SettingsRepository
import ch.heuscher.safe_home_button.service.overlay.GestureDetector
import ch.heuscher.safe_home_button.service.overlay.KeyboardDetector
import ch.heuscher.safe_home_button.service.overlay.KeyboardManager
import ch.heuscher.safe_home_button.service.overlay.OrientationHandler
import ch.heuscher.safe_home_button.service.overlay.OverlayViewManager
import ch.heuscher.safe_home_button.service.overlay.PositionAnimator
import ch.heuscher.safe_home_button.service.overlay.TooltipManager

/**
 * Simple service locator for dependency injection.
 * Used during the refactoring transition before full Hilt migration.
 */
object ServiceLocator {

    private lateinit var applicationContext: Context

    fun initialize(context: Context) {
        if (!::applicationContext.isInitialized) {
            applicationContext = context.applicationContext
        }
    }

    // Lazy initialization of singletons
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(settingsDataSource)
    }

    val settingsDataSource: SettingsDataSource by lazy {
        SharedPreferencesDataSource(applicationContext)
    }

    val keyboardDetector: KeyboardDetector by lazy {
        KeyboardDetector(windowManager, inputMethodManager)
    }

    val gestureDetector: GestureDetector by lazy {
        GestureDetector(ViewConfiguration.get(applicationContext))
    }

    val overlayViewManager: OverlayViewManager by lazy {
        OverlayViewManager(applicationContext, windowManager)
    }

    val orientationHandler: OrientationHandler by lazy {
        OrientationHandler(applicationContext)
    }

    // System services
    private val windowManager by lazy {
        applicationContext.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
    }

    private val inputMethodManager by lazy {
        applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
    }

    // Factory methods for per-instance components

    /**
     * Create a new KeyboardManager instance for a service
     */
    fun createKeyboardManager(
        context: Context,
        onAdjustPosition: (DotPosition) -> Unit,
        getCurrentPosition: () -> DotPosition?,
        getCurrentRotation: () -> Int,
        getUsableScreenSize: () -> Point,
        getSettings: suspend () -> ch.heuscher.safe_home_button.domain.model.OverlaySettings,
        isUserDragging: () -> Boolean
    ): KeyboardManager {
        return KeyboardManager(
            context = context,
            keyboardDetector = keyboardDetector,
            onAdjustPosition = onAdjustPosition,
            getCurrentPosition = getCurrentPosition,
            getCurrentRotation = getCurrentRotation,
            getUsableScreenSize = getUsableScreenSize,
            getSettings = getSettings,
            isUserDragging = isUserDragging
        )
    }

    /**
     * Create a new PositionAnimator instance for a service
     */
    fun createPositionAnimator(
        onPositionUpdate: (DotPosition) -> Unit,
        onAnimationComplete: (DotPosition) -> Unit
    ): PositionAnimator {
        return PositionAnimator(
            onPositionUpdate = onPositionUpdate,
            onAnimationComplete = onAnimationComplete
        )
    }

    /**
     * Create a new TooltipManager instance for a service
     */
    fun createTooltipManager(
        context: Context,
        getCurrentPosition: () -> DotPosition?,
        getScreenSize: () -> Point,
        onBringButtonToFront: () -> Unit = {}
    ): TooltipManager {
        return TooltipManager(
            context = context,
            getCurrentPosition = getCurrentPosition,
            getScreenSize = getScreenSize,
            onBringButtonToFront = onBringButtonToFront
        )
    }

    /**
     * Helper method to get repository from any context
     */
    fun getRepository(context: Context): SettingsRepository {
        initialize(context)
        return settingsRepository
    }
}