package com.expensetracker.sms

import com.expensetracker.sms.model.TxType
import com.expensetracker.sms.parser.SmsParser
import com.expensetracker.sms.parser.TollParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TollParser] — the dedicated fallback parser for FASTag/toll SMS.
 *
 * Each test asserts the full two-stage contract used at every ingestion call site
 * (readAndParseInbox, SmsReceiver, SmsNotificationListener):
 *
 *   SmsParser.parse(sender, body) ?: TollParser.parse(sender, body)
 *
 * i.e. the main parser must reject the message first, and TollParser must then
 * recognize it. Real-world samples taken from IDFC FIRST Bank FASTag SMS.
 *
 * Run from Android Studio: right-click → Run, or via:
 *   ./gradlew :app:test
 */
class TollParserTest {

    @Test
    fun `toll paid at named toll plaza`() {
        val sender = "AX-IDFCFB-S"
        val sms = "INR 240 toll paid from IDFC FIRST Bank Tag 3XXX3600 for vehicle no. " +
                "MH14JH5530 at Talegaon Toll Plaza on 04/07/2026 16:32. Avbl. Bal.: INR596.50."

        assertNull("Main parser must reject this wording", SmsParser.parse(sender, sms))

        val result = TollParser.parse(sender, sms)
        assertNotNull("TollParser should recognize the toll-plaza format", result)
        assertEquals(240.0, result!!.amount, 0.01)
        assertEquals(TxType.DEBIT, result.type)
        assertEquals("FASTAG", result.bank)
        assertEquals(596.50, result.availableBalance!!, 0.01)
        assertTrue("Merchant should contain the plaza name",
            result.merchant?.contains("TALEGAON") == true)
    }

    @Test
    fun `toll paid with highway name embedded in plaza name`() {
        val sender = "AX-IDFCFB-S"
        val sms = "INR 54 toll paid from IDFC FIRST Bank Tag 3XXX3600 for vehicle no. " +
                "MH14JH5530 at Dehuroad NH48 Toll Plaza on 04/07/2026 11:39. Avbl. Bal.: INR836.50."

        assertNull(SmsParser.parse(sender, sms))

        val result = TollParser.parse(sender, sms)
        assertNotNull(result)
        assertEquals(54.0, result!!.amount, 0.01)
        assertEquals(TxType.DEBIT, result.type)
        assertTrue(result.merchant?.contains("DEHUROAD") == true)
    }

    @Test
    fun `toll paid with decimal amount`() {
        val sender = "JM-IDFCFB-S"
        val sms = "INR 23.5 toll paid from IDFC FIRST Bank Tag 3XXX3600 for vehicle no. " +
                "MH14JH5530 at Kusgaon NH48 Toll Plaza on 23/11/2025 14:06. Avbl. Bal.: INR635.50."

        assertNull(SmsParser.parse(sender, sms))

        val result = TollParser.parse(sender, sms)
        assertNotNull(result)
        assertEquals(23.5, result!!.amount, 0.01)
    }

    @Test
    fun `non-toll FASTag usage at a mall`() {
        val sender = "JM-IDFCFB-S"
        val sms = "INR 30 using IDFC FIRST Bank FASTag 3XXX3600 done at SeasonMall " +
                "on 04/11/2025 13:53. Avbl. Bal.: INR 253.0"

        assertNull("Main parser must reject this wording", SmsParser.parse(sender, sms))

        val result = TollParser.parse(sender, sms)
        assertNotNull("TollParser should recognize the non-toll FASTag format", result)
        assertEquals(30.0, result!!.amount, 0.01)
        assertEquals(TxType.DEBIT, result.type)
        assertEquals("FASTAG", result.bank)
        assertTrue(result.merchant?.contains("SEASONMALL") == true)
    }

    @Test
    fun `bank-agnostic wording also matches other issuers`() {
        val sender = "AD-SBIFTG-S"
        val sms = "INR 100 toll paid from SBI Bank Tag XXXX1234 for vehicle no. " +
                "KA01AB1234 at Hosur Toll Plaza on 01/01/2026 10:00. Avbl. Bal.: INR 500.0"

        assertNull(SmsParser.parse(sender, sms))

        val result = TollParser.parse(sender, sms)
        assertNotNull("TollParser should not be tied to a specific issuing bank", result)
        assertEquals(100.0, result!!.amount, 0.01)
    }

    @Test
    fun `unrelated SMS is rejected`() {
        val sms = "Your OTP for login is 123456. Valid for 10 minutes."
        assertNull(TollParser.parse("VM-SOMEBANK", sms))
    }
}
