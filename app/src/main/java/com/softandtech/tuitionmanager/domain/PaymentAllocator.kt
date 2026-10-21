package com.softandtech.tuitionmanager.domain

/** Money from a payment not yet applied to any due. */
data class PaymentCredit(val paymentId: Long, val remainingPaise: Long)

/** A due with something still to pay. */
data class OutstandingDue(val dueId: Long, val outstandingPaise: Long)

data class Allocation(val paymentId: Long, val dueId: Long, val amountPaise: Long)

/**
 * Applies a student's unspent payment money to their unpaid dues, oldest first.
 * Callers pass [credits] oldest payment first and [dues] oldest due first.
 * Whatever is left over stays as advance credit for future dues.
 */
object PaymentAllocator {

    fun allocate(credits: List<PaymentCredit>, dues: List<OutstandingDue>): List<Allocation> {
        val result = mutableListOf<Allocation>()
        val dueIterator = dues.filter { it.outstandingPaise > 0 }.iterator()
        var due = if (dueIterator.hasNext()) dueIterator.next() else return result
        var dueLeft = due.outstandingPaise

        for (credit in credits) {
            var creditLeft = credit.remainingPaise
            while (creditLeft > 0) {
                val amount = minOf(creditLeft, dueLeft)
                result += Allocation(credit.paymentId, due.dueId, amount)
                creditLeft -= amount
                dueLeft -= amount
                if (dueLeft == 0L) {
                    if (!dueIterator.hasNext()) return result
                    due = dueIterator.next()
                    dueLeft = due.outstandingPaise
                }
            }
        }
        return result
    }
}
