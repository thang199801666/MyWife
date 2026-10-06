package com.example.videoshield

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguage {
    private const val PREFS = "app_language"
    fun tag(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) return if (locales[0].language == "en") "en" else "vi"
            return "vi"
        }
        return if (context.getSharedPreferences(PREFS, 0).getString("tag", "vi") == "en") "en" else "vi"
    }
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("initialized", false)) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales.isEmpty) manager.applicationLocales = LocaleList.forLanguageTags(if (prefs.getString("tag","vi") == "en") "en" else "vi")
        }
        prefs.edit().putBoolean("initialized", true).apply()
    }
    fun select(context: Context, language: String) {
        val selected = if (language == "en") "en" else "vi"
        context.getSharedPreferences(PREFS, 0).edit().putString("tag", selected).apply()
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(selected)
    }
    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(tag(context))
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }
    fun youtubeUrl(context: Context, value: String): String {
        if (!YouTubeAdapter.isTrustedBridgeUrl(value)) return value
        val uri = android.net.Uri.parse(value)
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.filter { it != "hl" }.forEach { key ->
            uri.getQueryParameters(key).forEach { builder.appendQueryParameter(key,it) }
        }
        return builder.appendQueryParameter("hl",tag(context)).build().toString()
    }
}

class VotuiApplication : Application() {
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.wrap(base)) }
    override fun onCreate() { super.onCreate(); AppLanguage.initialize(this) }
}

open class LocalizedActivity : Activity() {
    private var language = "vi"
    protected val uiLanguageTag: String get() = language
    override fun attachBaseContext(base: Context) {
        language = AppLanguage.tag(base)
        super.attachBaseContext(AppLanguage.wrap(base))
    }
    override fun onResume() {
        super.onResume()
        if (language != AppLanguage.tag(this)) recreate()
    }
}
