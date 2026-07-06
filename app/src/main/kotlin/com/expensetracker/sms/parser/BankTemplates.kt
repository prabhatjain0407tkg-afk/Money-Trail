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
            // UPI debit/credit: "Rs.450.00 debited from Kotak Bank Ac XX1234 on 12/06/25 to swiggy@upi. Avl Bal Rs.12050.00"
            Regex(
                """(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<type>debited|credited).*?Ac\s+[X*]+(?<acct>\d{4}).*?to\s+(?<merchant>[^@.\s]+).*?(?:(?:Avl\s*Bal|Avl)\s*(?:INR|Rs\.?)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // NEFT/IMPS salary credit: "INR 275335.00 credited to your Kotak Bank Ac XXXXXXXX8438 on 30-Apr-26. Beneficiary: DATAMETICA SOLUTIONS PRIVATE. NEFT Ref: N09012. Avl Bal INR 275335.00"
            Regex(
                """(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<type>debited|credited).*?Ac\s+[X*]+(?<acct>\d{4}).*?(?:Beneficiary\s*:?\s*(?<merchant>[A-Z0-9][A-Z0-9 &]{2,35}?)(?:\.|,|\s{2})|(?:NEFT|IMPS|RTGS)\s+(?:from|by)\s+(?<merchant2>[A-Z][A-Z0-9 &]{2,35}?)(?:\.|,|\s{2})).*?(?:(?:Avl\s*Bal|Avl)\s*(?:INR|Rs\.?)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // Amount-first fallback: "INR 450.00 debited/credited ... Ac XXXXXXXX1234"
            Regex(
                """(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?<type>debited|credited).*?Ac\s+[X*]+(?<acct>\d{4}).*?(?:(?:Avl\s*Bal|Avl)\s*(?:INR|Rs\.?)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // Account-first fallback: "Your Kotak Bank Ac XXXXXXXX8438 is credited with Rs.338434.00 ..."
            Regex(
                """[Aa]c\s+[X*]+(?<acct>\d{4}).*?(?<type>debited|credited).*?(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?)""",
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

    /** Canara Bank */
    private val CANARA = BankTemplate(
        bankName = "CANARA",
        senderPatterns = listOf(
            Regex(""".*CNRBNK.*""", RegexOption.IGNORE_CASE),
            Regex(""".*CANBK.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*CANARA.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "Acct No.XX1234 debited Rs.5000.Trf to SWIGGY INSTAMART Ref:XXX. Avl Bal:Rs.25000"
            Regex(
                """[Aa]/?c\s*(?:[Nn]o\.?)?\s*[X*]+(?<acct>\d{4}).*?(?<type>debited|credited).*?(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?).*?(?:Trf to|Transfer to|Paid to)\s+(?<merchant>[A-Z0-9 &]+?)(?:\s+(?:Ref|Avl|via)|,|\.).*?(?:(?:Avl Bal|Balance)[:\s]*(?:Rs\.?|INR)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** Union Bank of India (merged Andhra & Corporation banks) */
    private val UNION_BANK = BankTemplate(
        bankName = "UNION BANK",
        senderPatterns = listOf(
            Regex(""".*UNIBNK.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*UNIONBK.*""", RegexOption.IGNORE_CASE),
            Regex(""".*UBISMS.*""",  RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "A/c No.XX1234 Debited INR 5000 on 12-Jun-25. Paid to SWIGGY. Bal:INR 25000"
            Regex(
                """[Aa]/?c\s*(?:[Nn]o\.?)?\s*[X*]+(?<acct>\d{4}).*?(?<type>Debited|Credited).*?(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?).*?(?:Paid to|to)\s+(?<merchant>[A-Z0-9 &]+?)(?:\.|,|\s{2}).*?(?:(?:Bal|Balance)[:\s]*(?:INR|Rs\.?)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** Bank of India */
    private val BANK_OF_INDIA = BankTemplate(
        bankName = "BOI",
        senderPatterns = listOf(
            Regex(""".*BOISMS.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*BOISMSN.*""", RegexOption.IGNORE_CASE),
            Regex(""".*BOIMSN.*""",  RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "BOI A/c XX1234 Dr by Rs.5000 on 12-Jun-25. Info: SWIGGY. Avl Bal Rs.25000"
            Regex(
                """[Aa]/?c\s*[X*]+(?<acct>\d{4})\s+(?<type>Dr|Cr|debited|credited).*?(?:INR|Rs\.?)\s*(?<amount>[\d,]+(?:\.\d{1,2})?).*?(?:Info:|Paid to|to)\s+(?<merchant>[A-Z0-9 &]+?)(?:\.|,|\s{2}).*?(?:Avl Bal\s*(?:Rs\.?|INR)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** IndusInd Bank */
    private val INDUSIND = BankTemplate(
        bankName = "INDUSIND",
        senderPatterns = listOf(
            Regex(""".*INDBNK.*""",   RegexOption.IGNORE_CASE),
            Regex(""".*INDUSLND.*""", RegexOption.IGNORE_CASE),
            Regex(""".*INDUSBK.*""",  RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "INR 5000 debited from your IndusInd a/c XX1234. VPA swiggy@okhdfcbank. Bal INR 25000"
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4}).*?(?:VPA\s+|Paid to\s+|to\s+)(?<merchant>[^@.\s,]+).*?(?:(?:Bal|Balance)[:\s]*(?:INR|Rs\.?)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** Yes Bank */
    private val YES_BANK = BankTemplate(
        bankName = "YES BANK",
        senderPatterns = listOf(
            Regex(""".*YESBK.*""",   RegexOption.IGNORE_CASE),
            Regex(""".*YESBNK.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*YESBANK.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "YBL: INR 5000 debited from A/c XX1234. Paid to swiggy@upi. Avl Bal INR 25000"
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4}).*?(?:Paid to|VPA\s+|to\s+)(?<merchant>[^@.\s,]+).*?(?:(?:Avl Bal|Bal)[:\s]*(?:INR|Rs\.?)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** IDFC FIRST Bank */
    private val IDFC_FIRST = BankTemplate(
        bankName = "IDFC FIRST",
        senderPatterns = listOf(
            Regex(""".*IDFCBK.*""",   RegexOption.IGNORE_CASE),
            Regex(""".*IDFCFST.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*IDFCBANK.*""", RegexOption.IGNORE_CASE),
            Regex(""".*IDFCFB.*""",   RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "IDFC FIRST Bank: INR 5000 debited from a/c XX1234. Paid to swiggy@upi. Avbl Bal: INR 25000"
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4}).*?(?:Paid to|VPA\s+|to\s+)(?<merchant>[^@.\s,]+).*?(?:(?:Avbl Bal|Avail Bal|Bal)[:\s]*(?:INR|Rs\.?)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** Federal Bank */
    private val FEDERAL_BANK = BankTemplate(
        bankName = "FEDERAL",
        senderPatterns = listOf(
            Regex(""".*FEDBK.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*FDRLBK.*""", RegexOption.IGNORE_CASE),
            Regex(""".*FEDBNK.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "FedBank: Rs 5000 debited from A/c XX1234 on 12/06/25. UPI-swiggy@upi. Avl Bal Rs 25000"
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4}).*?(?:UPI-|Paid to\s+|to\s+)(?<merchant>[^@.\s,]+).*?(?:(?:Avl Bal|Bal)[:\s]*(?:Rs\.?|INR)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** RBL Bank (savings + credit card) */
    private val RBL = BankTemplate(
        bankName = "RBL",
        senderPatterns = listOf(
            Regex(""".*RBLBNK.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*RBLBANK.*""", RegexOption.IGNORE_CASE),
            Regex(""".*RBLBKM.*""",  RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // CC: "Rs.5000 spent at SWIGGY on Card ending 1234 on 12-Jun-25"
            Regex(
                """$AMT\s+(?<type>spent)\s+at\s+(?<merchant>[A-Za-z][A-Za-z0-9 &]{1,30}?)\s+on\s+Card\s+ending\s+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // Savings: "Rs.5000 debited from your a/c XX1234. Paid to swiggy@upi. Bal Rs.25000"
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4}).*?(?:Paid to|VPA\s+|to\s+)(?<merchant>[^@.\s,]+).*?(?:(?:Avl Bal|Bal)[:\s]*(?:Rs\.?|INR)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """$AMT\s+(?<type>debited|credited|spent).*?[X*]+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** AU Small Finance Bank */
    private val AU_BANK = BankTemplate(
        bankName = "AU BANK",
        senderPatterns = listOf(
            Regex(""".*AUBANK.*""", RegexOption.IGNORE_CASE),
            Regex(""".*AUSFB.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*AUBNKM.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "AU Bank: INR 5000 debited from A/c XX1234. Paid to swiggy@upi. Avl Bal: INR 25000"
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4}).*?(?:Paid to|VPA\s+|to\s+)(?<merchant>[^@.\s,]+).*?(?:(?:Avl Bal|Bal)[:\s]*(?:INR|Rs\.?)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** Ujjivan Small Finance Bank */
    private val UJJIVAN = BankTemplate(
        bankName = "UJJIVAN",
        senderPatterns = listOf(
            Regex(""".*UJJIVN.*""", RegexOption.IGNORE_CASE),
            Regex(""".*UJJSFB.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "Ujjivan SFB: Rs.5000 debited from a/c XX1234. UPI: swiggy@upi. Avl Bal: Rs.25000"
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4}).*?(?:UPI[:\s]+|Paid to\s+|to\s+)(?<merchant>[^@.\s,]+).*?(?:(?:Avl Bal|Bal)[:\s]*(?:Rs\.?|INR)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    /** Bandhan Bank */
    private val BANDHAN = BankTemplate(
        bankName = "BANDHAN",
        senderPatterns = listOf(
            Regex(""".*BNDNBK.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*BDNBK.*""",   RegexOption.IGNORE_CASE),
            Regex(""".*BANDHAN.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "Bandhan Bank: A/c XX1234 Dr Rs.5000 on 12-Jun-25. To: SWIGGY. Bal Rs.25000"
            Regex(
                """[Aa]/?c\s*[X*]+(?<acct>\d{4})\s+(?<type>Dr|debited|credited).*?$AMT.*?(?:[Tt]o[:\s]+)(?<merchant>[A-Z0-9 &]+?)(?:\.|,|\s+Bal).*?(?:Bal[:\s]*(?:Rs\.?|INR)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            Regex(
                """$AMT\s+(?<type>debited|credited).*?[X*]+(?<acct>\d{4})""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        )
    )

    // ─── Payment Platform Templates ─────────────────────────────────────────

    /**
     * Amazon Pay — distinct sender ID and SMS format from Amazon shopping.
     * Covers: Amazon Pay Balance deductions and Amazon Pay UPI payments.
     */
    private val AMAZON_PAY = BankTemplate(
        bankName = "AMAZON PAY",
        senderPatterns = listOf(
            Regex(""".*AMZNPY.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*AMZNPAY.*""", RegexOption.IGNORE_CASE),
            Regex(""".*AMAZON.*""",  RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "Rs.449 paid from Amazon Pay Balance to SWIGGY ORDER. Ref: XXX. Amazon Pay Balance: Rs.1250"
            Regex(
                """$AMT\s+(?:paid|deducted|debited).*?(?:to|towards|for)\s+(?<merchant>[A-Z0-9 &]+?)(?:\s+(?:ORDER|order|Ref)|[,.]|\s{2}).*?(?:Amazon Pay Balance[:\s]*(?:Rs\.?|INR)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // "You paid Rs.449 to SWIGGY using Amazon Pay UPI"
            Regex(
                """[Yy]ou paid\s+$AMT\s+to\s+(?<merchant>[A-Z0-9 &]+?)\s+using Amazon Pay""",
                setOf(RegexOption.IGNORE_CASE)
            ),
            Regex(
                """$AMT\s+(?<type>debited|paid|deducted).*?Amazon Pay""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        ),
        isAlwaysDebit = true
    )

    /**
     * NETC FASTag — toll deductions from FASTag wallet.
     * Merchant extracted as the toll plaza / highway name.
     */
    private val FASTAG = BankTemplate(
        bankName = "FASTAG",
        senderPatterns = listOf(
            Regex(""".*NETCFT.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*NHAIFT.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*IHMCL.*""",   RegexOption.IGNORE_CASE),
            Regex(""".*FASTAG.*""",  RegexOption.IGNORE_CASE),
            Regex(""".*FASTTAG.*""", RegexOption.IGNORE_CASE)
        ),
        bodyPatterns = listOf(
            // "Rs.65 debited from FASTag wallet for toll at NH-19 PANIPAT. Bal: Rs.2500"
            Regex(
                """$AMT\s+(?<type>debited|deducted).*?(?:for toll at|at toll|at)\s+(?<merchant>[A-Z0-9&/ -]+?)(?:\.|,|\s*Tag|\s*Avl|\s*Bal|\s*Available).*?(?:(?:Bal|Balance)[:\s]*(?:Rs\.?|INR)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ),
            // Fallback — just captures amount + confirms FASTag context
            Regex(
                """$AMT\s+(?<type>debited|deducted).*?(?:FASTag|Fastag).*?(?:(?:Bal|Balance)[:\s]*(?:Rs\.?|INR)\s*(?<bal>[\d,]+(?:\.\d{1,2})?))?""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
        ),
        isAlwaysDebit = true
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
        // Specific bank templates — tried first (higher confidence)
        HDFC_UPI_DEBIT,
        HDFC_CC,
        ICICI,
        SBI,
        AXIS,
        KOTAK,
        PAYTM,
        BOB,
        PNB,
        CANARA,
        UNION_BANK,
        BANK_OF_INDIA,
        INDUSIND,
        YES_BANK,
        IDFC_FIRST,
        FEDERAL_BANK,
        RBL,
        AU_BANK,
        UJJIVAN,
        BANDHAN,
        // Payment platform templates
        AMAZON_PAY,
        FASTAG,
        // Generic fallbacks — tried last
        GENERIC_UPI,
        CREDIT_CARD_GENERIC  // bank-agnostic CC fallback — catches SBI/HDFC/any "Rs.X spent" format
    )
}
