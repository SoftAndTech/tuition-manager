package com.softandtech.tuitionmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentAllocatorTest {

    @Test
    fun exactPaymentClearsOneDue() {
        val result = PaymentAllocator.allocate(listOf(PaymentCredit(1, 1000)), listOf(OutstandingDue(10, 1000)))
        assertEquals(listOf(Allocation(1, 10, 1000)), result)
    }

    @Test
    fun partialPaymentLeavesDueOpen() {
        val result = PaymentAllocator.allocate(listOf(PaymentCredit(1, 400)), listOf(OutstandingDue(10, 1000)))
        assertEquals(listOf(Allocation(1, 10, 400)), result)
    }

    @Test
    fun paymentCoversOldestDuesFirst() {
        val result = PaymentAllocator.allocate(
            listOf(PaymentCredit(1, 2500)),
            listOf(OutstandingDue(10, 1000), OutstandingDue(11, 1000), OutstandingDue(12, 1000)),
        )
        assertEquals(listOf(Allocation(1, 10, 1000), Allocation(1, 11, 1000), Allocation(1, 12, 500)), result)
    }

    @Test
    fun excessIsNotAllocated_staysAsAdvanceCredit() {
        val result = PaymentAllocator.allocate(listOf(PaymentCredit(1, 1500)), listOf(OutstandingDue(10, 1000)))
        assertEquals(listOf(Allocation(1, 10, 1000)), result)
        assertEquals(1000, result.sumOf { it.amountPaise })
    }

    @Test
    fun severalPaymentsCombineOnOneDue() {
        val result = PaymentAllocator.allocate(
            listOf(PaymentCredit(1, 300), PaymentCredit(2, 300), PaymentCredit(3, 1000)),
            listOf(OutstandingDue(10, 1000)),
        )
        assertEquals(listOf(Allocation(1, 10, 300), Allocation(2, 10, 300), Allocation(3, 10, 400)), result)
    }

    @Test
    fun nothingToDoWithoutDuesOrCredits() {
        assertTrue(PaymentAllocator.allocate(listOf(PaymentCredit(1, 500)), emptyList()).isEmpty())
        assertTrue(PaymentAllocator.allocate(emptyList(), listOf(OutstandingDue(10, 500))).isEmpty())
    }

    @Test
    fun skipsDuesWithNothingOutstanding() {
        val result = PaymentAllocator.allocate(
            listOf(PaymentCredit(1, 500)),
            listOf(OutstandingDue(10, 0), OutstandingDue(11, 500)),
        )
        assertEquals(listOf(Allocation(1, 11, 500)), result)
    }
}
