package com.example.data.local

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.data.model.HabitCategory
import com.example.data.model.PetOutfit
import com.example.data.model.SpeciesColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StarterQuestTemplate(
    val id: String,
    val title: String,
    @StringRes val titleRes: Int,
    val category: HabitCategory,
    val xpReward: Long,
    val defaultSelected: Boolean = true
)

data class OnboardingSelection(
    val hasCompletedOnboarding: Boolean = false,
    val preferredPetName: String = "Mochi",
    val preferredColor: SpeciesColor = SpeciesColor.MINT,
    val preferredOutfit: PetOutfit = PetOutfit.BOW,
    val selectedQuestIds: Set<String> = DEFAULT_QUEST_IDS,
    val hasAppliedToProfile: Boolean = false
) {
    companion object {
        val STARTER_QUEST_TEMPLATES = listOf(
            StarterQuestTemplate(
                id = "hydrate_500ml",
                title = "Drink Water (500ml)",
                titleRes = R.string.starter_quest_hydrate,
                category = HabitCategory.HYDRATION,
                xpReward = 25L,
                defaultSelected = true
            ),
            StarterQuestTemplate(
                id = "focus_study",
                title = "Focus Study Session",
                titleRes = R.string.starter_quest_study,
                category = HabitCategory.STUDY,
                xpReward = 40L,
                defaultSelected = true
            ),
            StarterQuestTemplate(
                id = "move_stretch",
                title = "Move & Stretch",
                titleRes = R.string.starter_quest_fitness,
                category = HabitCategory.FITNESS,
                xpReward = 35L,
                defaultSelected = true
            ),
            StarterQuestTemplate(
                id = "mindful_breathe",
                title = "5-Min Mindful Breathing",
                titleRes = R.string.starter_quest_mindful,
                category = HabitCategory.MINDFULNESS,
                xpReward = 25L,
                defaultSelected = false
            ),
            StarterQuestTemplate(
                id = "creative_spark",
                title = "Sketch or Journal 1 Page",
                titleRes = R.string.starter_quest_creative,
                category = HabitCategory.CREATIVE,
                xpReward = 30L,
                defaultSelected = false
            )
        )

        val DEFAULT_QUEST_IDS: Set<String> = STARTER_QUEST_TEMPLATES
            .filter { it.defaultSelected }
            .map { it.id }
            .toSet()
    }
}

object OnboardingPreferences {
    private const val PREFS_NAME = "chibipet_onboarding_prefs"
    private const val KEY_COMPLETED = "completed_onboarding"
    private const val KEY_PET_NAME = "preferred_pet_name"
    private const val KEY_SPECIES_COLOR = "preferred_species_color"
    private const val KEY_EQUIPPED_OUTFIT = "preferred_outfit"
    private const val KEY_SELECTED_QUESTS = "selected_quest_ids"
    private const val KEY_APPLIED_UID_PREFIX = "applied_to_uid_"

    private val _state = MutableStateFlow<OnboardingSelection?>(null)

    fun observe(context: Context): StateFlow<OnboardingSelection> {
        val current = _state.value
        if (current == null) {
            _state.value = read(context)
        }
        @Suppress("UNCHECKED_CAST")
        return _state.asStateFlow() as StateFlow<OnboardingSelection>
    }

    fun read(context: Context): OnboardingSelection {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val completed = prefs.getBoolean(KEY_COMPLETED, false)
        val petName = prefs.getString(KEY_PET_NAME, "Mochi")?.takeIf { it.isNotBlank() } ?: "Mochi"
        val colorKey = prefs.getString(KEY_SPECIES_COLOR, SpeciesColor.MINT.key) ?: SpeciesColor.MINT.key
        val outfitKey = prefs.getString(KEY_EQUIPPED_OUTFIT, PetOutfit.BOW.key) ?: PetOutfit.BOW.key
        val storedQuests = prefs.getStringSet(KEY_SELECTED_QUESTS, null)
        val questIds = if (storedQuests.isNullOrEmpty()) {
            OnboardingSelection.DEFAULT_QUEST_IDS
        } else {
            storedQuests
        }
        return OnboardingSelection(
            hasCompletedOnboarding = completed,
            preferredPetName = petName,
            preferredColor = SpeciesColor.fromKey(colorKey),
            preferredOutfit = PetOutfit.fromKey(outfitKey),
            selectedQuestIds = questIds
        )
    }

    fun saveOnboardingComplete(
        context: Context,
        petName: String,
        speciesColor: SpeciesColor,
        outfit: PetOutfit = PetOutfit.BOW,
        selectedQuestIds: Set<String>
    ) {
        val cleanName = petName.trim().take(30).ifEmpty { "Mochi" }
        val validQuests = selectedQuestIds.ifEmpty { OnboardingSelection.DEFAULT_QUEST_IDS }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_COMPLETED, true)
            .putString(KEY_PET_NAME, cleanName)
            .putString(KEY_SPECIES_COLOR, speciesColor.key)
            .putString(KEY_EQUIPPED_OUTFIT, outfit.key)
            .putStringSet(KEY_SELECTED_QUESTS, validQuests)
            .apply()
        _state.value = read(context)
    }

    fun reopenOnboarding(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_COMPLETED, false).apply()
        _state.value = read(context)
    }

    fun hasAppliedForUser(context: Context, userId: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("$KEY_APPLIED_UID_PREFIX$userId", false)
    }

    fun markAppliedForUser(context: Context, userId: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("$KEY_APPLIED_UID_PREFIX$userId", true).apply()
    }
}
