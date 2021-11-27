@file:Suppress("NAME_SHADOWING", "PropertyName")

package aa

import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Integer (whole number)
 */
typealias I = Long
/**
 * Real number (double)
 */
typealias R = Double
/**
 * Boolean (T/F)
 */
typealias B = Boolean
/**
 * String
 */
typealias S = String

/**
 * True
 */
const val T = true

/**
 * False
 */
const val F = false

interface DeepCopyable<out T> {
    fun copy(): DeepCopyable<T>
}

/**
 * Set
 * @constructor Elements can be [I], [R], [B], [S], ranges (e.g. `1..10` or `1.0..2.0`), as well as their exclusive counterparts
 * (such as `2 exclusiveTo 10`).
 */
@Suppress("UNCHECKED_CAST")
class M() : DeepCopyable<M> {
    constructor(vararg elements: Any) : this() {
        add(*elements)
    }

    val size get() = iSize + singleReals.size + booleans.size + strings.size + objects.size
    val empty get() = size == 0L
    private val iSize get() = iRanges.sumOf { it.last - it.first + 1 }

    val finite get() = rRanges.isEmpty()
    val infinite get() = !finite

    private val iRanges = TreeSet<LongRange> { r1, r2 ->
        // Because these ranges can't intersect, this is enough
        if (r1.first < r2.first) -1 else 1
    }
    private val rRanges = TreeSet<RExclusiveRange> { r1, r2 ->
        if (r1.start < r2.start) -1 else 1
    }
    private val singleReals = TreeSet<Double>()
    private val booleans = HashSet<Boolean>(2)
    private val strings = HashSet<String>()
    private val objects = HashSet<DeepCopyable<*>>()

    override fun copy(): M {
        val new = M()
        new.iRanges.addAll(iRanges)
        new.rRanges.addAll(rRanges)
        new.singleReals.addAll(singleReals)
        new.booleans.addAll(booleans)
        new.strings.addAll(strings)
        // Deep copy of other sets
        new.objects.addAll(objects.map { it.copy() })
        return new
    }

    private fun all() = buildList {
        addAll(iRanges)
        addAll(rRanges)
        addAll(singleReals)
        addAll(booleans)
        addAll(strings)
        addAll(objects)
    }

    fun containsAll(collection: Collection<Any>) = collection.all { contains(it) }

    operator fun contains(e: Any): B {
        return when (e) {
            is Int, is Long -> {
                val l = (e as Number).toLong()
                val d = e.toDouble()
                iRanges.any { l in it } || rRanges.any { d in it }
            }
            is Double -> {
                if (e.isInt()) {
                    val l = e.toLong()
                    if (iRanges.any { l in it }) return true
                }
                rRanges.any { e in it } || e in singleReals
            }
            is IntRange, is LongRange -> {
                var range = if (e is IntRange) e.first.toLong()..e.last.toLong() else e as LongRange
                if (range.isEmpty()) return true
                val ranges = ArrayList<LongRange>().apply { add(range) }
                var i = ranges.listIterator()
                range = i.next()
                // Remove all elements in iRanges from range
                @Suppress("DuplicatedCode")
                iRanges.forEach {
                    while (range.last < it.first) {
                        if (i.hasNext()) range = i.next()
                        else return@forEach
                    }
                    if (range.intersects(it)) {
                        i.remove()
                        val (left, right) = range.split(it.first, it.last)
                        if (!left.isEmpty()) i.add(left)
                        @Suppress("LiftReturnOrAssignment")
                        if (!right.isEmpty()) {
                            // Needed so that i.remove() will remove 'right'
                            i.add(right); i.previous(); i.next()
                            range = right
                        } else {
                            if (i.hasNext()) range = i.next()
                            else return@forEach
                        }
                    }
                }
                if (ranges.isEmpty()) return true
                // And all elements in rRanges
                i = ranges.listIterator()
                range = i.next()
                @Suppress("DuplicatedCode")
                rRanges.forEach {
                    while (range.last < it.firstInt()) {
                        if (i.hasNext()) range = i.next()
                        else return@forEach
                    }
                    if (range.intersects(it)) {
                        i.remove()
                        val (left, right) = range.split(it.firstInt(), it.lastInt())
                        if (!left.isEmpty()) i.add(left)
                        @Suppress("LiftReturnOrAssignment")
                        if (!right.isEmpty()) {
                            i.add(right); i.previous(); i.next()
                            range = right
                        } else {
                            if (i.hasNext()) range = i.next()
                            else return@forEach
                        }
                    }
                }
                ranges.isEmpty()
            }
            is IExclusiveRange -> e.toLongRanges().all { contains(it) }
            is ClosedFloatingPointRange<*> -> {
                val range = doubleExclusiveRange(e as ClosedFloatingPointRange<Double>)
                if (range.isEmpty()) return true
                if (range.start == range.endInclusive) contains(range.start)
                else rRanges.any {
                    it.start <= range.start && range.endInclusive <= it.endInclusive &&
                            it.exclusions.all { ex -> ex in range.exclusions || ex !in range }
                }
            }
            is Boolean -> e in booleans
            is String -> e in strings
            else -> e in objects
        }
    }

