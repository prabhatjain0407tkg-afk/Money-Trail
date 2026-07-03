package com.expensetracker.sms.categorizer

import com.expensetracker.sms.model.Category
import com.expensetracker.sms.model.ParsedSms
import com.expensetracker.sms.model.SubCategory

/**
 * Per-category icon options for visual tagging of transactions.
 * These are purely decorative — no auto-classification happens.
 * The user picks one in the category picker to quickly identify a transaction's payee type.
 */
object SubCategoryRules {

    /** (emoji, label) options shown in the icon picker for each category. */
    val ICON_OPTIONS: Map<Category, List<Pair<String, String>>> = mapOf(
        Category.FOOD to listOf(
            "🍽️" to "Restaurant",
            "📦" to "Delivery",
            "☕" to "Cafe",
            "🍷" to "Fine Dining",
            "🌮" to "Street Food",
            "🍫" to "Snacks",
        ),
        Category.GROCERY to listOf(
            "🏪" to "Supermarket",
            "⚡" to "Quick Commerce",
            "🥦" to "Fresh Produce",
            "🥛" to "Dairy",
            "🧴" to "Household",
            "🍳" to "Kitchenware",
        ),
        Category.COMMUTE to listOf(
            "🚕" to "Cab / Ride",
            "🚇" to "Metro / Bus",
            "⛽" to "Fuel",
            "🛣️" to "Toll / FASTag",
            "🅿️" to "Parking",
            "🔋" to "EV Charging",
        ),
        Category.TRAVEL to listOf(
            "✈️" to "Flight",
            "🚂" to "Train / Bus",
            "🏨" to "Hotel",
            "🎡" to "Activities",
            "🗺️" to "Local Transport",
            "🧳" to "Miscellaneous",
        ),
        Category.SHOPPING to listOf(
            "👗" to "Fashion",
            "💻" to "Electronics",
            "🏠" to "Home & Décor",
            "💄" to "Beauty",
            "💍" to "Jewellery",
            "🧸" to "Kids",
            "🏃" to "Sports",
        ),
        Category.BILLS to listOf(
            "⚡" to "Electricity",
            "🔥" to "Gas",
            "💧" to "Water",
            "📡" to "WiFi / Internet",
            "📱" to "Mobile",
            "📺" to "Subscription",
            "🏦" to "Loan EMI",
            "🏛️" to "Tax / Govt",
            "🏘️" to "Maintenance",
            "🧺" to "Laundry",
            "🗑️" to "Other Bill",
        ),
        Category.EDUCATION to listOf(
            "🏫" to "School / College",
            "📝" to "Coaching",
            "💻" to "Online Course",
            "📚" to "Books",
            "📋" to "Exam Fee",
        ),
        Category.HEALTH to listOf(
            "💊" to "Medicine",
            "💉" to "Doctor",
            "🔬" to "Lab Test / Pathology",
            "🏥" to "Hospital",
            "🦷" to "Dental",
            "🛡️" to "Health Insurance",
        ),
        Category.FITNESS to listOf(
            "🏋️" to "Gym",
            "🏊" to "Swimming",
            "🏸" to "Badminton",
            "⚽" to "Football / Cricket",
            "🎾" to "Tennis / Squash",
            "🧘" to "Yoga / Pilates",
            "🚴" to "Cycling",
            "🏆" to "Sports Club",
        ),
        Category.ENTERTAINMENT to listOf(
            "🎬" to "Movies",
            "🎪" to "Events",
            "🎮" to "Gaming",
            "📺" to "Streaming",
            "🏆" to "Sports",
        ),
        Category.INVESTMENT to listOf(
            "📈" to "Mutual Funds",
            "🏦" to "FD / RD",
            "🏖️" to "NPS / Pension",
            "🛡️" to "EPF / PF",
            "🥇" to "Gold",
            "🏘️" to "Real Estate",
            "₿" to "Crypto",
        ),
        Category.INCOME to listOf(
            "💼" to "Salary",
            "📲" to "UPI Received",
            "🏦" to "Bank Transfer",
            "💻" to "Freelance",
            "📈" to "Interest / Dividend",
            "↩️" to "Refund / Cashback",
        ),
        Category.CASH to listOf(
            "🏧" to "ATM Withdrawal",
            "🏦" to "Branch Withdrawal",
            "💳" to "Cash Advance",
            "📲" to "Micro-ATM / AePS",
            "🖥️" to "POS Cash",
        ),
        Category.TRANSFER to listOf(
            "🔄" to "Self Transfer",
            "👪" to "Family",
            "🤝" to "Friend",
            "🏢" to "Business",
        ),
        Category.CC_PAYMENT to listOf(
            "🟡" to "Via CRED",
            "💳" to "UPI / Net Banking",
            "🏦" to "Other",
        ),
    )

    /**
     * Resolve a stored icon-tag ID back into a [SubCategory].
     * The ID is the emoji string chosen by the user.
     */
    fun resolveIconTag(emoji: String?, category: Category, categoryIcon: String): SubCategory {
        if (emoji.isNullOrBlank()) {
            return SubCategory(category.name, category.displayName, categoryIcon, 0xFF636E72L)
        }
        val label = ICON_OPTIONS[category]?.firstOrNull { (e, _) -> e == emoji }?.second ?: emoji
        return SubCategory("ICON_$emoji", label, emoji, 0xFF636E72L)
    }
}
