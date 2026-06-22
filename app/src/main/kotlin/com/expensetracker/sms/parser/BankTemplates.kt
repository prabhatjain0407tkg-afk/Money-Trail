package com.expensetracker.sms.parser

/**
 * Regex templates for parsing Indian bank and UPI transaction SMS.
 *
 * Each [BankTemplate] has:
 *   - [senderPatterns] — matched against the SMS sender-ID to short-circuit search
 *   - [bodyPatterns]   — tried in order; first match wins
 *
 * Named capture groups used (all optional except `amount`):
 *   amount   — numeric string, may contain commas (e.g. "1,450.00")
 *   merchant — payee / merchant name string (will be uppercased + trimmed)
 *   acct     — last 4 digits of account or card
 *   bal      — available balance numeric string
 *   ref      — UPI / transaction reference number
 *   type     — raw string containing "debit"/"credit" (case-insensitive)
 *
 * To add a new bank: create a new BankTemplate entry in [ALL] and add the
 * sender pattern(s) you see in your own messages.
 */

internal data class BankTemplate(
    val bankName: String,
    val senderPatterns: List<Regex>,
    val bodyPatterns: List<Regex>,
    val isAlwaysDebit: Boolean = false,  // some templates only ever fire for debits
    val isAlwaysCredit: Boolean = false
)

internal object BankTemplates {

    // ─── Shared amount component ────────────────────────────────────────────
    // Handles: Rs. / Rs / INR / ₹  followed by optional space and digits with commas
    private const val AMT = """(?:Rs\.?|INR|₹)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)"""

    // ─── Templates ──────────────────────────────────────────────────────────

