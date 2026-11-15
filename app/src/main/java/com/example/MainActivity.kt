package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppLanguageManager
import com.example.data.local.OnboardingPreferences
import com.example.firebase.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.SplashOverlay
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.FriendsScreen
import com.example.ui.screens.HomePetScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.WardrobeAndStudioScreen
import com.example.ui.theme.ChibiPetTheme
import com.example.ui.viewmodel.ChibiPetViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

private enum class ChibiTab(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    PET(R.string.tab_pet, Icons.Rounded.Pets, Icons.Outlined.Pets, "tab_pet"),
    CHAT(R.string.tab_chat, Icons.Rounded.Forum, Icons.Outlined.Forum, "tab_chat"),
    FRIENDS(R.string.tab_friends, Icons.Rounded.Groups, Icons.Outlined.Groups, "tab_friends"),
    STUDIO(R.string.tab_shop, Icons.Rounded.Checkroom, Icons.Outlined.Checkroom, "tab_studio")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any Compose UI or ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ChibiPetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChibiPetAppRoot()
                }
            }
        }
    }
}

@Composable
private fun ChibiPetAppRoot() {
    val baseContext = LocalContext.current
    val selectedLanguageTag by AppLanguageManager.observeLanguageTag(baseContext).collectAsStateWithLifecycle()
    val localizedContext = remember(baseContext, selectedLanguageTag) {
        AppLanguageManager.createLocalizedContext(baseContext, selectedLanguageTag)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration
    ) {
        val context = LocalContext.current
        var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
        var isGuestMode by rememberSaveable { mutableStateOf(false) }
        var showSplash by rememberSaveable { mutableStateOf(true) }
        val onboardingSelection by OnboardingPreferences.observe(context).collectAsStateWithLifecycle()
        var onboardingApplyNonce by rememberSaveable { mutableIntStateOf(0) }

        DisposableEffect(Unit) {
            val listener = FirebaseAuth.AuthStateListener { auth ->
                currentUser = auth.currentUser
            }
            Firebase.auth.addAuthStateListener(listener)
            onDispose {
                Firebase.auth.removeAuthStateListener(listener)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val user = currentUser
            if (!onboardingSelection.hasCompletedOnboarding) {
                OnboardingScreen(
                    initialSelection = onboardingSelection,
                    onFinishOnboarding = { petName, speciesColor, outfit, selectedQuestIds ->
                        OnboardingPreferences.saveOnboardingComplete(
                            context = context,
                            petName = petName,
                            speciesColor = speciesColor,
                            outfit = outfit,
                            selectedQuestIds = selectedQuestIds
                        )
                        onboardingApplyNonce += 1
                    }
                )
            } else if (user == null && !isGuestMode) {
                AuthScreen(
                    preferredPetName = onboardingSelection.preferredPetName,
                    onReopenOnboarding = {
                        OnboardingPreferences.reopenOnboarding(context)
                    },
                    onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                    onContinueAsGuest = { isGuestMode = true }
                )
            } else {
                val effectiveUid = user?.uid ?: "guest_explorer_local"
                val effectiveName = user?.displayName ?: stringResource(R.string.auth_guest_explorer_name)
                key(effectiveUid) {
                    AuthenticatedChibiPetApp(
                        currentUserId = effectiveUid,
                        userDisplayName = effectiveName,
                        userEmail = user?.email,
                        onboardingApplyNonce = onboardingApplyNonce,
                        onReplayOnboarding = {
                            isGuestMode = false
                            OnboardingPreferences.reopenOnboarding(context)
                        }
                    )
                }
            }

            SplashOverlay(
                visible = showSplash,
                onFinished = { showSplash = false }
            )
        }
    }
}

@Composable
private fun AuthenticatedChibiPetApp(
    currentUserId: String,
    userDisplayName: String?,
    userEmail: String?,
    onboardingApplyNonce: Int,
    onReplayOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val databaseId = remember(context) {
        context.applicationContext.getString(R.string.firestore_database_id)
    }
    val viewModel: ChibiPetViewModel = viewModel(
        key = currentUserId,
        factory = ChibiPetViewModel.provideFactory(
            context = context,
            databaseId = databaseId,
            currentUserId = currentUserId,
            userDisplayName = userDisplayName
        )
    )

    LaunchedEffect(onboardingApplyNonce) {
        if (onboardingApplyNonce > 0) {
            val selection = OnboardingPreferences.read(context)
            viewModel.applyOnboardingCustomization(
                petName = selection.preferredPetName,
                speciesColor = selection.preferredColor,
                selectedQuestIds = selection.selectedQuestIds
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCurrentDate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val petProfileState by viewModel.petProfileState.collectAsStateWithLifecycle()
    val habitsState by viewModel.habitsState.collectAsStateWithLifecycle()
    val chatMessagesState by viewModel.chatMessagesState.collectAsStateWithLifecycle()
    val friendsState by viewModel.friendsState.collectAsStateWithLifecycle()
    val incomingCheers by viewModel.incomingCheers.collectAsStateWithLifecycle()
    val savedStickers by viewModel.savedStickers.collectAsStateWithLifecycle()
    val reminderSettings by viewModel.reminderSettings.collectAsStateWithLifecycle()
    val aiQuotaSnapshot by viewModel.aiQuotaSnapshot.collectAsStateWithLifecycle()
    val proofState by viewModel.proofState.collectAsStateWithLifecycle()
    val celebrationTick by viewModel.celebrationTick.collectAsStateWithLifecycle()
    val todayIsoDate by viewModel.todayIsoDate.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingPetReply.collectAsStateWithLifecycle()
    val isChatSending by viewModel.isChatSending.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val questCoachState by viewModel.questCoachState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.actionBannerMessage.collectAsStateWithLifecycle()
    val isFriendBusy by viewModel.isFriendBusy.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(ChibiTab.PET) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionBanner()
        }
    }

    if (selectedTab != ChibiTab.PET) {
        BackHandler {
            selectedTab = ChibiTab.PET
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        ChibiTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            val tabLabel = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tabLabel
                                    )
                                },
                                label = { Text(tabLabel) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail {
                        ChibiTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            val tabLabel = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tabLabel
                                    )
                                },
                                label = { Text(tabLabel) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                    },
                    label = "tab_content",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        ChibiTab.PET -> HomePetScreen(
                            petProfileState = petProfileState,
                            habitsState = habitsState,
                            incomingCheers = incomingCheers,
                            proofState = proofState,
                            questCoachState = questCoachState,
                            celebrationTick = celebrationTick,
                            todayIsoDate = todayIsoDate,
                            onPetTapped = viewModel::onPetTapped,
                            onGiveSnack = viewModel::givePetSnack,
                            onClaimCheer = viewModel::claimCheer,
                            onVerifyHabitPhoto = { habit, bmp ->
                                viewModel.verifyHabitWithPhoto(habit, bmp, forceLiteModel = false)
                            },
                            onRetryWithLiteModel = { habit, bmp ->
                                viewModel.verifyHabitWithPhoto(habit, bmp, forceLiteModel = true)
                            },
                            onHonorPledgeCheckIn = viewModel::verifyHabitByHonorPledge,
                            onDismissProofDialog = viewModel::dismissProofDialog,
                            onAddHabit = viewModel::addHabit,
                            onRequestQuestCoach = viewModel::requestSmartQuestCoach,
                            onRequestLocalQuestCoach = viewModel::requestLocalSmartQuestCoach,
                            onAddSuggestedQuests = viewModel::addSuggestedQuests,
                            onResetQuestCoach = viewModel::resetQuestCoachState,
                            onDeleteHabit = viewModel::deleteHabit,
                            onRenamePet = viewModel::updatePetNameAndColor,
                            onShowMessage = viewModel::showActionMessage,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChibiTab.CHAT -> ChatScreen(
                            petProfileState = petProfileState,
                            chatMessagesState = chatMessagesState,
                            streamingReply = streamingReply,
                            isSending = isChatSending,
                            chatError = chatError,
                            ecoLiteModeEnabled = aiQuotaSnapshot.ecoLiteModeEnabled,
                            onToggleEcoLiteMode = viewModel::setEcoLiteMode,
                            onSendLocalReflex = viewModel::sendLocalReflexChatReply,
                            onSendMessage = viewModel::sendChatMessage,
                            onDismissChatError = viewModel::dismissChatError,
                            onClearChat = viewModel::clearChat,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChibiTab.FRIENDS -> FriendsScreen(
                            myBuddyCode = viewModel.myBuddyCode,
                            petProfileState = petProfileState,
                            friendsState = friendsState,
                            isBusy = isFriendBusy,
                            onAddFriend = viewModel::addFriend,
                            onRefreshFriends = viewModel::refreshFriends,
                            onSendCheer = viewModel::sendCheer,
                            onRemoveFriend = viewModel::removeFriend,
                            onShowMessage = viewModel::showActionMessage,
                            modifier = Modifier.fillMaxSize()
                        )
                        ChibiTab.STUDIO -> WardrobeAndStudioScreen(
                            petProfileState = petProfileState,
                            studioState = studioState,
                            savedStickers = savedStickers,
                            reminderSettings = reminderSettings,
                            aiQuotaSnapshot = aiQuotaSnapshot,
                            celebrationTick = celebrationTick,
                            userEmail = userEmail,
                            onPetTapped = viewModel::onPetTapped,
                            onSelectOutfit = viewModel::equipOrUnlockOutfit,
                            onPurchaseConsumable = viewModel::purchaseConsumable,
                            onGenerateArt = viewModel::generateStudioArt,
                            onGenerateOfflineEmblem = viewModel::generateOfflineCollectibleSticker,
                            onResetStudio = viewModel::resetStudioState,
                            onLoadStickerBitmap = viewModel::loadStickerBitmap,
                            onGetStickerShareUri = viewModel::getStickerShareUri,
                            onDeleteSticker = viewModel::deleteSticker,
                            onUpdateReminderSettings = viewModel::updateReminderSettings,
                            onSendTestReminder = viewModel::sendTestReminder,
                            onToggleEcoLiteMode = viewModel::setEcoLiteMode,
                            onClaimGraceCredits = viewModel::claimDailyGraceCredits,
                            onReplayOnboarding = onReplayOnboarding,
                            onShowMessage = viewModel::showActionMessage,
                            onSignOutClick = {
                                signOutUser(credentialManager, scope)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
