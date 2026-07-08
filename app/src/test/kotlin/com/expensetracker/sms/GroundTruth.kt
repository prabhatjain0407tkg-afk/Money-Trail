package com.expensetracker.sms

import com.expensetracker.sms.parser.MessageType

/**
 * The single "is this really a transaction, and which way does money move?" label.
 *
 *  - [IGNORED]  the SMS is NOT a transaction (statement, OTP, promo, biller ack,
 *               store credit, …) → the pipeline must produce nothing.
 *  - [EXPENSE]  real money OUT of the user's account (a debit).
 *  - [INCOME]   real money IN to the user's account (a credit).
 */
enum class Outcome { IGNORED, EXPENSE, INCOME }

/**
 * Ground-truth corpus: real-world SMS samples labelled with their correct outcome.
 *
 * This is the parser's canonical regression dataset. Whenever a real message is
 * found to parse wrongly, add it here with the correct expectation — it is then
 * permanently guarded and contributes to the measured accuracy in [GroundTruthTest].
 *
 * Keep every sample REAL (from an actual phone). Redact only personal identifiers
 * (account tails, emails, phone numbers) — never reshape the sentence structure,
 * because the structure is exactly what the parser is being tested against.
 */
data class GtCase(
    val name: String,
    val sender: String,
    val body: String,
    /** Expected [com.expensetracker.sms.parser.SmsClassifier] verdict. */
    val type: MessageType,
    /** The bottom line: not-a-transaction (IGNORED) vs EXPENSE vs INCOME. */
    val outcome: Outcome,
    val amount: Double? = null,       // expected amount when it is an EXPENSE / INCOME
    val merchant: String? = null,     // parsed merchant must CONTAIN this (case-insensitive)
    val bank: String? = null,         // expected bank tag
)

object GroundTruth {

