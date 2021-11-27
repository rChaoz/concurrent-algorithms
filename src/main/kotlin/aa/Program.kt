package aa

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlinx.coroutines.launch as launchCoroutine

class ProgramScope internal constructor(
    private val program: suspend ProgramScope.() -> Unit,
    private val coroutineScope: CoroutineScope,
    private val result: AtomicBoolean,
    private val choices: Array<Any> = emptyArray(),
    private val randomSeed: Long = Random.nextLong()
) {
    // Random functions
    // Same seed everytime to ensure same values generated everytime
    private val random = Random(randomSeed)

    /**
     * Generate random integer.
     */
    fun randomI(): I = random.nextLong()

    /**
     * Generate a random integer in specified range.
     */
    fun randomI(range: IntRange): I = random.nextLong(range.first.toLong(), (range.last - 1).toLong())

    /**
     * Generate a random integer in specified range.
     */
    fun randomI(range: LongRange): I = random.nextLong(range.first, range.last - 1)

    /**
     * Generate a random Real number in interval [0, 1).
     */
    @Suppress("GrazieInspection")
    fun randomR(): R = random.nextDouble()

    /**
     * Generate a random boolean (T/F).
     */
    fun randomB(): B = random.nextBoolean()

    // Print functions
    fun print(s: String) {
        coroutineScope.ensureActive()
        if (result.get() || choiceCount < choices.size) return
        else kotlin.io.print(s)
    }

    fun print(s: Any) = print(s.toString())
    fun println(s: Any) = print("$s\n")
    fun println() = print("\n")

    fun global(block: () -> Unit) {
        coroutineScope.ensureActive()
        if (result.get() || choiceCount < choices.size) return
        block()
    }

    private var choiceCount = 0

    /**
     * Splits current program into |[m]| programs. Each gets a distinct value from m as the return value of this function.
     * A set may contain anything and this function attempts to automatically cast elements from m to any type. For example,
     * this code will fail:
     * ```
     * val x: I = choice(M(true, "string", 25))
     * ```
     * as it will not be able to convert 'true' or "string" to an Int.
     * @throws
     * @param m a finite set
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> choice(m: M): T {
        coroutineScope.ensureActive()
        if (choiceCount < choices.size) return choices[choiceCount++] as T
        if (m.infinite) throw InfiniteSetException("Cannot perform choice() on infinite set!")
        // Launch a new program for element in M
        val iterator = m.iterator()
        while (iterator.hasNext()) {
            val arr = Array(choices.size + 1) {
                if (it < choices.size) choices[it]
                else iterator.next()
            }
            ProgramScope(program, coroutineScope, result, arr, randomSeed).launch()
        }
        fail()
    }

    /**
     * Splits current program into (number of elements) programs. Each gets a distinct value as the return value of this function.
     * These elements may be anything and this function attempts to cast elements from to any type. For example,
     * this code will fail:
     * ```
     * val x: I = choice(true, "string", 25)
     * ```
     * as it will not be able to convert 'true' or "string" to an [I].
     * @throws
     * @param elements elements, see the constructor of [M]
     */
    suspend fun <T> choice(vararg elements: Any) = choice<T>(M(*elements))

    fun fail(): Nothing = throw FailException()

    fun success(result: Any? = null): Nothing = throw SuccessException(result)

    internal suspend fun launch() = coroutineScope.launchCoroutine {
        try {
            program()
        } catch (_: FailException) {
        } catch (e: SuccessException) {
            if (result.getAndSet(true)) return@launchCoroutine
            kotlin.io.println("\nSuccess!")
            e.result?.let { kotlin.io.println(it.toString()) }
            coroutineScope.cancel()
        }
    }

    private class FailException : RuntimeException()
    private class SuccessException(val result: Any?) : RuntimeException()
}

fun launch(multiThreaded: Boolean = true, programFunction: suspend ProgramScope.() -> Unit) {
    val result = AtomicBoolean(false)
    runBlocking {
        launchCoroutine(if (multiThreaded) Dispatchers.Default else coroutineContext) {
            ProgramScope(programFunction, this, result).launch()
        }
    }
    if (!result.get()) println("\nFail!")
}