    // Helper functions
    private fun <T : ClosedRange<*>> addRangeToList(newRange: T, list: TreeSet<T>, combineFunc: T.(T) -> T?) {
        var newRange = newRange
        loop@ while (true) {
            val i = list.iterator()
            while (i.hasNext()) {
                val range = i.next().combineFunc(newRange)
                if (range != null) {
                    i.remove()
                    newRange = range
                    continue@loop
                }
            }
            break
        }
        list.add(newRange)
    }

    /**
     * Add a new Int Range, handling overlap
     */
    private fun addRangeToList(newRange: LongRange) {
        // Very similar to removeInts
        rRanges.forEach {
            if (newRange.intersects(it)) {
                // Remove any excluded numbers that are in newRange
                // e.g. M = 1.0..10.0-(5.0)
                // we add 1..10
                // the -(5.0) should be removed
                it.exclusions.removeAll { ex -> ex.isInt() && ex.toLong() in newRange }

                val (left, right) = newRange.split(it.firstInt(), it.lastInt())
                // Take the un-intersected part and call again
                if (!left.isEmpty()) addRangeToList(left)
                if (!right.isEmpty()) addRangeToList(right)
                return
            }
        }
        // Lists are added if they don't intersect with anyone
        addRangeToList(newRange, iRanges, LongRange::combine)
    }

    /**
     * Add a new Real Range, handling overlap
     */
    private fun addRangeToList(newRange: RExclusiveRange) {
        // Remove existing single reals that are covered by this range
        singleReals.removeAll {
            if (newRange.wouldContain(it)) {
                newRange.exclusions.remove(it)
                true
            } else false
        }
        // Remove any overlapping integer ranges
        removeInts(realRange = newRange)
        addRangeToList(newRange, rRanges, RExclusiveRange::combine)
    }

    private fun removeInts(from: Long = 0L, to: Long = 0L, realRange: RExclusiveRange? = null) {
        // Init parameters
        var from = from
        var to = to
        realRange?.let { from = it.firstInt(); to = it.lastInt() }

        val toAdd = ArrayList<LongRange>(iRanges.size)
        val range = from..to
        iRanges.removeAll {
            if (it.intersects(range)) {
                realRange?.exclusions?.removeAll { ex -> ex.isInt() && ex.toLong() in it }

                val (left, right) = it.split(from, to)
                if (!left.isEmpty()) toAdd.add(left)
                if (!right.isEmpty()) toAdd.add(right)
                true
            } else false
        }
        toAdd.forEach { addRangeToList(it, iRanges, LongRange::combine) }
    }

    fun add(vararg elements: Any) = elements.forEach { addOne(it) }

    private fun addOne(e: Any) {
        var newLongRange: LongRange? = null
        var newRealRange: RExclusiveRange? = null

        when (e) {
            is Int, is Long -> newLongRange = single(e as Number)
            is Double -> {
                if (e.isInt()) newLongRange = single(e)
                else {
                    val range = rRanges.firstOrNull { it.wouldContain(e) }
                    if (range != null) range.exclusions.remove(e)
                    else singleReals.add(e)
                }
            }
            is IntRange -> newLongRange = e.first.toLong()..e.last.toLong()
            is LongRange -> newLongRange = e
            is IExclusiveRange -> for (range in e.toLongRanges()) addRangeToList(range)
            is ClosedFloatingPointRange<*> -> {
                val range = doubleExclusiveRange(e as ClosedFloatingPointRange<Double>)
                if (range.isEmpty()) return
                if (range.start == range.endInclusive) addOne(range.start)
                else newRealRange = range
            }
            is Boolean -> booleans.add(e)
            is String -> strings.add(e)
            is DeepCopyable<*> -> objects.add(e)
            else -> unknownTypeException(e)
        }

        if (newLongRange != null && !newLongRange.isEmpty()) addRangeToList(newLongRange)
        if (newRealRange != null) addRangeToList(newRealRange)
    }