    val cases: List<GtCase> = listOf(

        // ── Real debits ──────────────────────────────────────────────────────
        GtCase(
            name = "HDFC UPI debit",
            sender = "VM-HDFCBK",
            body = "HDFC Bank: Rs 450.00 debited from a/c **1234 on 12-Jun-25. " +
                    "Info: UPI-SWIGGY-swiggy@okicici. Avl Bal:Rs 12,300.00. " +
                    "If not done by you, call 18002586161.",
            type = MessageType.DEBIT, outcome = Outcome.EXPENSE,
            amount = 450.0, merchant = "SWIGGY", bank = "HDFC",
        ),
        GtCase(
            name = "HDFC credit-card spend",
            sender = "VM-HDFCBK",
            body = "INR 1,450.00 spent on HDFC Bank Credit Card ending 5678 at AMAZON " +
                    "on 12-06-25:15:30:00. Avl Credit Limit INR 45,000.00.",
            type = MessageType.DEBIT, outcome = Outcome.EXPENSE,
            amount = 1450.0, merchant = "AMAZON",
        ),
        GtCase(
            name = "ICICI UPI debit",
            sender = "VM-ICICIB",
            body = "ICICI Bank Acct XX1234 debited with INR 450.00 on 12-Jun-2025 20:30:00 IST. " +
                    "Info: UPI/202506121234/SWIGGY. Avl Bal: INR 12,050.00.",
            type = MessageType.DEBIT, outcome = Outcome.EXPENSE,
            amount = 450.0, bank = "ICICI",
        ),
        GtCase(
            name = "Kotak UPI debit",
            sender = "VM-KOTAKB",
            body = "Rs.450.00 debited from Kotak Bank Ac XX1234 on 12/06/25 to swiggy@upi. " +
                    "Avl Bal Rs.12050.00. Not you? Call 18602662666.",
            type = MessageType.DEBIT, outcome = Outcome.EXPENSE,
            amount = 450.0, bank = "KOTAK",
        ),

        // ── Real credits (incl. salary worded as "deposited") ────────────────
        GtCase(
            name = "ICICI salary credit",
            sender = "VM-ICICIB",
            body = "ICICI Bank Acct XX1234 credited with INR 75,000.00 on 01-Jun-2025. " +
                    "Info: NEFT/SALACORP/SALARY. Avl Bal: INR 85,000.00.",
            type = MessageType.CREDIT, outcome = Outcome.INCOME,
            amount = 75000.0, bank = "ICICI",
        ),
        GtCase(
            name = "Kotak credit",
            sender = "VM-KOTAKB",
            body = "INR 5,000.00 credited to your Kotak Bank Ac XXXXXXXX8438 on 14-Jun-26. " +
                    "Avl Bal INR 25,000.00",
            type = MessageType.CREDIT, outcome = Outcome.INCOME,
            amount = 5000.0, bank = "KOTAK",
        ),
        GtCase(
            name = "Salary deposited wording",
            sender = "VM-GENERIC",
            body = "Rs.50,000.00 deposited to your A/c XX1234 on 30-Jun-26 towards SALARY. " +
                    "Avl Bal Rs.75,000.00.",
            type = MessageType.CREDIT, outcome = Outcome.INCOME, amount = 50000.0,
        ),

        // ── FASTag / toll (parsed by TollParser after SmsParser declines) ────
        GtCase(
            name = "FASTag toll plaza",
            sender = "AX-IDFCFB-S",
            body = "INR 240 toll paid from IDFC FIRST Bank Tag 3XXX3600 for vehicle no. " +
                    "MH14JH5530 at Talegaon Toll Plaza on 04/07/2026 16:32. Avbl. Bal.: INR596.50.",
            type = MessageType.UNKNOWN, outcome = Outcome.EXPENSE,
            amount = 240.0, merchant = "TALEGAON", bank = "FASTAG",
        ),
        GtCase(
            name = "FASTag non-toll (mall)",
            sender = "JM-IDFCFB-S",
            body = "INR 30 using IDFC FIRST Bank FASTag 3XXX3600 done at SeasonMall " +
                    "on 04/11/2025 13:53. Avbl. Bal.: INR 253.0",
            type = MessageType.UNKNOWN, outcome = Outcome.EXPENSE,
            amount = 30.0, merchant = "SEASONMALL", bank = "FASTAG",
        ),

        // ── Must be rejected (never a transaction) ───────────────────────────
        GtCase(
            name = "ICICI credit-card statement",
            sender = "AD-ICICIB",
            body = "ICICI Bank Credit Card XX8009 Statement is sent to j.****90@gmail.com. " +
                    "Total of Rs 4,271.00 or minimum of Rs 220.00 is due by 29-MAY-26.",
            type = MessageType.STATEMENT, outcome = Outcome.IGNORED,
        ),
        GtCase(
            name = "JioHome biller acknowledgement",
            sender = "JM-JioHom",
            body = "Dear Customer,\nPayment of Rs. 706.82 for your JioHome connection with " +
                    "JioFixedVoice Number +917683359058 through Standing instructions on Autopay " +
                    "UPI has been received on 15-Jun-26. Thank you!\nTeam JioHome",
            type = MessageType.BILL_ACK, outcome = Outcome.IGNORED,
        ),
        GtCase(
            name = "Airtel payment acknowledgement",
            sender = "AD-AIRTEL",
            body = "We have received your payment of Rs.1,299.00 towards your Airtel " +
                    "postpaid bill. Thank you for the payment.",
            type = MessageType.BILL_ACK, outcome = Outcome.IGNORED,
        ),
        GtCase(
            name = "Bewakoof store credit",
            sender = "AD-BWKOOF",
            body = "An amount of INR 150.00 has been CREDITED to your Bewakoof account on " +
                    "11/06/2026. Auto-applied on Checkout. Use it to shop your favorites now - bwkoof.com/efq",
            type = MessageType.STORE_CREDIT, outcome = Outcome.IGNORED,
        ),
        GtCase(
            name = "OTP",
            sender = "VM-SOMEBANK",
            body = "Your OTP for login is 123456. Valid for 10 minutes. Do not share.",
            type = MessageType.OTP, outcome = Outcome.IGNORED,
        ),
        GtCase(
            name = "UPI collect request",
            sender = "VM-PHONEPE",
            body = "user@upi has requested Rs.500 from you on PhonePe. Approve to pay.",
            type = MessageType.REQUEST, outcome = Outcome.IGNORED,
        ),
        GtCase(
            name = "Loan offer promo",
            sender = "AD-LOANXX",
            body = "You are eligible for a pre-approved loan of Rs.5,00,000. Apply now!",
            type = MessageType.PROMOTION, outcome = Outcome.IGNORED,
        ),
        GtCase(
            name = "Card limit increase",
            sender = "VM-HDFCBK",
            body = "Your credit card limit has been increased to Rs.2,00,000.",
            type = MessageType.CARD_CONTROL, outcome = Outcome.IGNORED,
        ),
        // Reads like a credit to the classifier, but has no account/ref/balance and
        // an untrusted sender → the transaction-fingerprint gate drops it. Demonstrates
        // the brand-agnostic defence against fake "Rs.X credited" promos.
        GtCase(
            name = "Fake reward credit (no fingerprint)",
            sender = "AD-OFFERS",
            body = "Congratulations! Rs.500 has been credited as a special reward for you.",
            type = MessageType.CREDIT, outcome = Outcome.IGNORED,
        ),
    )
}
