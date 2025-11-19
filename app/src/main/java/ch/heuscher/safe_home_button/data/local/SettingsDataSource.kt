package ch.heuscher.safe_home_button.data.local

import ch.heuscher.safe_home_button.domain.model.DotPosition
import ch.heuscher.safe_home_button.domain.model.DotPositionPercent
import kotlinx.coroutines.flow.Flow

/**
 * Local data source interface for settings persistence.
 * Abstracts the data storage implementation (SharedPreferences, Room, etc.).
 */
interface SettingsDataSource {

    // Overlay state
    fun isOverlayEnabled(): Flow<Boolean>
    suspend fun setOverlayEnabled(enabled: Boolean)

    // Appearance settings
    fun getColor(): Flow<Int>
    suspend fun setColor(color: Int)

    fun getAlpha(): Flow<Int>
    suspend fun setAlpha(alpha: Int)

    // Position settings
    fun getPositionX(): Flow<Int>
    suspend fun setPositionX(x: Int)

    fun getPositionY(): Flow<Int>
    suspend fun setPositionY(y: Int)

    fun getPositionXPercent(): Flow<Float>
    suspend fun setPositionXPercent(percent: Float)

    fun getPositionYPercent(): Flow<Float>
    suspend fun setPositionYPercent(percent: Float)

    // Timing settings
    fun getRecentsTimeout(): Flow<Long>
    suspend fun setRecentsTimeout(timeout: Long)

    // Feature toggles
    fun isKeyboardAvoidanceEnabled(): Flow<Boolean>
    suspend fun setKeyboardAvoidanceEnabled(enabled: Boolean)

    fun getTapBehavior(): Flow<String>
    suspend fun setTapBehavior(behavior: String)

    // Tooltip settings
    fun isTooltipEnabled(): Flow<Boolean>
    suspend fun setTooltipEnabled(enabled: Boolean)

    // Haptic Feedback
    fun isHapticFeedbackEnabled(): Flow<Boolean>
    suspend fun setHapticFeedbackEnabled(enabled: Boolean)

    // Lock Position
    fun isPositionLocked(): Flow<Boolean>
    suspend fun setPositionLocked(locked: Boolean)

    // Screen information
    fun getScreenWidth(): Flow<Int>
    suspend fun setScreenWidth(width: Int)

    fun getScreenHeight(): Flow<Int>
    suspend fun setScreenHeight(height: Int)

    fun getRotation(): Flow<Int>
    suspend fun setRotation(rotation: Int)
}