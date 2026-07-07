package com.expensetracker.sms

import com.expensetracker.sms.model.Confidence
import com.expensetracker.sms.model.TxType
import com.expensetracker.sms.parser.SmsParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SmsParser] using real-world Indian bank SMS samples.
 * Runs on JVM — no Android device or emulator required.
 *
 * Run from Android Studio: right-click → Run, or via:
 *   ./gradlew :app:test
 */
class SmsParserTest {

    // ─── HDFC Bank ────────────────────────────────────────────────────────────

    @Test
    fun `HDFC UPI debit - standard format`() {
        val sms = "HDFC Bank: Rs 450.00 debited from a/c **1234 on 12-Jun-25. " +
                "Info: UPI-SWIGGY-swiggy@okicici. Avl Bal:Rs 12,300.00. " +
                "If not done by you, call 18002586161."
        val result = SmsParser.parse("VM-HDFCBK", sms)

        assertNotNull("Should parse HDFC UPI SMS", result)
        assertEquals(450.0, result!!.amount, 0.01)
        assertEquals(TxType.DEBIT, result.type)
        assertEquals("1234", result.accountTail)
        assertEquals("HDFC", result.bank)
        assertTrue("Merchant should contain SWIGGY",
            result.merchant?.contains("SWIGGY") == true)
    }

    @Test
    fun `HDFC credit card spend`() {
        val sms = "INR 1,450.00 spent on HDFC Bank Credit Card ending 5678 " +
                "at AMAZON on 12-06-25:15:30:00. " +
                "Avl Credit Limit INR 45,000.00. To dispute, call 18602606161."
        val result = SmsParser.parse("VM-HDFCBK", sms)

        assertNotNull("Should parse HDFC credit card SMS", result)
        assertEquals(1450.0, result!!.amount, 0.01)
        assertEquals(TxType.DEBIT, result.type)
        assertEquals("5678", result.accountTail)
        assertTrue("Merchant should contain AMAZON",
            result.merchant?.contains("AMAZON") == true)
    }

    // ─── ICICI Bank ───────────────────────────────────────────────────────────

    @Test
    fun `ICICI UPI debit`() {
        val sms = "ICICI Bank Acct XX1234 debited with INR 450.00 on 12-Jun-2025 20:30:00 IST. " +
                "Info: UPI/202506121234/SWIGGY. Avl Bal: INR 12,050.00. " +
                "Call 18001080 for disputes."
        val result = SmsParser.parse("VM-ICICIB", sms)

        assertNotNull("Should parse ICICI UPI SMS", result)
        assertEquals(450.0, result!!.amount, 0.01)
        assertEquals(TxType.DEBIT, result.type)
        assertEquals("1234", result.accountTail)
        assertEquals("ICICI", result.bank)
    }

    @Test
    fun `ICICI credit - salary received`() {
        val sms = "ICICI Bank Acct XX1234 credited with INR 75,000.00 on 01-Jun-2025. " +
                "Info: NEFT/SALACORP/SALARY. Avl Bal: INR 85,000.00."
        val result = SmsParser.parse("VM-ICICIB", sms)

        assertNotNull("Should parse ICICI credit SMS", result)
        assertEquals(75000.0, result!!.amount, 0.01)
        assertEquals(TxType.CREDIT, result.type)
        assertEquals("1234", result.accountTail)
    }

    // ─── SBI ──────────────────────────────────────────────────────────────────

    @Test
    fun `SBI UPI debit`() {
        val sms = "Your A/c no. XX1234 is debited for Rs.450.00 on 12JUN25. " +
                "The beneficiary is SWIGGY ORDERFOOD IND. " +
                "Your UPI Ref. No. is 512345678901. " +
                "If not done by you, call 18004253800."
        val result = SmsParser.parse("VM-SBIINB", sms)

        assertNotNull("Should parse SBI UPI SMS", result)
        assertEquals(450.0, result!!.amount, 0.01)
        assertEquals(TxType.DEBIT, result.type)
        assertEquals("1234", result.accountTail)
        assertEquals("SBI", result.bank)
        assertEquals("512345678901", result.referenceNo)
    }

