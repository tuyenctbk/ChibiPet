package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.AiQuotaSnapshot
import com.example.data.local.AppLanguageManager
import com.example.data.local.SavedSticker
import com.example.data.model.PetOutfit
import com.example.data.model.PetProfile
import com.example.data.model.ShopConsumable
import com.example.data.model.SpeciesColor
import com.example.notifications.ReminderSettings
import com.example.ui.pet3d.ChibiPet3DCanvas
import com.example.ui.theme.CoinGold
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.StudioArtState

@Composable
fun WardrobeAndStudioScreen(
    petProfileState: DataUiState<PetProfile>,
    studioState: StudioArtState,
    savedStickers: List<SavedSticker>,
    reminderSettings: ReminderSettings,
    aiQuotaSnapshot: AiQuotaSnapshot = AiQuotaSnapshot(),
    celebrationTick: Int,
    userEmail: String?,
    onPetTapped: () -> Unit,
    onSelectOutfit: (PetOutfit) -> Unit,
    onPurchaseConsumable: (ShopConsumable) -> Unit,
    onGenerateArt: (String, Bitmap?) -> Unit,
    onGenerateOfflineEmblem: (String) -> Unit = {},
    onResetStudio: () -> Unit,
    onLoadStickerBitmap: suspend (String) -> Bitmap?,
    onGetStickerShareUri: (String) -> Uri?,
    onDeleteSticker: (SavedSticker) -> Unit,
    onUpdateReminderSettings: (Boolean, Int, Int) -> Unit,
    onSendTestReminder: () -> Unit,
    onToggleEcoLiteMode: (Boolean) -> Unit = {},
    onClaimGraceCredits: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
    onShowMessage: (String) -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile = (petProfileState as? DataUiState.Success)?.data
    val defaultPrompt = stringResource(R.string.studio_default_prompt)
    var artPrompt by remember { mutableStateOf("") }
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var inspectedSticker by remember { mutableStateOf<SavedSticker?>(null) }
    val selectedLanguageTag by AppLanguageManager.observeLanguageTag(context)
        .collectAsStateWithLifecycle(initialValue = AppLanguageManager.getSavedLanguageTag(context))

    val refImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            referenceBitmap = decodeSampledBitmapFromUri(context, uri)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onUpdateReminderSettings(true, reminderSettings.hour, reminderSettings.minute)
        } else {
            onShowMessage(context.getString(R.string.studio_notif_perm_denied))
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.studio_header_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = stringResource(R.string.studio_header_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (profile != null) {
                        Surface(
                            color = CoinGold.copy(alpha = 0.18f),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = stringResource(R.string.studio_coins_cd),
                                    tint = CoinGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${profile.coins}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            if (profile != null) {
                // Live 3D Fitting Room Stage
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(18.dp))
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
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.studio_live_preview_badge,
                                        stringResource(PetOutfit.fromKey(profile.equippedOutfit).labelRes)
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Coin Shop Power-Ups
                item {
                    Text(
                        text = stringResource(R.string.studio_powerup_shop_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(ShopConsumable.entries, key = { it.key }) { item ->
                    val isFreeze = item == ShopConsumable.STREAK_FREEZE
                    val canBuy = profile.coins >= item.costCoins && (!isFreeze || profile.streakFreezes < 10L)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_item_${item.key}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isFreeze) Color(0xFF8EC5FF).copy(alpha = 0.3f) else CoinGold.copy(alpha = 0.25f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isFreeze) Icons.Rounded.AcUnit else Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (isFreeze) Color(0xFF2B7CD3) else CoinGold
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(item.labelRes),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isFreeze) {
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surface
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.studio_freeze_held, profile.streakFreezes),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = stringResource(item.descriptionRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { onPurchaseConsumable(item) },
                                enabled = canBuy,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${item.costCoins}")
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.studio_outfits_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(PetOutfit.entries, key = { it.key }) { outfit ->
                    val unlocked = outfit.key in profile.unlockedOutfits
                    val equipped = profile.equippedOutfit == outfit.key
                    OutfitRowCard(
                        outfit = outfit,
                        unlocked = unlocked,
                        equipped = equipped,
                        canAfford = profile.coins >= outfit.costCoins,
                        onClick = { onSelectOutfit(outfit) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.studio_art_header_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(R.string.studio_art_header_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val presets = listOf(
                            stringResource(R.string.studio_preset_1) to stringResource(R.string.studio_preset_1_short),
                            stringResource(R.string.studio_preset_2) to stringResource(R.string.studio_preset_2_short),
                            stringResource(R.string.studio_preset_3) to stringResource(R.string.studio_preset_3_short)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presets) { (fullPrompt, shortLabel) ->
                                AssistChip(
                                    onClick = { artPrompt = fullPrompt },
                                    label = { Text(shortLabel) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = artPrompt,
                            onValueChange = { artPrompt = it },
                            label = { Text(stringResource(R.string.studio_prompt_label)) },
                            placeholder = { Text(defaultPrompt) },
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_prompt_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    refImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (referenceBitmap == null) {
                                        stringResource(R.string.studio_remix_photo)
                                    } else {
                                        stringResource(R.string.studio_photo_ready)
                                    }
                                )
                            }
                            if (referenceBitmap != null) {
                                IconButton(
                                    onClick = { referenceBitmap = null },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.studio_clear_photo_cd)
                                    )
                                }
                            }
                            Button(
                                onClick = { onGenerateArt(artPrompt, referenceBitmap) },
                                enabled = artPrompt.isNotBlank() && studioState !is StudioArtState.Loading,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("generate_art_button")
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.studio_generate_button))
                            }
                        }

                        when (studioState) {
                            is StudioArtState.Idle -> Unit
                            is StudioArtState.Loading -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = stringResource(R.string.studio_crafting_status),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                            is StudioArtState.Success -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Image(
                                        bitmap = studioState.bitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.studio_generated_cd),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(260.dp)
                                            .clip(RoundedCornerShape(18.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Text(
                                        text = stringResource(R.string.studio_saved_vault_note),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    OutlinedButton(
                                        onClick = onResetStudio,
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text(stringResource(R.string.studio_clear_preview))
                                    }
                                }
                            }
                            is StudioArtState.Error -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = studioState.message,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Button(
                                            onClick = { onGenerateOfflineEmblem(artPrompt) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("craft_offline_emblem_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Bolt,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.studio_craft_offline_emblem))
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { onGenerateOfflineEmblem(artPrompt) },
                            enabled = studioState !is StudioArtState.Loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.studio_instant_emblem_button))
                        }
                    }
                }
            }

            if (savedStickers.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.studio_vault_title, savedStickers.size),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(savedStickers, key = { it.id }) { sticker ->
                                StickerThumbnailCard(
                                    sticker = sticker,
                                    onLoadBitmap = onLoadStickerBitmap,
                                    onClick = { inspectedSticker = sticker }
                                )
                            }
                        }
                    }
                }
            }

            // AI Free-Tier Quota & Eco Guard Card
            item {
                val localizedResetCountdown = aiQuotaSnapshot.formatResetCountdown(context)
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFFF39C12)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.studio_quota_card_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (aiQuotaSnapshot.ecoLiteModeEnabled) {
                                            stringResource(R.string.studio_quota_eco_on_sub, localizedResetCountdown)
                                        } else {
                                            stringResource(R.string.studio_quota_eco_off_sub, localizedResetCountdown)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = aiQuotaSnapshot.ecoLiteModeEnabled,
                                onCheckedChange = onToggleEcoLiteMode,
                                modifier = Modifier.testTag("eco_lite_mode_switch")
                            )
                        }

                        if (aiQuotaSnapshot.isNearLimitSoftNudge || aiQuotaSnapshot.isAtOrOverSoftLimit) {
                            Surface(
                                color = Color(0xFFF39C12).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (aiQuotaSnapshot.isAtOrOverSoftLimit) {
                                        stringResource(R.string.studio_quota_over_limit_banner, localizedResetCountdown)
                                    } else {
                                        stringResource(R.string.studio_quota_near_limit_banner)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuotaPill(
                                label = stringResource(R.string.studio_quota_pill_vision),
                                value = "${aiQuotaSnapshot.visionUsedToday}/${aiQuotaSnapshot.visionLimitToday}",
                                modifier = Modifier.weight(1f)
                            )
                            QuotaPill(
                                label = stringResource(R.string.studio_quota_pill_chat),
                                value = "${aiQuotaSnapshot.chatUsedToday}/${aiQuotaSnapshot.chatLimitToday}",
                                modifier = Modifier.weight(1f)
                            )
                            QuotaPill(
                                label = stringResource(R.string.studio_quota_pill_studio),
                                value = "${aiQuotaSnapshot.studioUsedToday}/${aiQuotaSnapshot.studioLimitToday}",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = aiQuotaSnapshot.formatValueReceipt(context),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (aiQuotaSnapshot.fallbackCascadeCountToday > 0) {
                                    stringResource(
                                        R.string.studio_quota_failover_saves,
                                        aiQuotaSnapshot.fallbackCascadeCountToday
                                    )
                                } else {
                                    stringResource(R.string.studio_quota_need_boost)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = onClaimGraceCredits,
                                enabled = !aiQuotaSnapshot.graceClaimedToday,
                                modifier = Modifier.testTag("claim_grace_credits_button")
                            ) {
                                Text(
                                    if (aiQuotaSnapshot.graceClaimedToday) {
                                        stringResource(R.string.studio_quota_grace_claimed)
                                    } else {
                                        stringResource(R.string.studio_quota_claim_grace)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Daily Pet Reminders Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.studio_reminders_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (reminderSettings.enabled) {
                                            stringResource(R.string.studio_reminders_active_sub, reminderSettings.formattedTime)
                                        } else {
                                            stringResource(R.string.studio_reminders_inactive_sub)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = reminderSettings.enabled,
                                onCheckedChange = { enable ->
                                    if (!enable) {
                                        onUpdateReminderSettings(false, reminderSettings.hour, reminderSettings.minute)
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onUpdateReminderSettings(true, reminderSettings.hour, reminderSettings.minute)
                                    }
                                },
                                modifier = Modifier.testTag("reminder_toggle_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                9 to 0,
                                14 to 0,
                                20 to 0
                            ).forEach { (h, m) ->
                                val label = ReminderSettings(true, h, m).formattedTime
                                FilterChip(
                                    selected = reminderSettings.hour == h && reminderSettings.minute == m,
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            onUpdateReminderSettings(true, h, m)
                                        }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onSendTestReminder()
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.studio_send_test_notif))
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.studio_signed_in_account),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = userEmail ?: stringResource(R.string.studio_google_account_fallback),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            OutlinedButton(
                                onClick = onSignOutClick,
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.studio_sign_out))
                            }
                        }

                        // App Language Picker (20+ Languages)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.studio_app_language_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.testTag("language_selector_row")
                        ) {
                            items(AppLanguageManager.SUPPORTED_LANGUAGES, key = { it.tag.ifEmpty { "system" } }) { lang ->
                                val isSelected = selectedLanguageTag == lang.tag
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { AppLanguageManager.setLanguageTag(context, lang.tag) },
                                    label = {
                                        Text(
                                            if (lang.tag.isEmpty()) {
                                                stringResource(R.string.studio_app_language_system)
                                            } else {
                                                lang.nativeName
                                            }
                                        )
                                    },
                                    modifier = Modifier.testTag("lang_chip_${lang.tag.ifEmpty { "system" }}")
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onReplayOnboarding,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("replay_onboarding_button")
                        ) {
                            Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.studio_replay_onboarding))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    inspectedSticker?.let { sticker ->
        val bmp by produceState<Bitmap?>(initialValue = null, sticker.fileName) {
            value = onLoadStickerBitmap(sticker.fileName)
        }
        AlertDialog(
            onDismissRequest = { inspectedSticker = null },
            title = { Text(stringResource(R.string.studio_saved_sticker_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bmp?.let { loaded ->
                        Image(
                            bitmap = loaded.asImageBitmap(),
                            contentDescription = sticker.prompt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        text = sticker.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = onGetStickerShareUri(sticker.fileName)
                        if (uri != null) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    context.getString(R.string.studio_share_sticker_chooser)
                                )
                            )
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.studio_share_button))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            bmp?.let { referenceBitmap = it }
                            inspectedSticker = null
                            onShowMessage(context.getString(R.string.studio_loaded_remix_msg))
                        }
                    ) {
                        Text(stringResource(R.string.studio_remix_button))
                    }
                    IconButton(
                        onClick = {
                            onDeleteSticker(sticker)
                            inspectedSticker = null
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.studio_delete_sticker_cd)
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun StickerThumbnailCard(
    sticker: SavedSticker,
    onLoadBitmap: suspend (String) -> Bitmap?,
    onClick: () -> Unit
) {
    val bmp by produceState<Bitmap?>(initialValue = null, sticker.fileName) {
        value = onLoadBitmap(sticker.fileName)
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                contentAlignment = Alignment.Center
            ) {
                val loaded = bmp
                if (loaded != null) {
                    Image(
                        bitmap = loaded.asImageBitmap(),
                        contentDescription = sticker.prompt,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
            }
            Text(
                text = sticker.prompt,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun OutfitRowCard(
    outfit: PetOutfit,
    unlocked: Boolean,
    equipped: Boolean,
    canAfford: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (equipped) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (equipped) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("outfit_card_${outfit.key}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(outfit.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(outfit.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onClick,
                enabled = !equipped && (unlocked || canAfford),
                shape = RoundedCornerShape(14.dp)
            ) {
                when {
                    equipped -> {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.studio_outfit_equipped))
                    }
                    unlocked -> Text(stringResource(R.string.studio_outfit_equip))
                    else -> {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${outfit.costCoins}")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
