package aa

import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Range of real numbers allowing exclusions
 */
class RExclusiveRange(range: ClosedFloatingPointRange<Double>) : ClosedFloatingPointRange<Double> by range {
    constructor(start: Double, endInclusive: Double) : this(start..endInclusive)

    internal val exclusions = TreeSet<Double>()

    internal fun exclude(value: Double) {
        if (!super.contains(value)) throw IllegalArgumentException("cannot exclude $value: not in range $start..$endInclusive")
        exclusions.add(value)
    }

    override fun isEmpty(): Boolean {
        return super.isEmpty() || (start == endInclusive && start in exclusions)
    }

    override fun contains(value: Double) = if (value in exclusions) false else super.contains(value)

    override fun toString() = buildString {
        append(if (start in exclusions) '(' else '[')
        append(start).append(", ").append(endInclusive)
        append(if (endInclusive in exclusions) ')' else ']')
        val others = exclusions.filterNot { it == start || it == endInclusive }
        if (others.isNotEmpty()) {
            append("-{")
            append(others.joinToString())
            append("}")
        }
    }

    internal fun wouldContain(value: Double) = super.contains(value)
}

internal fun doubleExclusiveRange(range: ClosedFloatingPointRange<Double>) = if (range is RExclusiveRange) range else RExclusiveRange(range)

fun RExclusiveRange.combine(other: RExclusiveRange): RExclusiveRange? =
    if (other.start <= this.endInclusive && this.start <= other.endInclusive) {
        val range = RExclusiveRange(min(this.start, other.start), max(this.endInclusive, other.endInclusive))
        this.exclusions.forEach { if (it !in other) range.exclude(it) }
        other.exclusions.forEach { if (it !in this) range.exclude(it) }
        range
    } else null

/**
 * Range of integers allowing exclusions
 */
class IExclusiveRange(range: ClosedRange<Long>) : ClosedRange<Long> by range {
    constructor(start: Long, endInclusive: Long) : this(start..endInclusive)

    private val exclusions = TreeSet<Long>()

    internal fun exclude(value: Long) {
        if (!super.contains(value)) throw IllegalArgumentException("cannot exclude $value: not in range $start..$endInclusive")
        exclusions.add(value)
    }

    override fun contains(value: Long) = if (value in exclusions) false else super.contains(value)

    /**
     * Returns a list of normal LongRanges containing all elements of this range minus exclusions, e.g.
     * ```
     * 1..5 excluding 1, 3
     * ```
     * will become
     * ```
     * 2..2, 4..5
     * ```
     */
    internal fun toLongRanges(): List<LongRange> {
        val ranges = ArrayList<LongRange>(exclusions.size + 1)
        var start = this.start
        var end = endInclusive
        for (e in exclusions) {
            if (e >= endInclusive) {
                if (e == endInclusive) --end
                break
            } else if (e == start) ++start
            else {
                ranges.add(start until e)
                start = e + 1
            }
        }
        if (start < endInclusive) ranges.add(start..end)
        return ranges
    }

    override fun toString() = "$start..$endInclusive"
}

private fun longExclusiveRange(range: ClosedRange<Long>) = if (range is IExclusiveRange) range else IExclusiveRange(range)

@JvmName("_longExclusiveRange")
private fun longExclusiveRange(range: ClosedRange<Int>) = IExclusiveRange(range.start.toLong()..range.endInclusive.toLong())

internal fun LongRange.intersects(range: RExclusiveRange) = this.first <= range.endInclusive && range.start <= this.last
internal fun LongRange.intersects(range: LongRange) = this.first <= range.last && range.first <= this.last

/**
 * Splits `this` by [left]..[right]. These values are inclusive, e.g.:
 * 1..5 split by 2..3 will be 1..1, 4..5
 * also 1..5 split by 0..1 will be <empty>, 2..5
 * @see [LongRange.intersects]
 */
internal fun LongRange.split(left: Long, right: Long) = (this.first until left) to (right + 1..this.last)

/**
 * Same like [LongRange.split], but values are exclusive
 */
internal fun RExclusiveRange.split(range: RExclusiveRange): Pair<RExclusiveRange, RExclusiveRange> {
    val left = RExclusiveRange(this.start, range.start)
    left.exclusions.addAll(this.exclusions.filter { left.wouldContain(it) })
    val right = RExclusiveRange(range.endInclusive, this.endInclusive)
    right.exclusions.addAll(this.exclusions.filter { right.wouldContain(it) })
    return left to right
}

/**
 * First int contained by this range, e.g.:
 * ```
 * [2.3, 4.6].firstInt() == 3
 * [2.0, 12.0].firstInt() == 2
 * (2.0, 7.0).firstInt() == 3
 * ```
 * @return [I] (Long)
 */
internal fun RExclusiveRange.firstInt() = start.toLong() + if (start.isInt() && start !in exclusions) 0 else 1

/**
 * Last int contained by this range, e.g.:
 * ```
 * [2.3, 4.6].firstInt() == 4
 * [2.0, 12.0].firstInt() == 12
 * (2.0, 7.0).lastInt() == 6
 * ```
 * @return [I] (Long)
 */