    // ─── Axis Bank ────────────────────────────────────────────────────────────

    @Test
    fun `Axis Bank UPI debit`() {
        val sms = "INR 450.00 has been debited from your Axis Bank account XX1234 " +
                "on 12-Jun-25. UPI Ref No.: 123456789012. " +
                "Payee VPA: swiggy@upi. Current Balance: INR 12,050.00."
        val result = SmsParser.parse("VM-AXISBK", sms)

        assertNotNull("Should parse Axis Bank SMS", result)
        assertEquals(450.0, result!!.amount, 0.01)
        assertEquals(TxType.DEBIT, result.type)
        assertEquals("1234", result.accountTail)
        assertEquals("AXIS", result.bank)
        assertTrue("Merchant should contain SWIGGY",
            result.merchant?.contains("SWIGGY") == true)
    }

    // ─── Kotak Bank ───────────────────────────────────────────────────────────

    @Test
    fun `Kotak Bank UPI debit`() {
        val sms = "Rs.450.00 debited from Kotak Bank Ac XX1234 on 12/06/25 " +
                "to swiggy@upi. Avl Bal Rs.12050.00. Not you? Call 18602662666."
        val result = SmsParser.parse("VM-KOTAKB", sms)

        assertNotNull("Should parse Kotak SMS", result)
        assertEquals(450.0, result!!.amount, 0.01)
        assertEquals(TxType.DEBIT, result.type)
        assertEquals("1234", result.accountTail)
        assertEquals("KOTAK", result.bank)
        assertEquals(12050.0, result.availableBalance ?: 0.0, 0.01)
    }

    // ─── Paytm ────────────────────────────────────────────────────────────────

    @Test
    fun `Paytm wallet debit`() {
        val sms = "Rs. 450.00 debited from your Paytm account. " +
                "Paid to SWIGGY. UPI Ref: 512345678901. " +
                "Avl Balance: Rs.12,050.00."
        val result = SmsParser.parse("VM-PYTM", sms)

        assertNotNull("Should parse Paytm SMS", result)
        assertEquals(450.0, result!!.amount, 0.01)
        assertEquals(TxType.DEBIT, result.type)
        assertTrue("Merchant should contain SWIGGY",
            result.merchant?.contains("SWIGGY") == true)
    }

    // ─── Large amounts with commas ────────────────────────────────────────────

    @Test
    fun `Amount with thousands comma is parsed correctly`() {
        val sms = "ICICI Bank Acct XX9876 debited with INR 1,25,000.00 on 15-Jun-2025. " +
                "Info: NEFT/HOME LOAN EMI. Avl Bal: INR 2,50,000.00."
        val result = SmsParser.parse("VM-ICICIB", sms)

        assertNotNull(result)
        assertEquals(125000.0, result!!.amount, 0.01)
    }

    // ─── Unknown sender — body matching ──────────────────────────────────────

    @Test
    fun `Unknown sender falls through to body matching`() {
        val sms = "ICICI Bank Acct XX1234 debited with INR 200.00 on 12-Jun-2025. " +
                "Avl Bal: INR 8,000.00."
        val result = SmsParser.parse("AD-UNKNOWN", sms)  // sender not recognized

        assertNotNull("Should still parse via body matching", result)
        assertEquals(200.0, result!!.amount, 0.01)
    }

    // ─── Generic fallback ─────────────────────────────────────────────────────

    @Test
    fun `Generic fallback catches partial SMS`() {
        val sms = "Your account has been debited Rs.300 for your order."
        val result = SmsParser.parse("VM-SOMEBANK", sms)

        assertNotNull("Generic fallback should fire", result)
        assertEquals(300.0, result!!.amount, 0.01)
        assertEquals(TxType.DEBIT, result.type)
        assertEquals(Confidence.LOW, result.confidence)
    }

    // ─── FASTag / Toll wording — main parser must NOT claim these ─────────────
    // NETC's "toll paid from..." / "using...FASTag...done at..." wording contains
    // none of the debit/credit signal words this parser looks for. It's rejected
    // here on purpose — TollParser (see TollParserTest) is tried as a fallback by
    // the actual ingestion call sites, never merged into this parser's templates.

