package com.expensetracker.sms.model

/**
 * Result of parsing a single bank/transaction SMS.
 * All fields except [amount], [type], and [rawText] are optional because
 * not every bank SMS includes every field.
 */
data class ParsedSms(
    val amount: Double,          // always in INR (converted if foreign)
    val type: TxType,
    val merchant: String?,       // normalized merchant name (SWIGGY, AMAZON, etc.)
    val accountTail: String?,    // last 4 digits of account/card
    val availableBalance: Double?,
    val referenceNo: String?,
    val bank: String,            // "HDFC", "ICICI", "GENERIC", etc.
    val rawText: String,
    val confidence: Confidence,
    val foreignCurrency: String? = null,  // e.g. "USD", "EUR" — null when INR
    val foreignAmount: Double?  = null,   // original foreign amount before conversion
    val detection: DetectionResult? = null  // explainable evidence score (see TransactionDetector)
)

enum class TxType { DEBIT, CREDIT, UNKNOWN }

enum class Confidence {
    /** Matched a specific bank template with amount + merchant. */
    HIGH,
    /** Matched a specific bank template but merchant was not extracted. */
    MEDIUM,
    /** Matched the generic heuristic only — review recommended. */
    LOW
}
