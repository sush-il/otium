package com.focusblock.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberChallengeTest {
    @Test fun `constants start with the right digits`() {
        assertTrue(MathConstants.PI.startsWith("14159265358979"))
        assertTrue(MathConstants.E.startsWith("71828182845904"))
        assertTrue(MathConstants.SQRT2.startsWith("41421356237309"))
        assertTrue(MathConstants.PHI.startsWith("61803398874989"))
        assertTrue(MathConstants.LN2.startsWith("69314718055994"))
    }

    @Test fun `every length gives exactly that many digits`() {
        val random = Random(42)
        repeat(500) {
            val length = random.nextInt(NumberChallenges.MIN_LENGTH, NumberChallenges.MAX_LENGTH + 1)
            val c = NumberChallenges.generate(length, random)
            assertEquals(length, c.digits.length)
            assertTrue(c.digits.all { it.isDigit() })
            assertTrue(c.title.isNotBlank() && c.about.isNotBlank() && c.where.isNotBlank())
        }
    }

    @Test fun `length is kept within limits`() {
        assertEquals(NumberChallenges.MIN_LENGTH, NumberChallenges.generate(1).digits.length)
        assertEquals(NumberChallenges.MAX_LENGTH, NumberChallenges.generate(999).digits.length)
    }

    @Test fun `challenges differ between calls`() {
        val random = Random(7)
        val set = (1..50).map { NumberChallenges.generate(20, random).digits }.toSet()
        assertTrue(set.size > 40)
    }
}
