package com.expensetracker.sms

import com.expensetracker.sms.categorizer.Categorizer
import com.expensetracker.sms.model.Category
import com.expensetracker.sms.model.Confidence
import com.expensetracker.sms.model.ParsedSms
import com.expensetracker.sms.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {

    private val categorizer = Categorizer()

    private fun sms(merchant: String?, type: TxType = TxType.DEBIT) = ParsedSms(
        amount = 100.0,
        type = type,
        merchant = merchant,
        accountTail = "1234",
        availableBalance = null,
        referenceNo = null,
        bank = "TEST",
        rawText = merchant ?: "",
        confidence = Confidence.HIGH
    )

    @Test fun `Swiggy maps to FOOD`() =
        assertEquals(Category.FOOD, categorizer.categorize(sms("SWIGGY")))

    @Test fun `Zomato maps to FOOD`() =
        assertEquals(Category.FOOD, categorizer.categorize(sms("ZOMATO")))

    @Test fun `Uber maps to COMMUTE`() =
        assertEquals(Category.COMMUTE, categorizer.categorize(sms("UBER")))

    @Test fun `Rapido maps to COMMUTE`() =
        assertEquals(Category.COMMUTE, categorizer.categorize(sms("RAPIDO")))

    @Test fun `Toll plaza merchant maps to COMMUTE`() =
        assertEquals(Category.COMMUTE, categorizer.categorize(sms("TALEGAON TOLL PLAZA")))

    @Test fun `FASTag mentioned in body with unrelated merchant maps to COMMUTE`() {
        val toll = sms("SEASONMALL").copy(
            rawText = "INR 30 using IDFC FIRST Bank FASTag 3XXX3600 done at SeasonMall on 04/11/2025 13:53."
        )
        assertEquals(Category.COMMUTE, categorizer.categorize(toll))
    }

    @Test fun `IRCTC maps to TRAVEL`() =
        assertEquals(Category.TRAVEL, categorizer.categorize(sms("IRCTC")))

    @Test fun `IndiGo maps to TRAVEL`() =
        assertEquals(Category.TRAVEL, categorizer.categorize(sms("INDIGO")))

    @Test fun `BigBasket maps to GROCERY`() =
        assertEquals(Category.GROCERY, categorizer.categorize(sms("BIGBASKET")))

    @Test fun `Blinkit maps to GROCERY`() =
        assertEquals(Category.GROCERY, categorizer.categorize(sms("BLINKIT")))

    @Test fun `Amazon maps to SHOPPING`() =
        assertEquals(Category.SHOPPING, categorizer.categorize(sms("AMAZON")))

    @Test fun `Flipkart maps to SHOPPING`() =
        assertEquals(Category.SHOPPING, categorizer.categorize(sms("FLIPKART")))

    @Test fun `JIO recharge maps to BILLS`() =
        assertEquals(Category.BILLS, categorizer.categorize(sms("JIO")))

    @Test fun `Electricity maps to BILLS`() =
        assertEquals(Category.BILLS, categorizer.categorize(sms("BSES ELECTRICITY")))

    @Test fun `BYJU maps to EDUCATION`() =
        assertEquals(Category.EDUCATION, categorizer.categorize(sms("BYJU")))

    @Test fun `Netflix maps to ENTERTAINMENT`() =
        assertEquals(Category.ENTERTAINMENT, categorizer.categorize(sms("NETFLIX")))

    @Test fun `Apollo pharmacy maps to HEALTH`() =
        assertEquals(Category.HEALTH, categorizer.categorize(sms("APOLLO PHARMACY")))

    @Test fun `CREDIT with unknown merchant defaults to INCOME`() =
        assertEquals(Category.INCOME, categorizer.categorize(sms(null, TxType.CREDIT)))

    @Test fun `DEBIT with null merchant and no body match is UNCATEGORIZED`() =
        assertEquals(Category.UNCATEGORIZED, categorizer.categorize(sms(null, TxType.DEBIT)))

    @Test fun `User override takes priority over seed rules`() {
        val overrides = mapOf("SWIGGY" to Category.ENTERTAINMENT)  // user recategorized
        val categorizerWithOverride = Categorizer(overrides)
        assertEquals(Category.ENTERTAINMENT,
            categorizerWithOverride.categorize(sms("SWIGGY")))
    }

    @Test fun `User override partial match works`() {
        val overrides = mapOf("UBER EATS" to Category.FOOD)
        val categorizerWithOverride = Categorizer(overrides)
        assertEquals(Category.FOOD,
            categorizerWithOverride.categorize(sms("UBER EATS INDIA")))
    }

    @Test fun `Case insensitive merchant matching`() {
        assertEquals(Category.FOOD, categorizer.categorize(sms("swiggy")))
        assertEquals(Category.FOOD, categorizer.categorize(sms("Swiggy")))
        assertEquals(Category.FOOD, categorizer.categorize(sms("SWIGGY")))
    }

    @Test fun `Partial merchant match works`() {
        // "SWIGGY FOOD PVT" should still match FOOD
        assertEquals(Category.FOOD, categorizer.categorize(sms("SWIGGY FOOD PVT")))
    }
}
