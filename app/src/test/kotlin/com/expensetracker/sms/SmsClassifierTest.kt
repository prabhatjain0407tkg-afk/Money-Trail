package com.expensetracker.sms

import com.expensetracker.sms.parser.MessageType
import com.expensetracker.sms.parser.SmsClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [SmsClassifier] — the first-pass message-type router.
 * Verifies precedence (ignore-types beat the generic debit/credit verbs) and that
 * each real-world category resolves correctly.
 */
class SmsClassifierTest {

    private fun type(sms: String) = SmsClassifier.classify(sms)

    // ── Real transactions ───────────────────────────────────────────────────
    @Test fun `debited is DEBIT`() =
        assertEquals(MessageType.DEBIT, type("Rs.450 debited from a/c XX1234 to swiggy@upi"))

    @Test fun `spent is DEBIT`() =
        assertEquals(MessageType.DEBIT, type("INR 666 spent on Axis Bank Card no. XX4501"))

    @Test fun `credited is CREDIT`() =
        assertEquals(MessageType.CREDIT, type("INR 5000 credited to your Kotak Bank Ac XX8438"))

    @Test fun `deposited is CREDIT`() =
        assertEquals(MessageType.CREDIT, type("Rs.50000 deposited to your A/c XX1234 towards SALARY"))

    // ── Precedence: ignore-types win over debit/credit verbs ─────────────────
    @Test fun `statement 'sent' does not become a DEBIT`() =
        assertEquals(
            MessageType.STATEMENT,
            type("ICICI Bank Credit Card XX8009 Statement is sent to you. " +
                    "Total of Rs 4271 is due by 29-MAY-26.")
        )

    @Test fun `amount due reminder is STATEMENT`() =
        assertEquals(MessageType.STATEMENT, type("Minimum amount due Rs.220 on your card by 05-Jul."))

    // ── Merchant-side / non-bank credits ─────────────────────────────────────
    @Test fun `store-credit is STORE_CREDIT not CREDIT`() =
        assertEquals(
            MessageType.STORE_CREDIT,
            type("INR 150 has been CREDITED to your Bewakoof account. Use it to shop now.")
        )

    @Test fun `biller payment ack is BILL_ACK not CREDIT`() =
        assertEquals(
            MessageType.BILL_ACK,
            type("Payment of Rs.706.82 for your JioHome connection has been received. Thank you!")
        )

    // ── Other ignore types ───────────────────────────────────────────────────
    @Test fun `otp is OTP`() =
        assertEquals(MessageType.OTP, type("904573 is the OTP for your transaction. Do not share."))

    @Test fun `collect request is REQUEST`() =
        assertEquals(MessageType.REQUEST, type("user@upi has requested Rs.500 from you on PhonePe"))

    @Test fun `loan offer is PROMOTION`() =
        assertEquals(MessageType.PROMOTION, type("You are eligible for a pre-approved loan. Apply now!"))

    @Test fun `limit increase is CARD_CONTROL`() =
        assertEquals(MessageType.CARD_CONTROL, type("Your credit card limit has been increased to Rs.2,00,000."))

    // ── Genuine loan-EMI debit must NOT be mistaken for a loan promo ──────────
    @Test fun `personal loan EMI debit stays DEBIT`() =
        assertEquals(
            MessageType.DEBIT,
            type("Rs.5000 debited towards your Personal Loan EMI from a/c XX1234.")
        )
}
