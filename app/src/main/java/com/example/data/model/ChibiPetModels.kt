package com.example.data.model

import androidx.annotation.StringRes
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest

enum class SpeciesColor(
    val key: String,
    val label: String,
    @StringRes val labelRes: Int,
    val primaryHex: Long,
    val bellyHex: Long,
    val cheekHex: Long
) {
    MINT("mint", "Cloud Mint", R.string.color_mint_label, 0xFF8BE2C8, 0xFFFDF8EE, 0xFFFF9EB5),
    PEACH("peach", "Peach Mochi", R.string.color_peach_label, 0xFFFFB59D, 0xFFFFF5EC, 0xFFFF7A9E),
    LAVENDER("lavender", "Starlight Lilac", R.string.color_lavender_label, 0xFFC8B6FF, 0xFFF8F4FF, 0xFFFFA5C5),
    LEMON("lemon", "Honey Yuzu", R.string.color_lemon_label, 0xFFFFDF7E, 0xFFFFFBEA, 0xFFFF9885),
    BERRY("berry", "Sky Berry", R.string.color_berry_label, 0xFF8EC5FF, 0xFFF2F8FF, 0xFFFF9BB8);

    companion object {
        fun fromKey(key: String): SpeciesColor =
            entries.firstOrNull { it.key == key } ?: MINT
    }
}

enum class PetOutfit(
    val key: String,
    val label: String,
    @StringRes val labelRes: Int,
    val costCoins: Int,
    val description: String,
    @StringRes val descriptionRes: Int
) {
    NONE("none", "Classic Cozy", R.string.outfit_none_label, 0, "Natural fuzzy charm", R.string.outfit_none_desc),
    BOW("bow", "Strawberry Bow", R.string.outfit_bow_label, 0, "Cute ribbon bow on top", R.string.outfit_bow_desc),
    CROWN("crown", "Royal Crown", R.string.outfit_crown_label, 40, "Golden crown for habit royalty", R.string.outfit_crown_desc),
    HEADPHONES("headphones", "Lo-Fi Beats", R.string.outfit_headphones_label, 60, "Pastel studio headphones", R.string.outfit_headphones_desc),
    WIZARD("wizard", "Starry Hat", R.string.outfit_wizard_label, 80, "Enchanted focus wizard hat", R.string.outfit_wizard_desc),
    SHADES("shades", "Cool Shades", R.string.outfit_shades_label, 50, "Sleek neon-rimmed sunglasses", R.string.outfit_shades_desc),
    CAPE("cape", "Hero Cape", R.string.outfit_cape_label, 100, "Flowing crimson victory cape", R.string.outfit_cape_desc);

    companion object {
        fun fromKey(key: String): PetOutfit =
            entries.firstOrNull { it.key == key } ?: NONE
    }
}

enum class ShopConsumable(
    val key: String,
    val label: String,
    @StringRes val labelRes: Int,
    val costCoins: Int,
    val description: String,
    @StringRes val descriptionRes: Int
) {
    STREAK_FREEZE(
        key = "streak_freeze",
        label = "Streak Freeze",
        labelRes = R.string.consumable_freeze_label,
        costCoins = 50,
        description = "Auto-protects a missed day so your habit streak stays unbroken",
        descriptionRes = R.string.consumable_freeze_desc
    ),
    GOLDEN_ELIXIR(
        key = "golden_elixir",
        label = "Golden Mood Elixir",
        labelRes = R.string.consumable_elixir_label,
        costCoins = 25,
        description = "Instantly restores Mood & Energy to 100% with a sparkle dance",
        descriptionRes = R.string.consumable_elixir_desc
    )
}

enum class HabitCategory(
    val key: String,
    val label: String,
    @StringRes val labelRes: Int,
    val proofHint: String,
    @StringRes val proofHintRes: Int
) {
    HYDRATION("hydration", "Hydration", R.string.cat_hydration_label, "Water bottle, glass, or mug", R.string.cat_hydration_hint),
    STUDY("study", "Study & Focus", R.string.cat_study_label, "Book, notebook, or study desk", R.string.cat_study_hint),
    FITNESS("fitness", "Fitness", R.string.cat_fitness_label, "Sneakers, yoga mat, or workout gear", R.string.cat_fitness_hint),
    MINDFULNESS("mindfulness", "Mindfulness", R.string.cat_mindfulness_label, "Journal, tea, or calm space", R.string.cat_mindfulness_hint),
    CREATIVE("creative", "Creative", R.string.cat_creative_label, "Sketchbook, instrument, or craft", R.string.cat_creative_hint);

    companion object {
        fun fromKey(key: String): HabitCategory =
            entries.firstOrNull { it.key == key } ?: HYDRATION
    }
}

@IgnoreExtraProperties
data class PetProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val petName: String = "Mochi",
    val speciesColor: String = SpeciesColor.MINT.key,
    val equippedOutfit: String = PetOutfit.NONE.key,
    val unlockedOutfits: List<String> = listOf(PetOutfit.NONE.key, PetOutfit.BOW.key),
    val level: Long = 1L,
    val xp: Long = 0L,
    val coins: Long = 50L,
    val happiness: Long = 85L,
    val energy: Long = 90L,
    val totalCompleted: Long = 0L,
    val streakFreezes: Long = 0L,
    val lastDailyBonusDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val xpForNextLevel: Long get() = level * 100L
    val xpProgressFraction: Float
        get() = (xp.toFloat() / xpForNextLevel.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
}

data class HabitCompletionOutcome(
    val leveledUp: Boolean,
    val dailyBonusClaimed: Boolean,
    val coinsEarned: Long,
    val xpEarned: Long
)

@IgnoreExtraProperties
data class Habit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = HabitCategory.HYDRATION.key,
    val xpReward: Long = 25L,
    val streakCount: Long = 0L,
    val lastVerifiedDate: String = "",
    val lastVerificationNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isCompletedOn(isoDate: String): Boolean = lastVerifiedDate == isoDate
}

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sender: String = "pet",
    val text: String = "",
    val habitTag: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PetCard(
    @DocumentId val id: String = "",
    val userId: String = "",
    val buddyCode: String = "",
    val petName: String = "Mochi",
    val speciesColor: String = SpeciesColor.MINT.key,
    val equippedOutfit: String = PetOutfit.NONE.key,
    val level: Long = 1L,
    val totalCompleted: Long = 0L,
    val topStreak: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FriendLink(
    @DocumentId val id: String = "",
    val userId: String = "",
    val friendCode: String = "",
    val friendUserId: String = "",
    val friendPetName: String = "",
    val friendSpeciesColor: String = SpeciesColor.MINT.key,
    val friendOutfit: String = PetOutfit.NONE.key,
    val friendLevel: Long = 1L,
    val friendTopStreak: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PetCheer(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val fromPetName: String = "",
    val message: String = "",
    val claimed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class HabitVerificationResult(
    val verified: Boolean,
    val confidence: Int,
    val detectedItem: String,
    val petReaction: String
)

data class SuggestedQuest(
    val title: String,
    val category: HabitCategory,
    val xpReward: Long,
    val reason: String
)

data class SmartQuestCoachPlan(
    val goalSummary: String,
    val coachMessage: String,
    val quests: List<SuggestedQuest>,
    val isLocalFallback: Boolean = false
)

fun buddyCodeForUid(uid: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
    val hex = digest.take(3).joinToString("") { "%02X".format(it) }
    return "PET-$hex"
}