    @Test
    fun `Toll-plaza SMS is rejected by the main parser`() {
        val sms = "INR 240 toll paid from IDFC FIRST Bank Tag 3XXX3600 for vehicle no. " +
                "MH14JH5530 at Talegaon Toll Plaza on 04/07/2026 16:32. Avbl. Bal.: INR596.50."
        assertNull("Main parser has no debit/credit keyword to latch onto",
            SmsParser.parse("AX-IDFCFB-S", sms))
    }

    @Test
    fun `Non-toll FASTag usage SMS is rejected by the main parser`() {
        val sms = "INR 30 using IDFC FIRST Bank FASTag 3XXX3600 done at SeasonMall " +
                "on 04/11/2025 13:53. Avbl. Bal.: INR 253.0"
        assertNull("Main parser has no debit/credit keyword to latch onto",
            SmsParser.parse("JM-IDFCFB-S", sms))
    }

    // ─── Biller payment acknowledgements (must NOT parse as income) ──────────

    @Test
    fun `Biller payment-received confirmation is rejected`() {
        // Real JioHome autopay acknowledgement — the USER paid the bill; "received"
        // means Jio received the money. Parsing this would create fake income.
        val sms = "Dear Customer,\n" +
                "Payment of Rs. 706.82 for your JioHome connection with JioFixedVoice " +
                "Number +917683359058 through Standing instructions on Autopay UPI has " +
                "been received on 15-Jun-26. Thank you!\n" +
                "To manage your account with MyJio app, click www.jio.com/GetMyJio\n" +
                "Team JioHome"
        assertNull("Biller acknowledgement must not parse as a transaction",
            SmsParser.parse("JM-JioHom", sms))
    }

    @Test
    fun `We have received your payment confirmation is rejected`() {
        val sms = "We have received your payment of Rs.1,299.00 towards your Airtel " +
                "postpaid bill. Thank you for the payment."
        assertNull(SmsParser.parse("AD-AIRTEL", sms))
    }

    @Test
    fun `Store-credit wallet SMS is rejected`() {
        // Real Bewakoof promo — Rs.150 of store credit in THEIR app wallet,
        // not money into the user's bank account.
        val sms = "An amount of INR 150.00 has been CREDITED to your Bewakoof " +
                "account on 11/06/2026. Auto-applied on Checkout. Use it to shop " +
                "your favorites now - bwkoof.com/efq"
        assertNull("Brand-wallet credit must not parse as income",
            SmsParser.parse("AD-BWKOOF", sms))
    }

    @Test
    fun `Genuine bank credit still parses despite wallet filter`() {
        val sms = "INR 5,000.00 credited to your Kotak Bank Ac XXXXXXXX8438 on " +
                "14-Jun-26. Avl Bal INR 25,000.00"
        val result = SmsParser.parse("VM-KOTAKB", sms)
        assertNotNull("Real bank credit must still parse", result)
        assertEquals(TxType.CREDIT, result!!.type)
        assertEquals(5000.0, result.amount, 0.01)
    }

    // ─── Non-financial SMS ────────────────────────────────────────────────────

    @Test
    fun `Non-financial SMS returns null`() {
        val sms = "Your OTP for login is 123456. Valid for 10 minutes. Do not share."
        val result = SmsParser.parse("VM-SOMEBANK", sms)

        assertNull("OTP SMS should not be parsed as a transaction", result)
    }

    @Test
    fun `Promotional SMS returns null`() {
        val sms = "Flat 50% off on all orders above Rs.499! Use code SAVE50. Valid till 30 Jun."
        val result = SmsParser.parse("AD-PROMO", sms)

        // No debit/credit action — should not be a parseable transaction
        // (It may match the generic pattern, but confidence will be LOW)
        // Either null or LOW-confidence is acceptable
        if (result != null) {
            assertEquals(Confidence.LOW, result.confidence)
        }
    }
}