internal fun RExclusiveRange.lastInt() = this.endInclusive.toLong() - if (endInclusive.isInt() && endInclusive in exclusions) 1 else 0

internal fun LongRange.combine(other: LongRange): LongRange? =
    if (other.first <= this.last && this.first <= other.last)
        LongRange(min(this.first, other.first), max(this.last, other.last))
    else null

/**
 * `num.toLong()..num.toLong()`
 */
internal fun single(num: Number) = num.toLong().let { it..it }

/**
 * Creates a new [RExclusiveRange]: `(a, b)`
 */
infix fun R.exclusiveTo(other: R) = RExclusiveRange(this, other).also {
    it.exclude(this)
    it.exclude(other)
}

/**
 * Creates a new [RExclusiveRange]: `(a, b]`
 */
infix fun R.exclusiveLeftTo(other: R) = RExclusiveRange(this, other).also {
    it.exclude(this)
}

/**
 * Creates a new [RExclusiveRange]: `[a, b)`
 */
infix fun R.exclusiveRightTo(other: R) = RExclusiveRange(this, other).also {
    it.exclude(other)
}

/**
 * Modifies existing [RExclusiveRange] to exclude given value and returns it.
 *
 * If `this` is not an instance of [RExclusiveRange], a new one will be made and returned.
 */
infix fun ClosedFloatingPointRange<R>.except(value: R): RExclusiveRange {
    val range = doubleExclusiveRange(this)
    range.exclude(value)
    return range
}

/**
 * Modifies existing [RExclusiveRange] to exclude given values and returns it.
 *
 * If `this` is not an instance of [RExclusiveRange], a new one will be made and returned.
 */
fun ClosedFloatingPointRange<R>.exclude(vararg values: R): RExclusiveRange {
    val range = doubleExclusiveRange(this)
    values.forEach { range.exclude(it) }
    return range
}


/**
 * Creates a new [IExclusiveRange]: `(a, b)`
 */
infix fun I.exclusiveTo(other: I) = IExclusiveRange(this, other).also {
    it.exclude(this)
    it.exclude(other)
}

/**
 * Creates a new [IExclusiveRange]: `(a, b)`
 */
infix fun Int.exclusiveTo(other: Int) = this.toLong() exclusiveTo other.toLong()

/**
 * Creates a new [IExclusiveRange]: `(a, b]`
 */
infix fun I.exclusiveLeftTo(other: I) = IExclusiveRange(this, other).also {
    it.exclude(this)
}

/**
 * Creates a new [IExclusiveRange]: `(a, b]`
 */
infix fun Int.exclusiveLeftTo(other: Int) = this.toLong() exclusiveLeftTo other.toLong()

/**
 * Creates a new [IExclusiveRange]: `[a, b)`
 */
infix fun I.exclusiveRightTo(other: I) = IExclusiveRange(this, other).also {
    it.exclude(other)
}

/**
 * Creates a new [IExclusiveRange]: `[a, b)`
 */
infix fun Int.exclusiveRightTo(other: Int) = this.toLong() exclusiveRightTo other.toLong()

/**
 * Modifies existing [IExclusiveRange] to exclude given value and returns it.
 *
 * If `this` is not an instance of [IExclusiveRange], a new one will be made and returned.
 */
infix fun ClosedRange<I>.except(value: I): IExclusiveRange {
    val range = longExclusiveRange(this)
    range.exclude(value)
    return range
}

/**
 * Modifies existing [IExclusiveRange] to exclude given value and returns it.
 *
 * If `this` is not an instance of [IExclusiveRange], a new one will be made and returned.
 */
infix fun ClosedRange<I>.except(value: Int) = this except value.toLong()

/**
 * Modifies existing [IExclusiveRange] to exclude given value and returns it.
 *
 * If `this` is not an instance of [IExclusiveRange], a new one will be made and returned.
 */
@JvmName("_except")
infix fun ClosedRange<Int>.except(value: Int) = longExclusiveRange(this).also {
    it.exclude(value.toLong())
}

/**
 * Modifies existing [IExclusiveRange] to exclude given values and returns it.
 *
 * If `this` is not an instance of [IExclusiveRange], a new one will be made and returned.
 */
fun ClosedRange<I>.exclude(vararg values: I): IExclusiveRange {
    val range = longExclusiveRange(this)
    values.forEach { range.exclude(it) }
    return range
}

/**
 * Modifies existing [IExclusiveRange] to exclude given values and returns it.
 *
 * If `this` is not an instance of [IExclusiveRange], a new one will be made and returned.
 */
fun ClosedRange<I>.exclude(vararg values: Int): IExclusiveRange {
    val range = longExclusiveRange(this)
    values.forEach { range.exclude(it.toLong()) }
    return range
}

/**
 * Modifies existing [IExclusiveRange] to exclude given values and returns it.
 *
 * If `this` is not an instance of [IExclusiveRange], a new one will be made and returned.
 */
@JvmName("_exclude")
fun ClosedRange<Int>.exclude(vararg values: Int): IExclusiveRange {
    val range = longExclusiveRange(this)
    values.forEach { range.exclude(it.toLong()) }
    return range
}

/**
 * Returns true if x is an integer (x == floor(x))
 */
fun Double.isInt() = kotlin.math.floor(this) == this
