package com.healthconnect.export.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleManager {
    private const val PREFS_NAME = "healthconnect_export_prefs"
    private const val KEY_LOCALE = "app_locale"

    /** Сохраняет код локали ("en", "ru", или null для системной) */
    fun saveLocale(
        context: Context,
        localeCode: String?,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCALE, localeCode)
            .apply()
    }

    /** Читает сохранённый код локали (null = системная) */
    fun getSavedLocale(context: Context): String? =
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCALE, null)

    /** Оборачивает Context с указанным locale (null = системный) */
    fun wrapContext(
        context: Context,
        localeCode: String?,
    ): Context {
        val locale =
            if (localeCode != null) {
                Locale.forLanguageTag(localeCode)
            } else {
                Locale.getDefault()
            }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /** Возвращает отображаемое название локали на её же языке */
    fun localeDisplayName(code: String?): String =
        when (code) {
            "ru" -> "Русский"
            "es" -> "Español"
            "en" -> "English"
            else -> "System"
        }
}
