package com.softandtech.tuitionmanager.data.repository

import com.softandtech.tuitionmanager.data.db.TuitionDatabase
import com.softandtech.tuitionmanager.data.db.entity.ExpenseEntity
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ExpenseRepository @Inject constructor(
    db: TuitionDatabase,
) {
    private val expenseDao = db.expenseDao()

    suspend fun add(expense: ExpenseEntity): Long {
        require(expense.amountPaise > 0) { "Expense amount must be positive" }
        return expenseDao.insert(expense)
    }

    suspend fun delete(expense: ExpenseEntity) = expenseDao.delete(expense)

    fun observeMonth(month: YearMonth): Flow<List<ExpenseEntity>> =
        expenseDao.observeBetween(month.atDay(1), month.atEndOfMonth())

    fun observeMonthTotal(month: YearMonth): Flow<Long> =
        expenseDao.observeTotalBetween(month.atDay(1), month.atEndOfMonth())
}
