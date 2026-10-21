package com.softandtech.tuitionmanager.data.repository

import androidx.room.withTransaction
import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class StudentRepository @Inject constructor(
    private val db: TuitionDatabase,
    private val feeRepository: FeeRepository,
) {
    private val studentDao = db.studentDao()
    private val enrollmentDao = db.enrollmentDao()

    fun observeStudents(query: String = "", active: Boolean = true): Flow<List<StudentEntity>> =
        studentDao.search(query.trim(), active)

    fun observeStudent(id: Long): Flow<StudentEntity?> = studentDao.observeById(id)

    fun observeActiveCount(): Flow<Int> = studentDao.observeActiveCount()

    suspend fun activeCount(): Int = studentDao.activeCount()

    suspend fun add(student: StudentEntity): Long {
        require(student.name.isNotBlank()) { "Student name is required" }
        return studentDao.insert(student.copy(name = student.name.trim()))
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