    fun remove(vararg elements: Any) = elements.forEach { removeOne(it) }

    private fun removeOne(e: Any) {
        when (e) {
            is Int, is Long, is Double -> {
                if (e !is Double || e.isInt()) {
                    val num = (e as Number).toLong()
                    removeInts(num, num)
                }
                val d = (e as Number).toDouble()
                rRanges.forEach { if (d in it) it.exclude(d) }
                singleReals.remove(d)
            }
            is IntRange, is LongRange -> {
                val range = if (e is IntRange) e.first.toLong()..e.last.toLong() else e as LongRange
                removeInts(range.first, range.last)
                rRanges.forEach {
                    for (i in max(it.firstInt(), range.first)..min(it.lastInt(), range.last))
                        it.exclude(i.toDouble())
                }
            }
            is IExclusiveRange -> for (range in e.toLongRanges()) removeOne(range)
            is ClosedFloatingPointRange<*> -> {
                val range = doubleExclusiveRange(e as ClosedFloatingPointRange<Double>)
                if (range.isEmpty()) return
                else if (range.start == range.endInclusive) removeOne(range.start)
                else {
                    singleReals.removeAll { it in range }
                    if (range.start !in range.exclusions) removeOne(range.start)
                    if (range.endInclusive !in range.exclusions) removeOne(range.endInclusive)
                    val toAdd = ArrayList<RExclusiveRange>(rRanges.size)
                    val realsToAdd = ArrayList<R>()
                    rRanges.removeAll {
                        if (it.start < range.endInclusive && range.start < it.endInclusive) {
                            realsToAdd.addAll(range.exclusions.filter { ex -> it.wouldContain(ex) && ex !in it.exclusions })
                            val (left, right) = it.split(range)
                            if (!left.isEmpty()) toAdd.add(left)
                            if (!right.isEmpty()) toAdd.add(right)
                            true
                        } else false
                    }
                    toAdd.forEach { addRangeToList(it, rRanges, RExclusiveRange::combine) }
                    removeInts(realRange = range)
                    realsToAdd.forEach { addOne(it) }
                }
            }
            is Boolean -> booleans.remove(e)
            is String -> strings.remove(e)
            else -> objects.remove(e)
        }
    }

    fun clear() {
        iRanges.clear()
        rRanges.clear()
        singleReals.clear()
        booleans.clear()
        strings.clear()
        objects.clear()
    }

    /**
     * Reunion of 2 sets
     * @return a new set containing the elements of both sets
     */
    infix fun u(other: M): M {
        val copy = copy()
        copy += other
        return copy
    }

    /**
     * Intersection of 2 sets
     * @return a new set containing the common elements of both sets
     */
    infix fun i(other: M): M {
        val c1 = copy()
        val c2 = copy()
        c1 -= other
        c2 -= c1
        return c2
    }

    infix fun includedIn(other: M) = other.containsAll(this.all())
    infix fun includes(other: M) = this.containsAll(other.all())

    override fun equals(other: Any?) = other is M && this includes other && other includes this

    /**
     * Cartesian product of 2 sets
     * @return a new set containing the cartesian product
     *
     */

    /**
     * Remove all elements from the seconds set
     * @return a new set containing all elements found in this set and not found in given set
     */
    operator fun minus(other: M): M {
        val new = copy()
        new -= other
        return new
    }

    operator fun minusAssign(m: M) = m.all().forEach { removeOne(it) }
    operator fun plusAssign(m: M) = m.all().forEach { addOne(it) }

    /**
     * Iterate over whole numbers.
     */
    fun ints() = object : Iterator<I> {
        private var iterator = iRanges.iterator()
        private var current = 0L
        private var endInclusive = -1L

        override fun hasNext() = current <= endInclusive || iterator.hasNext()

        override fun next(): I {
            if (current <= endInclusive) return current++
            else iterator.next().let {
                current = it.first
                endInclusive = it.last
                return current++
            }
        }
    }

    /**
     * Iterate over all numbers.
     * Throws an exception if set is infinite (i.e. contains a range of real numbers)
     */
    fun nums(): Iterator<Double> {
        checkFinite()
        return object : Iterator<Double> {
            private val iterator = NumsIterator()
            override fun hasNext() = iterator.hasNext()
            override fun next() = iterator.next() as Double
        }
    }

    /**
     * Iterate over all numbers, but infinite ranges are returned as [RExclusiveRange] instances
     * e.g.
     */
    fun finiteNums(): Iterator<Any> = NumsIterator()

