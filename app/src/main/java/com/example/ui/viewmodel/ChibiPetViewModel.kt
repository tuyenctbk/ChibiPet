package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.local.AiQuotaManager
import com.example.data.local.AiQuotaSnapshot
import com.example.data.local.OnboardingPreferences
import com.example.data.local.OnboardingSelection
import com.example.data.local.SavedSticker
import com.example.data.local.StickerGalleryStore
import com.example.data.model.ChatMessage
import com.example.data.model.FriendLink
import com.example.data.model.Habit
import com.example.data.model.HabitCategory
import com.example.data.model.HabitVerificationResult
import com.example.data.model.PetCheer
import com.example.data.model.PetOutfit
import com.example.data.model.PetProfile
import com.example.data.model.ShopConsumable
import com.example.data.model.SmartQuestCoachPlan
import com.example.data.model.SpeciesColor
import com.example.data.model.SuggestedQuest
import com.example.data.model.buddyCodeForUid
import com.example.data.repository.AiFeatureType
import com.example.data.repository.AiQuotaExceededException
import com.example.data.repository.ChibiPetAiRepository
import com.example.data.repository.ChibiPetRepository
import com.example.data.repository.UsageQuotaManager
import com.example.notifications.HabitReminderManager
import com.example.notifications.ReminderSettings
import com.example.widget.ChibiPetWidget
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface ProofVerificationState {
    data object Idle : ProofVerificationState
    data class Analyzing(val habit: Habit, val previewBitmap: Bitmap) : ProofVerificationState
    data class Verified(
        val habit: Habit,
        val result: HabitVerificationResult,
        val leveledUp: Boolean,
        val dailyBonusClaimed: Boolean = false,
        val coinsEarned: Long = 0L,
        val xpEarned: Long = 0L
    ) : ProofVerificationState
    data class Rejected(val habit: Habit, val result: HabitVerificationResult) : ProofVerificationState
    data class Error(
        val habit: Habit,
        val message: String,
        val isQuotaError: Boolean = false,
        val previewBitmap: Bitmap? = null
    ) : ProofVerificationState
}

sealed interface StudioArtState {
    data object Idle : StudioArtState
    data object Loading : StudioArtState
    data class Success(val bitmap: Bitmap, val promptUsed: String) : StudioArtState
    data class Error(
        val message: String,
        val isQuotaError: Boolean = false,
        val promptUsed: String = ""
    ) : StudioArtState
}

sealed interface QuestCoachUiState {
    data object Idle : QuestCoachUiState
    data object Loading : QuestCoachUiState
    data class Success(val plan: SmartQuestCoachPlan) : QuestCoachUiState
    data class Error(
        val message: String,
        val isQuotaError: Boolean = false,
        val goalPrompt: String = ""
    ) : QuestCoachUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChibiPetViewModel(
    appContext: Context,
    private val repository: ChibiPetRepository,
    private val usageQuotaManager: UsageQuotaManager,
    private val aiRepository: ChibiPetAiRepository,
    private val currentUserId: String,
    userDisplayName: String?
) : ViewModel() {

    private val appContext = appContext.applicationContext
    private val stickerStore = StickerGalleryStore(this.appContext)

    val myBuddyCode: String = buddyCodeForUid(currentUserId)

    private val _todayIsoDate = MutableStateFlow(computeTodayIsoDate())
    val todayIsoDate: StateFlow<String> = _todayIsoDate.asStateFlow()

    val aiQuotaSnapshot: StateFlow<AiQuotaSnapshot> = _todayIsoDate
        .flatMapLatest { todayIso ->
            usageQuotaManager.observeUsageQuota(currentUserId, todayIso)
        }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing Firestore usage quota; using cached snapshot", error)
            emit(usageQuotaManager.currentSnapshot)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = usageQuotaManager.currentSnapshot
        )

    private fun buildDefaultPetProfile(): PetProfile {
        val onboarding = OnboardingPreferences.read(appContext)
        val outfitKey = onboarding.preferredOutfit.key
        val defaultName = appContext.getString(R.string.default_pet_name)
        return PetProfile(
            userId = currentUserId,
            petName = onboarding.preferredPetName.ifBlank { defaultName },
            speciesColor = onboarding.preferredColor.key,
            equippedOutfit = outfitKey,
            unlockedOutfits = listOf(PetOutfit.NONE.key, outfitKey).distinct(),
            level = 1L,
            xp = 0L,
            coins = 20L,
            happiness = 85L,
            energy = 90L,
            totalCompleted = 0L,
            streakFreezes = 1L
        )
    }

