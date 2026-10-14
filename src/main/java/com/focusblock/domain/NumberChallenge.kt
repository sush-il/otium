package com.focusblock.domain

import java.math.BigInteger
import kotlin.random.Random

/**
 * A number the user has to type to get past Lock mode. [title] and [about] say what the
 * digits are, so typing them is a little less dull; [where] says which part they come from.
 */
data class NumberChallenge(val digits: String, val title: String, val about: String, val where: String)

/**
 * Builds fresh typing challenges from well-known maths constants and number sequences.
 * Everything is bundled or computed, so it works offline. Each challenge starts somewhere
 * random, so the digits can't be memorised.
 */
object NumberChallenges {
    const val MIN_LENGTH = 10
    const val MAX_LENGTH = 40

    private class Constant(val title: String, val about: String, val decimals: String)

    private class Sequence(
        val title: String,
        val about: String,
        val startRange: IntRange,
        val term: (Int) -> String,
        val where: (Int) -> String,
    )

    private val constants = listOf(
        Constant("π (pi)", "A circle's circumference ÷ its width.", MathConstants.PI),
        Constant("e (Euler's number)", "The base of natural growth.", MathConstants.E),
        Constant("√2", "The diagonal of a 1×1 square.", MathConstants.SQRT2),
        Constant("√3", "The diagonal of a 1×1×1 cube.", MathConstants.SQRT3),
        Constant("φ (the golden ratio)", "(1 + √5) ÷ 2, found in spirals and sunflowers.", MathConstants.PHI),
        Constant("ln 2", "The natural log of 2.", MathConstants.LN2),
    )

    private val primes: List<Int> by lazy {
        val limit = 30_000
        val composite = BooleanArray(limit + 1)
        (2..limit).filter { n ->
            if (composite[n]) false else {
                var m = n.toLong() * n
                while (m <= limit) { composite[m.toInt()] = true; m += n }
                true
            }
        }
    }

    private val fibonacci: List<BigInteger> by lazy {
        val list = mutableListOf(BigInteger.ONE, BigInteger.ONE)
        while (list.size < 400) list += list[list.size - 1] + list[list.size - 2]
        list
    }

    private val sequences = listOf(
        Sequence(
            "The prime numbers", "Divisible only by 1 and themselves.",
            0..2_000, { primes[it].toString() }, { "Starts at the ${it + 1}th prime, ${primes[it]}." },
        ),
        Sequence(
            "The Fibonacci numbers", "Each is the sum of the two before it.",
            5..300, { fibonacci[it].toString() }, { "Starts at the ${it + 1}th Fibonacci number." },
        ),
        Sequence(
            "Powers of 2", "2, 4, 8, 16… doubling each time.",
            1..300, { BigInteger.TWO.pow(it).toString() }, { "Starts at 2 to the power of $it." },
        ),
        Sequence(
            "Square numbers", "A number times itself: 1, 4, 9…",
            10..9_000, { (it.toLong() * it).toString() }, { "Starts at $it × $it." },
        ),
        Sequence(
            "Counting numbers", "Just counting up.",
            10..90_000, { it.toString() }, { "Starts at $it." },
        ),
    )

    fun generate(length: Int, random: Random = Random.Default): NumberChallenge {
        val n = length.coerceIn(1, MAX_LENGTH)
        val pick = random.nextInt(constants.size + sequences.size)
        if (pick < constants.size) {
            val c = constants[pick]
            val start = random.nextInt(0, c.decimals.length - n + 1)
            val from = start + 1
            return NumberChallenge(
                c.decimals.substring(start, start + n), c.title, c.about,
                "Decimal places $from to ${from + n - 1}.",
            )
        }
        val s = sequences[pick - constants.size]
        val start = random.nextInt(s.startRange.first, s.startRange.last + 1)
        val digits = StringBuilder()
        var i = start
        while (digits.length < n) digits.append(s.term(i++))
        return NumberChallenge(digits.substring(0, n), s.title, s.about, s.where(start))
    }
}
