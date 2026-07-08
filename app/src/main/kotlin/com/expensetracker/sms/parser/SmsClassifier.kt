package com.expensetracker.sms.parser

import com.expensetracker.sms.model.TxType

/**
 * First-pass classifier: routes an incoming SMS to a coarse [MessageType] BEFORE
 * any amount/merchant extraction.
 *
 * Bank SMS come in many shapes that look transaction-like but are NOT money moving
 * in or out of the user's account — statements, amount-due reminders, OTPs, offers,
 * collect-requests, card-control notices, biller acknowledgements, store credit.
 * Classifying up front lets the parser drop those cleanly instead of scattering
 * reject-lists through the extraction logic, and gives a reliable debit-vs-credit
 * hint for the messages that ARE real.
 *
 * Two rules keep this safe:
 *  1. Non-transaction categories are matched FIRST (precedence), so "Statement is
 *     sent to ..." is a STATEMENT, never a DEBIT just because it contains "sent".
 *  2. Reject signals are SPECIFIC phrases ("statement is sent", "is due by",
 *     "minimum amount due"), never bare words like "statement" or "loan", so a
 *     genuine "Rs.X spent ..." SMS is never misclassified.
 */
enum class MessageType {
    DEBIT,        // money out: spent, debited, withdrawn, purchase
    CREDIT,       // money in: credited, received, deposited, refund
    STATEMENT,    // statement generated / amount-due reminder — not a transaction
    BILL_ACK,     // biller confirms it received the user's payment — not income
    STORE_CREDIT, // merchant wallet / loyalty credit — not money in the bank
    OTP,          // one-time password
    REQUEST,      // UPI collect request — money not yet moved
    PROMOTION,    // offers, loan ads, reward promos
    CARD_CONTROL, // card blocked, limit increased, PIN changed, card dispatched
    UNKNOWN;      // no clear signal — let the parser try anyway

    /** Never a real transaction — safe to drop before any template parsing. */
    val isHardIgnore: Boolean
        get() = this == OTP || this == REQUEST || this == PROMOTION || this == CARD_CONTROL

    /**
     * Looks transaction-like but isn't. Dropped ONLY after every bank template has
     * failed (right before the loose generic fallback), so a genuine transaction —
     * which always matches a specific template first — is never affected.
     */
    val isSoftIgnore: Boolean
        get() = this == STATEMENT || this == BILL_ACK || this == STORE_CREDIT

    /** Debit/credit hint used by the parser when a template can't tell on its own. */
    fun toTxTypeHint(): TxType? = when (this) {
        DEBIT  -> TxType.DEBIT
        CREDIT -> TxType.CREDIT
        else   -> null
    }
}

object SmsClassifier {

    fun classify(body: String): MessageType {
        val lower = body.lowercase()

        // Precedence: the non-transaction categories are matched first so their
        // specific phrasing wins over the generic debit/credit verbs below.
        if (OTP.any        { it.containsMatchIn(body) }) return MessageType.OTP
        if (STATEMENT.any  { it.containsMatchIn(body) }) return MessageType.STATEMENT
        if (REQUEST.any    { it.containsMatchIn(body) }) return MessageType.REQUEST
        if (CARD_CONTROL.any { it.containsMatchIn(body) }) return MessageType.CARD_CONTROL
        if (PROMOTION.any  { it.containsMatchIn(body) }) return MessageType.PROMOTION
        if (STORE_CREDIT.any { it.containsMatchIn(body) }) return MessageType.STORE_CREDIT
        if (BILL_ACK.any   { it.containsMatchIn(body) }) return MessageType.BILL_ACK

        if (DEBIT_WORDS.any  { lower.contains(it) }) return MessageType.DEBIT
        if (CREDIT_WORDS.any { lower.contains(it) }) return MessageType.CREDIT

        return MessageType.UNKNOWN
    }

