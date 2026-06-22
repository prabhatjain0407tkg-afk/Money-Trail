package com.expensetracker.sms.parser

import kotlin.math.floor

object CurrencyRates {

    // Approximate rates to INR (updated periodically — baked into build)
    private val TO_INR: Map<String, Double> = mapOf(
        "USD" to 86.0,
        "EUR" to 93.0,
        "GBP" to 109.0,
        "AED" to 23.4,
        "SGD" to 64.0,
        "AUD" to 56.0,
        "CAD" to 63.0,
        "JPY" to 0.57,
        "CHF" to 96.0,
        "HKD" to 11.0,
        "THB" to 2.4,
        "MYR" to 19.5,
        "IDR" to 0.0053,
        "PHP" to 1.5,
        "KWD" to 280.0,
        "SAR" to 22.9,
        "QAR" to 23.6,
        "OMR" to 223.0,
        "BHD" to 228.0,
        "NZD" to 51.0,
        "ZAR" to 4.7,
        "CNY" to 11.9,
        "BDT" to 0.74,
        "LKR" to 0.29,
        "NPR" to 0.63,
        "SEK" to 8.3,
        "NOK" to 8.1,
        "DKK" to 12.5,
        "TRY" to 2.7,
    )

    fun isKnown(currency: String): Boolean =
        currency.uppercase() in TO_INR

    fun toInr(amount: Double, currency: String): Double =
        amount * (TO_INR[currency.uppercase()] ?: 1.0)

    fun formatForeign(currency: String, amount: Double): String {
        val formatted = if (amount == kotlin.math.floor(amount)) {
            "%.0f".format(amount)
        } else {
            "%.2f".format(amount)
        }
        return "$currency $formatted"
    }
}
