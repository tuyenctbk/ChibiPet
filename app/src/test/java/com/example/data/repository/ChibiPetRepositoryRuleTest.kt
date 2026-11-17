package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.HabitCategory
import com.example.data.model.PetOutfit
import com.example.data.model.ShopConsumable
import com.example.data.model.SpeciesColor
import com.example.data.model.buddyCodeForUid
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ChibiPetRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureInitializedForUser_createsPetAndStarterHabits() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repository = ChibiPetRepository(firestore)

        val initResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureInitializedForUser(
                displayName = "Alice",
                initialQuests = listOf(
                    Triple("Morning Hydration", HabitCategory.HYDRATION.key, 25L),
                    Triple("Deep Work Block", HabitCategory.STUDY.key, 40L),
                    Triple("Evening Stretch", HabitCategory.FITNESS.key, 35L)
                )
            )
        }
        assertTrue(initResult.isSuccess)

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePetProfile(uid).first { it != null }
        }
        assertNotNull(profile)
        assertEquals("Mochi", profile?.petName)
        assertEquals(uid, profile?.userId)
        assertEquals(1L, profile?.streakFreezes)

        val habits = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeHabits(uid).first { it.isNotEmpty() }
        }
        assertEquals(3, habits.size)
    }

    @Test
    fun createHabit_recordVerification_and_shopConsumable_work() = runBlocking {
        val uid = signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val repository = ChibiPetRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) { repository.ensureInitializedForUser("Bob").getOrThrow() }

        val habitTitle = "Evening Run ${UUID.randomUUID().toString().take(6)}"
        val habitId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createHabit(habitTitle, HabitCategory.FITNESS, 40L).getOrThrow()
        }

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePetProfile(uid).first { it != null }
        }!!
        val allHabits = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeHabits(uid).first { list -> list.any { it.id == habitId } }
        }
        val habit = allHabits.first { it.id == habitId }

        val outcome = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordVerifiedHabitCompletion(
                profile = profile,
                habit = habit,
                allHabits = allHabits,
                todayIsoDate = "2026-11-14",
                verificationNote = "Verified running shoes!"
            ).getOrThrow()
        }
        assertEquals(20L, outcome.coinsEarned)
        assertEquals(40L, outcome.xpEarned)

        val updatedProfile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePetProfile(uid).first { it != null && it.totalCompleted >= 1L }
        }!!
        assertEquals(40L, updatedProfile.xp)
        assertEquals(1L, updatedProfile.totalCompleted)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.purchaseConsumable(updatedProfile, ShopConsumable.STREAK_FREEZE).getOrThrow()
        }
        val afterFreeze = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePetProfile(uid).first { it?.streakFreezes == 2L }
        }!!
        assertEquals(20L, afterFreeze.coins)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.boostPetCareWithSnack(
                profile = afterFreeze,
                coinCost = 5L,
                happinessDelta = 8L,
                energyDelta = 12L
            ).getOrThrow()
        }
        val afterSnack = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePetProfile(uid).first { it?.coins == 15L }
        }!!
        assertEquals(15L, afterSnack.coins)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updatePetAppearance(
                petName = "Boba",
                speciesColor = SpeciesColor.PEACH,
                equippedOutfit = PetOutfit.BOW
            ).getOrThrow()
        }

        val renamedProfile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePetProfile(uid).first { it?.petName == "Boba" }
        }!!
        assertEquals(SpeciesColor.PEACH.key, renamedProfile.speciesColor)
    }

    @Test
    fun buddyCard_friendLink_and_cheerFlow_succeedsAcrossUsers() = runBlocking {
        val aliceEmail = "alice_bud_${UUID.randomUUID()}@test.com"
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = ChibiPetRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.ensureInitializedForUser("Alice").getOrThrow() }

        val bobEmail = "bob_bud_${UUID.randomUUID()}@test.com"
        val bobUid = signInTestUser(bobEmail)
        val bobRepo = ChibiPetRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.ensureInitializedForUser("Bob").getOrThrow() }

        val aliceCode = buddyCodeForUid(aliceUid)
        val linked = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.addFriendByCode(aliceCode).getOrThrow()
        }
        assertEquals(aliceUid, linked.friendUserId)

        val bobFriends = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeFriends(bobUid).first { it.isNotEmpty() }
        }
        assertTrue(bobFriends.any { it.friendCode == aliceCode })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.sendCheerToFriend(
                friendCode = aliceCode,
                friendUserId = aliceUid,
                myPetName = "Boba",
                message = "Go Mochi!"
            ).getOrThrow()
        }

        // Sign back in as Alice and claim Bob's cheer
        signInTestUser(aliceEmail)
        val aliceCheers = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeIncomingCheers(aliceUid).first { it.isNotEmpty() }
        }
        val cheer = aliceCheers.first()
        assertEquals("Boba", cheer.fromPetName)

        val aliceProfile = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observePetProfile(aliceUid).first { it != null }
        }!!
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.claimCheer(cheer.id, aliceProfile).getOrThrow()
        }
    }

    @Test
    fun observeHabits_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso_${UUID.randomUUID()}@test.com")
        val aliceRepo = ChibiPetRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.ensureInitializedForUser("Alice").getOrThrow() }

        signInTestUser("bob_iso_${UUID.randomUUID()}@test.com")
        val bobRepo = ChibiPetRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeHabits(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull(caught)
        val firestoreEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeHabits_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = ChibiPetRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeHabits("some_user_123").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull(caught)
        val firestoreEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun usageQuotaManager_tracksInteractions_checksLimits_claimsGrace_and_resetsOnNewDay() = runBlocking {
        val uid = signInTestUser("quota_${UUID.randomUUID()}@test.com")
        val quotaManager = UsageQuotaManager(firestore)

        // 1. Initialize daily quota for 2026-11-14
        val day1 = "2026-11-14"
        val initial = withTimeout(DEFAULT_TIMEOUT_MS) {
            quotaManager.ensureOrResetDailyQuota(day1).getOrThrow()
        }
        assertEquals(day1, initial.dateIso)
        assertEquals(0, initial.visionUsedToday)
        assertEquals(0, initial.chatUsedToday)
        assertEquals(0, initial.studioUsedToday)

        // 2. Record AI interactions across Vision, Chat, and Studio
        withTimeout(DEFAULT_TIMEOUT_MS) {
            quotaManager.recordInteraction(AiFeatureType.VISION, usedFallbackLite = false, todayIsoDate = day1).getOrThrow()
            quotaManager.recordInteraction(AiFeatureType.CHAT, usedFallbackLite = true, todayIsoDate = day1).getOrThrow()
            quotaManager.recordInteraction(AiFeatureType.STUDIO, usedFallbackLite = false, todayIsoDate = day1).getOrThrow()
        }

        val observedAfterCalls = withTimeout(FLOW_TIMEOUT_MS) {
            quotaManager.observeUsageQuota(uid, day1).first {
                it.visionUsedToday == 1 && it.chatUsedToday == 1 && it.studioUsedToday == 1
            }
        }
        assertEquals(1, observedAfterCalls.fallbackCascadeCountToday)

        // 3. Refund an interaction and verify counter decrements
        val afterRefund = withTimeout(DEFAULT_TIMEOUT_MS) {
            quotaManager.refundInteraction(AiFeatureType.STUDIO, todayIsoDate = day1).getOrThrow()
        }
        assertEquals(0, afterRefund.studioUsedToday)

        // 4. Claim +3 Daily Grace Credits (first claim succeeds, second returns false)
        val firstClaim = withTimeout(DEFAULT_TIMEOUT_MS) {
            quotaManager.claimDailyGraceCredits(day1).getOrThrow()
        }
        assertTrue(firstClaim)
        val secondClaim = withTimeout(DEFAULT_TIMEOUT_MS) {
            quotaManager.claimDailyGraceCredits(day1).getOrThrow()
        }
        assertEquals(false, secondClaim)

        val checkAfterGrace = quotaManager.checkQuotaLimit(AiFeatureType.VISION, quotaManager.currentSnapshot)
        assertEquals(18, checkAfterGrace.limitForFeature) // 15 base + 3 grace
        assertEquals(17, checkAfterGrace.remainingForFeature)

        // 5. Roll over to next calendar day (2026-11-15) -> counters and graceClaimed reset to 0/false
        val day2 = "2026-11-15"
        val resetSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            quotaManager.ensureOrResetDailyQuota(day2).getOrThrow()
        }
        assertEquals(day2, resetSnap.dateIso)
        assertEquals(0, resetSnap.visionUsedToday)
        assertEquals(0, resetSnap.chatUsedToday)
        assertEquals(0, resetSnap.studioUsedToday)
        assertEquals(0, resetSnap.bonusGraceCreditsToday)
        assertEquals(false, resetSnap.graceClaimedToday)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}