    // ── OTP ────────────────────────────────────────────────────────────────────
    // Requires the code adjacent to "OTP is/for/:" (code after) or "N is the OTP"
    // (code before), so a transaction's "do not share OTP" footer isn't mistaken.
    private val OTP = listOf(
        Regex("""\botp\b\s*(?:is|for|:)\s*[\s\S]{0,10}\d{3,8}""", RegexOption.IGNORE_CASE),
        Regex("""\d{3,8}\s+is\s+(?:the\s+|your\s+)?(?:otp|one[\s-]?time)""", RegexOption.IGNORE_CASE),
        Regex("""one[\s-]?time\s+password""",     RegexOption.IGNORE_CASE),
        Regex("""verification\s+code""",          RegexOption.IGNORE_CASE),
    )

    // ── Statement / amount-due reminder / balance inquiry ───────────────────────
    private val STATEMENT = listOf(
        Regex("""statement\s+is\s+(?:sent|generated|ready|available)""", RegexOption.IGNORE_CASE),
        Regex("""\bis\s+due\s+(?:by|on)\b""",                RegexOption.IGNORE_CASE),
        Regex("""\bdue\s+(?:by|on)\s+\d""",                  RegexOption.IGNORE_CASE),  // "due by 29-MAY-26"
        Regex("""(?:total|minimum|min)\s+(?:amount\s+)?due""", RegexOption.IGNORE_CASE),
        Regex("""minimum\s+(?:amount\s+)?(?:of\s+)?(?:rs\.?|inr|₹)\s*[\d,]+""", RegexOption.IGNORE_CASE),
        Regex("""mini\s+statement""",                        RegexOption.IGNORE_CASE),
        Regex("""account\s+statement""",                     RegexOption.IGNORE_CASE),
        Regex("""monthly\s+statement""",                     RegexOption.IGNORE_CASE),
        Regex("""credit\s+card[\s\S]{0,25}?statement""",     RegexOption.IGNORE_CASE),  // "Credit Card XX8009 Statement"
        // Balance-inquiry responses (not a transaction)
        Regex("""passbook\s+balance""",                      RegexOption.IGNORE_CASE),
        Regex("""balance\s+against""",                       RegexOption.IGNORE_CASE),
        Regex("""your\s+(?:account\s+)?balance\s+(?:is|as\s+of)""", RegexOption.IGNORE_CASE),
        Regex("""account\s+balance\s+(?:is|:)""",            RegexOption.IGNORE_CASE),
        Regex("""closing\s+balance""",                       RegexOption.IGNORE_CASE),
        Regex("""credit\s+(?:score|report)""",               RegexOption.IGNORE_CASE),
        Regex("""your\s+credit\s+limit\s+is""",              RegexOption.IGNORE_CASE),
    )

    // ── UPI collect / payment request — money NOT yet moved ─────────────────────
    private val REQUEST = listOf(
        Regex("""requested\s+money\s+from\s+you""",          RegexOption.IGNORE_CASE),
        Regex("""has\s+requested\s+(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d+)?\s+from\s+you""", RegexOption.IGNORE_CASE),
        Regex("""will\s+be\s+debited.*on\s+approv""",        RegexOption.IGNORE_CASE),
        Regex("""collect\s+request""",                       RegexOption.IGNORE_CASE),
        Regex("""raised\s+a\s+collect""",                    RegexOption.IGNORE_CASE),
        Regex("""payment\s+request.*approv""",               RegexOption.IGNORE_CASE),
        Regex("""approv.*payment\s+request""",               RegexOption.IGNORE_CASE),
    )

    // ── Card control — block / limit change / PIN / dispatch ────────────────────
    // NB: uses "limit increased/enhanced", NOT bare "limit" (real spends carry
    // "Avl Credit Limit INR X").
    private val CARD_CONTROL = listOf(
        Regex("""card\s+(?:is\s+|has\s+been\s+)?(?:blocked|hotlisted|deactivated|activated|unblocked)""", RegexOption.IGNORE_CASE),
        Regex("""(?:credit|card)\s+limit\s+(?:has\s+been\s+|is\s+)?(?:increased|enhanced|revised|upgraded)""", RegexOption.IGNORE_CASE),
        Regex("""limit\s+(?:increase|enhancement)""",        RegexOption.IGNORE_CASE),
        Regex("""pin\s+(?:has\s+been\s+|is\s+)?(?:changed|generated|set|reset)""", RegexOption.IGNORE_CASE),
        Regex("""card\s+(?:is\s+)?(?:dispatched|delivered|shipped)""", RegexOption.IGNORE_CASE),
    )

