package com.softandtech.tuitionmanager.data.repository

import androidx.room.withTransaction
import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.EnrollmentEntity
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.db.model.StudentListItem
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** A batch to join while adding a new student. */
data class NewEnrollment(
    val batchId: Long,
    val feeOverridePaise: Long? = null,
    val billFrom: YearMonth,
)

@Singleton
class StudentRepository @Inject constructor(
    private val db: TuitionDatabase,
    private val feeRepository: FeeRepository,
) {
    private val studentDao = db.studentDao()
    private val enrollmentDao = db.enrollmentDao()

    fun observeStudents(query: String = "", active: Boolean = true): Flow<List<StudentEntity>> =
        studentDao.search(query.trim(), active)

    fun observeListItems(query: String = "", active: Boolean = true): Flow<List<StudentListItem>> =
        studentDao.searchListItems(query.trim(), active)

    fun observeStudent(id: Long): Flow<StudentEntity?> = studentDao.observeById(id)

    suspend fun getById(id: Long): StudentEntity? = studentDao.getById(id)

    fun observeActiveCount(): Flow<Int> = studentDao.observeActiveCount()

    suspend fun activeCount(): Int = studentDao.activeCount()

    suspend fun add(student: StudentEntity): Long = add(student, emptyList())

    /** Saves a new student and their batches together, so a failure never leaves half a record. */
    suspend fun add(student: StudentEntity, enrollments: List<NewEnrollment>): Long {
        require(student.name.isNotBlank()) { "Student name is required" }
        return db.withTransaction {
            val id = studentDao.insert(student.copy(name = student.name.trim()))
            enrollments.distinctBy { it.batchId }.forEach {
                require(it.feeOverridePaise == null || it.feeOverridePaise >= 0) { "Fee cannot be negative" }
                enrollmentDao.insert(
                    EnrollmentEntity(
                        studentId = id,
                        batchId = it.batchId,
                        feeOverridePaise = it.feeOverridePaise,
                        joinedOn = student.joinDate,
                        billFrom = it.billFrom,
                    ),
                )
            }
            id
        }
    }

    suspend fun update(student: StudentEntity) {
        require(student.name.isNotBlank()) { "Student name is required" }
        studentDao.update(student.copy(name = student.name.trim()))
    }

    /**
     * Marks a student as left: bills anything owed up to [today], then closes their batch
     * enrollments so no further months are charged. History is kept.
     */
    suspend fun deactivate(studentId: Long, today: LocalDate) {
        feeRepository.generateDues(today)
        db.withTransaction {
            enrollmentDao.closeOpenEnrollments(studentId, today)
            studentDao.setActive(studentId, false)
        }
    }

    /** Brings a student back. Re-enroll them in batches to resume billing. */
    suspend fun reactivate(studentId: Long) = studentDao.setActive(studentId, true)
}
