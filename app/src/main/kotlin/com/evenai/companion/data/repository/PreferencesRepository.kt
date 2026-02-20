package com.evenai.companion.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.evenai.companion.domain.model.OnboardingStep
import com.evenai.companion.domain.model.PrivacySettings
import com.evenai.companion.domain.model.Widget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "g1_companion_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDING_DONE     = booleanPreferencesKey("onboarding_done")
        val ACTIVE_WIDGET       = stringPreferencesKey("active_widget")
        val MIC_ENABLED         = booleanPreferencesKey("mic_enabled")
        val DATA_RETENTION      = booleanPreferencesKey("data_retention")
        val DEVELOPER_MODE      = booleanPreferencesKey("developer_mode")
    }

    // ── Onboarding ────────────────────────────────────────────────────────────
    val onboardingDone: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    // ── Active widget ─────────────────────────────────────────────────────────
    val activeWidget: Flow<Widget> = context.dataStore.data
        .map { prefs ->
            prefs[Keys.ACTIVE_WIDGET]?.let {
                runCatching { Widget.valueOf(it) }.getOrNull()
            } ?: Widget.AI_ASSISTANT
        }

    suspend fun setActiveWidget(widget: Widget) {
        context.dataStore.edit { it[Keys.ACTIVE_WIDGET] = widget.name }
    }

    // ── Privacy ───────────────────────────────────────────────────────────────
    val privacySettings: Flow<PrivacySettings> = context.dataStore.data
        .map { prefs ->
            PrivacySettings(
                microphoneEnabled    = prefs[Keys.MIC_ENABLED] ?: false,
                dataRetentionEnabled = prefs[Keys.DATA_RETENTION] ?: false
            )
        }

    suspend fun setMicEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MIC_ENABLED] = enabled }
    }

    // ── Developer mode ────────────────────────────────────────────────────────
    val developerModeEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.DEVELOPER_MODE] ?: false }

    suspend fun toggleDeveloperMode() {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVELOPER_MODE] = !(prefs[Keys.DEVELOPER_MODE] ?: false)
        }
    }
}
