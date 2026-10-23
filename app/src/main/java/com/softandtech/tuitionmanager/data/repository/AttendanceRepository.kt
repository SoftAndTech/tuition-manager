package com.softandtech.tuitionmanager.data.repository

import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.AttendanceEntity
import com.softandtech.tuitionmanager.domain.AttendanceSummary
import com.softandtech.tuitionmanager.domain.model.AttendanceStatus
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AttendanceRepository @Inject constructor(
    db: TuitionDatabase,
) {
    private val attendanceDao = db.attendanceDao()

    /** Saves a whole register at once. Marking the same day again overwrites the earlier marks. */
    suspend fun mark(batchId: Long, date: LocalDate, marks: Map<Long, AttendanceStatus>) {
        attendanceDao.upsertAll(
            marks.map { (studentId, status) ->
                AttendanceEntity(batchId = batchId, studentId = studentId, date = date, status = status)
            },
        )
    }

    fun observeForBatchOnDate(batchId: Long, date: LocalDate): Flow<List<AttendanceEntity>> =
        attendanceDao.observeForBatchOnDate(batchId, date)

    /** batchId → number of students marked on [date]. */
    fun observeMarkedCounts(date: LocalDate): Flow<Map<Long, Int>> =
        attendanceDao.observeMarkedCounts(date).map { rows -> rows.associate { it.batchId to it.count } }

    fun observeSummary(studentId: Long, from: LocalDate, to: LocalDate): Flow<AttendanceSummary> =
        attendanceDao.observeStatusCounts(studentId, from, to)
            .map { counts -> AttendanceSummary.from(counts.associate { it.status to it.count }) }
}
