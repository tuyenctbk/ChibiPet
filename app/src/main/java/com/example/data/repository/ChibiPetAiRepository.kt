package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import com.example.data.local.AiQuotaManager
import com.example.data.model.ChatMessage
import com.example.data.model.Habit
import com.example.data.model.HabitCategory
import com.example.data.model.HabitVerificationResult
import com.example.data.model.PetOutfit
import com.example.data.model.PetProfile
import com.example.data.model.SmartQuestCoachPlan
import com.example.data.model.SpeciesColor
import com.example.data.model.SuggestedQuest
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale
import kotlin.math.max

class AiQuotaExceededException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class ChibiPetAiRepository {

    private val verificationSchema = Schema.obj(
        properties = mapOf(
            "verified" to Schema.boolean(
                description = "True if the photo reasonably depicts real-world proof related to the habit."
            ),
            "confidence" to Schema.integer(
                description = "Confidence score from 0 to 100."
            ),
            "detectedItem" to Schema.string(
                description = "Short description of what is visible in the photo (max 80 chars)."
            ),
            "petReaction" to Schema.string(
                description = "Cute, encouraging 1-2 sentence in-character reaction from the ChibiPet."
            )
        )
    )

    private val questCoachSchema = Schema.obj(
        properties = mapOf(
            "coachMessage" to Schema.string(
                description = "Encouraging 1-2 sentence coaching tip from the ChibiPet tailored to the user's goal."
            ),
            "quests" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(
                            description = "Concise, actionable daily habit quest title (max 50 chars)."
                        ),
                        "category" to Schema.enumeration(
                            values = listOf("hydration", "study", "fitness", "mindfulness", "creative"),
                            description = "Habit category key."
                        ),
                        "xpReward" to Schema.integer(
                            description = "Recommended XP reward: 20 for quick 2-min habits, 35 for daily routines, or 60 for deep focus."
                        ),
                        "reason" to Schema.string(
                            description = "Short 1-sentence reason why this micro-habit helps achieve the goal."
                        )
                    )
                ),
                description = "Exactly 3 bite-sized daily quests tailored to the user's goal."
            )
        )
    )

    private val visionVerificationFlashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FLASH_PRIMARY,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = verificationSchema
                temperature = 0.3f
            }
        )
    }

    private val visionVerificationLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FLASH_LITE_FALLBACK,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = verificationSchema
                temperature = 0.3f
            }
        )
    }

    private val questCoachFlashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FLASH_PRIMARY,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = questCoachSchema
                temperature = 0.5f
            }
        )
    }

    private val questCoachLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FLASH_LITE_FALLBACK,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = questCoachSchema
                temperature = 0.5f
            }
        )
    }

    private val imageStudioPrimaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_IMAGE_PRIMARY,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val imageStudioLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_IMAGE_LITE_FALLBACK,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    // Short-lived deduplication cache so accidental double-submits don't burn free-tier quota
    private var lastVerificationCacheKey: String? = null
    private var lastVerificationCachedAt: Long = 0L
    private var lastVerificationCachedResult: HabitVerificationResult? = null

    private var lastCoachGoalKey: String? = null
    private var lastCoachCachedAt: Long = 0L
    private var lastCoachCachedPlan: SmartQuestCoachPlan? = null

    suspend fun verifyHabitProof(
        bitmap: Bitmap,
        habit: Habit,
        petName: String,
        preferLiteModel: Boolean = false,
        onModelSelected: (usedLiteFallback: Boolean) -> Unit = {}
    ): Result<HabitVerificationResult> {
        return try {
            val cacheKey = "${habit.id}_${bitmap.width}x${bitmap.height}_${bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)}"
            val now = System.currentTimeMillis()
            val cached = lastVerificationCachedResult
            if (cacheKey == lastVerificationCacheKey && cached != null && now - lastVerificationCachedAt < 45_000L) {
                return Result.success(cached)
            }

            val maxDim = if (preferLiteModel) 768 else 1024
            val scaledBitmap = scaleBitmapForAi(bitmap, maxDimension = maxDim)
            val category = HabitCategory.fromKey(habit.category)
            val prompt = """
                You are $petName, a cheerful 3D pocket companion helping your owner build healthy habits.
                Your owner submitted this photo as proof for the habit:
                - Habit title: "${habit.title}"
                - Category: ${category.label} (expected examples: ${category.proofHint})

                Inspect the photo fairly and encouragingly:
                - Mark verified=true if the image genuinely shows an object, activity, or workspace connected to completing this habit (e.g., water bottle/cup/glass for hydration, book/notebook/desk/laptop for study, sneakers/gym/weights/mat/outdoors walk for fitness, journal/tea/calm corner for mindfulness, art/instrument/craft for creative).
                - Mark verified=false if the photo is completely unrelated, blurry blank wall/black screen, or clearly not matching the habit.
                Return JSON matching the schema with a playful reaction in $petName's voice.
            """.trimIndent()

            val response = try {
                val primaryModel = if (preferLiteModel) visionVerificationLiteModel else visionVerificationFlashModel
                onModelSelected(preferLiteModel)
                withTimeout(AI_TIMEOUT_MS) {
                    primaryModel.generateContent(
                        content {
                            image(scaledBitmap)
                            text(prompt)
                        }
                    )
                }
            } catch (primaryErr: Exception) {
                if (primaryErr is CancellationException && primaryErr !is TimeoutCancellationException) {
                    throw primaryErr
                }
                if (!preferLiteModel && AiQuotaManager.isQuotaOrRateLimitError(primaryErr)) {
                    Log.w(TAG, "Primary vision model hit quota/rate-limit; cascading to $MODEL_FLASH_LITE_FALLBACK", primaryErr)
                    delay(650L)
                    onModelSelected(true)
                    withTimeout(AI_TIMEOUT_MS) {
                        visionVerificationLiteModel.generateContent(
                            content {
                                image(scaledBitmap)
                                text(prompt)
                            }
                        )
                    }
                } else {
                    throw primaryErr
                }
            }

            val rawJson = response.text
                ?: throw IllegalStateException("Gemini returned an empty verification response.")
            val json = JSONObject(rawJson)
            val result = HabitVerificationResult(
                verified = json.optBoolean("verified", false),
                confidence = json.optInt("confidence", 0).coerceIn(0, 100),
                detectedItem = json.optString("detectedItem", "Photo analyzed"),
                petReaction = json.optString(
                    "petReaction",
                    "Thanks for showing me!"
                )
            )
            lastVerificationCacheKey = cacheKey
            lastVerificationCachedAt = System.currentTimeMillis()
            lastVerificationCachedResult = result
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Vision verification timed out", e)
            Result.failure(Exception("Photo check timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vision verification failed", e)
            if (AiQuotaManager.isQuotaOrRateLimitError(e)) {
                Result.failure(
                    AiQuotaExceededException(
                        "Free-tier AI vision quota reached for this minute. You can retry with Flash-Lite or use Honor Pledge Check-In to protect your streak!",
                        e
                    )
                )
            } else {
                Result.failure(e)
            }
        }
    }

    fun streamPetReply(
        userMessage: String,
        history: List<ChatMessage>,
        petProfile: PetProfile,
        habits: List<Habit>,
        todayIsoDate: String,
        preferLiteModel: Boolean = false,
        onModelSelected: (usedLiteFallback: Boolean) -> Unit = {}
    ): Flow<String> = flow {
        val completedCount = habits.count { it.isCompletedOn(todayIsoDate) }
        val pendingHabits = habits.filterNot { it.isCompletedOn(todayIsoDate) }
            .joinToString(", ") { it.title }
            .ifBlank { "All done for today!" }
        val outfitLabel = PetOutfit.fromKey(petProfile.equippedOutfit).label

        val systemInstruction = content {
            text(
                """
                You are ${petProfile.petName}, an adorable, expressive 3D ChibiPet living on the user's phone!
                Current stats:
                - Level: ${petProfile.level} (${petProfile.xp}/${petProfile.xpForNextLevel} XP)
                - Happiness: ${petProfile.happiness}/100, Energy: ${petProfile.energy}/100
                - Equipped outfit: $outfitLabel
                - Habits completed today: $completedCount of ${habits.size}
                - Still remaining today: $pendingHabits

                Personality & Rules:
                - Be warm, playful, affectionate, and genuinely encouraging without being preachy.
                - Remember details from prior messages in our chat history!
                - Keep replies concise (2-4 sentences) and easy to read on mobile.
                - Celebrate progress when habits are done, or cheer them on gently for remaining habits.
                """.trimIndent()
            )
        }

        // In Eco Lite Mode, trim sliding context window to 8 messages to save ~55% input tokens
        val historyWindowSize = if (preferLiteModel) 8 else 18
        val recentHistory = history
            .filter { it.text.isNotBlank() }
            .takeLast(historyWindowSize)
            .map { msg ->
                val role = if (msg.sender == "user") "user" else "model"
                content(role) { text(msg.text) }
            }

        val accumulated = StringBuilder()

        suspend fun streamWithModel(modelName: String) {
            val chatModel = Firebase.ai.generativeModel(
                modelName = modelName,
                systemInstruction = systemInstruction
            )
            val chat = chatModel.startChat(history = recentHistory)
            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(userMessage).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
        }

        try {
            val initialModel = if (preferLiteModel) MODEL_FLASH_LITE_FALLBACK else MODEL_FLASH_PRIMARY
            onModelSelected(preferLiteModel)
            streamWithModel(initialModel)
        } catch (primaryErr: Exception) {
            if (primaryErr is CancellationException && primaryErr !is TimeoutCancellationException) {
                throw primaryErr
            }
            if (!preferLiteModel && accumulated.isEmpty() && AiQuotaManager.isQuotaOrRateLimitError(primaryErr)) {
                Log.w(TAG, "Primary chat model hit quota/rate-limit; cascading to $MODEL_FLASH_LITE_FALLBACK", primaryErr)
                delay(500L)
                onModelSelected(true)
                streamWithModel(MODEL_FLASH_LITE_FALLBACK)
            } else if (AiQuotaManager.isQuotaOrRateLimitError(primaryErr)) {
                throw AiQuotaExceededException(
                    "Free-tier AI chat rate limit reached. Switch on Eco Lite Mode or tap 'Quick Pet Reflex' while cloud quota cools down!",
                    primaryErr
                )
            } else {
                throw primaryErr
            }
        }

        if (accumulated.isBlank()) {
            throw IllegalStateException("ChibiPet returned an empty reply.")
        }
    }

    suspend fun generateOrEditPetArt(
        promptText: String,
        referenceBitmap: Bitmap?,
        petProfile: PetProfile,
        preferLiteModel: Boolean = false,
        onModelSelected: (usedLiteFallback: Boolean) -> Unit = {}
    ): Result<Bitmap> {
        return try {
            val outfitLabel = PetOutfit.fromKey(petProfile.equippedOutfit).label
            val enrichedPrompt = buildString {
                append("Create a cute 3D vinyl toy style ChibiPet artwork featuring ")
                append("${petProfile.petName} (a pastel ${petProfile.speciesColor} chibi cloud-fox mascot wearing $outfitLabel). ")
                append("User creative direction: $promptText. ")
                append("Keep it vibrant, polished, whimsical, and heartwarming.")
            }

            val response = try {
                val model = if (preferLiteModel) imageStudioLiteModel else imageStudioPrimaryModel
                onModelSelected(preferLiteModel)
                withTimeout(AI_TIMEOUT_MS) {
                    if (referenceBitmap != null) {
                        val scaled = scaleBitmapForAi(referenceBitmap, maxDimension = if (preferLiteModel) 768 else 1024)
                        model.generateContent(
                            content {
                                image(scaled)
                                text(enrichedPrompt)
                            }
                        )
                    } else {
                        model.generateContent(enrichedPrompt)
                    }
                }
            } catch (primaryErr: Exception) {
                if (primaryErr is CancellationException && primaryErr !is TimeoutCancellationException) {
                    throw primaryErr
                }
                if (!preferLiteModel && AiQuotaManager.isQuotaOrRateLimitError(primaryErr)) {
                    Log.w(TAG, "Primary image model hit quota/rate-limit; cascading to $MODEL_IMAGE_LITE_FALLBACK", primaryErr)
                    delay(700L)
                    onModelSelected(true)
                    withTimeout(AI_TIMEOUT_MS) {
                        if (referenceBitmap != null) {
                            val scaled = scaleBitmapForAi(referenceBitmap, maxDimension = 768)
                            imageStudioLiteModel.generateContent(
                                content {
                                    image(scaled)
                                    text(enrichedPrompt)
                                }
                            )
                        } else {
                            imageStudioLiteModel.generateContent(enrichedPrompt)
                        }
                    }
                } else {
                    throw primaryErr
                }
            }

            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()

            val bitmap = imagePart?.image
                ?: throw IllegalStateException("No image was returned by the model.")
            Result.success(bitmap)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            Result.failure(Exception("Studio art generation timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            if (AiQuotaManager.isQuotaOrRateLimitError(e)) {
                Result.failure(
                    AiQuotaExceededException(
                        "Cloud AI Image quota or free-tier limit reached (${e.localizedMessage ?: "429"}). You can craft an instant 'Chibi Collectible Emblem Card' below with zero cloud quota!",
                        e
                    )
                )
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Generates a rich, zero-token in-character reply grounded in the user's real live Firestore stats
     * when cloud AI chat quota is temporarily cooling down.
     */
    fun buildLocalReflexReply(
        userMessage: String,
        petProfile: PetProfile,
        habits: List<Habit>,
        todayIsoDate: String
    ): String {
        val completedToday = habits.count { it.isCompletedOn(todayIsoDate) }
        val pending = habits.filterNot { it.isCompletedOn(todayIsoDate) }
        val topStreak = habits.maxOfOrNull { it.streakCount } ?: 0L
        val lower = userMessage.lowercase(Locale.US)

        return when {
            completedToday == habits.size && habits.isNotEmpty() ->
                "*happy ear wiggle!* You finished all ${habits.size} quests today and unlocked our Daily Sweep Chest! My mood is ${petProfile.happiness}% and I'm so proud of our ${topStreak}-day top streak!"
            "next" in lower || "habit" in lower || "todo" in lower -> {
                val nextHabit = pending.firstOrNull()
                if (nextHabit != null) {
                    "Let's tackle \"${nextHabit.title}\" next for +${nextHabit.xpReward} XP! Snap a quick photo when you're done and I'll do my victory backflip!"
                } else {
                    "We've already crushed every quest on our list today! Time to relax or try on outfits in the Boutique!"
                }
            }
            "boost" in lower || "cheer" in lower || "tired" in lower ->
                "*nuzzles your hand* You've already completed ${petProfile.totalCompleted} verified habits with me at Level ${petProfile.level}! Take a deep breath—even one small step today keeps our spark glowing!"
            else -> {
                val remainingNote = if (pending.isNotEmpty()) {
                    "We have ${pending.size} quest(s) left today (like \"${pending.first().title}\")."
                } else {
                    "All of today's quests are complete!"
                }
                "*boops your screen!* I'm resting my cloud antenna for a moment (Eco Reflex Mode), but I'm right here with you at Lv.${petProfile.level}! $remainingNote"
            }
        }
    }

    /**
     * Renders a high-resolution 800x800 collectible ChibiPet Emblem Sticker on-device using Canvas
     * when cloud image generation hits a free-tier or billing limit.
     */
    fun createOfflineCollectibleCardBitmap(
        petProfile: PetProfile,
        promptText: String
    ): Bitmap {
        val size = 800
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val species = SpeciesColor.fromKey(petProfile.speciesColor)
        val outfit = PetOutfit.fromKey(petProfile.equippedOutfit)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(0xFF1F192E.toInt(), 0xFF31264B.toInt(), species.primaryHex.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Outer gold collector frame
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = 0xFFFFD166.toInt()
        }
        canvas.drawRoundRect(RectF(32f, 32f, size - 32f, size - 32f), 48f, 48f, framePaint)

        // Glowing halo behind mascot
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFD166
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, 330f, 185f, haloPaint)

        // Chibi ears
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = species.primaryHex.toInt()
            style = Paint.Style.FILL
        }
        val innerEarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = species.cheekHex.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawOval(RectF(230f, 140f, 330f, 270f), bodyPaint)
        canvas.drawOval(RectF(470f, 140f, 570f, 270f), bodyPaint)
        canvas.drawOval(RectF(252f, 165f, 312f, 248f), innerEarPaint)
        canvas.drawOval(RectF(488f, 165f, 548f, 248f), innerEarPaint)

        // Chibi head & body
        canvas.drawRoundRect(RectF(220f, 210f, 580f, 495f), 160f, 160f, bodyPaint)

        // Belly patch
        val bellyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = species.bellyHex.toInt()
        }
        canvas.drawOval(RectF(295f, 370f, 505f, 485f), bellyPaint)

        // Expressive eyes & blush
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF231942.toInt() }
        val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88FF758F.toInt() }

        canvas.drawCircle(330f, 320f, 24f, eyePaint)
        canvas.drawCircle(470f, 320f, 24f, eyePaint)
        canvas.drawCircle(322f, 312f, 8f, shinePaint)
        canvas.drawCircle(462f, 312f, 8f, shinePaint)
        canvas.drawOval(RectF(265f, 340f, 315f, 368f), blushPaint)
        canvas.drawOval(RectF(485f, 340f, 535f, 368f), blushPaint)

        // Cute nose
        canvas.drawCircle(400f, 342f, 10f, eyePaint)

        // Banner plaque at bottom
        val plaquePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6221C35.toInt()
        }
        canvas.drawRoundRect(RectF(64f, 535f, size - 64f, 725f), 36f, 36f, plaquePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD166.toInt()
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${petProfile.petName} • Lv.${petProfile.level}", size / 2f, 595f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEAE2FF.toInt()
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Outfit: ${outfit.label} • Coat: ${species.label}", size / 2f, 640f, subPaint)

        val caption = promptText.trim().take(46).ifEmpty { "ChibiPet Collector Edition" }
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB8C0FF.toInt()
            textSize = 23f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("\"$caption\"", size / 2f, 688f, captionPaint)

        return bmp
    }

    /**
     * AI Smart Quest Coach: Generates 3 structured daily micro-quests tailored to the user's goal
     * using `Schema.obj` JSON output on `gemini-3.8-flash`, automatically cascading to
     * `gemini-3.5-flash-lite` on rate limits or Eco Lite Mode.
     */
    suspend fun generateSmartQuestPlan(
        userGoal: String,
        petProfile: PetProfile,
        existingHabits: List<Habit>,
        preferLiteModel: Boolean = false,
        onModelSelected: (usedLiteFallback: Boolean) -> Unit = {}
    ): Result<SmartQuestCoachPlan> {
        val cleanGoal = userGoal.trim().ifEmpty { "Build balanced daily energy and focus" }
        val normalizedKey = cleanGoal.lowercase(Locale.US)
        val now = System.currentTimeMillis()
        val cached = lastCoachCachedPlan
        if (normalizedKey == lastCoachGoalKey && cached != null && now - lastCoachCachedAt < 60_000L) {
            return Result.success(cached)
        }

        val existingList = existingHabits.joinToString(", ") { "\"${it.title}\" (${it.category})" }
            .ifBlank { "None yet" }

        val prompt = """
            You are ${petProfile.petName} (Level ${petProfile.level}), an encouraging 3D ChibiPet habit coach.
            Your owner wants to achieve this personal goal: "$cleanGoal"
            Existing habits already tracked (do not duplicate these): $existingList

            Design 3 specific, realistic daily micro-quests that can be verified with a quick photo or daily check-in.
            Use xpReward values from [20, 35, 60] and categories from [hydration, study, fitness, mindfulness, creative].
        """.trimIndent()

        return try {
            val response = try {
                val model = if (preferLiteModel) questCoachLiteModel else questCoachFlashModel
                onModelSelected(preferLiteModel)
                withTimeout(AI_TIMEOUT_MS) {
                    model.generateContent(prompt)
                }
            } catch (primaryErr: Exception) {
                if (primaryErr is CancellationException && primaryErr !is TimeoutCancellationException) {
                    throw primaryErr
                }
                if (!preferLiteModel && AiQuotaManager.isQuotaOrRateLimitError(primaryErr)) {
                    Log.w(TAG, "Primary quest coach hit quota/rate-limit; cascading to $MODEL_FLASH_LITE_FALLBACK", primaryErr)
                    delay(500L)
                    onModelSelected(true)
                    withTimeout(AI_TIMEOUT_MS) {
                        questCoachLiteModel.generateContent(prompt)
                    }
                } else {
                    throw primaryErr
                }
            }

            val rawJson = response.text
                ?: throw IllegalStateException("Quest Coach returned an empty response.")
            val json = JSONObject(rawJson)
            val coachMsg = json.optString(
                "coachMessage",
                "${petProfile.petName} crafted 3 bite-sized quests to help you reach your goal!"
            )
            val questsArray = json.optJSONArray("quests")
            val parsedQuests = mutableListOf<SuggestedQuest>()
            if (questsArray != null) {
                for (i in 0 until questsArray.length()) {
                    val item = questsArray.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim().take(60)
                    if (title.isEmpty()) continue
                    val catKey = item.optString("category", "hydration")
                    val rawXp = item.optInt("xpReward", 35)
                    val normalizedXp = when {
                        rawXp <= 25 -> 20L
                        rawXp >= 50 -> 60L
                        else -> 35L
                    }
                    val reason = item.optString("reason", "Builds steady daily momentum.")
                    parsedQuests.add(
                        SuggestedQuest(
                            title = title,
                            category = HabitCategory.fromKey(catKey),
                            xpReward = normalizedXp,
                            reason = reason
                        )
                    )
                }
            }

            if (parsedQuests.isEmpty()) {
                throw IllegalStateException("Could not parse suggested quests.")
            }

            val plan = SmartQuestCoachPlan(
                goalSummary = cleanGoal,
                coachMessage = coachMsg,
                quests = parsedQuests.take(3),
                isLocalFallback = false
            )
            lastCoachGoalKey = normalizedKey
            lastCoachCachedAt = System.currentTimeMillis()
            lastCoachCachedPlan = plan
            Result.success(plan)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Quest coach timed out", e)
            Result.failure(Exception("AI Quest Coach timed out. Tap 'Instant Local Plan' for zero-wait suggestions!"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Quest coach generation failed", e)
            if (AiQuotaManager.isQuotaOrRateLimitError(e)) {
                Result.failure(
                    AiQuotaExceededException(
                        "Cloud AI quota reached for this minute. Tap 'Instant Local Plan (0 Quota)' below to generate your custom quest pack immediately!",
                        e
                    )
                )
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Zero-Quota On-Device Smart Quest Planner: Analyzes the user's goal keywords and current habits
     * to synthesize 3 non-duplicate, category-balanced quests when cloud AI quota is exhausted or offline.
     */
    fun buildLocalSmartQuestPlan(
        userGoal: String,
        petProfile: PetProfile,
        existingHabits: List<Habit>
    ): SmartQuestCoachPlan {
        val cleanGoal = userGoal.trim().ifEmpty { "Balanced Daily Routine" }
        val lower = cleanGoal.lowercase(Locale.US)
        val existingTitlesLower = existingHabits.map { it.title.lowercase(Locale.US) }.toSet()

        val candidates: List<SuggestedQuest> = when {
            "sleep" in lower || "calm" in lower || "stress" in lower || "night" in lower || "relax" in lower -> listOf(
                SuggestedQuest(
                    title = "Brew herbal tea & dim screens 30m before bed",
                    category = HabitCategory.MINDFULNESS,
                    xpReward = 35L,
                    reason = "Signals your nervous system to wind down for deeper rest."
                ),
                SuggestedQuest(
                    title = "Write 3 gratitude lines in evening journal",
                    category = HabitCategory.MINDFULNESS,
                    xpReward = 20L,
                    reason = "Clears mental clutter before sleep."
                ),
                SuggestedQuest(
                    title = "Morning glass of water by the window",
                    category = HabitCategory.HYDRATION,
                    xpReward = 20L,
                    reason = "Rehydrates your body and anchors your circadian rhythm."
                )
            )
            "study" in lower || "exam" in lower || "learn" in lower || "read" in lower || "focus" in lower || "work" in lower -> listOf(
                SuggestedQuest(
                    title = "25-minute Pomodoro deep study sprint",
                    category = HabitCategory.STUDY,
                    xpReward = 60L,
                    reason = "High-focus block without phone distractions."
                ),
                SuggestedQuest(
                    title = "Read 10 pages & summarize 1 key takeaway",
                    category = HabitCategory.STUDY,
                    xpReward = 35L,
                    reason = "Active recall strengthens long-term memory."
                ),
                SuggestedQuest(
                    title = "Keep a full water bottle on study desk",
                    category = HabitCategory.HYDRATION,
                    xpReward = 20L,
                    reason = "Steady hydration prevents afternoon focus dips."
                )
            )
            "fit" in lower || "run" in lower || "gym" in lower || "walk" in lower || "energy" in lower || "workout" in lower -> listOf(
                SuggestedQuest(
                    title = "15-minute brisk outdoor walk or jog",
                    category = HabitCategory.FITNESS,
                    xpReward = 35L,
                    reason = "Boosts circulation, mood, and daily stamina."
                ),
                SuggestedQuest(
                    title = "10-minute full-body stretch & mobility flow",
                    category = HabitCategory.FITNESS,
                    xpReward = 20L,
                    reason = "Releases posture tension and protects joints."
                ),
                SuggestedQuest(
                    title = "Post-movement electrolyte or water refill",
                    category = HabitCategory.HYDRATION,
                    xpReward = 20L,
                    reason = "Replenishes hydration after active movement."
                )
            )
            "art" in lower || "draw" in lower || "music" in lower || "write" in lower || "creat" in lower -> listOf(
                SuggestedQuest(
                    title = "15-minute daily sketch, instrument, or craft session",
                    category = HabitCategory.CREATIVE,
                    xpReward = 35L,
                    reason = "Builds creative muscle memory through daily play."
                ),
                SuggestedQuest(
                    title = "Capture 1 inspiring idea or doodle in notebook",
                    category = HabitCategory.CREATIVE,
                    xpReward = 20L,
                    reason = "Lowers perfectionism and keeps inspiration flowing."
                ),
                SuggestedQuest(
                    title = "5-minute mindful breathing before creating",
                    category = HabitCategory.MINDFULNESS,
                    xpReward = 20L,
                    reason = "Quiets inner critic for joyful creative flow."
                )
            )
            else -> listOf(
                SuggestedQuest(
                    title = "Drink 1 full glass of water after waking up",
                    category = HabitCategory.HYDRATION,
                    xpReward = 20L,
                    reason = "Easy 2-minute keystone win to start the day."
                ),
                SuggestedQuest(
                    title = "20-minute focused progress on \"$cleanGoal\"",
                    category = HabitCategory.STUDY,
                    xpReward = 60L,
                    reason = "Dedicated daily block directly advancing your goal."
                ),
                SuggestedQuest(
                    title = "10-minute outdoor walk & stretch break",
                    category = HabitCategory.FITNESS,
                    xpReward = 35L,
                    reason = "Refreshes physical and mental energy."
                )
            )
        }

        val filtered = candidates.filter { it.title.lowercase(Locale.US) !in existingTitlesLower }
        return SmartQuestCoachPlan(
            goalSummary = cleanGoal,
            coachMessage = "*happy ear wiggle!* ${petProfile.petName} crafted this instant Eco Coach plan for \"$cleanGoal\" (0 Cloud Quota used)!",
            quests = filtered.ifEmpty { candidates },
            isLocalFallback = true
        )
    }

    private fun scaleBitmapForAi(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longest = max(width, height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val targetW = (width * ratio).toInt().coerceAtLeast(1)
        val targetH = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    companion object {
        private const val TAG = "ChibiPetAiRepo"
        private const val AI_TIMEOUT_MS = 60_000L
        const val MODEL_FLASH_PRIMARY = "gemini-3.8-flash"
        const val MODEL_FLASH_LITE_FALLBACK = "gemini-3.5-flash-lite"
        const val MODEL_IMAGE_PRIMARY = "gemini-nano-banana-2.1"
        const val MODEL_IMAGE_LITE_FALLBACK = "gemini-3.1-flash-lite-image"
    }
}