    val petProfileState: StateFlow<DataUiState<PetProfile>> = repository.observePetProfile(currentUserId)
        .map<PetProfile?, DataUiState<PetProfile>> { profile ->
            DataUiState.Success(profile ?: buildDefaultPetProfile())
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing pet profile; falling back to local profile", rootError)
            emit(DataUiState.Success(buildDefaultPetProfile()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Success(buildDefaultPetProfile())
        )

    val habitsState: StateFlow<DataUiState<List<Habit>>> = repository.observeHabits(currentUserId)
        .map<List<Habit>, DataUiState<List<Habit>>> { list ->
            DataUiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing habits", rootError)
            emit(DataUiState.Success(emptyList()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Success(emptyList())
        )

    val chatMessagesState: StateFlow<DataUiState<List<ChatMessage>>> = repository.observeChatMessages(currentUserId)
        .map<List<ChatMessage>, DataUiState<List<ChatMessage>>> { list ->
            DataUiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing chat messages", rootError)
            emit(DataUiState.Success(emptyList()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Success(emptyList())
        )

    val friendsState: StateFlow<DataUiState<List<FriendLink>>> = repository.observeFriends(currentUserId)
        .map<List<FriendLink>, DataUiState<List<FriendLink>>> { list ->
            DataUiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing friends", rootError)
            emit(DataUiState.Success(emptyList()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataUiState.Success(emptyList())
        )

    val incomingCheers: StateFlow<List<PetCheer>> = repository.observeIncomingCheers(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing cheers", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val savedStickers: StateFlow<List<SavedSticker>> = stickerStore.stickersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val reminderSettings: StateFlow<ReminderSettings> = HabitReminderManager.observeSettings(this.appContext)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ReminderSettings()
        )

    private val _proofState = MutableStateFlow<ProofVerificationState>(ProofVerificationState.Idle)
    val proofState: StateFlow<ProofVerificationState> = _proofState.asStateFlow()

    private val _celebrationTick = MutableStateFlow(0)
    val celebrationTick: StateFlow<Int> = _celebrationTick.asStateFlow()

    private val _streamingPetReply = MutableStateFlow<String?>(null)
    val streamingPetReply: StateFlow<String?> = _streamingPetReply.asStateFlow()

    private val _isChatSending = MutableStateFlow(false)
    val isChatSending: StateFlow<Boolean> = _isChatSending.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _isChatQuotaExceeded = MutableStateFlow(false)
    val isChatQuotaExceeded: StateFlow<Boolean> = _isChatQuotaExceeded.asStateFlow()

    private val _lastUserChatPrompt = MutableStateFlow("")
    val lastUserChatPrompt: StateFlow<String> = _lastUserChatPrompt.asStateFlow()

    private val _studioState = MutableStateFlow<StudioArtState>(StudioArtState.Idle)
    val studioState: StateFlow<StudioArtState> = _studioState.asStateFlow()

    private val _questCoachState = MutableStateFlow<QuestCoachUiState>(QuestCoachUiState.Idle)
    val questCoachState: StateFlow<QuestCoachUiState> = _questCoachState.asStateFlow()

    private val _actionBannerMessage = MutableStateFlow<String?>(null)
    val actionBannerMessage: StateFlow<String?> = _actionBannerMessage.asStateFlow()

    private val _isFriendBusy = MutableStateFlow(false)
    val isFriendBusy: StateFlow<Boolean> = _isFriendBusy.asStateFlow()

    private var lastReconciledDate: String = ""
    private var lastSyncedCardDigest: String = ""
    private var isPurchasingOrCareBusy: Boolean = false
    private var lastSnackMillis: Long = 0L
    private var isInitializationCompleted: Boolean = false

    init {
        viewModelScope.launch {
            usageQuotaManager.ensureOrResetDailyQuota(_todayIsoDate.value)
        }

        viewModelScope.launch {
            val onboarding = OnboardingPreferences.read(this@ChibiPetViewModel.appContext)
            val chosenQuests = OnboardingSelection.STARTER_QUEST_TEMPLATES
                .filter { it.id in onboarding.selectedQuestIds }
                .map { template ->
                    val localizedTitle = runCatching {
                        this@ChibiPetViewModel.appContext.getString(template.titleRes)
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: template.title
                    Triple(localizedTitle, template.category.key, template.xpReward)
                }
            repository.ensureInitializedForUser(
                displayName = userDisplayName,
                initialPetName = onboarding.preferredPetName,
                initialColor = onboarding.preferredColor,
                initialOutfit = onboarding.preferredOutfit,
                initialQuests = chosenQuests
            ).onSuccess {
                isInitializationCompleted = true
                if (onboarding.hasCompletedOnboarding) {
                    OnboardingPreferences.markAppliedForUser(
                        this@ChibiPetViewModel.appContext,
                        currentUserId
                    )
                }
            }.onFailure { err ->
                isInitializationCompleted = true
                Log.e(TAG, "Failed to initialize user data", err)
                _actionBannerMessage.value = err.localizedMessage
                    ?: this@ChibiPetViewModel.appContext.getString(R.string.vm_err_init_profile)
            }
        }

        // Keep Home Widget, Reminder Snapshot, Buddy Card, and Daily Streak Freezes synced
        viewModelScope.launch {
            combine(petProfileState, habitsState, todayIsoDate) { profState, habState, today ->
                Triple(profState, habState, today)
            }.collect { (profState, habState, today) ->
                val profile = (profState as? DataUiState.Success)?.data ?: return@collect
                val habits = (habState as? DataUiState.Success)?.data ?: return@collect

                val completedToday = habits.count { it.isCompletedOn(today) }
                val pendingCount = (habits.size - completedToday).coerceAtLeast(0)
                val topStreak = habits.maxOfOrNull { it.streakCount } ?: 0L

                HabitReminderManager.syncHabitSnapshot(
                    context = this@ChibiPetViewModel.appContext,
                    petName = profile.petName,
                    pendingCount = pendingCount
                )
                runCatching {
                    ChibiPetWidget.pushUpdate(
                        context = this@ChibiPetViewModel.appContext,
                        petName = profile.petName,
                        level = profile.level,
                        mood = profile.happiness,
                        completedToday = completedToday,
                        totalHabits = habits.size,
                        topStreak = topStreak
                    )
                }

                val cardDigest = "${profile.petName}|${profile.speciesColor}|${profile.equippedOutfit}|${profile.level}|${profile.totalCompleted}|$topStreak"
                if (cardDigest != lastSyncedCardDigest) {
                    lastSyncedCardDigest = cardDigest
                    repository.syncMyPetCard(profile, topStreak)
                }

                if (isInitializationCompleted && lastReconciledDate != today && habits.isNotEmpty()) {
                    lastReconciledDate = today
                    repository.reconcileDailyStreaks(profile, habits, today)
                        .onSuccess { protected ->
                            if (protected.isNotEmpty()) {
                                _actionBannerMessage.value =
                                    this@ChibiPetViewModel.appContext.getString(
                                        R.string.vm_streak_freeze_saved,
                                        protected.joinToString(", ")
                                    )
                            }
                        }
                }
            }
        }
    }

    fun applyOnboardingCustomization(
        petName: String,
        speciesColor: SpeciesColor,
        outfit: PetOutfit = PetOutfit.BOW,
        selectedQuestIds: Set<String>
    ) {
        viewModelScope.launch {
            repository.updatePetAppearance(
                petName = petName,
                speciesColor = speciesColor,
                equippedOutfit = outfit
            )
            val existingHabits = (habitsState.value as? DataUiState.Success)?.data.orEmpty()
            val existingTitlesLower = existingHabits.map { it.title.lowercase(Locale.US) }.toSet()
            val templatesToAdd = OnboardingSelection.STARTER_QUEST_TEMPLATES.filter { template ->
                val localizedTitle = runCatching { appContext.getString(template.titleRes) }.getOrNull() ?: template.title
                template.id in selectedQuestIds &&
                    template.title.lowercase(Locale.US) !in existingTitlesLower &&
                    localizedTitle.lowercase(Locale.US) !in existingTitlesLower
            }
            for (template in templatesToAdd) {
                val localizedTitle = runCatching { appContext.getString(template.titleRes) }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: template.title
                repository.createHabit(localizedTitle, template.category, template.xpReward)
            }
            OnboardingPreferences.markAppliedForUser(appContext, currentUserId)
            _celebrationTick.value += 1
            _actionBannerMessage.value = appContext.getString(
                R.string.vm_welcome_ready_quests,
                petName.trim().ifEmpty { appContext.getString(R.string.default_pet_name) }
            )
        }
    }

    fun setEcoLiteMode(enabled: Boolean) {
        viewModelScope.launch {
            usageQuotaManager.setEcoLiteMode(enabled, _todayIsoDate.value)
            _actionBannerMessage.value = if (enabled) {
                appContext.getString(R.string.vm_eco_lite_on)
            } else {
                appContext.getString(R.string.vm_eco_lite_off)
            }
        }
    }

    fun claimDailyGraceCredits() {
        viewModelScope.launch {
            usageQuotaManager.claimDailyGraceCredits(_todayIsoDate.value)
                .onSuccess { claimed ->
                    _actionBannerMessage.value = if (claimed) {
                        appContext.getString(R.string.vm_grace_claimed_success)
                    } else {
                        appContext.getString(R.string.vm_grace_already_claimed)
                    }
                }
                .onFailure { err ->
                    _actionBannerMessage.value = err.localizedMessage
                        ?: appContext.getString(R.string.vm_err_claim_grace)
                }
        }
    }

    fun refreshCurrentDate() {
        val nextToday = computeTodayIsoDate()
        val previous = _todayIsoDate.value
        _todayIsoDate.value = nextToday
        if (nextToday != previous) {
            viewModelScope.launch {
                usageQuotaManager.ensureOrResetDailyQuota(nextToday)
            }
        }
    }

    fun clearActionBanner() {
        _actionBannerMessage.value = null
    }

    fun dismissChatError() {
        _chatError.value = null
        _isChatQuotaExceeded.value = false
    }

    fun dismissProofDialog() {
        _proofState.value = ProofVerificationState.Idle
    }

    fun showActionMessage(message: String) {
        _actionBannerMessage.value = message
    }

    fun onPetTapped() {
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: return
        if (profile.happiness < 100L) {
            viewModelScope.launch {
                repository.boostPetCare(profile, happinessDelta = 2L, energyDelta = 0L)
            }
        }
    }

    fun givePetSnack() {
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: return
        if (isPurchasingOrCareBusy) return
        val now = System.currentTimeMillis()
        if (now - lastSnackMillis < 8_000L) {
            _actionBannerMessage.value = appContext.getString(R.string.vm_snack_still_munching, profile.petName)
            return
        }
        if (profile.happiness >= 100L && profile.energy >= 100L) {
            _actionBannerMessage.value = appContext.getString(R.string.vm_snack_already_full, profile.petName)
            return
        }
        if (profile.coins < SNACK_COST_COINS) {
            _actionBannerMessage.value = appContext.getString(R.string.vm_snack_need_coins, SNACK_COST_COINS.toInt())
            return
        }
        viewModelScope.launch {
            isPurchasingOrCareBusy = true
            try {
                repository.boostPetCareWithSnack(
                    profile = profile,
                    coinCost = SNACK_COST_COINS,
                    happinessDelta = 8L,
                    energyDelta = 12L
                ).onSuccess {
                    lastSnackMillis = System.currentTimeMillis()
                    _celebrationTick.value += 1
                    _actionBannerMessage.value = appContext.getString(
                        R.string.vm_snack_munched_success,
                        profile.petName,
                        SNACK_COST_COINS.toInt()
                    )
                }.onFailure { err ->
                    _actionBannerMessage.value = err.localizedMessage
                        ?: appContext.getString(R.string.vm_err_give_treat)
                }
            } finally {
                isPurchasingOrCareBusy = false
            }
        }
    }

    fun purchaseConsumable(item: ShopConsumable) {
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: return
        if (isPurchasingOrCareBusy) return
        viewModelScope.launch {
            isPurchasingOrCareBusy = true
            try {
                repository.purchaseConsumable(profile, item)
                    .onSuccess {
                        _celebrationTick.value += 1
                        _actionBannerMessage.value = when (item) {
                            ShopConsumable.STREAK_FREEZE ->
                                appContext.getString(R.string.vm_bought_streak_freeze)
                            ShopConsumable.GOLDEN_ELIXIR ->
                                appContext.getString(R.string.vm_bought_golden_elixir, profile.petName)
                        }
                    }
                    .onFailure { err ->
                        _actionBannerMessage.value = err.localizedMessage
                            ?: appContext.getString(R.string.vm_err_purchase_failed)
                    }
            } finally {
                isPurchasingOrCareBusy = false
            }
        }
    }

    fun verifyHabitWithPhoto(
        habit: Habit,
        bitmap: Bitmap,
        forceLiteModel: Boolean = false
    ) {
        refreshCurrentDate()
        val currentToday = _todayIsoDate.value
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: return
        val allHabits = (habitsState.value as? DataUiState.Success)?.data.orEmpty()
        val quotaCheck = usageQuotaManager.checkQuotaLimit(
            feature = AiFeatureType.VISION,
            snapshot = aiQuotaSnapshot.value
        )
        val preferLite = forceLiteModel || quotaCheck.preferLiteModel

        viewModelScope.launch {
            _proofState.value = ProofVerificationState.Analyzing(habit, bitmap)
            var usedLite = preferLite
            val result = aiRepository.verifyHabitProof(
                bitmap = bitmap,
                habit = habit,
                petName = profile.petName,
                preferLiteModel = preferLite,
                onModelSelected = { usedLite = it }
            )
            result.fold(
                onSuccess = { verification ->
                    usageQuotaManager.recordInteraction(
                        feature = AiFeatureType.VISION,
                        usedFallbackLite = usedLite,
                        todayIsoDate = currentToday
                    )
                    if (verification.verified) {
                        val completionRes = repository.recordVerifiedHabitCompletion(
                            profile = profile,
                            habit = habit,
                            allHabits = allHabits,
                            todayIsoDate = currentToday,
                            verificationNote = verification.detectedItem
                        )
                        completionRes.fold(
                            onSuccess = { outcome ->
                                _celebrationTick.value += 1
                                _proofState.value = ProofVerificationState.Verified(
                                    habit = habit,
                                    result = verification,
                                    leveledUp = outcome.leveledUp,
                                    dailyBonusClaimed = outcome.dailyBonusClaimed,
                                    coinsEarned = outcome.coinsEarned,
                                    xpEarned = outcome.xpEarned
                                )
                                val rewardSummary = when {
                                    outcome.dailyBonusClaimed ->
                                        appContext.getString(
                                            R.string.vm_reward_summary_sweep,
                                            outcome.xpEarned.toInt(),
                                            outcome.coinsEarned.toInt()
                                        )
                                    outcome.xpEarned > 0L ->
                                        appContext.getString(
                                            R.string.vm_reward_summary_normal,
                                            outcome.xpEarned.toInt(),
                                            outcome.coinsEarned.toInt()
                                        )
                                    else ->
                                        appContext.getString(R.string.vm_reward_summary_extra, habit.title)
                                }
                                repository.addChatMessage(
                                    sender = "pet",
                                    text = "${verification.petReaction} ($rewardSummary)",
                                    habitTag = habit.category
                                )
                            },
                            onFailure = { dbErr ->
                                _proofState.value = ProofVerificationState.Error(
                                    habit = habit,
                                    message = dbErr.localizedMessage
                                        ?: appContext.getString(R.string.vm_err_save_habit_progress)
                                )
                            }
                        )
                    } else {
                        _proofState.value = ProofVerificationState.Rejected(habit, verification)
                    }
                },
                onFailure = { aiErr ->
                    val isQuota = aiErr is AiQuotaExceededException || AiQuotaManager.isQuotaOrRateLimitError(aiErr)
                    if (isQuota) {
                        usageQuotaManager.recordRateLimitHit(
                            cooldownSeconds = 20,
                            reason = "Vision free-tier rate limit reached; switched to Flash-Lite fallback.",
                            todayIsoDate = currentToday
                        )
                    }
                    _proofState.value = ProofVerificationState.Error(
                        habit = habit,
                        message = aiErr.localizedMessage
                            ?: appContext.getString(R.string.vm_err_photo_verify_failed),
                        isQuotaError = isQuota,
                        previewBitmap = bitmap
                    )
                }
            )
        }
    }

    /**
     * Honor-Pledge fallback when Cloud Vision Free-Tier Quota is exhausted so a user never loses
     * their daily habit streak due to a temporary rate limit.
     */
    fun verifyHabitByHonorPledge(habit: Habit) {
        refreshCurrentDate()
        val currentToday = _todayIsoDate.value
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: return
        val allHabits = (habitsState.value as? DataUiState.Success)?.data.orEmpty()

        viewModelScope.launch {
            val honorResult = HabitVerificationResult(
                verified = true,
                confidence = 100,
                detectedItem = appContext.getString(R.string.vm_honor_pledge_detected),
                petReaction = appContext.getString(R.string.vm_honor_pledge_reaction, habit.title)
            )
            val completionRes = repository.recordVerifiedHabitCompletion(
                profile = profile,
                habit = habit,
                allHabits = allHabits,
                todayIsoDate = currentToday,
                verificationNote = honorResult.detectedItem
            )
            completionRes.fold(
                onSuccess = { outcome ->
                    _celebrationTick.value += 1
                    _proofState.value = ProofVerificationState.Verified(
                        habit = habit,
                        result = honorResult,
                        leveledUp = outcome.leveledUp,
                        dailyBonusClaimed = outcome.dailyBonusClaimed,
                        coinsEarned = outcome.coinsEarned,
                        xpEarned = outcome.xpEarned
                    )
                    val rewardSummary = appContext.getString(
                        R.string.vm_reward_summary_normal,
                        outcome.xpEarned.toInt(),
                        outcome.coinsEarned.toInt()
                    )
                    repository.addChatMessage(
                        sender = "pet",
                        text = "${honorResult.petReaction} ($rewardSummary)",
                        habitTag = habit.category
                    )
                },
                onFailure = { dbErr ->
                    _proofState.value = ProofVerificationState.Error(
                        habit = habit,
                        message = dbErr.localizedMessage
                            ?: appContext.getString(R.string.vm_err_honor_pledge_failed)
                    )
                }
            )
        }
    }

    fun addHabit(title: String, category: HabitCategory, xpReward: Long) {
        viewModelScope.launch {
            repository.createHabit(title, category, xpReward)
                .onSuccess {
                    _actionBannerMessage.value = appContext.getString(R.string.vm_added_quest, title.trim())
                }
                .onFailure {
                    _actionBannerMessage.value = it.localizedMessage
                        ?: appContext.getString(R.string.vm_err_create_habit)
                }
        }
    }

    fun requestSmartQuestCoach(goalPrompt: String, forceLiteModel: Boolean = false) {
        refreshCurrentDate()
        val currentToday = _todayIsoDate.value
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: PetProfile()
        val existingHabits = (habitsState.value as? DataUiState.Success)?.data.orEmpty()
        val quotaCheck = usageQuotaManager.checkQuotaLimit(
            feature = AiFeatureType.CHAT,
            snapshot = aiQuotaSnapshot.value
        )
        val preferLite = forceLiteModel || quotaCheck.preferLiteModel

        viewModelScope.launch {
            _questCoachState.value = QuestCoachUiState.Loading
            var usedLite = preferLite
            val res = aiRepository.generateSmartQuestPlan(
                userGoal = goalPrompt,
                petProfile = profile,
                existingHabits = existingHabits,
                preferLiteModel = preferLite,
                onModelSelected = { usedLite = it }
            )
            res.fold(
                onSuccess = { plan ->
                    usageQuotaManager.recordInteraction(
                        feature = AiFeatureType.CHAT,
                        usedFallbackLite = usedLite,
                        todayIsoDate = currentToday
                    )
                    _questCoachState.value = QuestCoachUiState.Success(plan)
                },
                onFailure = { err ->
                    val isQuota = err is AiQuotaExceededException || AiQuotaManager.isQuotaOrRateLimitError(err)
                    if (isQuota) {
                        usageQuotaManager.recordRateLimitHit(
                            cooldownSeconds = 20,
                            reason = "Quest Coach hit cloud rate limit.",
                            todayIsoDate = currentToday
                        )
                    }
                    _questCoachState.value = QuestCoachUiState.Error(
                        message = err.localizedMessage
                            ?: appContext.getString(R.string.vm_err_quest_plan),
                        isQuotaError = isQuota,
                        goalPrompt = goalPrompt
                    )
                }
            )
        }
    }

    fun requestLocalSmartQuestCoach(goalPrompt: String) {
        requestSmartQuestCoach(goalPrompt, forceLiteModel = true)
    }

    fun addSuggestedQuests(quests: List<SuggestedQuest>) {
        if (quests.isEmpty()) return
        viewModelScope.launch {
            var addedCount = 0
            for (quest in quests) {
                val res = repository.createHabit(quest.title, quest.category, quest.xpReward)
                if (res.isSuccess) addedCount += 1
            }
            if (addedCount > 0) {
                _celebrationTick.value += 1
                _actionBannerMessage.value = appContext.getString(R.string.vm_added_coached_quests, addedCount)
            }
            _questCoachState.value = QuestCoachUiState.Idle
        }
    }

    fun resetQuestCoachState() {
        _questCoachState.value = QuestCoachUiState.Idle
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            repository.deleteHabit(habitId)
                .onFailure {
                    _actionBannerMessage.value = it.localizedMessage
                        ?: appContext.getString(R.string.vm_err_delete_habit)
                }
        }
    }

    fun updatePetNameAndColor(newName: String, color: SpeciesColor) {
        viewModelScope.launch {
            repository.updatePetAppearance(petName = newName, speciesColor = color)
                .onSuccess {
                    _actionBannerMessage.value = appContext.getString(
                        R.string.vm_updated_pet_look,
                        newName.trim().ifEmpty { appContext.getString(R.string.default_pet_name) }
                    )
                }
                .onFailure {
                    _actionBannerMessage.value = it.localizedMessage
                        ?: appContext.getString(R.string.vm_err_update_pet)
                }
        }
    }

    fun equipOrUnlockOutfit(outfit: PetOutfit) {
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: return
        if (isPurchasingOrCareBusy) return
        viewModelScope.launch {
            isPurchasingOrCareBusy = true
            try {
                repository.unlockAndEquipOutfit(profile, outfit)
                    .onSuccess {
                        _celebrationTick.value += 1
                        val outfitLabel = runCatching { appContext.getString(outfit.labelRes) }
                            .getOrNull() ?: outfit.label
                        _actionBannerMessage.value = appContext.getString(R.string.vm_equipped_outfit, outfitLabel)
                    }
                    .onFailure { err ->
                        _actionBannerMessage.value = err.localizedMessage
                            ?: appContext.getString(R.string.vm_err_equip_outfit)
                    }
            } finally {
                isPurchasingOrCareBusy = false
            }
        }
    }

    fun sendChatMessage(userText: String, forceLiteModel: Boolean = false) {
        refreshCurrentDate()
        val currentToday = _todayIsoDate.value
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _isChatSending.value) return
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: return
        val habits = (habitsState.value as? DataUiState.Success)?.data.orEmpty()
        val history = (chatMessagesState.value as? DataUiState.Success)?.data.orEmpty()
        val quotaCheck = usageQuotaManager.checkQuotaLimit(
            feature = AiFeatureType.CHAT,
            snapshot = aiQuotaSnapshot.value
        )
        val preferLite = forceLiteModel || quotaCheck.preferLiteModel

        viewModelScope.launch {
            _chatError.value = null
            _isChatQuotaExceeded.value = false
            _lastUserChatPrompt.value = trimmed
            _isChatSending.value = true
            _streamingPetReply.value = ""

            if (!forceLiteModel) {
                val saveUserMsg = repository.addChatMessage(sender = "user", text = trimmed)
                if (saveUserMsg.isFailure) {
                    _isChatSending.value = false
                    _streamingPetReply.value = null
                    _chatError.value = saveUserMsg.exceptionOrNull()?.localizedMessage
                        ?: appContext.getString(R.string.vm_err_send_message)
                    return@launch
                }
            }

            try {
                var finalReply = ""
                var usedLite = preferLite
                aiRepository.streamPetReply(
                    userMessage = trimmed,
                    history = history,
                    petProfile = profile,
                    habits = habits,
                    todayIsoDate = currentToday,
                    preferLiteModel = preferLite,
                    onModelSelected = { usedLite = it }
                ).collect { accumulated ->
                    finalReply = accumulated
                    _streamingPetReply.value = accumulated
                }

                if (finalReply.isNotBlank()) {
                    usageQuotaManager.recordInteraction(
                        feature = AiFeatureType.CHAT,
                        usedFallbackLite = usedLite,
                        todayIsoDate = currentToday
                    )
                    repository.addChatMessage(sender = "pet", text = finalReply)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Streaming chat reply failed", e)
                val isQuota = e is AiQuotaExceededException || AiQuotaManager.isQuotaOrRateLimitError(e)
                _isChatQuotaExceeded.value = isQuota
                if (isQuota) {
                    usageQuotaManager.recordRateLimitHit(
                        cooldownSeconds = 20,
                        reason = "Chat free-tier rate limit reached.",
                        todayIsoDate = currentToday
                    )
                }
                _chatError.value = e.localizedMessage
                    ?: appContext.getString(R.string.vm_err_chat_reply)
            } finally {
                _streamingPetReply.value = null
                _isChatSending.value = false
            }
        }
    }

    fun sendLocalReflexChatReply() {
        val prompt = _lastUserChatPrompt.value.trim()
        if (prompt.isNotEmpty()) {
            sendChatMessage(prompt, forceLiteModel = true)
        }
    }

    fun clearChat() {
        val messages = (chatMessagesState.value as? DataUiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            repository.clearChatHistory(messages)
                .onFailure {
                    _chatError.value = it.localizedMessage
                        ?: appContext.getString(R.string.vm_err_clear_chat)
                }
        }
    }

    fun generateStudioArt(promptText: String, referenceBitmap: Bitmap?) {
        refreshCurrentDate()
        val currentToday = _todayIsoDate.value
        val cleanPrompt = promptText.trim()
        if (cleanPrompt.isEmpty()) return
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: return
        val quotaCheck = usageQuotaManager.checkQuotaLimit(
            feature = AiFeatureType.STUDIO,
            snapshot = aiQuotaSnapshot.value
        )
        val preferLite = quotaCheck.preferLiteModel

        viewModelScope.launch {
            _studioState.value = StudioArtState.Loading
            var usedLite = preferLite
            val res = aiRepository.generateOrEditPetArt(
                promptText = cleanPrompt,
                referenceBitmap = referenceBitmap,
                petProfile = profile,
                preferLiteModel = preferLite,
                onModelSelected = { usedLite = it }
            )
            res.fold(
                onSuccess = { bmp ->
                    usageQuotaManager.recordInteraction(
                        feature = AiFeatureType.STUDIO,
                        usedFallbackLite = usedLite,
                        todayIsoDate = currentToday
                    )
                    _studioState.value = StudioArtState.Success(bmp, cleanPrompt)
                    runCatching { stickerStore.saveSticker(bmp, cleanPrompt) }
                        .onFailure { Log.w(TAG, "Could not persist sticker", it) }
                },
                onFailure = { err ->
                    val isQuota = err is AiQuotaExceededException || AiQuotaManager.isQuotaOrRateLimitError(err)
                    if (isQuota) {
                        usageQuotaManager.recordRateLimitHit(
                            cooldownSeconds = 30,
                            reason = "Studio image free-tier/billing limit reached.",
                            todayIsoDate = currentToday
                        )
                    }
                    _studioState.value = StudioArtState.Error(
                        message = err.localizedMessage
                            ?: appContext.getString(R.string.vm_err_image_gen),
                        isQuotaError = isQuota,
                        promptUsed = cleanPrompt
                    )
                }
            )
        }
    }

    fun generateOfflineCollectibleSticker(promptText: String) {
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: return
        val cleanPrompt = promptText.trim().ifEmpty { "${profile.petName} Collector Emblem" }
        viewModelScope.launch {
            _studioState.value = StudioArtState.Loading
            val bmp = withContext(Dispatchers.Default) {
                aiRepository.createOfflineCollectibleCardBitmap(profile, cleanPrompt)
            }
            _studioState.value = StudioArtState.Success(bmp, "$cleanPrompt (Collector Emblem)")
            runCatching { stickerStore.saveSticker(bmp, "$cleanPrompt (Emblem)") }
                .onFailure { Log.w(TAG, "Could not persist emblem sticker", it) }
            _celebrationTick.value += 1
            _actionBannerMessage.value = appContext.getString(R.string.vm_crafted_emblem_banner)
        }
    }

    fun resetStudioState() {
        _studioState.value = StudioArtState.Idle
    }

    suspend fun loadStickerBitmap(fileName: String): Bitmap? = stickerStore.loadBitmap(fileName)

    fun getStickerShareUri(fileName: String) = stickerStore.getShareableUri(fileName)

    fun deleteSticker(sticker: SavedSticker) {
        viewModelScope.launch {
            stickerStore.deleteSticker(sticker)
            _actionBannerMessage.value = appContext.getString(R.string.vm_removed_sticker)
        }
    }

    fun addFriend(code: String) {
        if (_isFriendBusy.value) return
        viewModelScope.launch {
            _isFriendBusy.value = true
            repository.addFriendByCode(code)
                .onSuccess { friend ->
                    _actionBannerMessage.value = appContext.getString(R.string.vm_added_friend, friend.friendPetName)
                }
                .onFailure { err ->
                    _actionBannerMessage.value = err.localizedMessage
                        ?: appContext.getString(R.string.vm_err_add_friend)
                }
            _isFriendBusy.value = false
        }
    }

    fun refreshFriends() {
        val friends = (friendsState.value as? DataUiState.Success)?.data.orEmpty()
        if (friends.isEmpty() || _isFriendBusy.value) return
        viewModelScope.launch {
            _isFriendBusy.value = true
            repository.refreshFriends(friends)
                .onSuccess {
                    _actionBannerMessage.value = appContext.getString(R.string.vm_refreshed_friends)
                }
                .onFailure {
                    _actionBannerMessage.value = it.localizedMessage
                        ?: appContext.getString(R.string.vm_err_refresh_friends)
                }
            _isFriendBusy.value = false
        }
    }

    fun removeFriend(friendCode: String) {
        viewModelScope.launch {
            repository.removeFriend(friendCode)
                .onFailure {
                    _actionBannerMessage.value = it.localizedMessage
                        ?: appContext.getString(R.string.vm_err_remove_friend)
                }
        }
    }

    fun sendCheer(friend: FriendLink, message: String) {
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.sendCheerToFriend(
                friendCode = friend.friendCode,
                friendUserId = friend.friendUserId,
                myPetName = profile.petName,
                message = message
            ).onSuccess {
                _actionBannerMessage.value = appContext.getString(R.string.vm_sent_cheer, friend.friendPetName)
            }.onFailure { err ->
                _actionBannerMessage.value = err.localizedMessage
                    ?: appContext.getString(R.string.vm_err_send_cheer)
            }
        }
    }

    fun claimCheer(cheer: PetCheer) {
        val profile = (petProfileState.value as? DataUiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.claimCheer(cheer.id, profile)
                .onSuccess {
                    _celebrationTick.value += 1
                    _actionBannerMessage.value = appContext.getString(R.string.vm_claimed_cheer, cheer.fromPetName)
                }
                .onFailure { err ->
                    _actionBannerMessage.value = err.localizedMessage
                        ?: appContext.getString(R.string.vm_err_claim_cheer)
                }
        }
    }

    fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            HabitReminderManager.setReminderEnabled(appContext, enabled, hour, minute)
            val label = ReminderSettings(enabled, hour, minute).formattedTime
            _actionBannerMessage.value = if (enabled) {
                appContext.getString(R.string.vm_reminder_set, label)
            } else {
                appContext.getString(R.string.vm_reminder_paused)
            }
        }
    }

    fun sendTestReminder() {
        val profile = (petProfileState.value as? DataUiState.Success)?.data
        val habits = (habitsState.value as? DataUiState.Success)?.data.orEmpty()
        val pending = habits.count { !it.isCompletedOn(_todayIsoDate.value) }
        val posted = HabitReminderManager.postPetReminderNotification(
            context = appContext,
            petName = profile?.petName ?: appContext.getString(R.string.default_pet_name),
            pendingCount = pending
        )
        _actionBannerMessage.value = if (posted) {
            appContext.getString(R.string.vm_test_notif_sent)
        } else {
            appContext.getString(R.string.vm_test_notif_perm_needed)
        }
    }

    private fun computeTodayIsoDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        private const val TAG = "ChibiPetVM"
        const val SNACK_COST_COINS = 5L

        fun provideFactory(
            context: Context,
            databaseId: String,
            currentUserId: String,
            userDisplayName: String?
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appCtx = context.applicationContext
                val db = FirebaseFirestore.getInstance(databaseId)
                ChibiPetViewModel(
                    appContext = appCtx,
                    repository = ChibiPetRepository(db, appCtx),
                    usageQuotaManager = UsageQuotaManager(db, appCtx),
                    aiRepository = ChibiPetAiRepository(),
                    currentUserId = currentUserId,
                    userDisplayName = userDisplayName
                )
            }
        }
    }
}
