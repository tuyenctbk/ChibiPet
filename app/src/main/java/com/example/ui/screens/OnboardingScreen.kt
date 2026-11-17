package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.OnboardingSelection
import com.example.data.model.PetOutfit
import com.example.data.model.SpeciesColor
import com.example.ui.pet3d.ChibiPet3DCanvas
import com.example.ui.theme.CoinGold
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    initialSelection: OnboardingSelection,
    onFinishOnboarding: (
        petName: String,
        speciesColor: SpeciesColor,
        outfit: PetOutfit,
        selectedQuestIds: Set<String>
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    val defaultPetName = stringResource(R.string.default_pet_name)
    var petName by rememberSaveable { mutableStateOf(initialSelection.preferredPetName) }
    var selectedColorKey by rememberSaveable { mutableStateOf(initialSelection.preferredColor.key) }
    var previewOutfitKey by rememberSaveable { mutableStateOf(initialSelection.preferredOutfit.key) }
    var selectedQuestIds by remember {
        mutableStateOf(initialSelection.selectedQuestIds)
    }
    var celebrationTick by remember { mutableIntStateOf(0) }

    val selectedColor = SpeciesColor.fromKey(selectedColorKey)
    val previewOutfit = PetOutfit.fromKey(previewOutfitKey)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("onboarding_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar with step badge & Skip CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.onboarding_step_badge, pagerState.currentPage + 1, 4),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (pagerState.currentPage < 3) {
                    TextButton(
                        onClick = {
                            onFinishOnboarding(
                                petName.trim().ifEmpty { defaultPetName },
                                selectedColor,
                                PetOutfit.BOW,
                                selectedQuestIds
                            )
                        },
                        modifier = Modifier.testTag("onboarding_skip_button")
                    ) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingPageMeet3DBuddy(
                        petName = petName.ifBlank { defaultPetName },
                        selectedColor = selectedColor,
                        previewOutfit = previewOutfit,
                        celebrationTick = celebrationTick,
                        onSelectColor = { selectedColorKey = it.key },
                        onSelectPreviewOutfit = { previewOutfitKey = it.key },
                        onPetBooped = { celebrationTick += 1 }
                    )
                    1 -> OnboardingPageVisionLoop()
                    2 -> OnboardingPageSmartFreeTierAndSocial()
                    else -> OnboardingPageCustomizeAdoption(
                        petName = petName,
                        onPetNameChange = { petName = it.take(30) },
                        selectedColor = selectedColor,
                        onSelectColor = { selectedColorKey = it.key },
                        selectedQuestIds = selectedQuestIds,
                        onToggleQuest = { questId ->
                            selectedQuestIds = if (questId in selectedQuestIds) {
                                selectedQuestIds - questId
                            } else {
                                selectedQuestIds + questId
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Page indicator dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 28.dp else 10.dp,
                        animationSpec = tween(250),
                        label = "dot_width"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        animationSpec = tween(250),
                        label = "dot_color"
                    )
                    Box(
                        modifier = Modifier
                            .height(10.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Bottom Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.onboarding_prev_step_cd)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.onboarding_back))
                    }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinishOnboarding(
                                petName.trim().ifEmpty { defaultPetName },
                                selectedColor,
                                PetOutfit.BOW,
                                selectedQuestIds
                            )
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("onboarding_next_button")
                ) {
                    Text(
                        text = if (pagerState.currentPage < 3) {
                            stringResource(R.string.onboarding_next)
                        } else {
                            stringResource(R.string.onboarding_adopt_start, petName.trim().ifEmpty { defaultPetName })
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (pagerState.currentPage < 3) {
                            Icons.AutoMirrored.Rounded.ArrowForward
                        } else {
                            Icons.Rounded.Check
                        },
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingPageMeet3DBuddy(
    petName: String,
    selectedColor: SpeciesColor,
    previewOutfit: PetOutfit,
    celebrationTick: Int,
    onSelectColor: (SpeciesColor) -> Unit,
    onSelectPreviewOutfit: (PetOutfit) -> Unit,
    onPetBooped: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_p1_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.onboarding_p1_sub, petName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    ChibiPet3DCanvas(
                        speciesColor = selectedColor,
                        equippedOutfit = previewOutfit,
                        happiness = 95,
                        energy = 95,
                        celebrationTick = celebrationTick,
                        onPetTapped = onPetBooped,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_p1_stage_hint),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                // Coat color quick selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_p1_coat_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SpeciesColor.entries.forEach { color ->
                        val isSelected = color == selectedColor
                        Surface(
                            shape = CircleShape,
                            color = Color(color.primaryHex),
                            border = if (isSelected) {
                                BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { onSelectColor(color) }
                        ) {}
                    }
                }

                // Outfit preview chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        PetOutfit.BOW,
                        PetOutfit.CROWN,
                        PetOutfit.WIZARD,
                        PetOutfit.HEADPHONES,
                        PetOutfit.CAPE
                    ).forEach { outfit ->
                        FilterChip(
                            selected = previewOutfit == outfit,
                            onClick = { onSelectPreviewOutfit(outfit) },
                            label = { Text(stringResource(outfit.labelRes), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageVisionLoop() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_welcome_meadow_1794675773694),
                contentDescription = stringResource(R.string.onboarding_p2_banner_cd),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.onboarding_p2_badge), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Text(
            text = stringResource(R.string.onboarding_p2_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.onboarding_p2_sub),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        OnboardingStepCard(
            icon = Icons.Rounded.CameraAlt,
            tint = MaterialTheme.colorScheme.primary,
            title = stringResource(R.string.onboarding_p2_step1_title),
            subtitle = stringResource(R.string.onboarding_p2_step1_sub)
        )
        OnboardingStepCard(
            icon = Icons.Rounded.CheckCircle,
            tint = SuccessGreen,
            title = stringResource(R.string.onboarding_p2_step2_title),
            subtitle = stringResource(R.string.onboarding_p2_step2_sub)
        )
        OnboardingStepCard(
            icon = Icons.Rounded.Redeem,
            tint = CoinGold,
            title = stringResource(R.string.onboarding_p2_step3_title),
            subtitle = stringResource(R.string.onboarding_p2_step3_sub)
        )
    }
}

@Composable
private fun OnboardingPageSmartFreeTierAndSocial() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_p3_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.onboarding_p3_sub),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        OnboardingStepCard(
            icon = Icons.Rounded.Forum,
            tint = MaterialTheme.colorScheme.primary,
            title = stringResource(R.string.onboarding_p3_step1_title),
            subtitle = stringResource(R.string.onboarding_p3_step1_sub)
        )
        OnboardingStepCard(
            icon = Icons.Rounded.Bolt,
            tint = Color(0xFFF39C12),
            title = stringResource(R.string.onboarding_p3_step2_title),
            subtitle = stringResource(R.string.onboarding_p3_step2_sub)
        )
        OnboardingStepCard(
            icon = Icons.Rounded.Shield,
            tint = SuccessGreen,
            title = stringResource(R.string.onboarding_p3_step3_title),
            subtitle = stringResource(R.string.onboarding_p3_step3_sub)
        )
        OnboardingStepCard(
            icon = Icons.Rounded.Groups,
            tint = MaterialTheme.colorScheme.tertiary,
            title = stringResource(R.string.onboarding_p3_step4_title),
            subtitle = stringResource(R.string.onboarding_p3_step4_sub)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingPageCustomizeAdoption(
    petName: String,
    onPetNameChange: (String) -> Unit,
    selectedColor: SpeciesColor,
    onSelectColor: (SpeciesColor) -> Unit,
    selectedQuestIds: Set<String>,
    onToggleQuest: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.onboarding_p4_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.onboarding_p4_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = petName,
                    onValueChange = onPetNameChange,
                    label = { Text(stringResource(R.string.onboarding_p4_name_label)) },
                    placeholder = { Text(stringResource(R.string.default_pet_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_pet_name_input")
                )

                Text(
                    text = stringResource(R.string.onboarding_p4_coat_title),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpeciesColor.entries.forEach { color ->
                        val isSelected = color == selectedColor
                        val colorLabel = stringResource(color.labelRes)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onSelectColor(color) }
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(color.primaryHex),
                                border = if (isSelected) {
                                    BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                                } else {
                                    null
                                },
                                modifier = Modifier.size(38.dp)
                            ) {}
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = colorLabel.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_p4_quests_title, selectedQuestIds.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OnboardingSelection.STARTER_QUEST_TEMPLATES.forEach { quest ->
                    val selected = quest.id in selectedQuestIds
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleQuest(quest.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(quest.titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(
                                        R.string.onboarding_p4_quest_meta,
                                        stringResource(quest.category.labelRes),
                                        quest.xpReward.toInt()
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                                contentDescription = stringResource(
                                    if (selected) R.string.onboarding_selected_cd else R.string.onboarding_not_selected_cd
                                ),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.16f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
