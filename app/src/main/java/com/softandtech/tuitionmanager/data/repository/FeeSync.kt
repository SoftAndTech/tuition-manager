package com.softandtech.tuitionmanager.data.repository

import com.softandtech.tuitionmanager.data.settings.SettingsRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings monthly dues up to date using the tutor's join-month policy. Generation is idempotent,
 * so this is called freely: on app start, and whenever a screen that shows fees opens.
 */
@Singleton
class FeeSync @Inject constructor(
    private val feeRepository: FeeRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) {
    suspend fun run(): Int =
        feeRepository.generateDues(LocalDate.now(clock), settingsRepository.current().joinMonthPolicy)
}
