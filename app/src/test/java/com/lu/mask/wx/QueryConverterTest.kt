package com.lu.mask.wx


import org.junit.Assert.assertEquals
import org.junit.Test

class QueryConverterTest {

    @Test
    fun convertToParameterized_EmptyInput_ReturnsEmptyPair() {
        val result = convertToParameterized("")
        assertEquals("", result.first)
        assertEquals(0, result.second.size)
    }

    @Test
    fun convertToParameterized_NoMatchingPattern_ReturnsOriginalStringAndEmptyArray() {
        val result = convertToParameterized("column1 IS NULL")
        assertEquals("column1 IS NULL", result.first)
        assertEquals(0, result.second.size)
    }

    @Test
    fun convertToParameterized_SingleMatchingPattern_ReturnsParameterizedStringAndSingleArgument() {
        val result = convertToParameterized("column1 = 'value1'")
        assertEquals("column1 = ?", result.first)
        assertEquals(arrayOf("value1"), result.second)
    }

    @Test
    fun convertToParameterized_MultipleMatchingPatterns_ReturnsParameterizedStringAndMultipleArguments() {
        val result = convertToParameterized("column1 = 'value1' AND column2 > 'value2'")
        assertEquals("column1 = ? AND column2 > ?", result.first)
        assertEquals(arrayOf("value1", "value2"), result.second)
    }

    @Test
    fun convertToParameterized_DifferentOperators_ReturnsParameterizedStringAndCorrectArguments() {
        val result = convertToParameterized("column1 = 'value1' AND column2 != 'value2' OR column3 >= 'value3'")
        assertEquals("column1 = ? AND column2 != ? OR column3 >= ?", result.first)
        assertEquals(arrayOf("value1", "value2", "value3"), result.second)
    }

    @Test
    fun convertToParameterized_QuotedValues_RemovesQuotesAndReturnsCorrectArguments() {
        val result = convertToParameterized("column1 = 'value1' AND column2 = 'value2'")
        assertEquals("column1 = ? AND column2 = ?", result.first)
        assertEquals(arrayOf("value1", "value2"), result.second)
    }

    @Test
    fun convertToParameterized_UnquotedValues_ReturnsParameterizedStringAndCorrectArguments() {
        val result = convertToParameterized("column1 = value1 AND column2 = value2")
        assertEquals("column1 = ? AND column2 = ?", result.first)
        assertEquals(arrayOf("value1", "value2"), result.second)
    }

    private fun convertToParameterized(rawWhere: String): Pair<String, Array<String>> {
        val regex = "(=|<|>|<=|>=|!=)\\s*('?[\\w\\d]+'?)".toRegex()
        var counter = 0
        val args = mutableListOf<String>()

        val processed = regex.replace(rawWhere) { match ->
            val value = match.groupValues[2].trim().trim('\'')
            args.add(value)
            "${match.groupValues[1]} ?"
        }

        return Pair(processed, args.toTypedArray())
    }
}