    /** HDFC Bank — savings account UPI debit */
    private val HDFC_UPI_DEBIT = BankTemplate(
        bankName = "HDFC",
        senderPatterns = listOf(
            Regex(""".*HDFCBK.*""", RegexOption.IGNORE_CASE),
            Regex(""".*HDFCBANK.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "HDFC Bank: Rs 450.00 debited from a/c **1234 on 12-Jun-25. Info: UPI-SWIGGY-swiggy@okicici. Avl Bal:Rs 12,300.00"
            // Note: balance uses (?<bal>...) — Java regex forbids two groups with the same name in one pattern.
            Regex(
                """$AMT\s+debited from a/c[*X]+(?<acct>\d{4}).*?UPI-(?<merchant>[A-Z0-9 &]+?)-.*?(?:Avl Bal[:\s]*(?:Rs\.?|INR|₹)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // Pattern when UPI-MERCHANT- format is not present — generic HDFC debit
            Regex(
                """$AMT\s+debited from a/c[*X]+(?<acct>\d{4}).*?(?:to|Info:)\s*(?<merchant>[A-Z0-9 &@.]+?)(?:\.|,|${'$'}|\s{2})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        ),
        isAlwaysDebit = true
    )

    /** HDFC Bank — credit card spend */
    private val HDFC_CC = BankTemplate(
        bankName = "HDFC",
        senderPatterns = listOf(
            Regex(""".*HDFCBK.*""", RegexOption.IGNORE_CASE),
            Regex(""".*HDFCCC.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "INR 450.00 spent on HDFC Bank Credit Card ending 1234 at SWIGGY on 12-06-25"
            Regex(
                """(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+spent on HDFC Bank Credit Card ending\s*(?<acct>\d{4})\s+at\s+(?<merchant>.+?)\s+on\s""",
                setOf(RegexOption.IGNORE_CASE)
            )
        ),
        isAlwaysDebit = true
    )

    /** ICICI Bank — savings account UPI / NEFT */
    private val ICICI = BankTemplate(
        bankName = "ICICI",
        senderPatterns = listOf(
            Regex(""".*ICICIB.*""", RegexOption.IGNORE_CASE),
            Regex(""".*ICICIBANK.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "ICICI Bank Acct XX1234 debited with INR 450.00 on 12-Jun-2025. Info: UPI/ref/SWIGGY. Avl Bal: INR 12,050.00"
            Regex(
                """ICICI Bank Acct XX(?<acct>\d{4})\s+(?<type>debited|credited) with\s+(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?).*?(?:UPI/[^/]+/(?<merchant>[^.\s]+))?.*?(?:Avl Bal[:\s]*(?:INR|Rs\.?)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // "Dear Customer, Rs. 450.00 has been debited from your ICICI Bank A/c XX1234"
            Regex(
                """(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+has been\s+(?<type>debited|credited).*?A/c XX(?<acct>\d{4}).*?(?:to|from)\s+(?<merchant>[A-Z0-9 &]+?)(?:\.|,|Available)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** State Bank of India */
    private val SBI = BankTemplate(
        bankName = "SBI",
        senderPatterns = listOf(
            Regex(""".*SBIINB.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*SBIPSG.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*SBICRD.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*SBICARD.*""", RegexOption.IGNORE_CASE),
            Regex(""".*SBI.*""",     RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // SBI Credit Card spend:
            // "Rs.733.40 spent on your SBI Credit Card ending 5854 at IndianRailwayCatering on 17/04/26."
            // "Rs.3,000.00 spent on your SBI Credit Card ending 5854 at RoshniServices on 11/06/26."
            Regex(
                """Rs\.(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<type>spent)\s+on\s+your\s+SBI\s+Credit\s+Card\s+ending\s+(?<acct>\d{4})\s+at\s+(?<merchant>[A-Za-z][A-Za-z0-9 &]{1,30}?)\s+on\s+\d""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // SBI Credit Card — no merchant captured (fallback)
            Regex(
                """Rs\.(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<type>spent)\s+on\s+your\s+SBI\s+Credit\s+Card\s+ending\s+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // SBI savings/current account:
            // "Your A/c no. XX1234 is debited for Rs.450.00 on 12JUN25. The beneficiary is SWIGGY."
            Regex(
                """A/c\s*(?:no\.?|number)?\s*XX(?<acct>\d{4})\s+is\s+(?<type>debited|credited).*?(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?).*?(?:beneficiary is|payee:|to)\s+(?<merchant>[A-Z0-9 &]+?)(?:\.|,|\s{2}|UPI).*?(?:Ref\.?\s*No\.?\s*is\s+(?<ref>\d+))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // Fallback — just extract amount and debit/credit type
            Regex(
                """(?<type>debited|credited).*?(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?).*?A/c\s*[X*]*(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** Axis Bank */
    private val AXIS = BankTemplate(
        bankName = "AXIS",
        senderPatterns = listOf(
            Regex(""".*AXISBK.*""", RegexOption.IGNORE_CASE),
            Regex(""".*AXISBANK.*""", RegexOption.IGNORE_CASE),
            Regex(""".*AXIS.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // Axis Bank CC — foreign currency spend:
            // "Spent USD 23.6 Axis Bank Card no. XX4501 14-06-26 23:34:37 IST ANTHROPIC* Avl Limit: INR 88215.03"
            Regex(
                """Spent (?<ccy>[A-Z]{3}) (?<amount>[\d.]+)\s+Axis Bank Card no\.\s*[X*]+(?<acct>\d{4})[^I]+IST\s+(?<merchant>[A-Z*][A-Z0-9* &]{1,30}?)\s+Avl Limit""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // Axis Bank CC — INR spend:
            // "Spent INR 666 Axis Bank Card no. XX4501 ... Bundl Techn Avl Limit: INR 90469.65"
            Regex(
                """Spent (?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+Axis Bank Card no\.\s*[X*]+(?<acct>\d{4})[^A]+?\s+(?<merchant>[A-Za-z][A-Za-z0-9* &]{1,30}?)\s+Avl Limit""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // Axis Bank UPI/NEFT — "INR 450 has been debited ... for payment to swiggy@upi"
            Regex(
                """(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+has been\s+(?<type>debited|credited).*?Axis Bank.*?[X*]+(?<acct>\d{4}).*?(?:for payment to|Payee VPA:)\s*(?<merchant>[^@.\s,]+)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // Axis Bank UPI — "INR 450 has been debited from your Axis Bank account XX1234"
            Regex(
                """(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+has been\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4}).*?(?:Payee VPA:\s*(?<merchant>[^@.\s]+))?.*?(?:(?:Bal|Balance)[:\s]*(?:INR|Rs\.?)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // Generic Axis debit with "to/from MERCHANT"
            Regex(
                """(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4}).*?(?:to|from)\s+(?<merchant>[A-Z0-9 &]+?)(?:\.|,|\s{2})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** Kotak Mahindra Bank */
    private val KOTAK = BankTemplate(
        bankName = "KOTAK",
        senderPatterns = listOf(
            Regex(""".*KOTAKB.*""", RegexOption.IGNORE_CASE),
            Regex(""".*KOTAK.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "Rs.450.00 debited from Kotak Bank Ac XX1234 on 12/06/25 to swiggy@upi. Avl Bal Rs.12050.00"
            Regex(
                """(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<type>debited|credited).*?Ac\s+XX(?<acct>\d{4}).*?to\s+(?<merchant>[^@.\s]+).*?(?:Avl Bal\s*(?:INR|Rs\.?)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** Paytm Payments Bank / Paytm wallet */
    private val PAYTM = BankTemplate(
        bankName = "PAYTM",
        senderPatterns = listOf(
            Regex(""".*PYTM.*""", RegexOption.IGNORE_CASE),
            Regex(""".*PAYTM.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "Rs. 450.00 debited from your Paytm account. Paid to SWIGGY. UPI Ref: 512345"
            Regex(
                """(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<type>debited|credited).*?(?:Paid to|Transfer to|received from)\s+(?<merchant>[A-Z0-9 &]+?)(?:\.|,|\s{2}|UPI).*?(?:Ref[:\s]+(?<ref>\d+))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // "Your Paytm Wallet has been debited with Rs.450.00 for payment to SWIGGY"
            Regex(
                """(?<type>debited|credited) with\s+(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+for payment to\s+(?<merchant>[A-Z0-9 &]+?)(?:\.|,|${'$'})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** Bank of Baroda */
    private val BOB = BankTemplate(
        bankName = "BOB",
        senderPatterns = listOf(
            Regex(""".*BARODAMPLS.*""", RegexOption.IGNORE_CASE),
            Regex(""".*BOBALRT.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            Regex(
                """(?<type>Debit|Credit)\s+alert.*?(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?).*?A/c[*X ]+(?<acct>\d{4}).*?(?:to|from)\s+(?<merchant>[A-Z0-9 &]+?)(?:\.|,|\s{2})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** Punjab National Bank */
    private val PNB = BankTemplate(
        bankName = "PNB",
        senderPatterns = listOf(
            Regex(""".*PNBSMS.*""", RegexOption.IGNORE_CASE),
            Regex(""".*PUNBSM.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            Regex(
                """A/c[*X ]+(?<acct>\d{4}).*?(?<type>Debited|Credited)\s+(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?).*?(?:Ref\.?\s*No\.?\s*(?<ref>\d+))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /**
     * Generic UPI — catches most UPI apps (Google Pay, PhonePe, BHIM, etc.)
     * when the sender isn't bank-specific.
     */
    private val GENERIC_UPI = BankTemplate(
        bankName = "UPI",
        senderPatterns = listOf(
            Regex(""".*GPAY.*""", RegexOption.IGNORE_CASE),
            Regex(""".*PHONEPE.*""", RegexOption.IGNORE_CASE),
            Regex(""".*BHIM.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "Rs.450 sent to SWIGGY via UPI. UPI Ref: 512345678"
            Regex(
                """(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+sent to\s+(?<merchant>[A-Z0-9 &]+?)\s+via UPI.*?(?:Ref[:\s]+(?<ref>\d+))?""",
                setOf(RegexOption.IGNORE_CASE)
            ),
            // "Payment of Rs.450 successful to VPA swiggy@upi"
            Regex(
                """[Pp]ayment of\s+(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+successful to\s+(?:VPA\s+)?(?<merchant>[^@.\s]+)""",
                RegexOption.IGNORE_CASE
            )
        ),
        isAlwaysDebit = true
    )

    /**
     * Generic credit card "spent" template — catches ANY bank's credit card SMS that follows
     * the Indian standard format: "Rs.X spent on your BANK Credit Card ending XXXX at MERCHANT on DD"
     * Tried after bank-specific templates so it only fires when the specific template misses.
     */
    private val CREDIT_CARD_GENERIC = BankTemplate(
        bankName = "CREDITCARD",
        senderPatterns = emptyList(),   // body-matched; no sender restriction
        bodyPatterns = listOf(
            // With merchant: "Rs.X spent on your SBI/HDFC/Axis/... Credit Card ending XXXX at MERCHANT on DD"
            Regex(
                """Rs\.(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<type>spent)\s+on\s+your\s+(?:[A-Za-z]+\s+){1,4}?Credit\s+Card\s+ending\s+(?<acct>\d{4})\s+at\s+(?<merchant>[A-Za-z][A-Za-z0-9 &]{1,40}?)\s+on\s+\d""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // Without merchant (fallback): just capture amount + account
            Regex(
                """Rs\.(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<type>spent)\s+on\s+your\s+(?:[A-Za-z]+\s+){1,4}?Credit\s+Card\s+ending\s+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
        ),
        isAlwaysDebit = true
    )

    /**
     * Generic heuristic fallback — used when no bank template matches.
     * Lower confidence. Looks for any currency amount + debit/credit signal.
     */
    internal val GENERIC_FALLBACK = BankTemplate(
        bankName = "GENERIC",
        senderPatterns = emptyList(),
        bodyPatterns = listOf(
            Regex(
                """(?:INR|Rs\.?|₹)\s*(?<amount>[\d,]+(?:\.\d{1,2})?).*?(?<type>debited|credited|spent|sent|received)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """(?<type>debited|credited|spent|sent|received).*?(?:INR|Rs\.?|₹)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** All templates tried in order when the sender doesn't match directly. */
    val ALL: List<BankTemplate> = listOf(
        HDFC_UPI_DEBIT,
        HDFC_CC,
        ICICI,
        SBI,
        AXIS,
        KOTAK,
        PAYTM,
        BOB,
        PNB,
        GENERIC_UPI,
        CREDIT_CARD_GENERIC  // bank-agnostic CC fallback — catches SBI/HDFC/any "Rs.X spent" format
    )
}
