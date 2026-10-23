package com.softandtech.tuitionmanager.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.softandtech.tuitionmanager.domain.model.JoinMonthPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The tutor's own details, printed on receipts and reminders, plus billing preferences. */
data class AppSettings(
    val instituteName: String = "",
    val tutorName: String = "",
    val tutorPhone: String = "",
    val upiId: String = "",
    val receiptPrefix: String = DEFAULT_RECEIPT_PREFIX,
    val joinMonthPolicy: JoinMonthPolicy = JoinMonthPolicy.FULL,
    /** Morning notification when fees are overdue. Off until the tutor turns it on (it needs a permission). */
    val dailyReminder: Boolean = false,
) {
    /** Name to sign receipts and messages with: institute, else tutor, else empty. */
    val signature: String get() = instituteName.ifBlank { tutorName }.trim()

    companion object {
        const val DEFAULT_RECEIPT_PREFIX = "R-"
    }
}

/**
 * Settings live in app-private DataStore (excluded from backup like everything else). They hold
 * only the tutor's own details — never student data, which stays in the encrypted database.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        AppSettings(
            instituteName = p[INSTITUTE].orEmpty(),
            tutorName = p[TUTOR].orEmpty(),
            tutorPhone = p[PHONE].orEmpty(),
            upiId = p[UPI].orEmpty(),
            receiptPrefix = p[PREFIX] ?: AppSettings.DEFAULT_RECEIPT_PREFIX,
            joinMonthPolicy = p[POLICY]?.let { runCatching { JoinMonthPolicy.valueOf(it) }.getOrNull() } ?: JoinMonthPolicy.FULL,
            dailyReminder = p[DAILY_REMINDER] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun save(s: AppSettings) {
        dataStore.edit { p ->
            p[INSTITUTE] = s.instituteName.trim()
            p[TUTOR] = s.tutorName.trim()
            p[PHONE] = s.tutorPhone.trim()
            p[UPI] = s.upiId.trim()
            p[PREFIX] = s.receiptPrefix.trim()
            p[POLICY] = s.joinMonthPolicy.name
        }
    }

    /** Saved on its own (not with the profile form) because it takes effect immediately. */
    suspend fun setDailyReminder(enabled: Boolean) {
        dataStore.edit { it[DAILY_REMINDER] = enabled }
    }

    private companion object {
        val INSTITUTE = stringPreferencesKey("institute_name")
        val TUTOR = stringPreferencesKey("tutor_name")
        val PHONE = stringPreferencesKey("tutor_phone")
        val UPI = stringPreferencesKey("upi_id")
        val PREFIX = stringPreferencesKey("receipt_prefix")
        val POLICY = stringPreferencesKey("join_month_policy")
        val DAILY_REMINDER = booleanPreferencesKey("daily_reminder")
    }
}
