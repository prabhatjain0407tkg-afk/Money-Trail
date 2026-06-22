package com.expensetracker.sms.model

enum class Category(val displayName: String) {
    FOOD("Food & Dining"),
    GROCERY("Grocery"),
    COMMUTE("Commute"),
    TRAVEL("Travel & Fun"),
    SHOPPING("Shopping"),
    BILLS("Bills & Utilities"),
    EDUCATION("Education"),
    HEALTH("Health & Medical"),
    FITNESS("Fitness & Sports"),
    ENTERTAINMENT("Entertainment"),
    INCOME("Income"),
    INVESTMENT("Investment"),
    CASH("Cash & ATM"),
    TRANSFER("Transfer / Self"),
    CC_PAYMENT("CC Bill Payment"),
    UNCATEGORIZED("Uncategorized"),
    CUSTOM("My Categories")
}
