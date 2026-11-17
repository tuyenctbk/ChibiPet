package com.example.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.PetProfile
import com.example.ui.viewmodel.DataUiState

@Composable
fun ChatScreen(
    petProfileState: DataUiState<PetProfile>,
    chatMessagesState: DataUiState<List<ChatMessage>>,
    streamingReply: String?,
    isSending: Boolean,
    chatError: String?,
    ecoLiteModeEnabled: Boolean = false,
    onToggleEcoLiteMode: (Boolean) -> Unit = {},
    onSendLocalReflex: () -> Unit = {},
    onSendMessage: (String) -> Unit,
    onDismissChatError: () -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val profile = (petProfileState as? DataUiState.Success)?.data
    val messages = (chatMessagesState as? DataUiState.Success)?.data.orEmpty()
    val petName = profile?.petName ?: stringResource(R.string.default_pet_name)
    var draft by remember { mutableStateOf("") }
    var voiceEnabled by rememberSaveable { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsRef by remember { mutableStateOf<TextToSpeech?>(null) }
    var lastSpokenMsgId by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.setPitch(1.32f)
                instance?.setSpeechRate(1.06f)
                ttsReady = true
            }
        }
        ttsRef = instance
        onDispose {
            runCatching {
                instance?.stop()
                instance?.shutdown()
            }
            ttsRef = null
            ttsReady = false
        }
    }

    fun speakChibiText(rawText: String) {
        val engine = ttsRef ?: return
        if (!ttsReady) return
        val cleaned = rawText.replace(Regex("\\*[^*]+\\*"), "").trim().ifEmpty { rawText }
        runCatching {
            engine.setPitch(1.32f)
            engine.setSpeechRate(1.06f)
            engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "chibi_tts_${System.currentTimeMillis()}")
        }
    }

    LaunchedEffect(messages.size, voiceEnabled, ttsReady) {
        val newest = messages.lastOrNull()
        if (voiceEnabled && ttsReady && newest != null && newest.sender != "user") {
            val idKey = newest.id.ifBlank { "${newest.text.hashCode()}" }
            if (lastSpokenMsgId.isNotEmpty() && idKey != lastSpokenMsgId) {
                speakChibiText(newest.text)
            }
            lastSpokenMsgId = idKey
        } else if (newest != null) {
            lastSpokenMsgId = newest.id.ifBlank { "${newest.text.hashCode()}" }
        }
    }

    LaunchedEffect(messages.size, streamingReply) {
        val total = messages.size + if (streamingReply != null) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Pets,
                                    contentDescription = petName,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.chat_title, petName),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (profile != null) {
                                    val modeLabel = stringResource(
                                        if (ecoLiteModeEnabled) R.string.chat_mode_lite else R.string.chat_mode_standard
                                    )
                                    stringResource(
                                        R.string.chat_subtitle_status,
                                        profile.level.toInt(),
                                        profile.happiness.toInt(),
                                        modeLabel
                                    )
                                } else {
                                    stringResource(R.string.chat_loading_buddy)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = voiceEnabled,
                            onClick = {
                                val next = !voiceEnabled
                                voiceEnabled = next
                                if (!next) {
                                    runCatching { ttsRef?.stop() }
                                } else {
                                    messages.lastOrNull { it.sender != "user" }?.let { speakChibiText(it.text) }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (voiceEnabled) {
                                        Icons.AutoMirrored.Rounded.VolumeUp
                                    } else {
                                        Icons.AutoMirrored.Rounded.VolumeOff
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    stringResource(
                                        if (voiceEnabled) R.string.chat_voice_on else R.string.chat_voice_off
                                    )
                                )
                            },
                            modifier = Modifier.testTag("chat_voice_chip")
                        )
                        FilterChip(
                            selected = ecoLiteModeEnabled,
                            onClick = { onToggleEcoLiteMode(!ecoLiteModeEnabled) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    stringResource(
                                        if (ecoLiteModeEnabled) R.string.chat_eco_on else R.string.chat_eco_off
                                    )
                                )
                            },
                            modifier = Modifier.testTag("chat_eco_lite_chip")
                        )
                        IconButton(
                            onClick = onClearChat,
                            enabled = messages.isNotEmpty() && !isSending
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.chat_clear_cd)
                            )
                        }
                    }
                }
            }

            if (chatError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = chatError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismissChatError) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.chat_dismiss_error_cd),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = onSendLocalReflex,
                                modifier = Modifier.testTag("chat_local_reflex_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.dialog_proof_retry_lite))
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty() && streamingReply == null) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.chat_empty_greeting, petName),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                }

                items(messages, key = { it.id.ifBlank { "${it.sender}_${it.text.hashCode()}" } }) { msg ->
                    ChatBubble(
                        sender = msg.sender,
                        text = msg.text,
                        habitTag = msg.habitTag,
                        onSpeakClick = if (msg.sender != "user") {
                            { speakChibiText(msg.text) }
                        } else {
                            null
                        }
                    )
                }

                if (streamingReply != null) {
                    item(key = "streaming_bubble") {
                        if (streamingReply.isEmpty()) {
                            TypingIndicatorBubble()
                        } else {
                            ChatBubble(
                                sender = "pet",
                                text = streamingReply,
                                habitTag = null,
                                onSpeakClick = null
                            )
                        }
                    }
                }
            }

            val prompts = listOf(
                stringResource(R.string.chat_prompt_1),
                stringResource(R.string.chat_prompt_2),
                stringResource(R.string.chat_prompt_3)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(prompts) { suggestion ->
                    AssistChip(
                        onClick = { onSendMessage(suggestion) },
                        enabled = !isSending,
                        label = { Text(suggestion) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(1000) },
                    placeholder = { Text(stringResource(R.string.chat_input_placeholder, petName)) },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val toSend = draft
                        draft = ""
                        onSendMessage(toSend)
                    },
                    enabled = draft.isNotBlank() && !isSending,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("send_chat_button")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.chat_send_cd)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicatorBubble() {
    val transition = rememberInfiniteTransition(label = "typing")
    val offsets = List(3) { index ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = -5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420, delayMillis = index * 120),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_$index"
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 6.dp,
                bottomEnd = 20.dp
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                offsets.forEach { offsetY ->
                    Box(
                        modifier = Modifier
                            .offset(y = offsetY.value.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    sender: String,
    text: String,
    habitTag: String?,
    onSpeakClick: (() -> Unit)? = null
) {
    val isUser = sender == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            },
            tonalElevation = if (isUser) 0.dp else 2.dp,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            ),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!habitTag.isNullOrBlank()) {
                    Text(
                        text = "#$habitTag",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (onSpeakClick != null) {
            IconButton(
                onClick = onSpeakClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = stringResource(R.string.chat_speak_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

