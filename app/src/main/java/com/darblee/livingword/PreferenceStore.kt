package com.darblee.livingword

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.darblee.livingword.ui.theme.ColorThemeOption
import kotlinx.coroutines.flow.first

internal class PreferenceStore(private val context: Context) {
    // Wrap the private variables in a "companion object" so they are not initialized more than once
    companion object {
        private val Context.datastore: DataStore<Preferences> by preferencesDataStore(name = "APP_SETTING_KEY")
        private val AI_MODEL_KEY = booleanPreferencesKey("AIModel")
        private val COLOR_MODE_KEY = stringPreferencesKey("ColorMode")
    }

    // 'suspend' will pause the co-routine thread to allow other thread to perform task
    suspend fun saveColorModeToSetting(colorMode: ColorThemeOption) {
        context.datastore.edit { pref ->
            pref[PreferenceStore.Companion.COLOR_MODE_KEY] = colorMode.toString()
        }
    }

    // Get the data as a stand-alone method instead of Flow<boolean> method
    suspend fun readColorModeFromSetting(): ColorThemeOption {
        val preferences = context.datastore.data.first()

        val colorModeString = preferences[PreferenceStore.Companion.COLOR_MODE_KEY]

        if (colorModeString == ColorThemeOption.Light.toString())
            return (ColorThemeOption.Light)

        if (colorModeString == ColorThemeOption.Dark.toString())
            return (ColorThemeOption.Dark)

        return ColorThemeOption.System
    }
}