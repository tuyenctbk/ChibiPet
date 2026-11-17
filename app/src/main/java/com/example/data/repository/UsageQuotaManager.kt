package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.AiQuotaManager
import com.example.data.local.AiQuotaSnapshot
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AiFeatureType(val key: String, val displayLabel: String) {
    VISION("vision", "Vision Habit Check"),
    CHAT("chat", "Companion Chat"),
    STUDIO("studio", "Nano Banana Studio")
}

data class QuotaCheckResult(
    val allowed: Boolean,
    val preferLiteModel: Boolean,
    val isNearLimitSoftNudge: Boolean,
    val isAtOrOverSoftLimit: Boolean,
    val remainingForFeature: Int,
    val limitForFeature: Int,
    val snapshot: AiQuotaSnapshot,
    val message: String? = null
)

@IgnoreExtraProperties
data class UsageQuotaDocument(
    @DocumentId val id: String = DAILY_DOC_ID,
    val userId: String = "",
    val dateIso: String = "",
    val visionUsed: Long = 0L,
    val chatUsed: Long = 0L,
    val studioUsed: Long = 0L,
    val bonusGraceCredits: Long = 0L,
    val graceClaimed: Boolean = false,
    val ecoLiteMode: Boolean = false,
    val fallbackCascadeCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toSnapshot(
        activeTodayIso: String,
        cooldownUntilMillis: Long = 0L,
        lastQuotaWarning: String? = null
    ): AiQuotaSnapshot {
        val isSameDay = dateIso == activeTodayIso
        return if (isSameDay) {
            AiQuotaSnapshot(
                dateIso = dateIso,
                visionUsedToday = visionUsed.toInt(),
                chatUsedToday = chatUsed.toInt(),
                studioUsedToday = studioUsed.toInt(),
                bonusGraceCreditsToday = bonusGraceCredits.toInt(),
                graceClaimedToday = graceClaimed,
                ecoLiteModeEnabled = ecoLiteMode,
                fallbackCascadeCountToday = fallbackCascadeCount.toInt(),
                cooldownUntilMillis = cooldownUntilMillis,
                lastQuotaWarning = lastQuotaWarning
            )
        } else {
            // Automatic daily quota reset view when the calendar date rolls over
            AiQuotaSnapshot(
                dateIso = activeTodayIso,
                visionUsedToday = 0,
                chatUsedToday = 0,
                studioUsedToday = 0,
                bonusGraceCreditsToday = 0,
                graceClaimedToday = false,
                ecoLiteModeEnabled = ecoLiteMode,
                fallbackCascadeCountToday = 0,
                cooldownUntilMillis = cooldownUntilMillis,
                lastQuotaWarning = lastQuotaWarning
            )
        }
    }

    companion object {
        const val DAILY_DOC_ID = "daily"
    }
}

/**
 * Firestore-backed manager that tracks daily AI interactions (`/users/{userId}/usageQuota/daily`),
 * evaluates per-feature soft/hard limits, handles automatic daily quota resets on date rollover,
 * supports atomic deduct-with-refund, and manages one-time daily `+3 Grace Credits`.
 */
