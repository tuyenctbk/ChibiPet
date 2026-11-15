package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.Habit
import com.example.data.model.HabitCategory
import com.example.data.model.PetCheer
import com.example.data.model.PetOutfit
import com.example.data.model.PetProfile
import com.example.data.model.SpeciesColor
import com.example.data.model.SuggestedQuest
import com.example.ui.components.CameraXProofDialog
import com.example.ui.pet3d.ChibiPet3DCanvas
import com.example.ui.theme.CoinGold
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.ProofVerificationState
import com.example.ui.viewmodel.QuestCoachUiState
import java.io.File
import kotlin.math.max

@Composable
fun HomePetScreen(
    petProfileState: DataUiState<PetProfile>,
    habitsState: DataUiState<List<Habit>>,
    incomingCheers: List<PetCheer>,
    proofState: ProofVerificationState,
    questCoachState: QuestCoachUiState = QuestCoachUiState.Idle,
    celebrationTick: Int,
    todayIsoDate: String,
    onPetTapped: () -> Unit,
    onGiveSnack: () -> Unit,
    onClaimCheer: (PetCheer) -> Unit,
    onVerifyHabitPhoto: (Habit, Bitmap) -> Unit,
    onRetryWithLiteModel: (Habit, Bitmap) -> Unit = { h, b -> onVerifyHabitPhoto(h, b) },
    onHonorPledgeCheckIn: (Habit) -> Unit = {},
    onDismissProofDialog: () -> Unit,
    onAddHabit: (String, HabitCategory, Long) -> Unit,
    onRequestQuestCoach: (String) -> Unit = {},
    onRequestLocalQuestCoach: (String) -> Unit = {},
    onAddSuggestedQuests: (List<SuggestedQuest>) -> Unit = {},
    onResetQuestCoach: () -> Unit = {},
    onDeleteHabit: (String) -> Unit,
    onRenamePet: (String, SpeciesColor) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingProofHabit by remember { mutableStateOf<Habit?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var liveCameraHabit by remember { mutableStateOf<Habit?>(null) }
    var showAddHabitDialog by remember { mutableStateOf(false) }
    var showEditPetDialog by remember { mutableStateOf(false) }
    var photoSourceTargetHabit by remember { mutableStateOf<Habit?>(null) }
    var habitPendingDelete by remember { mutableStateOf<Habit?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf<HabitCategory?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val habit = pendingProofHabit
        pendingProofHabit = null
        if (granted && habit != null) {
            liveCameraHabit = habit
        } else if (!granted) {
            onShowMessage(context.getString(R.string.home_camera_perm_denied))
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val habit = pendingProofHabit
        if (uri != null && habit != null) {
            decodeSampledBitmapFromUri(context, uri)?.let { bmp ->
                onVerifyHabitPhoto(habit, bmp)
            }
        }
        pendingProofHabit = null
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val habit = pendingProofHabit
        val uri = pendingCameraUri
        if (success && habit != null && uri != null) {
            decodeSampledBitmapFromUri(context, uri)?.let { bmp ->
                onVerifyHabitPhoto(habit, bmp)
            }
        }
        pendingProofHabit = null
    }

    val profile = (petProfileState as? DataUiState.Success)?.data
    val habits = (habitsState as? DataUiState.Success)?.data.orEmpty()
    val filteredHabits = remember(habits, selectedCategoryFilter, todayIsoDate) {
        val byCat = if (selectedCategoryFilter == null) {
            habits
        } else {
            habits.filter { it.category == selectedCategoryFilter?.key }
        }
        byCat.sortedBy { it.isCompletedOn(todayIsoDate) }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                when (petProfileState) {
                    is DataUiState.Loading -> {
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is DataUiState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.home_load_pet_error, petProfileState.message),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    is DataUiState.Success -> {
                        PetStageCard(
                            profile = petProfileState.data,
                            celebrationTick = celebrationTick,
                            onPetTapped = onPetTapped,
                            onGiveSnack = onGiveSnack,
                            onEditClick = { showEditPetDialog = true }
                        )
                    }
                }
            }

            if (incomingCheers.isNotEmpty()) {
                item {
                    val topCheer = incomingCheers.first()
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.home_cheer_from, topCheer.fromPetName),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = topCheer.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Button(onClick = { onClaimCheer(topCheer) }) {
                                Text(stringResource(R.string.home_claim_cheer))
                            }
                        }
                    }
                }
            }

            item {
                val completedToday = habits.count { it.isCompletedOn(todayIsoDate) }
                val allDoneToday = habits.isNotEmpty() && completedToday == habits.size
                DailyQuestBonusCard(
                    completedToday = completedToday,
                    totalHabits = habits.size,
                    allDoneToday = allDoneToday,
                    bonusClaimedToday = profile?.lastDailyBonusDate == todayIsoDate
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_habits_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            val completedToday = habits.count { it.isCompletedOn(todayIsoDate) }
                            Text(
                                text = stringResource(R.string.home_habits_progress, completedToday, habits.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { showAddHabitDialog = true },
                            modifier = Modifier.testTag("add_habit_button")
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.home_add_habit_cd),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.home_new_quest))
                        }
                    }

                    if (habits.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedCategoryFilter == null,
                                    onClick = { selectedCategoryFilter = null },
                                    label = { Text(stringResource(R.string.home_filter_all, habits.size)) }
                                )
                            }
                            items(HabitCategory.entries) { cat ->
                                val count = habits.count { it.category == cat.key }
                                if (count > 0) {
                                    FilterChip(
                                        selected = selectedCategoryFilter == cat,
                                        onClick = {
                                            selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                                        },
                                        label = {
                                            Text(
                                                stringResource(
                                                    R.string.home_filter_category,
                                                    stringResource(cat.labelRes),
                                                    count
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (habitsState) {
                is DataUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is DataUiState.Error -> {
                    item {
                        Text(
                            text = habitsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is DataUiState.Success -> {
                    if (habits.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_empty_habits_title),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = stringResource(R.string.home_empty_habits_sub),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredHabits, key = { it.id }) { habit ->
                            HabitCard(
                                habit = habit,
                                isDoneToday = habit.isCompletedOn(todayIsoDate),
                                onSnapProofClick = { photoSourceTargetHabit = habit },
                                onDeleteClick = { habitPendingDelete = habit }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    photoSourceTargetHabit?.let { targetHabit ->
        AlertDialog(
            onDismissRequest = { photoSourceTargetHabit = null },
            title = { Text(stringResource(R.string.dialog_verify_title, targetHabit.title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val hint = stringResource(HabitCategory.fromKey(targetHabit.category).proofHintRes)
                    Text(
                        stringResource(
                            R.string.dialog_verify_body,
                            profile?.petName ?: "Mochi",
                            hint
                        )
                    )
                    Button(
                        onClick = {
                            photoSourceTargetHabit = null
                            val hasCamera = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCamera) {
                                liveCameraHabit = targetHabit
                            } else {
                                pendingProofHabit = targetHabit
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("camerax_proof_button")
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.dialog_verify_camerax))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pendingProofHabit = targetHabit
                                photoSourceTargetHabit = null
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gallery_proof_button")
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.dialog_verify_gallery))
                        }
                        OutlinedButton(
                            onClick = {
                                pendingProofHabit = targetHabit
                                photoSourceTargetHabit = null
                                val uri = createTempImageUri(context)
                                pendingCameraUri = uri
                                takePictureLauncher.launch(uri)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("camera_proof_button")
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.dialog_verify_syscam))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { photoSourceTargetHabit = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    liveCameraHabit?.let { habit ->
        CameraXProofDialog(
            habit = habit,
            petName = profile?.petName ?: "Mochi",
            onDismiss = { liveCameraHabit = null },
            onCaptured = { bitmap ->
                liveCameraHabit = null
                onVerifyHabitPhoto(habit, bitmap)
            },
            onFallbackError = { msg ->
                liveCameraHabit = null
                onShowMessage(msg)
            }
        )
    }

    if (proofState !is ProofVerificationState.Idle) {
        ProofVerificationDialog(
            state = proofState,
            petName = profile?.petName ?: "Mochi",
            onRetryWithLite = onRetryWithLiteModel,
            onHonorPledgeCheckIn = onHonorPledgeCheckIn,
            onDismiss = onDismissProofDialog
        )
    }

    if (showAddHabitDialog) {
        AddHabitDialog(
            questCoachState = questCoachState,
            onRequestQuestCoach = onRequestQuestCoach,
            onRequestLocalQuestCoach = onRequestLocalQuestCoach,
            onAddAllSuggested = { quests ->
                onAddSuggestedQuests(quests)
                showAddHabitDialog = false
            },
            onDismiss = {
                onResetQuestCoach()
                showAddHabitDialog = false
            },
            onConfirm = { title, category, xp ->
                onAddHabit(title, category, xp)
                onResetQuestCoach()
                showAddHabitDialog = false
            }
        )
    }

    if (showEditPetDialog && profile != null) {
        EditPetDialog(
            currentName = profile.petName,
            currentColor = SpeciesColor.fromKey(profile.speciesColor),
            onDismiss = { showEditPetDialog = false },
            onConfirm = { name, color ->
                onRenamePet(name, color)
                showEditPetDialog = false
            }
        )
    }

    habitPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { habitPendingDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_habit_title)) },
            text = {
                Text(
                    stringResource(R.string.dialog_delete_habit_body, target.title, target.streakCount)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteHabit(target.id)
                        habitPendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { habitPendingDelete = null }) {
                    Text(stringResource(R.string.dialog_keep_quest))
                }
            }
        )
    }
}

@Composable
private fun DailyQuestBonusCard(
    completedToday: Int,
    totalHabits: Int,
    allDoneToday: Boolean,
    bonusClaimedToday: Boolean
) {
    if (totalHabits == 0) return
    val progress by animateFloatAsState(
        targetValue = (completedToday.toFloat() / totalHabits.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "daily_sweep_progress"
    )
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allDoneToday || bonusClaimedToday) {
                CoinGold.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            }
        ),
        border = if (allDoneToday || bonusClaimedToday) {
            BorderStroke(1.2.dp, CoinGold.copy(alpha = 0.6f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = CoinGold.copy(alpha = 0.24f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Redeem,
                                contentDescription = stringResource(R.string.home_sweep_chest_cd),
                                tint = CoinGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (bonusClaimedToday || allDoneToday) {
                                stringResource(R.string.home_sweep_unlocked_title)
                            } else {
                                stringResource(R.string.home_sweep_locked_title)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (bonusClaimedToday || allDoneToday) {
                                stringResource(R.string.home_sweep_unlocked_sub)
                            } else {
                                stringResource(R.string.home_sweep_locked_sub, totalHabits)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = "$completedToday/$totalHabits",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape),
                color = if (allDoneToday || bonusClaimedToday) CoinGold else MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun PetStageCard(
    profile: PetProfile,
    celebrationTick: Int,
    onPetTapped: () -> Unit,
    onGiveSnack: () -> Unit,
    onEditClick: () -> Unit
) {
    val animatedXp by animateFloatAsState(
        targetValue = profile.xpProgressFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "xp_progress"
    )

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = stringResource(R.string.home_level_badge, profile.level),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = profile.petName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_pet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.home_edit_pet_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (profile.streakFreezes > 0L) {
                        Surface(
                            color = Color(0xFF8EC5FF).copy(alpha = 0.25f),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AcUnit,
                                    contentDescription = stringResource(R.string.home_streak_freezes_cd),
                                    tint = Color(0xFF2B7CD3),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${profile.streakFreezes}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = onGiveSnack,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("give_snack_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cookie,
                            contentDescription = stringResource(R.string.home_give_treat_cd),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.home_treat_button), style = MaterialTheme.typography.labelSmall)
                    }

                    val animatedCoins by animateIntAsState(
                        targetValue = profile.coins.toInt(),
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "coin_count"
                    )
                    Surface(
                        color = CoinGold.copy(alpha = 0.16f),
                        shape = CircleShape
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = stringResource(R.string.home_star_coins_cd),
                                tint = CoinGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$animatedCoins",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(265.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .testTag("chibipet_3d_stage")
            ) {
                ChibiPet3DCanvas(
                    speciesColor = SpeciesColor.fromKey(profile.speciesColor),
                    equippedOutfit = PetOutfit.fromKey(profile.equippedOutfit),
                    happiness = profile.happiness.toInt(),
                    energy = profile.energy.toInt(),
                    celebrationTick = celebrationTick,
                    onPetTapped = onPetTapped,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_stage_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.home_experience_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.home_xp_progress, profile.xp, profile.xpForNextLevel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animatedXp },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPill(
                    icon = Icons.Rounded.Favorite,
                    label = stringResource(R.string.home_stat_mood),
                    value = "${profile.happiness}%",
                    tint = Color(0xFFE95C7C),
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Rounded.Bolt,
                    label = stringResource(R.string.home_stat_energy),
                    value = "${profile.energy}%",
                    tint = Color(0xFFF39C12),
                    modifier = Modifier.weight(1f)
                )
                StatPill(
                    icon = Icons.Rounded.CheckCircle,
                    label = stringResource(R.string.home_stat_verified),
                    value = "${profile.totalCompleted}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    isDoneToday: Boolean,
    onSnapProofClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val category = HabitCategory.fromKey(habit.category)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDoneToday) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isDoneToday) BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("habit_card_${habit.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = stringResource(category.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (habit.streakCount > 0L) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.LocalFireDepartment,
                                    contentDescription = stringResource(R.string.home_streak_cd),
                                    tint = Color(0xFFFF6B35),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = stringResource(R.string.home_streak_days, habit.streakCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF6B35),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.home_delete_habit_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = habit.lastVerificationNote.isNotBlank()) {
                Text(
                    text = stringResource(R.string.home_last_proof, habit.lastVerificationNote),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.home_habit_reward,
                        habit.xpReward,
                        (habit.xpReward / 2L).coerceAtLeast(10L)
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Button(
                    onClick = onSnapProofClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("verify_habit_button_${habit.id}")
                ) {
                    Icon(
                        imageVector = if (isDoneToday) Icons.Rounded.CheckCircle else Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isDoneToday) {
                            stringResource(R.string.home_verified_today)
                        } else {
                            stringResource(R.string.home_verify_proof)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProofVerificationDialog(
    state: ProofVerificationState,
    petName: String,
    onRetryWithLite: (Habit, Bitmap) -> Unit,
    onHonorPledgeCheckIn: (Habit) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (state !is ProofVerificationState.Analyzing) onDismiss()
        },
        title = {
            Text(
                text = when (state) {
                    is ProofVerificationState.Analyzing -> stringResource(R.string.dialog_proof_analyzing_title, petName)
                    is ProofVerificationState.Verified ->
                        if (state.leveledUp) {
                            stringResource(R.string.dialog_proof_levelup_title)
                        } else {
                            stringResource(R.string.dialog_proof_verified_title)
                        }
                    is ProofVerificationState.Rejected -> stringResource(R.string.dialog_proof_rejected_title)
                    is ProofVerificationState.Error ->
                        if (state.isQuotaError) {
                            stringResource(R.string.dialog_proof_quota_title)
                        } else {
                            stringResource(R.string.dialog_proof_error_title)
                        }
                    ProofVerificationState.Idle -> ""
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (state) {
                    is ProofVerificationState.Analyzing -> {
                        Image(
                            bitmap = state.previewBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.dialog_proof_submitted_cd),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.dialog_proof_checking_body, state.habit.title),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is ProofVerificationState.Verified -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = stringResource(
                                        R.string.dialog_proof_detected,
                                        state.result.detectedItem,
                                        state.result.confidence
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.result.petReaction,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (state.xpEarned > 0L || state.dailyBonusClaimed) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (state.dailyBonusClaimed) {
                                            stringResource(
                                                R.string.dialog_proof_reward_sweep,
                                                state.xpEarned,
                                                state.coinsEarned
                                            )
                                        } else {
                                            stringResource(
                                                R.string.dialog_proof_reward_normal,
                                                state.xpEarned,
                                                state.coinsEarned
                                            )
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    is ProofVerificationState.Rejected -> {
                        Text(
                            text = stringResource(R.string.dialog_proof_saw, state.result.detectedItem),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = state.result.petReaction,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    is ProofVerificationState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.dialog_proof_protection_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.dialog_proof_protection_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (state.previewBitmap != null) {
                                        OutlinedButton(
                                            onClick = { onRetryWithLite(state.habit, state.previewBitmap) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(R.string.dialog_proof_retry_lite))
                                        }
                                    }
                                    Button(
                                        onClick = { onHonorPledgeCheckIn(state.habit) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("honor_pledge_checkin_button")
                                    ) {
                                        Text(stringResource(R.string.dialog_proof_honor_checkin))
                                    }
                                }
                            }
                        }
                    }
                    ProofVerificationState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            if (state !is ProofVerificationState.Analyzing) {
                TextButton(onClick = onDismiss) {
                    Text(
                        if (state is ProofVerificationState.Verified) {
                            stringResource(R.string.dialog_proof_yay)
                        } else {
                            stringResource(R.string.common_close)
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun AddHabitDialog(
    questCoachState: QuestCoachUiState,
    onRequestQuestCoach: (String) -> Unit,
    onRequestLocalQuestCoach: (String) -> Unit,
    onAddAllSuggested: (List<SuggestedQuest>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, HabitCategory, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(HabitCategory.HYDRATION) }
    var selectedXp by remember { mutableLongStateOf(35L) }
    var coachGoal by remember { mutableStateOf("") }

    val presetEnergy = stringResource(R.string.coach_goal_preset_energy)
    val presetStudy = stringResource(R.string.coach_goal_preset_study)
    val presetCalm = stringResource(R.string.coach_goal_preset_calm)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_add_habit_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.coach_section_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedTextField(
                            value = coachGoal,
                            onValueChange = { coachGoal = it.take(80) },
                            placeholder = { Text(stringResource(R.string.coach_goal_hint)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("coach_goal_input")
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(presetEnergy, presetStudy, presetCalm)) { preset ->
                                FilterChip(
                                    selected = coachGoal == preset,
                                    onClick = { coachGoal = preset },
                                    label = { Text(preset, maxLines = 1) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    onRequestQuestCoach(coachGoal.ifBlank { presetEnergy })
                                },
                                enabled = questCoachState !is QuestCoachUiState.Loading,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_coach_suggest_button")
                            ) {
                                if (questCoachState is QuestCoachUiState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(stringResource(R.string.coach_ask_ai_button), maxLines = 1)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    onRequestLocalQuestCoach(coachGoal.ifBlank { presetEnergy })
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_coach_local_fallback_button")
                            ) {
                                Text(
                                    stringResource(R.string.coach_local_plan_button),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        when (questCoachState) {
                            is QuestCoachUiState.Success -> {
                                val plan = questCoachState.plan
                                Text(
                                    text = plan.coachMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                plan.quests.forEach { quest ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = quest.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${stringResource(quest.category.labelRes)} • +${quest.xpReward} XP",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    title = quest.title
                                                    selectedCategory = quest.category
                                                    selectedXp = quest.xpReward
                                                }
                                            ) {
                                                Text(stringResource(R.string.coach_use_quest))
                                            }
                                        }
                                    }
                                }
                                Button(
                                    onClick = { onAddAllSuggested(plan.quests) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("coach_add_all_quests_button")
                                ) {
                                    Text(stringResource(R.string.coach_add_all_button))
                                }
                            }
                            is QuestCoachUiState.Error -> {
                                Text(
                                    text = questCoachState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> Unit
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text(stringResource(R.string.dialog_add_habit_name_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_add_habit_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_habit_title_input")
                )
                Text(stringResource(R.string.dialog_add_habit_category_label), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HabitCategory.entries.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(stringResource(cat.labelRes), maxLines = 1) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HabitCategory.entries.drop(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(stringResource(cat.labelRes), maxLines = 1) }
                        )
                    }
                }
                Text(stringResource(R.string.dialog_add_habit_effort_label), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        20L to stringResource(R.string.effort_quick),
                        35L to stringResource(R.string.effort_daily),
                        60L to stringResource(R.string.effort_deep)
                    ).forEach { (xp, label) ->
                        FilterChip(
                            selected = selectedXp == xp,
                            onClick = { selectedXp = xp },
                            label = { Text(stringResource(R.string.effort_chip_format, label, xp), maxLines = 1) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, selectedCategory, selectedXp) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_habit_button")
            ) {
                Text(stringResource(R.string.common_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun EditPetDialog(
    currentName: String,
    currentColor: SpeciesColor,
    onDismiss: () -> Unit,
    onConfirm: (String, SpeciesColor) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var selectedColor by remember { mutableStateOf(currentColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_edit_pet_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text(stringResource(R.string.dialog_edit_pet_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.dialog_edit_pet_coat_label), style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SpeciesColor.entries.forEach { color ->
                        val isSelected = selectedColor == color
                        Surface(
                            shape = CircleShape,
                            color = Color(color.primaryHex),
                            border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                            modifier = Modifier
                                .size(42.dp)
                                .clickable { selectedColor = color }
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

fun createTempImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "habit_proofs").apply { mkdirs() }
    val file = File(dir, "proof_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 1024): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.isMutableRequired = true
                val w = info.size.width
                val h = info.size.height
                val longest = max(w, h)
                if (longest > maxDim && longest > 0) {
                    val scale = maxDim.toFloat() / longest.toFloat()
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            var inSampleSize = 1
            val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
            while (longest / inSampleSize > maxDim) {
                inSampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inMutable = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        }
    } catch (_: Exception) {
        null
    }
}
