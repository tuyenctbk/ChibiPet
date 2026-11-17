package com.example.data.local

import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SupportedLanguage(
    val tag: String,
    val nativeName: String
)

object AppLanguageManager {
    private const val PREFS_NAME = "chibipet_language_prefs"
    private const val KEY_LANGUAGE_TAG = "selected_language_tag"

    val SUPPORTED_LANGUAGES: List<SupportedLanguage> = listOf(
        SupportedLanguage("", "System Default"),
        SupportedLanguage("en", "English"),
        SupportedLanguage("es", "Español"),
        SupportedLanguage("fr", "Français"),
        SupportedLanguage("de", "Deutsch"),
        SupportedLanguage("ja", "日本語"),
        SupportedLanguage("ko", "한국어"),
        SupportedLanguage("vi", "Tiếng Việt"),
        SupportedLanguage("zh-CN", "简体中文"),
        SupportedLanguage("zh-TW", "繁體中文"),
        SupportedLanguage("pt", "Português"),
        SupportedLanguage("it", "Italiano"),
        SupportedLanguage("ru", "Русский"),
        SupportedLanguage("ar", "العربية"),
        SupportedLanguage("hi", "हिन्दी"),
        SupportedLanguage("in", "Bahasa Indonesia"),
        SupportedLanguage("th", "ไทย"),
        SupportedLanguage("tr", "Türkçe"),
        SupportedLanguage("nl", "Nederlands"),
        SupportedLanguage("pl", "Polski"),
        SupportedLanguage("sv", "Svenska"),
        SupportedLanguage("tl", "Filipino")
    )

    private val _selectedTag = MutableStateFlow<String?>(null)

    fun observeLanguageTag(context: Context): StateFlow<String> {
        if (_selectedTag.value == null) {
            _selectedTag.value = getSavedLanguageTag(context)
        }
        @Suppress("UNCHECKED_CAST")
        return _selectedTag.asStateFlow() as StateFlow<String>
    }

    fun getSavedLanguageTag(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE_TAG, "") ?: ""
    }

    fun setLanguageTag(context: Context, tag: String) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, tag)
            .apply()
        _selectedTag.value = tag

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val localeManager = appContext.getSystemService(LocaleManager::class.java)
                localeManager?.applicationLocales = if (tag.isBlank()) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(tag)
                }
            }
        }
    }

    fun createLocalizedContext(baseContext: Context, languageTag: String): Context {
        if (languageTag.isBlank()) return baseContext
        val locale = when (languageTag) {
            "zh-CN" -> Locale.SIMPLIFIED_CHINESE
            "zh-TW" -> Locale.TRADITIONAL_CHINESE
            "in" -> Locale("in")
            else -> Locale.forLanguageTag(languageTag)
        }
        Locale.setDefault(locale)
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        val configContext = baseContext.createConfigurationContext(config)
        return object : ContextWrapper(baseContext) {
            override fun getResources(): Resources = configContext.resources
            override fun getAssets(): AssetManager = configContext.assets
        }
    }
}
