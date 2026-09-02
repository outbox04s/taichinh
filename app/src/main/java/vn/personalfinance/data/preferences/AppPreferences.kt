package vn.personalfinance.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.dataStore by preferencesDataStore("app_settings")

class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val biometricKey = booleanPreferencesKey("biometric_enabled")
    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[biometricKey] ?: false }
    suspend fun setBiometricEnabled(enabled: Boolean) { context.dataStore.edit { it[biometricKey] = enabled } }
}
