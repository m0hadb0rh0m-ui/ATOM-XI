package com.atom.aix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AtomCalculatorTest {
 @Test fun arithmeticPrecedence(){assertEquals("21",AtomCalculator.evaluate("3+6*3"))}
 @Test fun parentheses(){assertEquals("10",AtomCalculator.evaluate("(3+2)*2"))}
 @Test fun decimalDivision(){assertEquals("2.5",AtomCalculator.evaluate("5/2"))}
 @Test fun rejectsDivisionByZero(){assertThrows(IllegalArgumentException::class.java){AtomCalculator.evaluate("3/0")}}
 @Test fun rejectsCodeInjection(){assertThrows(IllegalArgumentException::class.java){AtomCalculator.evaluate("1+System.exit(0)")}}
}