class UsageQuotaManager(
    private val db: FirebaseFirestore,
    context: Context? = null
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        context.applicationContext
    )

    private val auth = Firebase.auth
    private val localMirror: AiQuotaManager? = context?.applicationContext?.let { AiQuotaManager(it) }

    private val _cooldownUntilMillis = MutableStateFlow(0L)
    private val _lastQuotaWarning = MutableStateFlow<String?>(null)
    private val _latestSnapshotCache = MutableStateFlow(
        localMirror?.currentSnapshot ?: AiQuotaSnapshot(dateIso = computeTodayIso())
    )

    val currentSnapshot: AiQuotaSnapshot
        get() = _latestSnapshotCache.value

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore usage quota.")
    }

    private fun quotaDocRef(userId: String) =
        db.collection("users").document(userId)
            .collection("usageQuota").document(UsageQuotaDocument.DAILY_DOC_ID)

    /**
     * Real-time Firestore listener for `/users/{userId}/usageQuota/daily`.
     * Automatically projects a fresh 0-usage snapshot if `dateIso != todayIsoDate`.
     */
    fun observeUsageQuota(
        userId: String,
        todayIsoDate: String = computeTodayIso()
    ): Flow<AiQuotaSnapshot> = flow {
        val fbUid = auth.currentUser?.uid
        if (fbUid.isNullOrBlank() || fbUid != userId) {
            emitAll(
                combine(
                    _latestSnapshotCache,
                    _cooldownUntilMillis,
                    _lastQuotaWarning
                ) { cached, cooldownUntil, warning ->
                    cached.copy(
                        dateIso = todayIsoDate,
                        cooldownUntilMillis = cooldownUntil,
                        lastQuotaWarning = warning
                    )
                }
            )
            return@flow
        }
        val docRef = quotaDocRef(userId)
        val firestoreFlow = docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UsageQuotaDocument::class.java, ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                }
                throw error
            }

        emitAll(
            combine(
                firestoreFlow,
                _cooldownUntilMillis,
                _lastQuotaWarning
            ) { doc, cooldownUntil, warning ->
                val resolved = doc?.toSnapshot(
                    activeTodayIso = todayIsoDate,
                    cooldownUntilMillis = cooldownUntil,
                    lastQuotaWarning = warning
                ) ?: AiQuotaSnapshot(
                    dateIso = todayIsoDate,
                    ecoLiteModeEnabled = localMirror?.currentSnapshot?.ecoLiteModeEnabled ?: false,
                    cooldownUntilMillis = cooldownUntil,
                    lastQuotaWarning = warning
                )
                _latestSnapshotCache.value = resolved
                resolved
            }
        )
    }

    /**
     * Ensures the daily Firestore quota ledger exists for [todayIsoDate], or resets the daily
     * counters (`visionUsed`, `chatUsed`, `studioUsed`, `bonusGraceCredits`, `graceClaimed`,
     * `fallbackCascadeCount`) to 0 when the stored `dateIso` is from a previous day.
     */
    suspend fun ensureOrResetDailyQuota(
        todayIsoDate: String = computeTodayIso()
    ): Result<AiQuotaSnapshot> {
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: return Result.success(_latestSnapshotCache.value.copy(dateIso = todayIsoDate))
        val docRef = quotaDocRef(uid)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val initialEco = localMirror?.currentSnapshot?.ecoLiteModeEnabled ?: false
                val payload = mapOf(
                    "userId" to uid,
                    "dateIso" to todayIsoDate,
                    "visionUsed" to 0L,
                    "chatUsed" to 0L,
                    "studioUsed" to 0L,
                    "bonusGraceCredits" to 0L,
                    "graceClaimed" to false,
                    "ecoLiteMode" to initialEco,
                    "fallbackCascadeCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                val fresh = AiQuotaSnapshot(
                    dateIso = todayIsoDate,
                    ecoLiteModeEnabled = initialEco,
                    cooldownUntilMillis = _cooldownUntilMillis.value,
                    lastQuotaWarning = _lastQuotaWarning.value
                )
                _latestSnapshotCache.value = fresh
                Result.success(fresh)
            } else {
                val existing = snap.toObject(UsageQuotaDocument::class.java, ServerTimestampBehavior.ESTIMATE)
                    ?: UsageQuotaDocument(userId = uid, dateIso = todayIsoDate)
                if (existing.dateIso != todayIsoDate) {
                    val resetUpdates = mapOf(
                        "dateIso" to todayIsoDate,
                        "visionUsed" to 0L,
                        "chatUsed" to 0L,
                        "studioUsed" to 0L,
                        "bonusGraceCredits" to 0L,
                        "graceClaimed" to false,
                        "fallbackCascadeCount" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.update(resetUpdates).await()
                    _cooldownUntilMillis.value = 0L
                    _lastQuotaWarning.value = null
                    val resetSnapshot = AiQuotaSnapshot(
                        dateIso = todayIsoDate,
                        visionUsedToday = 0,
                        chatUsedToday = 0,
                        studioUsedToday = 0,
                        bonusGraceCreditsToday = 0,
                        graceClaimedToday = false,
                        ecoLiteModeEnabled = existing.ecoLiteMode,
                        fallbackCascadeCountToday = 0
                    )
                    _latestSnapshotCache.value = resetSnapshot
                    Result.success(resetSnapshot)
                } else {
                    val current = existing.toSnapshot(
                        activeTodayIso = todayIsoDate,
                        cooldownUntilMillis = _cooldownUntilMillis.value,
                        lastQuotaWarning = _lastQuotaWarning.value
                    )
                    _latestSnapshotCache.value = current
                    Result.success(current)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Evaluates whether the user is within their daily quota for [feature], computes remaining
     * credits and 80% soft-nudge status, and indicates whether the call should prefer Flash-Lite.
     */
    fun checkQuotaLimit(
        feature: AiFeatureType,
        snapshot: AiQuotaSnapshot = _latestSnapshotCache.value,
        enforceHardCap: Boolean = false
    ): QuotaCheckResult {
        val (used, limit) = when (feature) {
            AiFeatureType.VISION -> snapshot.visionUsedToday to snapshot.visionLimitToday
            AiFeatureType.CHAT -> snapshot.chatUsedToday to snapshot.chatLimitToday
            AiFeatureType.STUDIO -> snapshot.studioUsedToday to snapshot.studioLimitToday
        }
        val remaining = (limit - used).coerceAtLeast(0)
        val featureRatio = used.toFloat() / limit.coerceAtLeast(1).toFloat()
        val isNearLimit = featureRatio >= 0.80f && used < limit
        val isAtOrOver = used >= limit
        val preferLite = snapshot.shouldAutoPreferLiteModel || isNearLimit || isAtOrOver
        val allowed = if (enforceHardCap) !isAtOrOver else true

        val statusMessage = when {
            isAtOrOver && !snapshot.graceClaimedToday ->
                "${feature.displayLabel} daily allowance ($used/$limit) reached — switched to Eco Flash-Lite or claim +3 Grace Credits!"
            isAtOrOver ->
                "${feature.displayLabel} daily allowance ($used/$limit) reached — running in Eco Flash-Lite mode until midnight reset."
            isNearLimit ->
                "$remaining ${feature.displayLabel} credit(s) left today (${snapshot.resetCountdownLabel})."
            else -> null
        }

        return QuotaCheckResult(
            allowed = allowed,
            preferLiteModel = preferLite,
            isNearLimitSoftNudge = isNearLimit || snapshot.isNearLimitSoftNudge,
            isAtOrOverSoftLimit = isAtOrOver || snapshot.isAtOrOverSoftLimit,
            remainingForFeature = remaining,
            limitForFeature = limit,
            snapshot = snapshot,
            message = statusMessage
        )
    }

    /**
     * Checks daily rollover first, validates limits (when [enforceHardCap] is true), and
     * increments the feature usage counter in Firestore at `/users/{uid}/usageQuota/daily`.
     */
    suspend fun recordInteraction(
        feature: AiFeatureType,
        usedFallbackLite: Boolean = false,
        todayIsoDate: String = computeTodayIso(),
        enforceHardCap: Boolean = false
    ): Result<AiQuotaSnapshot> {
        val baseSnapshot = ensureOrResetDailyQuota(todayIsoDate)
            .getOrElse { _latestSnapshotCache.value.copy(dateIso = todayIsoDate) }
        val check = checkQuotaLimit(feature, baseSnapshot, enforceHardCap = enforceHardCap)
        if (!check.allowed) {
            return Result.failure(
                AiQuotaExceededException(
                    check.message ?: "${feature.displayLabel} daily limit reached."
                )
            )
        }

        val nextVision = baseSnapshot.visionUsedToday + if (feature == AiFeatureType.VISION) 1 else 0
        val nextChat = baseSnapshot.chatUsedToday + if (feature == AiFeatureType.CHAT) 1 else 0
        val nextStudio = baseSnapshot.studioUsedToday + if (feature == AiFeatureType.STUDIO) 1 else 0
        val nextCascade = baseSnapshot.fallbackCascadeCountToday + if (usedFallbackLite) 1 else 0

        when (feature) {
            AiFeatureType.VISION -> localMirror?.recordVisionCall(usedFallbackLite)
            AiFeatureType.CHAT -> localMirror?.recordChatCall(usedFallbackLite)
            AiFeatureType.STUDIO -> localMirror?.recordStudioCall(usedFallbackLite)
        }

        val updated = baseSnapshot.copy(
            dateIso = todayIsoDate,
            visionUsedToday = nextVision,
            chatUsedToday = nextChat,
            studioUsedToday = nextStudio,
            fallbackCascadeCountToday = nextCascade
        )
        _latestSnapshotCache.value = updated

        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: return Result.success(updated)
        val docRef = quotaDocRef(uid)
        return try {
            val updates = mutableMapOf<String, Any>(
                "dateIso" to todayIsoDate,
                "visionUsed" to nextVision.toLong(),
                "chatUsed" to nextChat.toLong(),
                "studioUsed" to nextStudio.toLong(),
                "fallbackCascadeCount" to nextCascade.toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is AiQuotaExceededException) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            }
            Result.failure(e)
        }
    }

    /**
     * Refunds a previously recorded interaction credit for [feature] if a downstream operation
     * fails or is cancelled, ensuring users never lose daily quota to transient errors.
     */
    suspend fun refundInteraction(
        feature: AiFeatureType,
        todayIsoDate: String = computeTodayIso()
    ): Result<AiQuotaSnapshot> {
        val baseSnapshot = ensureOrResetDailyQuota(todayIsoDate)
            .getOrElse { _latestSnapshotCache.value.copy(dateIso = todayIsoDate) }
        val nextVision = (baseSnapshot.visionUsedToday - if (feature == AiFeatureType.VISION) 1 else 0).coerceAtLeast(0)
        val nextChat = (baseSnapshot.chatUsedToday - if (feature == AiFeatureType.CHAT) 1 else 0).coerceAtLeast(0)
        val nextStudio = (baseSnapshot.studioUsedToday - if (feature == AiFeatureType.STUDIO) 1 else 0).coerceAtLeast(0)
        val updated = baseSnapshot.copy(
            visionUsedToday = nextVision,
            chatUsedToday = nextChat,
            studioUsedToday = nextStudio
        )
        _latestSnapshotCache.value = updated

        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: return Result.success(updated)
        val docRef = quotaDocRef(uid)
        return try {
            docRef.update(
                mapOf(
                    "visionUsed" to nextVision.toLong(),
                    "chatUsed" to nextChat.toLong(),
                    "studioUsed" to nextStudio.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Grants the one-time daily `+3 Grace Credits` boost in Firestore for [todayIsoDate].
     * Returns `Result.success(true)` if newly claimed today, or `Result.success(false)` if already claimed.
     */
    suspend fun claimDailyGraceCredits(
        todayIsoDate: String = computeTodayIso()
    ): Result<Boolean> {
        val baseSnapshot = ensureOrResetDailyQuota(todayIsoDate)
            .getOrElse { _latestSnapshotCache.value.copy(dateIso = todayIsoDate) }
        if (baseSnapshot.graceClaimedToday) {
            return Result.success(false)
        }
        val nextBonus = (baseSnapshot.bonusGraceCreditsToday + GRACE_CREDIT_INCREMENT).coerceAtMost(100)
        _cooldownUntilMillis.value = 0L
        _lastQuotaWarning.value = null
        localMirror?.claimDailyGraceCredits()
        _latestSnapshotCache.value = baseSnapshot.copy(
            bonusGraceCreditsToday = nextBonus,
            graceClaimedToday = true,
            cooldownUntilMillis = 0L,
            lastQuotaWarning = null
        )

        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: return Result.success(true)
        val docRef = quotaDocRef(uid)
        return try {
            docRef.update(
                mapOf(
                    "bonusGraceCredits" to nextBonus.toLong(),
                    "graceClaimed" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Updates the user's Eco Lite Mode preference in Firestore and mirrors locally.
     */
    suspend fun setEcoLiteMode(
        enabled: Boolean,
        todayIsoDate: String = computeTodayIso()
    ): Result<AiQuotaSnapshot> {
        val baseSnapshot = ensureOrResetDailyQuota(todayIsoDate)
            .getOrElse { _latestSnapshotCache.value.copy(dateIso = todayIsoDate) }
        localMirror?.setEcoLiteMode(enabled)
        val updated = baseSnapshot.copy(ecoLiteModeEnabled = enabled)
        _latestSnapshotCache.value = updated

        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: return Result.success(updated)
        val docRef = quotaDocRef(uid)
        return try {
            docRef.update(
                mapOf(
                    "ecoLiteMode" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Records a temporary rate-limit cooldown and increments `fallbackCascadeCount` in Firestore.
     */
    suspend fun recordRateLimitHit(
        cooldownSeconds: Int = 25,
        reason: String,
        todayIsoDate: String = computeTodayIso()
    ): Result<AiQuotaSnapshot> {
        val until = System.currentTimeMillis() + cooldownSeconds * 1000L
        _cooldownUntilMillis.value = until
        _lastQuotaWarning.value = reason
        localMirror?.recordRateLimitHit(cooldownSeconds, reason)

        val uid = auth.currentUser?.uid ?: return Result.success(_latestSnapshotCache.value)
        val docRef = quotaDocRef(uid)
        return try {
            val baseSnapshot = ensureOrResetDailyQuota(todayIsoDate).getOrThrow()
            val nextCascade = (baseSnapshot.fallbackCascadeCountToday + 1).coerceAtMost(10000)
            docRef.update(
                mapOf(
                    "fallbackCascadeCount" to nextCascade.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val updated = baseSnapshot.copy(
                fallbackCascadeCountToday = nextCascade,
                cooldownUntilMillis = until,
                lastQuotaWarning = reason
            )
            _latestSnapshotCache.value = updated
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun clearQuotaWarning() {
        _lastQuotaWarning.value = null
        localMirror?.clearQuotaWarning()
        _latestSnapshotCache.value = _latestSnapshotCache.value.copy(lastQuotaWarning = null)
    }

    companion object {
        const val GRACE_CREDIT_INCREMENT = 3

        fun computeTodayIso(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
