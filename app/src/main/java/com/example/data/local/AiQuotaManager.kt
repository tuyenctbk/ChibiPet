package com.example.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AiQuotaSnapshot(
    val dateIso: String = "",
    val visionUsedToday: Int = 0,
    val chatUsedToday: Int = 0,
    val studioUsedToday: Int = 0,
    val bonusGraceCreditsToday: Int = 0,
    val graceClaimedToday: Boolean = false,
    val ecoLiteModeEnabled: Boolean = false,
    val fallbackCascadeCountToday: Int = 0,
    val cooldownUntilMillis: Long = 0L,
    val lastQuotaWarning: String? = null
) {
    val visionLimitToday: Int
        get() = RECOMMENDED_DAILY_VISION_SOFT_LIMIT + bonusGraceCreditsToday

    val chatLimitToday: Int
        get() = RECOMMENDED_DAILY_CHAT_SOFT_LIMIT + (bonusGraceCreditsToday * 2)

    val studioLimitToday: Int
        get() = RECOMMENDED_DAILY_STUDIO_SOFT_LIMIT + bonusGraceCreditsToday

    val totalRequestsToday: Int
        get() = visionUsedToday + chatUsedToday + studioUsedToday

    val totalSoftLimitToday: Int
        get() = visionLimitToday + chatLimitToday + studioLimitToday

    val usageFraction: Float
        get() = (totalRequestsToday.toFloat() / totalSoftLimitToday.coerceAtLeast(1).toFloat())
            .coerceIn(0f, 1f)

    /** 80% Soft Nudge rule from AudioBrain: warn gently before hitting 100% */
    val isNearLimitSoftNudge: Boolean
        get() = usageFraction >= 0.80f && usageFraction < 1.0f

    val isAtOrOverSoftLimit: Boolean
        get() = usageFraction >= 1.0f

    val shouldAutoPreferLiteModel: Boolean
        get() = ecoLiteModeEnabled || isNearLimitSoftNudge || isAtOrOverSoftLimit || fallbackCascadeCountToday > 0

    /** Reset countdown until midnight local time */
    val resetCountdownHoursAndMinutes: Pair<Long, Long>
        get() {
            val now = Calendar.getInstance()
            val midnight = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffMinutes = ((midnight.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(1L)
            return (diffMinutes / 60L) to (diffMinutes % 60L)
        }

    val resetCountdownLabel: String
        get() {
            val (hours, mins) = resetCountdownHoursAndMinutes
            return "Resets in ${hours}h ${mins}m"
        }

    fun formatResetCountdown(context: Context): String {
        val (hours, mins) = resetCountdownHoursAndMinutes
        return context.getString(com.example.R.string.studio_quota_reset_countdown, hours.toInt(), mins.toInt())
    }

    /** Value Receipt summary showing user value delivered today */
    val valueReceiptSummary: String
        get() = "Today's AI Value Receipt: $visionUsedToday habit proof(s) verified • $chatUsedToday companion chat(s) • $studioUsedToday studio creation(s)"

    fun formatValueReceipt(context: Context): String =
        context.getString(
            com.example.R.string.studio_quota_value_receipt,
            visionUsedToday,
            chatUsedToday,
            studioUsedToday
        )

    fun isCoolingDown(nowMillis: Long = System.currentTimeMillis()): Boolean =
        cooldownUntilMillis > nowMillis

    fun remainingCooldownSeconds(nowMillis: Long = System.currentTimeMillis()): Int =
        ((cooldownUntilMillis - nowMillis) / 1000L).toInt().coerceAtLeast(0)

    companion object {
        const val RECOMMENDED_DAILY_VISION_SOFT_LIMIT = 15
        const val RECOMMENDED_DAILY_CHAT_SOFT_LIMIT = 30
        const val RECOMMENDED_DAILY_STUDIO_SOFT_LIMIT = 6
    }
}

class AiQuotaManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _snapshot = MutableStateFlow(loadSnapshot())
    val snapshotFlow: StateFlow<AiQuotaSnapshot> = _snapshot.asStateFlow()

    val currentSnapshot: AiQuotaSnapshot
        get() {
            ensureTodayBucket()
            return _snapshot.value
        }

    fun setEcoLiteMode(enabled: Boolean) {
        ensureTodayBucket()
        prefs.edit().putBoolean(KEY_ECO_LITE_MODE, enabled).apply()
        _snapshot.value = loadSnapshot()
    }

    /** One-Time Daily Grace Credits (+3 Vision/Studio & +6 Chat) inspired by AudioBrain */
    fun claimDailyGraceCredits(): Boolean {
        ensureTodayBucket()
        if (prefs.getBoolean(KEY_GRACE_CLAIMED, false)) return false
        val nextBonus = prefs.getInt(KEY_BONUS_CREDITS, 0) + 3
        prefs.edit()
            .putBoolean(KEY_GRACE_CLAIMED, true)
            .putInt(KEY_BONUS_CREDITS, nextBonus)
            .putLong(KEY_COOLDOWN_UNTIL, 0L)
            .remove(KEY_LAST_WARNING)
            .apply()
        _snapshot.value = loadSnapshot()
        return true
    }

    fun recordVisionCall(usedFallbackLite: Boolean = false) {
        ensureTodayBucket()
        val nextCount = prefs.getInt(KEY_VISION_USED, 0) + 1
        val nextCascade = prefs.getInt(KEY_CASCADE_COUNT, 0) + if (usedFallbackLite) 1 else 0
        prefs.edit()
            .putInt(KEY_VISION_USED, nextCount)
            .putInt(KEY_CASCADE_COUNT, nextCascade)
            .apply()
        _snapshot.value = loadSnapshot()
    }

    fun recordChatCall(usedFallbackLite: Boolean = false) {
        ensureTodayBucket()
        val nextCount = prefs.getInt(KEY_CHAT_USED, 0) + 1
        val nextCascade = prefs.getInt(KEY_CASCADE_COUNT, 0) + if (usedFallbackLite) 1 else 0
        prefs.edit()
            .putInt(KEY_CHAT_USED, nextCount)
            .putInt(KEY_CASCADE_COUNT, nextCascade)
            .apply()
        _snapshot.value = loadSnapshot()
    }

    fun recordStudioCall(usedFallbackLite: Boolean = false) {
        ensureTodayBucket()
        val nextCount = prefs.getInt(KEY_STUDIO_USED, 0) + 1
        val nextCascade = prefs.getInt(KEY_CASCADE_COUNT, 0) + if (usedFallbackLite) 1 else 0
        prefs.edit()
            .putInt(KEY_STUDIO_USED, nextCount)
            .putInt(KEY_CASCADE_COUNT, nextCascade)
            .apply()
        _snapshot.value = loadSnapshot()
    }

    fun recordRateLimitHit(cooldownSeconds: Int = 25, reason: String) {
        ensureTodayBucket()
        val until = System.currentTimeMillis() + cooldownSeconds * 1000L
        val nextCascade = prefs.getInt(KEY_CASCADE_COUNT, 0) + 1
        prefs.edit()
            .putLong(KEY_COOLDOWN_UNTIL, until)
            .putInt(KEY_CASCADE_COUNT, nextCascade)
            .putString(KEY_LAST_WARNING, reason)
            .apply()
        _snapshot.value = loadSnapshot()
    }

    fun clearQuotaWarning() {
        prefs.edit().remove(KEY_LAST_WARNING).apply()
        _snapshot.value = loadSnapshot()
    }

    private fun ensureTodayBucket() {
        val today = computeTodayIso()
        val savedDate = prefs.getString(KEY_DATE_ISO, "") ?: ""
        if (savedDate != today) {
            prefs.edit()
                .putString(KEY_DATE_ISO, today)
                .putInt(KEY_VISION_USED, 0)
                .putInt(KEY_CHAT_USED, 0)
                .putInt(KEY_STUDIO_USED, 0)
                .putInt(KEY_BONUS_CREDITS, 0)
                .putBoolean(KEY_GRACE_CLAIMED, false)
                .putInt(KEY_CASCADE_COUNT, 0)
                .putLong(KEY_COOLDOWN_UNTIL, 0L)
                .remove(KEY_LAST_WARNING)
                .apply()
            _snapshot.value = loadSnapshot()
        }
    }

    private fun loadSnapshot(): AiQuotaSnapshot {
        val today = computeTodayIso()
        val savedDate = prefs.getString(KEY_DATE_ISO, today) ?: today
        return if (savedDate != today) {
            AiQuotaSnapshot(
                dateIso = today,
                ecoLiteModeEnabled = prefs.getBoolean(KEY_ECO_LITE_MODE, false)
            )
        } else {
            AiQuotaSnapshot(
                dateIso = savedDate,
                visionUsedToday = prefs.getInt(KEY_VISION_USED, 0),
                chatUsedToday = prefs.getInt(KEY_CHAT_USED, 0),
                studioUsedToday = prefs.getInt(KEY_STUDIO_USED, 0),
                bonusGraceCreditsToday = prefs.getInt(KEY_BONUS_CREDITS, 0),
                graceClaimedToday = prefs.getBoolean(KEY_GRACE_CLAIMED, false),
                ecoLiteModeEnabled = prefs.getBoolean(KEY_ECO_LITE_MODE, false),
                fallbackCascadeCountToday = prefs.getInt(KEY_CASCADE_COUNT, 0),
                cooldownUntilMillis = prefs.getLong(KEY_COOLDOWN_UNTIL, 0L),
                lastQuotaWarning = prefs.getString(KEY_LAST_WARNING, null)
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "chibipet_ai_quota_prefs"
        private const val KEY_DATE_ISO = "quota_date_iso"
        private const val KEY_VISION_USED = "vision_used_today"
        private const val KEY_CHAT_USED = "chat_used_today"
        private const val KEY_STUDIO_USED = "studio_used_today"
        private const val KEY_BONUS_CREDITS = "bonus_grace_credits_today"
        private const val KEY_GRACE_CLAIMED = "grace_claimed_today"
        private const val KEY_ECO_LITE_MODE = "eco_lite_mode"
        private const val KEY_CASCADE_COUNT = "cascade_count_today"
        private const val KEY_COOLDOWN_UNTIL = "cooldown_until_millis"
        private const val KEY_LAST_WARNING = "last_quota_warning"

        fun isQuotaOrRateLimitError(throwable: Throwable?): Boolean {
            var curr = throwable
            while (curr != null) {
                val msg = (curr.message ?: "") + " " + curr.javaClass.simpleName
                val lower = msg.lowercase(Locale.US)
                if (
                    "429" in lower ||
                    "resource_exhausted" in lower ||
                    "quota" in lower ||
                    "rate limit" in lower ||
                    "too many requests" in lower ||
                    "overloaded" in lower ||
                    "503" in lower ||
                    "billing" in lower
                ) {
                    return true
                }
                curr = curr.cause
            }
            return false
        }

        private fun computeTodayIso(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
