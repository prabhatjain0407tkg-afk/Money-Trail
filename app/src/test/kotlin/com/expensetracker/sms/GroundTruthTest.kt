package com.expensetracker.sms

import com.expensetracker.sms.parser.SmsClassifier
import com.expensetracker.sms.parser.SmsParser
import com.expensetracker.sms.parser.TollParser
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * Runs the entire [GroundTruth] corpus through the REAL ingestion pipeline
 * (`SmsParser.parse ?: TollParser.parse` — exactly what the app's SMS receivers
 * use) plus the classifier, and asserts every labelled expectation.
 *
 * Unlike the focused unit tests, this validates the whole corpus in one shot and
 * reports every mismatch together, then prints an accuracy line. Growing the
 * corpus in [GroundTruth] automatically grows this test's coverage.
 */
class GroundTruthTest {

    /** Mirror of the two-stage pipeline used at every real ingestion call site. */
    private fun pipeline(sender: String, body: String) =
        SmsParser.parse(sender, body) ?: TollParser.parse(sender, body)

    @Test
    fun `ground truth corpus`() {
        val mismatches = mutableListOf<String>()

        for (c in GroundTruth.cases) {
            // 1. Classifier verdict
            val actualType = SmsClassifier.classify(c.body)
            if (actualType != c.type) {
                mismatches += "[${c.name}] type: expected ${c.type}, got $actualType"
            }

            // 2. Full-pipeline parse result
            val parsed = pipeline(c.sender, c.body)
            if (c.parses) {
                if (parsed == null) {
                    mismatches += "[${c.name}] expected a transaction, got null"
                    continue
                }
                c.txType?.let {
                    if (parsed.type != it) mismatches += "[${c.name}] txType: expected $it, got ${parsed.type}"
                }
                c.amount?.let {
                    if (abs(parsed.amount - it) > 0.01) mismatches += "[${c.name}] amount: expected $it, got ${parsed.amount}"
                }
                c.merchant?.let { m ->
                    if (parsed.merchant?.contains(m, ignoreCase = true) != true)
                        mismatches += "[${c.name}] merchant: expected to contain '$m', got '${parsed.merchant}'"
                }
                c.bank?.let {
                    if (parsed.bank != it) mismatches += "[${c.name}] bank: expected $it, got ${parsed.bank}"
                }
            } else {
                if (parsed != null) {
                    mismatches += "[${c.name}] expected NO transaction, but parsed " +
                            "${parsed.type} ${parsed.amount} (bank=${parsed.bank})"
                }
            }
        }

        val total = GroundTruth.cases.size
        val correct = total - GroundTruth.cases.count { c ->
            mismatches.any { it.startsWith("[${c.name}]") }
        }
        println("Ground truth: $correct/$total cases fully correct (${mismatches.size} field mismatches)")

        if (mismatches.isNotEmpty()) {
            fail("Ground-truth mismatches (${mismatches.size}):\n" + mismatches.joinToString("\n"))
        }
    }
}
