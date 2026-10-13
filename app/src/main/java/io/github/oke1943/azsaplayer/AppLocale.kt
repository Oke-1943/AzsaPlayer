package io.github.oke1943.azsaplayer

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * 表示言語（システム／日本語／English）。
 * Android 12 にはアプリごとの言語設定がないため、アプリ内で選べるようにする。
 * 起動直後（Activity・Serviceの生成時）に同期的に読む必要があるので、SharedPreferencesに保存する。
 */
object AppLocale {
    const val SYSTEM = "system"
    const val JAPANESE = "ja"
    const val ENGLISH = "en"
    val CHOICES = listOf(SYSTEM, JAPANESE, ENGLISH)

    private const val PREFS = "app_locale"
    private const val KEY = "language"

    fun saved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun save(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language).apply()
    }

    /** 選んだ言語を反映したContext。システムに従う場合はそのまま返す。 */
    fun wrap(base: Context): Context {
        val lang = saved(base)
        if (lang == SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(lang)))
        return base.createConfigurationContext(config)
    }
}
