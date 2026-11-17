package com.example.data.repository

import android.content.Context
import android.util.Log
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
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class ChibiPetRepository(
    private val db: FirebaseFirestore,
    context: Context? = null
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        context.applicationContext
    )

    private val appContext: Context? = context?.applicationContext
    private val auth = Firebase.auth

    private val localProfileFlow = MutableStateFlow<PetProfile?>(null)
    private val localHabitsFlow = MutableStateFlow<List<Habit>>(emptyList())
    private val localChatMessagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val localFriendsFlow = MutableStateFlow<List<FriendLink>>(emptyList())

    private fun currentFirebaseUid(): String? = auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    private fun isLocalOnlySession(userId: String): Boolean {
        return currentFirebaseUid() == null &&
            (userId == "guest_explorer_local" || userId.startsWith("google_"))
    }

    private fun requireUserId(): String {
        return currentFirebaseUid() ?: "guest_explorer_local"
    }

    private fun loadLocalStateIfNeeded(userId: String) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences("chibipet_local_repo_$userId", Context.MODE_PRIVATE)
        if (localProfileFlow.value == null && prefs.contains("petName")) {
            val unlockedSet = prefs.getStringSet("unlockedOutfits", null)
                ?.toList()
                ?.ifEmpty { listOf(PetOutfit.NONE.key, PetOutfit.BOW.key) }
                ?: listOf(PetOutfit.NONE.key, PetOutfit.BOW.key)
            localProfileFlow.value = PetProfile(
                id = MAIN_PROFILE_ID,
                userId = userId,
                petName = prefs.getString("petName", null)
                    ?.takeIf { it.isNotBlank() }
                    ?: ctx.getString(R.string.default_pet_name),
                speciesColor = prefs.getString("speciesColor", SpeciesColor.MINT.key) ?: SpeciesColor.MINT.key,
                equippedOutfit = prefs.getString("equippedOutfit", PetOutfit.BOW.key) ?: PetOutfit.BOW.key,
                unlockedOutfits = unlockedSet,
                level = prefs.getLong("level", 1L),
                xp = prefs.getLong("xp", 0L),
                coins = prefs.getLong("coins", 50L),
                happiness = prefs.getLong("happiness", 85L),
                energy = prefs.getLong("energy", 90L),
                totalCompleted = prefs.getLong("totalCompleted", 0L),
                streakFreezes = prefs.getLong("streakFreezes", 1L),
                lastDailyBonusDate = prefs.getString("lastDailyBonusDate", "") ?: "",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
        }
        if (localHabitsFlow.value.isEmpty()) {
            val rawHabits = prefs.getString("habits_json", null)
            if (!rawHabits.isNullOrBlank()) {
                runCatching {
                    val arr = org.json.JSONArray(rawHabits)
                    val list = mutableListOf<Habit>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            Habit(
                                id = obj.optString("id", "local_habit_${i + 1}"),
                                userId = userId,
                                title = obj.optString("title", "Daily Quest"),
                                category = obj.optString("category", HabitCategory.HYDRATION.key),
                                xpReward = obj.optLong("xpReward", 30L),
                                streakCount = obj.optLong("streakCount", 0L),
                                lastVerifiedDate = obj.optString("lastVerifiedDate", ""),
                                lastVerificationNote = obj.optString("lastVerificationNote", ""),
                                createdAt = Timestamp.now(),
                                updatedAt = Timestamp.now()
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        localHabitsFlow.value = list
                    }
                }
            }
        }
    }

    private fun persistLocalProfile(profile: PetProfile) {
        val ctx = appContext ?: return
        val uid = profile.userId.ifBlank { requireUserId() }
        val prefs = ctx.getSharedPreferences("chibipet_local_repo_$uid", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("petName", profile.petName)
            .putString("speciesColor", profile.speciesColor)
            .putString("equippedOutfit", profile.equippedOutfit)
            .putStringSet("unlockedOutfits", profile.unlockedOutfits.toSet())
            .putLong("level", profile.level)
            .putLong("xp", profile.xp)
            .putLong("coins", profile.coins)
            .putLong("happiness", profile.happiness)
            .putLong("energy", profile.energy)
            .putLong("totalCompleted", profile.totalCompleted)
            .putLong("streakFreezes", profile.streakFreezes)
            .putString("lastDailyBonusDate", profile.lastDailyBonusDate)
            .apply()
    }

    private fun persistLocalHabits(userId: String, habits: List<Habit>) {
        val ctx = appContext ?: return
        val uid = userId.ifBlank { requireUserId() }
        val prefs = ctx.getSharedPreferences("chibipet_local_repo_$uid", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        habits.forEach { h ->
            arr.put(
                org.json.JSONObject().apply {
                    put("id", h.id)
                    put("title", h.title)
                    put("category", h.category)
                    put("xpReward", h.xpReward)
                    put("streakCount", h.streakCount)
                    put("lastVerifiedDate", h.lastVerifiedDate)
                    put("lastVerificationNote", h.lastVerificationNote)
                }
            )
        }
        prefs.edit().putString("habits_json", arr.toString()).apply()
    }

    fun observePetProfile(userId: String): Flow<PetProfile?> = flow {
        if (isLocalOnlySession(userId)) {
            loadLocalStateIfNeeded(userId)
            emitAll(localProfileFlow)
            return@flow
        }
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
        if (isLocalOnlySession(userId)) {
            loadLocalStateIfNeeded(userId)
            emitAll(localHabitsFlow)
            return@flow
        }
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
        if (isLocalOnlySession(userId)) {
            emitAll(localChatMessagesFlow)
            return@flow
        }
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
        if (isLocalOnlySession(userId)) {
            emitAll(localFriendsFlow)
            return@flow
        }
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
        if (isLocalOnlySession(userId)) {
            emit(emptyList())
            return@flow
        }
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
        initialPetName: String = "",
        initialColor: SpeciesColor = SpeciesColor.MINT,
        initialOutfit: PetOutfit = PetOutfit.BOW,
        initialQuests: List<Triple<String, String, Long>> = emptyList()
    ): Result<Unit> = runCatching {
        val fallbackName = appContext?.getString(R.string.default_pet_name) ?: "Mochi"
        val cleanPetName = initialPetName.trim().take(30).ifEmpty { fallbackName }
        val initialUnlocked = listOf(PetOutfit.NONE.key, PetOutfit.BOW.key, initialOutfit.key).distinct()
        val fbUid = currentFirebaseUid()
        if (fbUid == null) {
            val localUid = requireUserId()
            loadLocalStateIfNeeded(localUid)
            if (localProfileFlow.value == null) {
                val createdProfile = PetProfile(
                    id = MAIN_PROFILE_ID,
                    userId = localUid,
                    petName = cleanPetName,
                    speciesColor = initialColor.key,
                    equippedOutfit = initialOutfit.key,
                    unlockedOutfits = initialUnlocked,
                    level = 1L,
                    xp = 0L,
                    coins = 50L,
                    happiness = 85L,
                    energy = 90L,
                    totalCompleted = 0L,
                    streakFreezes = 1L,
                    lastDailyBonusDate = "",
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
                localProfileFlow.value = createdProfile
                persistLocalProfile(createdProfile)
            }
            if (localHabitsFlow.value.isEmpty() && initialQuests.isNotEmpty()) {
                val createdHabits = initialQuests.mapIndexed { idx, (title, category, xp) ->
                    Habit(
                        id = "local_habit_${idx + 1}",
                        userId = localUid,
                        title = title,
                        category = category,
                        xpReward = xp,
                        streakCount = 0L,
                        lastVerifiedDate = "",
                        lastVerificationNote = "",
                        createdAt = Timestamp.now(),
                        updatedAt = Timestamp.now()
                    )
                }
                localHabitsFlow.value = createdHabits
                persistLocalHabits(localUid, createdHabits)
            }
            return@runCatching
        }

        val uid = fbUid
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
                "equippedOutfit" to initialOutfit.key,
                "unlockedOutfits" to initialUnlocked,
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

            if (initialQuests.isNotEmpty()) {
                val habitsCol = db.collection("users").document(uid).collection("habits")
                for ((title, category, xp) in initialQuests) {
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
            }

            syncMyPetCard(
                profile = PetProfile(
                    userId = uid,
                    petName = cleanPetName,
                    speciesColor = initialColor.key,
                    equippedOutfit = initialOutfit.key,
                    level = 1L,
                    totalCompleted = 0L
                ),
                topStreak = 0L
            )
        }
    }

    suspend fun syncMyPetCard(profile: PetProfile, topStreak: Long): Result<Unit> = runCatching {
        val uid = currentFirebaseUid() ?: return@runCatching
        val code = buddyCodeForUid(uid)
        val cardRef = db.collection("petCards").document(code)
        val payload = mapOf(
            "userId" to uid,
            "buddyCode" to code,
            "petName" to profile.petName.take(30).ifBlank {
                appContext?.getString(R.string.default_pet_name) ?: "Mochi"
            },
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
        val code = rawCode.trim().uppercase(Locale.US)
        require(code.matches(Regex("^[A-Z0-9_\\-]{4,32}$"))) {
            "Enter a valid Buddy Code (e.g. PET-A1B2C3)."
        }
        val uid = currentFirebaseUid()
            ?: throw IllegalStateException("Sign in with Google to connect with friends by Buddy Code.")
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
        val uid = currentFirebaseUid() ?: return@runCatching
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
        val uid = currentFirebaseUid()
        if (uid == null) {
            localFriendsFlow.value = localFriendsFlow.value.filterNot { it.friendCode == friendCode }
            return@runCatching
        }
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
        val uid = currentFirebaseUid()
            ?: throw IllegalStateException("Sign in with Google to send cheers to friends.")
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
        val uid = currentFirebaseUid() ?: return@runCatching
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
        val cleanName = petName?.trim()?.takeIf { it.isNotEmpty() }?.take(30)
        val currentLocal = localProfileFlow.value
        if (currentLocal != null) {
            val updatedUnlocked = if (equippedOutfit != null) {
                (currentLocal.unlockedOutfits + equippedOutfit.key).distinct()
            } else {
                currentLocal.unlockedOutfits
            }
            val updated = currentLocal.copy(
                petName = cleanName ?: currentLocal.petName,
                speciesColor = speciesColor?.key ?: currentLocal.speciesColor,
                equippedOutfit = equippedOutfit?.key ?: currentLocal.equippedOutfit,
                unlockedOutfits = updatedUnlocked,
                updatedAt = Timestamp.now()
            )
            localProfileFlow.value = updated
            persistLocalProfile(updated)
        }
        val uid = currentFirebaseUid() ?: return@runCatching
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        cleanName?.let { updates["petName"] = it }
        speciesColor?.let { updates["speciesColor"] = it.key }
        equippedOutfit?.let {
            updates["equippedOutfit"] = it.key
            updates["unlockedOutfits"] = FieldValue.arrayUnion(it.key)
        }

        try {
            profileRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) {
                handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
                throw e
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
            throw e
        }
    }

    suspend fun unlockAndEquipOutfit(profile: PetProfile, outfit: PetOutfit): Result<Unit> = runCatching {
        val alreadyUnlocked = outfit.key in profile.unlockedOutfits
        if (!alreadyUnlocked && profile.coins < outfit.costCoins) {
            throw IllegalStateException("Not enough Star Coins! Complete more habits to earn ${outfit.costCoins} coins.")
        }
        val newCoins = if (alreadyUnlocked) profile.coins else (profile.coins - outfit.costCoins)
        val newUnlocked = if (alreadyUnlocked) profile.unlockedOutfits else (profile.unlockedOutfits + outfit.key)

        val updatedLocal = profile.copy(
            coins = newCoins,
            unlockedOutfits = newUnlocked,
            equippedOutfit = outfit.key,
            updatedAt = Timestamp.now()
        )
        localProfileFlow.value = updatedLocal
        persistLocalProfile(updatedLocal)

        val uid = currentFirebaseUid() ?: return@runCatching
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
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
        if (profile.coins < item.costCoins) {
            throw IllegalStateException("Need ${item.costCoins} Star Coins to buy ${item.label}.")
        }
        val updates = when (item) {
            ShopConsumable.STREAK_FREEZE -> {
                if (profile.streakFreezes >= 10L) {
                    throw IllegalStateException("You already hold the maximum of 10 Streak Freezes!")
                }
                val updatedLocal = profile.copy(
                    coins = profile.coins - item.costCoins,
                    streakFreezes = profile.streakFreezes + 1L,
                    updatedAt = Timestamp.now()
                )
                localProfileFlow.value = updatedLocal
                persistLocalProfile(updatedLocal)
                mapOf(
                    "coins" to (profile.coins - item.costCoins),
                    "streakFreezes" to (profile.streakFreezes + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            ShopConsumable.GOLDEN_ELIXIR -> {
                val updatedLocal = profile.copy(
                    coins = profile.coins - item.costCoins,
                    happiness = 100L,
                    energy = 100L,
                    updatedAt = Timestamp.now()
                )
                localProfileFlow.value = updatedLocal
                persistLocalProfile(updatedLocal)
                mapOf(
                    "coins" to (profile.coins - item.costCoins),
                    "happiness" to 100L,
                    "energy" to 100L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
        }
        val uid = currentFirebaseUid() ?: return@runCatching
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
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
        val newHappiness = (profile.happiness + happinessDelta).coerceIn(0L, 100L)
        val newEnergy = (profile.energy + energyDelta).coerceIn(0L, 100L)
        val updatedLocal = profile.copy(
            happiness = newHappiness,
            energy = newEnergy,
            updatedAt = Timestamp.now()
        )
        localProfileFlow.value = updatedLocal
        persistLocalProfile(updatedLocal)
        val uid = currentFirebaseUid() ?: return@runCatching
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
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
        if (profile.coins < coinCost) {
            throw IllegalStateException("Need $coinCost Star Coins for a Berry Treat!")
        }
        val newCoins = (profile.coins - coinCost).coerceAtLeast(0L)
        val newHappiness = (profile.happiness + happinessDelta).coerceIn(0L, 100L)
        val newEnergy = (profile.energy + energyDelta).coerceIn(0L, 100L)
        val updatedLocal = profile.copy(
            coins = newCoins,
            happiness = newHappiness,
            energy = newEnergy,
            updatedAt = Timestamp.now()
        )
        localProfileFlow.value = updatedLocal
        persistLocalProfile(updatedLocal)
        val uid = currentFirebaseUid() ?: return@runCatching
        val profileRef = db.collection("users").document(uid)
            .collection("petProfile").document(MAIN_PROFILE_ID)
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
        val cleanTitle = title.trim().take(80)
        require(cleanTitle.isNotEmpty()) { "Habit title cannot be empty" }
        val coercedXp = xpReward.coerceIn(5L, 500L)
        val uid = currentFirebaseUid()
        if (uid == null) {
            val localUid = requireUserId()
            val localId = "local_${UUID.randomUUID().toString().take(8)}"
            val newHabit = Habit(
                id = localId,
                userId = localUid,
                title = cleanTitle,
                category = category.key,
                xpReward = coercedXp,
                streakCount = 0L,
                lastVerifiedDate = "",
                lastVerificationNote = "",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            val updatedList = localHabitsFlow.value + newHabit
            localHabitsFlow.value = updatedList
            persistLocalHabits(localUid, updatedList)
            return@runCatching localId
        }
        val habitRef = db.collection("users").document(uid).collection("habits").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to cleanTitle,
            "category" to category.key,
            "xpReward" to coercedXp,
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
        require(habitId.isNotBlank()) { "Habit ID must not be blank" }
        val updatedList = localHabitsFlow.value.filterNot { it.id == habitId }
        localHabitsFlow.value = updatedList
        persistLocalHabits(requireUserId(), updatedList)
        val uid = currentFirebaseUid() ?: return@runCatching
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
        val yesterdayIso = shiftIsoDate(todayIsoDate, -1) ?: return@runCatching emptyList()
        var availableFreezes = profile.streakFreezes
        val protectedTitles = mutableListOf<String>()
        val freezeNote = appContext?.getString(R.string.repo_streak_freeze_note) ?: "Protected by Streak Freeze"
        val uid = currentFirebaseUid()
        if (uid == null) {
            var changed = false
            val updatedHabits = habits.map { habit ->
                val gap = if (habit.streakCount <= 0L || habit.lastVerifiedDate.isBlank()) null
                else daysBetweenIsoDates(habit.lastVerifiedDate, todayIsoDate)
                when {
                    gap == 2L && availableFreezes > 0L -> {
                        availableFreezes -= 1L
                        changed = true
                        protectedTitles.add(habit.title)
                        habit.copy(
                            lastVerifiedDate = yesterdayIso,
                            lastVerificationNote = freezeNote,
                            updatedAt = Timestamp.now()
                        )
                    }
                    gap != null && gap >= 2L -> {
                        changed = true
                        habit.copy(streakCount = 0L, updatedAt = Timestamp.now())
                    }
                    else -> habit
                }
            }
            if (changed) {
                localHabitsFlow.value = updatedHabits
                val updatedProfile = profile.copy(
                    streakFreezes = availableFreezes,
                    updatedAt = Timestamp.now()
                )
                localProfileFlow.value = updatedProfile
                persistLocalHabits(profile.userId, updatedHabits)
                persistLocalProfile(updatedProfile)
            }
            return@runCatching protectedTitles
        }

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
                        "lastVerificationNote" to freezeNote,
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

        val updatedHabit = habit.copy(
            streakCount = newStreak,
            lastVerifiedDate = todayIsoDate,
            lastVerificationNote = cleanNote,
            updatedAt = Timestamp.now()
        )
        val currentList = localHabitsFlow.value.ifEmpty { allHabits }
        val updatedHabitsList = currentList.map { if (it.id == habit.id) updatedHabit else it }
        localHabitsFlow.value = updatedHabitsList
        val updatedProfile = profile.copy(
            level = newLevel,
            xp = newXp,
            coins = newCoins,
            happiness = newHappiness,
            energy = newEnergy,
            totalCompleted = newTotal,
            streakFreezes = remainingFreezes,
            lastDailyBonusDate = newBonusDate,
            updatedAt = Timestamp.now()
        )
        localProfileFlow.value = updatedProfile
        persistLocalHabits(profile.userId, updatedHabitsList)
        persistLocalProfile(updatedProfile)

        val uid = currentFirebaseUid()
        if (uid != null) {
            val habitRef = db.collection("users").document(uid).collection("habits").document(habit.id)
            val profileRef = db.collection("users").document(uid)
                .collection("petProfile").document(MAIN_PROFILE_ID)

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
                handleFirestoreError(e, OperationType.WRITE, habitRef.path)
                throw e
            }
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
        val cleanText = text.trim().take(2000)
        require(cleanText.isNotEmpty()) { "Chat message cannot be empty" }
        val uid = currentFirebaseUid()
        if (uid == null) {
            val localId = "msg_${UUID.randomUUID().toString().take(8)}"
            val msg = ChatMessage(
                id = localId,
                userId = requireUserId(),
                sender = sender,
                text = cleanText,
                habitTag = habitTag?.trim()?.take(60),
                createdAt = Timestamp.now()
            )
            localChatMessagesFlow.value = localChatMessagesFlow.value + msg
            return@runCatching localId
        }
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
        localChatMessagesFlow.value = emptyList()
        val uid = currentFirebaseUid() ?: return@runCatching
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
