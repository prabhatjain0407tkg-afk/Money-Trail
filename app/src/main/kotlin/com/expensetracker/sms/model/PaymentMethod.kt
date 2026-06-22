package com.expensetracker.sms.model

enum class PaymentMethod(val displayName: String, val icon: String) {
    CREDIT_CARD("Credit Card",  "💳"),
    DEBIT_CARD ("Debit Card",   "🏦"),
    UPI        ("UPI",          "📱"),
    NET_BANKING("Net Banking",  "🌐"),
    OTHER      ("Other / Cash", "💵")
}