    // ── Promotions / offers ─────────────────────────────────────────────────────
    // Loan patterns require offer context so a genuine loan-EMI debit isn't dropped.
    private val PROMOTION = listOf(
        Regex("""up\s+to\s+(?:rs\.?|inr|₹)\s*[\d,]+""",      RegexOption.IGNORE_CASE),
        Regex("""(?:credited|received)[\s\S]{0,20}?\bbonus""", RegexOption.IGNORE_CASE),
        Regex("""bonus[\s\S]{0,20}?(?:credited|received)""", RegexOption.IGNORE_CASE),
        Regex("""available\s+for\s+withdrawal""",            RegexOption.IGNORE_CASE),
        Regex("""apply\s+now""",                             RegexOption.IGNORE_CASE),
        Regex("""loan\s+offer""",                            RegexOption.IGNORE_CASE),
        Regex("""(?:pre.?approved|instant|personal)\s+loan\s+(?:offer|of\s+(?:rs|inr|₹)|eligib|approved|available|up\s?to)""", RegexOption.IGNORE_CASE),
        Regex("""eligible\s+for\s+(?:a\s+)?(?:pre.?approved\s+)?loan""", RegexOption.IGNORE_CASE),
        Regex("""click\s+(?:here|to\s+apply)""",             RegexOption.IGNORE_CASE),
        // Hindi / Hinglish promo phrases
        Regex("""apply\s+karein""",                          RegexOption.IGNORE_CASE),
        Regex("""abhi\s+apply""",                            RegexOption.IGNORE_CASE),
        Regex("""app\s+(?:se\s+)?download""",                RegexOption.IGNORE_CASE),
        Regex("""(?:payen|paayein|hasil\s+karein)""",        RegexOption.IGNORE_CASE),
    )

    // ── Store credit / brand wallet ─────────────────────────────────────────────
    private val STORE_CREDIT = listOf(
        // Brand name wedged between "your" and "account/wallet" = merchant wallet,
        // not the user's bank. Lookahead lets real bank phrasing through.
        Regex("""(?:credited|added)\s+to\s+your\s+(?!(?:bank|a/?c|account|acct|sb|savings|current|ppf|nps|loan|deposit|demat|salary)\b)[a-z0-9]+\s+(?:account|wallet)""", RegexOption.IGNORE_CASE),
        Regex("""auto-?applied\s+(?:on|at)\s+checkout""",    RegexOption.IGNORE_CASE),
        Regex("""use\s+it\s+to\s+shop""",                    RegexOption.IGNORE_CASE),
    )

    // ── Biller payment acknowledgement — the user paid; biller confirms receipt ──
    private val BILL_ACK = listOf(
        Regex("""received\s+towards\s+your[\s\S]{0,40}?credit\s+card""", RegexOption.IGNORE_CASE),
        Regex("""payment[\s\S]{0,40}?received[\s\S]{0,40}?credit\s+card""", RegexOption.IGNORE_CASE),
        Regex("""payment\s+of\s+(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?\s+(?:for|towards)\s+your[\s\S]{0,150}?receiv""", RegexOption.IGNORE_CASE),
        Regex("""we\s+have\s+received\s+your\s+payment""",   RegexOption.IGNORE_CASE),
        Regex("""your\s+payment[\s\S]{0,80}?(?:has\s+been|was)\s+received""", RegexOption.IGNORE_CASE),
    )

    // ── Transaction verbs (type hint; not required to be exhaustive) ────────────
    private val DEBIT_WORDS = listOf(
        "debited", "spent", "withdrawn", "deducted", "purchase",
        "paid to", "paid at", "sent to", "sent rs", "sent inr", "sent ₹",
    )
    private val CREDIT_WORDS = listOf(
        "credited", "received", "deposited", "refund", "cashback",
    )
}
