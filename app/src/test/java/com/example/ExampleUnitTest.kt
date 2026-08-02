package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun test_double_formatting() {
    // Tests zero representation
    assertEquals("0", formatDouble(0.0))
    assertEquals("0", formatDouble(-0.0))
    // Tests standard decimal values
    assertEquals("1.5", formatDouble(1.50000))
    assertEquals("100", formatDouble(100.0))
    // Tests complex decimal values
    assertEquals("1,234.56", formatDouble(1234.56))
  }

  @Test
  fun test_subtraction_group_logic() {
    val group1 = CalculationGroup(1, "Sales", "100.5", "40.2")
    assertEquals(60.3, group1.result, 0.001)

    val group2 = CalculationGroup(2, "Expenses", "", "15.0")
    // Empty value treated as 0.0 -> 0.0 - 15.0 = -15.0
    assertEquals(-15.0, group2.result, 0.001)

    val group3 = CalculationGroup(3, "Taxes", "50", "")
    assertEquals(50.0, group3.result, 0.001)
  }
}