    private open inner class NumsIterator : Iterator<Any> {
        private val ints = ints()
        private val reals = singleReals.iterator()
        private val ranges = rRanges.iterator()
        private var iBuff: I? = null
        private var rBuff: R? = null
        private var range: RExclusiveRange? = null

        override fun hasNext() = iBuff != null || rBuff != null || range != null
                || ints.hasNext() || reals.hasNext() || ranges.hasNext()

        override fun next(): Any {
            if (iBuff == null && ints.hasNext()) iBuff = ints.next()
            if (rBuff == null && reals.hasNext()) rBuff = reals.next()
            if (range == null && ranges.hasNext()) range = ranges.next()

            val min = arrayOf(iBuff, rBuff, range?.start).filterNotNull().let {
                var min = it[0].toDouble() to it[0]
                for (i in 1 until it.size) if (it[i].toDouble() < min.first) min = it[i].toDouble() to it[i]
                min.second
            }

            when (min) {
                iBuff -> iBuff = null
                rBuff -> rBuff = null
                range?.start -> range!!.let {
                    range = null
                    return it
                }
            }

            return min
        }
    }

    /**
     * Iterate over booleans (T/F)
     */
    fun booleans() = booleans.iterator()

    /**
     * Iterate over strings
     */
    fun strings() = strings.iterator()

    /**
     * Iterate over objects (such as Graphs(G) or other Sets(M))
     */
    fun objects(): Iterator<Any> = objects.iterator()

    /**
     * Iterate over everything.
     * Throws an exception if set is infinite (i.e. contains a range of real numbers)
     * @return an iterator of [I], [R], [B], [S] and other objects such as [M]
     */
    operator fun iterator(): Iterator<Any> {
        checkFinite()
        return finiteIterator()
    }

    /**
     * Iterate over everything, but infinite ranges are returned as [RExclusiveRange] instances
     * e.g.
     */
    fun finiteIterator() = object : Iterator<Any> {
        val iterators = arrayOf(booleans(), strings(), objects())
        var next = 0
        var iterator: Iterator<Any> = NumsIterator()

        override fun hasNext(): Boolean {
            while (!iterator.hasNext() && next < iterators.size) iterator = iterators[next++]
            return iterator.hasNext()
        }

        override fun next(): Any {
            // Make sure iterator is set correctly
            hasNext()
            return iterator.next()
        }
    }

    /**
     * @throws InfiniteSetException if set is infinite
     */
    private fun checkFinite() {
        if (infinite) throw InfiniteSetException("Unable to iterate over infinite set!")
    }

    /**
     * Print the contents of this set.
     *
     * Equivalent to printing [toString] followed by an empty line.
     */
    fun printAll() {
        println(toString())
        println()
    }

    /**
     * Returns a string containing all elements of this Set, separated by a comma.
     * Real ranges are expressed in this form: `[2.2, 3.5)`.
     */
    @Suppress("GrazieInspection")
    override fun toString() = buildString {
        val it = finiteIterator()
        if (it.hasNext()) append(it.next().toString())
        while (it.hasNext()) append(", ").append(it.next().toString())
    }

    override fun hashCode(): Int {
        var result = iRanges.hashCode()
        result = 31 * result + rRanges.hashCode()
        result = 31 * result + singleReals.hashCode()
        result = 31 * result + booleans.hashCode()
        result = 31 * result + strings.hashCode()
        result = 31 * result + objects.hashCode()
        return result
    }
}

class InfiniteSetException(message: String) : IllegalStateException(message)

private fun unknownTypeException(e: Any): Nothing = throw IllegalArgumentException("Unknown type: [${e::class.simpleName}]")

/**
 * Graph
 */
class G private constructor(val verticesCount: Int, val oriented: B, val E: HashSet<Pair<Int, Int>>) : DeepCopyable<G> {
    val V get() = 1..verticesCount
    val edgeCount get() = E.size

    constructor(verticesCount: Int, oriented: B, vararg edges: Pair<Int, Int>) : this(verticesCount, oriented, edges.toHashSet()) {
        if (!oriented) edges.forEach { E.add(it.second to it.first) }
    }

    override fun copy() = G(verticesCount, oriented, HashSet(E))
}

/**
 * Map (dictionary)
 */
@Suppress("UNCHECKED_CAST")
class D(vararg initialValues: Pair<Any, Any>) {
    private val map = hashMapOf(*initialValues)

    /**
     * A map may contain anything and this function attempts to automatically cast values to any type, which may fail.
     */
    operator fun <T : Any> get(key: Any) = map[key] as T

    operator fun set(key: Any, value: Any) {
        map[key] = value
    }
}

fun eq(a: Any, b: Any) = a == b
