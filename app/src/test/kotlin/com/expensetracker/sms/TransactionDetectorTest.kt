package com.expensetracker.sms

import com.expensetracker.sms.parser.TransactionDetector
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TransactionDetector] — the explainable evidence scorer that
 * decides whether an SMS carries genuine transaction fingerprints.
 */
class TransactionDetectorTest {

    @Test
    fun `strong bank SMS scores high with all fingerprints`() {
        val r = TransactionDetector.detect(
            "VM-HDFCBK",
            "INR 1,450.00 spent on HDFC Bank Credit Card ending 5678 at AMAZON. " +
                    "UPI Ref 512345678901. Avl Bal INR 45,000.00."
        )
        assertTrue("Should have a structural fingerprint", r.hasFingerprint)
        assertTrue("Confidence should be high, was ${r.confidence}", r.confidence >= 0.9)
        // The signals that fired should be spelled out for auditing.
        assertTrue(r.reasons.contains("Trusted bank sender"))
        assertTrue(r.reasons.contains("Card/account number found"))
        assertTrue(r.reasons.contains("Transaction reference found"))
        assertTrue(r.reasons.contains("Available balance present"))
        assertTrue(r.reasons.any { it.startsWith("Financial verb") })
    }

    @Test
    fun `promo credit has no fingerprint and low confidence`() {
        val r = TransactionDetector.detect(
            "AD-OFFERS",
            "Congratulations! Rs.500 has been credited as a special reward. Shop now!"
        )
        assertFalse("A promo carries no transaction fingerprint", r.hasFingerprint)
        assertTrue("Confidence should be low, was ${r.confidence}", r.confidence < 0.6)
    }

    @Test
    fun `store-credit is penalised`() {
        val r = TransactionDetector.detect(
            "AD-BWKOOF",
            "INR 150 credited to your Bewakoof account. Use it to shop your favorites."
        )
        assertFalse(r.hasFingerprint)
        assertTrue(r.reasons.contains("Store-credit phrasing present"))
    }

    @Test
    fun `untrusted sender but real fingerprints still detected`() {
        // A genuine transaction from a bank we don't have a template for still shows
        // fingerprints (masked account + balance), so it is trusted structurally.
        val r = TransactionDetector.detect(
            "AX-NEWBNK",
            "Rs.900 debited from A/c XX4321 on 05-Jul. Avl Bal Rs.10,000."
        )
        assertTrue(r.hasFingerprint)
        assertTrue(r.confidence >= 0.7)
    }
}
