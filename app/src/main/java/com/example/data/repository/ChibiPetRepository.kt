package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.FriendLink
import com.example.data.model.Habit
import com.example.data.model.HabitCategory
import com.example.data.model.HabitCompletionOutcome
import com.example.data.model.PetCard
import com.example.data.model.PetCheer
import com.example.data.model.PetOutfit
import com.example.data.model.PetProfile
import com.example.data.model.ShopConsumable
import com.example.data.model.SpeciesColor
import com.example.data.model.buddyCodeForUid
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class ChibiPetRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observePetProfile(userId: String): Flow<PetProfile?> = flow {
        val docRef = db.collection("users").document(userId)
            .collection("petProfile").document(MAIN_PROFILE_ID)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(PetProfile::class.java, ServerTimestampBehavior.ESTIMATE)
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
        )
    }

    fun observeHabits(userId: String): Flow<List<Habit>> = flow {
        val colRef = db.collection("users").document(userId).collection("habits")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Habit::class.java, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeChatMessages(userId: String): Flow<List<ChatMessage>> = flow {
        val colRef = db.collection("users").document(userId).collection("chatMessages")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ChatMessage::class.java, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeFriends(userId: String): Flow<List<FriendLink>> = flow {
        val colRef = db.collection("users").document(userId).collection("friends")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(FriendLink::class.java, ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeIncomingCheers(userId: String): Flow<List<PetCheer>> = flow {
        val code = buddyCodeForUid(userId)
        val colRef = db.collection("petCards").document(code).collection("cheers")
        emitAll(
            colRef.whereEqualTo("toUserId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PetCheer::class.java, ServerTimestampBehavior.ESTIMATE)
                        .filter { !it.claimed }
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureInitializedForUser(
        displayName: String?,
        initialPetName: String = "Mochi",
        initialColor: SpeciesColor = SpeciesColor.MINT,
        initialQuests: List<Triple<String, String, Long>> = listOf(
            Triple("Drink Water (500ml)", HabitCategory.HYDRATION.key, 25L),
            Triple("Focus Study Session", HabitCategory.STUDY.key, 40L),
            Triple("Move & Stretch", HabitCategory.FITNESS.key, 35L)
        )
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanPetName = initialPetName.trim().take(30).ifEmpty { "Mochi" }
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
        val profileSnap = try {
            profileRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, profileRef.path)
            throw e
        }

        if (!profileSnap.exists()) {
            val petPayload = mapOf(
                "userId" to uid,
                "petName" to cleanPetName,
                "speciesColor" to initialColor.key,
                "equippedOutfit" to PetOutfit.BOW.key,
                "unlockedOutfits" to listOf(PetOutfit.NONE.key, PetOutfit.BOW.key),
                "level" to 1L,
                "xp" to 0L,
                "coins" to 50L,
                "happiness" to 85L,
                "energy" to 90L,
                "totalCompleted" to 0L,
                "streakFreezes" to 1L,
                "lastDailyBonusDate" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                profileRef.set(petPayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, profileRef.path)
                throw e
            }

            val starterHabits = initialQuests.ifEmpty {
                listOf(
                    Triple("Drink Water (500ml)", HabitCategory.HYDRATION.key, 25L),
                    Triple("Focus Study Session", HabitCategory.STUDY.key, 40L),
                    Triple("Move & Stretch", HabitCategory.FITNESS.key, 35L)
                )
            }
            val habitsCol = db.collection("users").document(uid).collection("habits")
            for ((title, category, xp) in starterHabits) {
                val habitRef = habitsCol.document()
                val payload = mapOf(
                    "userId" to uid,
                    "title" to title,
                    "category" to category,
                    "xpReward" to xp,
                    "streakCount" to 0L,
                    "lastVerifiedDate" to "",
                    "lastVerificationNote" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                try {
                    habitRef.set(payload).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, habitRef.path)
                    throw e
                }
            }

            val firstName = displayName?.substringBefore(" ")?.takeIf { it.isNotBlank() } ?: "friend"
            addChatMessage(
                sender = "pet",
                text = "Yay, $firstName! I'm $cleanPetName, your pocket 3D buddy! Snap a proof photo of your water bottle, study desk, or workout gear to help me level up!",
                habitTag = "welcome"
            ).getOrThrow()

            syncMyPetCard(
                profile = PetProfile(
                    userId = uid,
                    petName = cleanPetName,
                    speciesColor = initialColor.key,
                    equippedOutfit = PetOutfit.BOW.key,
                    level = 1L,
                    totalCompleted = 0L
                ),
                topStreak = 0L
            )
        }
    }

    suspend fun syncMyPetCard(profile: PetProfile, topStreak: Long): Result<Unit> = runCatching {
        val uid = requireUserId()
        val code = buddyCodeForUid(uid)
        val cardRef = db.collection("petCards").document(code)
        val payload = mapOf(
            "userId" to uid,
            "buddyCode" to code,
            "petName" to profile.petName.take(30).ifBlank { "Mochi" },
            "speciesColor" to SpeciesColor.fromKey(profile.speciesColor).key,
            "equippedOutfit" to PetOutfit.fromKey(profile.equippedOutfit).key,
            "level" to profile.level.coerceIn(1L, 999L),
            "totalCompleted" to profile.totalCompleted.coerceIn(0L, 1_000_000L),
            "topStreak" to topStreak.coerceIn(0L, 10_000L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            cardRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, cardRef.path)
            throw e
        }
    }

    suspend fun addFriendByCode(rawCode: String): Result<FriendLink> = runCatching {
        val uid = requireUserId()
        val code = rawCode.trim().uppercase(Locale.US)
        require(code.matches(Regex("^[A-Z0-9_\\-]{4,32}$"))) {
            "Enter a valid Buddy Code (e.g. PET-A1B2C3)."
        }
        val myCode = buddyCodeForUid(uid)
        require(code != myCode) { "That's your own Buddy Code! Share it with a friend instead." }

        val cardRef = db.collection("petCards").document(code)
        val cardSnap = try {
            cardRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, cardRef.path)
            throw e
        }
        val card = cardSnap.toObject(PetCard::class.java)
            ?: throw IllegalArgumentException("No ChibiPet found with code $code.")

        val friendRef = db.collection("users").document(uid).collection("friends").document(code)
        val existingSnap = try {
            friendRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, friendRef.path)
            throw e
        }

        if (existingSnap.exists()) {
            friendRef.update(
                mapOf(
                    "friendPetName" to card.petName,
                    "friendSpeciesColor" to card.speciesColor,
                    "friendOutfit" to card.equippedOutfit,
                    "friendLevel" to card.level,
                    "friendTopStreak" to card.topStreak,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            val payload = mapOf(
                "userId" to uid,
                "friendCode" to code,
                "friendUserId" to card.userId,
                "friendPetName" to card.petName,
                "friendSpeciesColor" to card.speciesColor,
                "friendOutfit" to card.equippedOutfit,
                "friendLevel" to card.level,
                "friendTopStreak" to card.topStreak,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                friendRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, friendRef.path)
                throw e
            }
        }

        FriendLink(
            id = code,
            userId = uid,
            friendCode = code,
            friendUserId = card.userId,
            friendPetName = card.petName,
            friendSpeciesColor = card.speciesColor,
            friendOutfit = card.equippedOutfit,
            friendLevel = card.level,
            friendTopStreak = card.topStreak
        )
    }

    suspend fun refreshFriends(friends: List<FriendLink>): Result<Unit> = runCatching {
        val uid = requireUserId()
        for (friend in friends) {
            val cardRef = db.collection("petCards").document(friend.friendCode)
            val cardSnap = try {
                cardRef.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            val card = cardSnap.toObject(PetCard::class.java) ?: continue
            val friendRef = db.collection("users").document(uid)
                .collection("friends").document(friend.friendCode)
            try {
                friendRef.update(
                    mapOf(
                        "friendPetName" to card.petName,
                        "friendSpeciesColor" to card.speciesColor,
                        "friendOutfit" to card.equippedOutfit,
                        "friendLevel" to card.level,
                        "friendTopStreak" to card.topStreak,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, friendRef.path)
                throw e
            }
        }
    }

    suspend fun removeFriend(friendCode: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val friendRef = db.collection("users").document(uid).collection("friends").document(friendCode)
        try {
            friendRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, friendRef.path)
            throw e
        }
    }

    suspend fun sendCheerToFriend(
        friendCode: String,
        friendUserId: String,
        myPetName: String,
        message: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cheerRef = db.collection("petCards").document(friendCode)
            .collection("cheers").document()
        val payload = mapOf(
            "fromUserId" to uid,
            "toUserId" to friendUserId,
            "fromPetName" to myPetName.trim().take(30).ifBlank { "A Buddy" },
            "message" to message.trim().take(120).ifBlank { "Sent a cheering boop!" },
            "claimed" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            cheerRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, cheerRef.path)
            throw e
        }
    }

    suspend fun claimCheer(cheerId: String, profile: PetProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        val code = buddyCodeForUid(uid)
        val cheerRef = db.collection("petCards").document(code)
            .collection("cheers").document(cheerId)
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
        val batch = db.batch()
        batch.update(cheerRef, mapOf("claimed" to true))
        batch.update(
            profileRef,
            mapOf(
                "happiness" to (profile.happiness + 5L).coerceAtMost(100L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, cheerRef.path)
            throw e
        }
    }

    suspend fun updatePetAppearance(
        petName: String? = null,
        speciesColor: SpeciesColor? = null,
        equippedOutfit: PetOutfit? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        petName?.trim()?.takeIf { it.isNotEmpty() }?.let { updates["petName"] = it.take(30) }
        speciesColor?.let { updates["speciesColor"] = it.key }
        equippedOutfit?.let { updates["equippedOutfit"] = it.key }

        try {
            profileRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
            throw e
        }
    }

    suspend fun unlockAndEquipOutfit(profile: PetProfile, outfit: PetOutfit): Result<Unit> = runCatching {
        val uid = requireUserId()
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
        val alreadyUnlocked = outfit.key in profile.unlockedOutfits
        if (!alreadyUnlocked && profile.coins < outfit.costCoins) {
            throw IllegalStateException("Not enough Star Coins! Complete more habits to earn ${outfit.costCoins} coins.")
        }
        val newCoins = if (alreadyUnlocked) profile.coins else (profile.coins - outfit.costCoins)
        val newUnlocked = if (alreadyUnlocked) profile.unlockedOutfits else (profile.unlockedOutfits + outfit.key)
        val updates = mapOf(
            "coins" to newCoins,
            "unlockedOutfits" to newUnlocked,
            "equippedOutfit" to outfit.key,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            profileRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
            throw e
        }
    }

    suspend fun purchaseConsumable(profile: PetProfile, item: ShopConsumable): Result<Unit> = runCatching {
        val uid = requireUserId()
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
        if (profile.coins < item.costCoins) {
            throw IllegalStateException("Need ${item.costCoins} Star Coins to buy ${item.label}.")
        }
        val updates = when (item) {
            ShopConsumable.STREAK_FREEZE -> {
                if (profile.streakFreezes >= 10L) {
                    throw IllegalStateException("You already hold the maximum of 10 Streak Freezes!")
                }
                mapOf(
                    "coins" to (profile.coins - item.costCoins),
                    "streakFreezes" to (profile.streakFreezes + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            ShopConsumable.GOLDEN_ELIXIR -> {
                mapOf(
                    "coins" to (profile.coins - item.costCoins),
                    "happiness" to 100L,
                    "energy" to 100L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
        }
        try {
            profileRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
            throw e
        }
    }

    suspend fun boostPetCare(profile: PetProfile, happinessDelta: Long, energyDelta: Long): Result<Unit> = runCatching {
        val uid = requireUserId()
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
        val newHappiness = (profile.happiness + happinessDelta).coerceIn(0L, 100L)
        val newEnergy = (profile.energy + energyDelta).coerceIn(0L, 100L)
        try {
            profileRef.update(
                mapOf(
                    "happiness" to newHappiness,
                    "energy" to newEnergy,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
            throw e
        }
    }

    suspend fun boostPetCareWithSnack(
        profile: PetProfile,
        coinCost: Long,
        happinessDelta: Long,
        energyDelta: Long
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (profile.coins < coinCost) {
            throw IllegalStateException("Need $coinCost Star Coins for a Berry Treat!")
        }
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
        val newCoins = (profile.coins - coinCost).coerceAtLeast(0L)
        val newHappiness = (profile.happiness + happinessDelta).coerceIn(0L, 100L)
        val newEnergy = (profile.energy + energyDelta).coerceIn(0L, 100L)
        try {
            profileRef.update(
                mapOf(
                    "coins" to newCoins,
                    "happiness" to newHappiness,
                    "energy" to newEnergy,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
            throw e
        }
    }

    suspend fun createHabit(
        title: String,
        category: HabitCategory,
        xpReward: Long = 30L
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanTitle = title.trim().take(80)
        require(cleanTitle.isNotEmpty()) { "Habit title cannot be empty" }
        val habitRef = db.collection("users").document(uid).collection("habits").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to cleanTitle,
            "category" to category.key,
            "xpReward" to xpReward.coerceIn(5L, 500L),
            "streakCount" to 0L,
            "lastVerifiedDate" to "",
            "lastVerificationNote" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            habitRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, habitRef.path)
            throw e
        }
        habitRef.id
    }

    suspend fun deleteHabit(habitId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(habitId.isNotBlank()) { "Habit ID must not be blank" }
        val habitRef = db.collection("users").document(uid).collection("habits").document(habitId)
        try {
            habitRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, habitRef.path)
            throw e
        }
    }

    suspend fun reconcileDailyStreaks(
        profile: PetProfile,
        habits: List<Habit>,
        todayIsoDate: String
    ): Result<List<String>> = runCatching {
        val uid = requireUserId()
        val yesterdayIso = shiftIsoDate(todayIsoDate, -1) ?: return@runCatching emptyList()
        var availableFreezes = profile.streakFreezes
        val protectedTitles = mutableListOf<String>()
        val batch = db.batch()
        var hasChanges = false

        for (habit in habits) {
            if (habit.streakCount <= 0L || habit.lastVerifiedDate.isBlank()) continue
            val gap = daysBetweenIsoDates(habit.lastVerifiedDate, todayIsoDate) ?: continue
            if (gap == 2L && availableFreezes > 0L) {
                availableFreezes -= 1L
                hasChanges = true
                protectedTitles.add(habit.title)
                val habitRef = db.collection("users").document(uid).collection("habits").document(habit.id)
                batch.update(
                    habitRef,
                    mapOf(
                        "lastVerifiedDate" to yesterdayIso,
                        "lastVerificationNote" to "Protected by Streak Freeze",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else if (gap >= 2L) {
                hasChanges = true
                val habitRef = db.collection("users").document(uid).collection("habits").document(habit.id)
                batch.update(
                    habitRef,
                    mapOf(
                        "streakCount" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }

        if (hasChanges) {
            if (availableFreezes != profile.streakFreezes) {
                val profileRef = db.collection("users").document(uid)
                    .collection("petProfile").document(MAIN_PROFILE_ID)
                batch.update(
                    profileRef,
                    mapOf(
                        "streakFreezes" to availableFreezes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            try {
                batch.commit().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, "users/$uid/habits")
                throw e
            }
        }
        protectedTitles
    }

    suspend fun recordVerifiedHabitCompletion(
        profile: PetProfile,
        habit: Habit,
        allHabits: List<Habit>,
        todayIsoDate: String,
        verificationNote: String
    ): Result<HabitCompletionOutcome> = runCatching {
        val uid = requireUserId()
        val habitRef = db.collection("users").document(uid).collection("habits").document(habit.id)
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)

        val alreadyCompletedToday = habit.lastVerifiedDate == todayIsoDate
        val gap = if (habit.lastVerifiedDate.isBlank()) null else daysBetweenIsoDates(habit.lastVerifiedDate, todayIsoDate)
        var remainingFreezes = profile.streakFreezes
        val newStreak = when {
            alreadyCompletedToday -> habit.streakCount
            gap == null || gap == 1L -> habit.streakCount + 1L
            gap == 2L && habit.streakCount > 0L && remainingFreezes > 0L -> {
                remainingFreezes -= 1L
                habit.streakCount + 1L
            }
            else -> 1L
        }
        val cleanNote = verificationNote.trim().take(300)

        val allCompletedNow = allHabits.isNotEmpty() && allHabits.all { h ->
            h.id == habit.id || h.isCompletedOn(todayIsoDate)
        }
        val claimDailySweepBonus = !alreadyCompletedToday &&
            allCompletedNow &&
            profile.lastDailyBonusDate != todayIsoDate

        val baseXp = if (alreadyCompletedToday) 0L else habit.xpReward
        val bonusXp = if (claimDailySweepBonus) DAILY_SWEEP_BONUS_XP else 0L
        val totalXpEarned = baseXp + bonusXp

        val baseCoins = if (alreadyCompletedToday) 0L else (habit.xpReward / 2L).coerceAtLeast(10L)
        val bonusCoins = if (claimDailySweepBonus) DAILY_SWEEP_BONUS_COINS else 0L
        val totalCoinsEarned = baseCoins + bonusCoins

        var newLevel = profile.level
        var newXp = profile.xp + totalXpEarned
        var leveledUp = false
        while (newXp >= newLevel * 100L && newLevel < 999L) {
            newXp -= newLevel * 100L
            newLevel += 1L
            leveledUp = true
        }
        val newCoins = (profile.coins + totalCoinsEarned).coerceAtMost(1_000_000L)
        val newHappiness = (profile.happiness + if (alreadyCompletedToday) 3L else 15L).coerceAtMost(100L)
        val newEnergy = (profile.energy + if (alreadyCompletedToday) 2L else 10L).coerceAtMost(100L)
        val newTotal = if (alreadyCompletedToday) profile.totalCompleted else (profile.totalCompleted + 1L)
        val newBonusDate = if (claimDailySweepBonus) todayIsoDate else profile.lastDailyBonusDate

        val batch = db.batch()
        batch.update(
            habitRef,
            mapOf(
                "streakCount" to newStreak,
                "lastVerifiedDate" to todayIsoDate,
                "lastVerificationNote" to cleanNote,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            profileRef,
            mapOf(
                "level" to newLevel,
                "xp" to newXp,
                "coins" to newCoins,
                "happiness" to newHappiness,
                "energy" to newEnergy,
                "totalCompleted" to newTotal,
                "streakFreezes" to remainingFreezes,
                "lastDailyBonusDate" to newBonusDate,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "${habitRef.path} + ${profileRef.path}")
            throw e
        }
        HabitCompletionOutcome(
            leveledUp = leveledUp,
            dailyBonusClaimed = claimDailySweepBonus,
            coinsEarned = totalCoinsEarned,
            xpEarned = totalXpEarned
        )
    }

    suspend fun addChatMessage(
        sender: String,
        text: String,
        habitTag: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanText = text.trim().take(2000)
        require(cleanText.isNotEmpty()) { "Chat message cannot be empty" }
        val msgRef = db.collection("users").document(uid).collection("chatMessages").document()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("sender", sender)
            put("text", cleanText)
            if (!habitTag.isNullOrBlank()) {
                put("habitTag", habitTag.trim().take(60))
            }
            put("createdAt", FieldValue.serverTimestamp())
        }
        try {
            msgRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
        msgRef.id
    }

    suspend fun clearChatHistory(messages: List<ChatMessage>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val validMessages = messages.filter { it.id.isNotBlank() }
        if (validMessages.isEmpty()) return@runCatching
        val colRef = db.collection("users").document(uid).collection("chatMessages")
        for (chunk in validMessages.chunked(400)) {
            val batch = db.batch()
            chunk.forEach { msg ->
                batch.delete(colRef.document(msg.id))
            }
            try {
                batch.commit().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, colRef.path)
                throw e
            }
        }
    }

    private fun daysBetweenIsoDates(startIso: String, endIso: String): Long? {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val start = fmt.parse(startIso) ?: return null
            val end = fmt.parse(endIso) ?: return null
            TimeUnit.MILLISECONDS.toDays(end.time - start.time)
        } catch (_: Exception) {
            null
        }
    }

    private fun shiftIsoDate(isoDate: String, deltaDays: Int): String? {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val parsed = fmt.parse(isoDate) ?: return null
            val cal = Calendar.getInstance().apply {
                time = parsed
                add(Calendar.DAY_OF_YEAR, deltaDays)
            }
            fmt.format(cal.time)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val MAIN_PROFILE_ID = "main"
        const val DAILY_SWEEP_BONUS_COINS = 25L
        const val DAILY_SWEEP_BONUS_XP = 30L
    }
}
