package com.softandtech.tuitionmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.softandtech.tuitionmanager.data.db.entity.StudentEntity
import com.softandtech.tuitionmanager.data.db.model.StudentListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    @Insert
    suspend fun insert(student: StudentEntity): Long

    @Update
    suspend fun update(student: StudentEntity)

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: Long): StudentEntity?

    @Query("SELECT * FROM students WHERE id = :id")
    fun observeById(id: Long): Flow<StudentEntity?>

    @Query("SELECT COUNT(*) FROM students WHERE active = 1")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM students WHERE active = 1")
    suspend fun activeCount(): Int

    /** Empty [query] returns everyone. Matches name, phone numbers and class. */
    @Query(
        """
        SELECT * FROM students
        WHERE active = :active
          AND (:query = '' OR name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%'
               OR parent_phone LIKE '%' || :query || '%' OR class_name LIKE '%' || :query || '%')
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun search(query: String, active: Boolean = true): Flow<List<StudentEntity>>

    /** Same filter as [search], shaped for the list screen with each student's current batches. */
    @Query(
        """
        SELECT s.id, s.name, s.class_name AS className, s.phone, s.parent_phone AS parentPhone,
               (SELECT GROUP_CONCAT(b.name, ', ') FROM enrollments e JOIN batches b ON b.id = e.batch_id
                WHERE e.student_id = s.id AND e.left_on IS NULL) AS batchNames
        FROM students s
        WHERE s.active = :active
          AND (:query = '' OR s.name LIKE '%' || :query || '%' OR s.phone LIKE '%' || :query || '%'
               OR s.parent_phone LIKE '%' || :query || '%' OR s.class_name LIKE '%' || :query || '%')
        ORDER BY s.name COLLATE NOCASE
        """,
    )
    fun searchListItems(query: String, active: Boolean): Flow<List<StudentListItem>>

    @Query("UPDATE students SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
