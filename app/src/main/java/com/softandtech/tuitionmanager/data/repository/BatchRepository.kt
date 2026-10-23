package com.softandtech.tuitionmanager.data.repository

import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.BatchEntity
import com.softandtech.tuitionmanager.data.db.entity.EnrollmentEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.db.model.BatchWithCount
import com.softandtech.tuitionmanager.data.db.model.EnrollmentWithBatch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class BatchRepository @Inject constructor(
    db: TuitionDatabase,
) {
    private val batchDao = db.batchDao()
    private val enrollmentDao = db.enrollmentDao()

    fun observeActive(): Flow<List<BatchEntity>> = batchDao.observeActive()

    suspend fun getById(id: Long): BatchEntity? = batchDao.getById(id)

    suspend fun activeCount(): Int = batchDao.activeCount()

    suspend fun add(batch: BatchEntity): Long {
        validate(batch)
        return batchDao.insert(batch.copy(name = batch.name.trim()))
    }

    suspend fun update(batch: BatchEntity) {
        validate(batch)
        batchDao.update(batch.copy(name = batch.name.trim()))
    }

    /** Archived batches stop billing but keep their history. */
    suspend fun archive(batchId: Long) = batchDao.setActive(batchId, false)

    /**
     * @param billFrom first month to charge; defaults to the joining month. Pass the current
     *   month when entering a student who joined long ago and has already paid past months.
     */
    suspend fun enroll(
        studentId: Long,
        batchId: Long,
        joinedOn: LocalDate,
        feeOverridePaise: Long? = null,
        billFrom: YearMonth = YearMonth.from(joinedOn),
    ): Long {
        require(feeOverridePaise == null || feeOverridePaise >= 0) { "Fee cannot be negative" }
        require(!enrollmentDao.hasOpenEnrollment(studentId, batchId)) { "Student is already in this batch" }
        return enrollmentDao.insert(
            EnrollmentEntity(
                studentId = studentId,
                batchId = batchId,
                feeOverridePaise = feeOverridePaise,
                joinedOn = joinedOn,
                billFrom = billFrom,
            ),
        )
    }

    /** The month of [leftOn] is still billed; later months are not. */
    suspend fun leave(enrollmentId: Long, leftOn: LocalDate) {
        val enrollment = enrollmentDao.getById(enrollmentId) ?: return
        require(!leftOn.isBefore(enrollment.joinedOn)) { "Leaving date is before joining date" }
        enrollmentDao.update(enrollment.copy(leftOn = leftOn))
    }

    fun observeEnrollments(studentId: Long): Flow<List<EnrollmentWithBatch>> =
        enrollmentDao.observeWithBatchForStudent(studentId)

    fun observeActiveWithCount(): Flow<List<BatchWithCount>> = batchDao.observeActiveWithCount()

    fun observeBatch(batchId: Long): Flow<BatchEntity?> = batchDao.observeById(batchId)

    fun observeStudentsInBatch(batchId: Long, date: LocalDate): Flow<List<StudentEntity>> =
        enrollmentDao.observeStudentsInBatch(batchId, date)

    private fun validate(batch: BatchEntity) {
        require(batch.name.isNotBlank()) { "Batch name is required" }
        require(batch.monthlyFeePaise >= 0) { "Fee cannot be negative" }
        require(batch.feeDueDay in 1..31) { "Due day must be between 1 and 31" }
    }
}
