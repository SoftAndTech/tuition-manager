package com.softandtech.tuitionmanager.notify

import com.softandtech.tuitionmanager.data.repository.FeeRepository
import com.softandtech.tuitionmanager.data.repository.FeeSync
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class OverdueSummary(val students: Int, val totalPaise: Long)

/** Brings dues up to date (a new month may have started) and totals what is overdue. */
@Singleton
class DailyFeeCheck @Inject constructor(
    private val feeSync: FeeSync,
    private val feeRepository: FeeRepository,
    private val clock: Clock,
) {
    suspend fun run(): OverdueSummary {
        feeSync.run()
        val overdue = feeRepository.observeOverdue(LocalDate.now(clock)).first()
        return OverdueSummary(overdue.size, overdue.sumOf { it.outstandingPaise })
    }
}
