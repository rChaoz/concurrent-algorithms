package aa.examples

import aa.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Proof of concept - program with a choice. Although under the hood the entire code is re-run, the splitting
 * at the choice() call is emulated - for example, by ensuring the random functions always return the same values,
 * in the same order. Here, the same value is added into s, although the program is run 10 times, each time
 * the choice() function returning a different value (1..10).
 *
 * Additionally, duplicate print commands are ignored. For example, although the program will run 20 times -
 * where x will be 1..10, and the second choice will be either T or F for each x, the `println("x=$x, s=$S")`
 * statement will be ignored 10/20 times - effectively making it seem as if program execution split into 2
 * at the second choice.
 *
 * Global variables should *never* be used. If really needed, however, you can access global variables inside
 * a global scope: `global {}` - this ensures that the choice() emulation is successful. Try removing `global {}`
 * from around the `atomicInt` and `normal` uses and see what happens!
 *
 * Also, you should only ever use Atomic variables - not normal ones. If you run this code multiple times,
 * you may see that only the atomic int reaches 10 every time, while the normal one does not - this happens
 * because, if 2 threads attempt to increment the same value at the same time, they will only increment it by
 * 1, not 2. Atomic variables prevent this.
 */
@Suppress("LocalVariableName")
fun simple(multiThreaded: Boolean = true) = launch(multiThreaded) {
    global {
        atomicInt.getAndIncrement()
        normal++
    }

    val S = M()
    S.add(randomI(1..100))
    S.add(randomR())
    println(S)

    val x: I = choice(1..10)
    println("x=$x, s=$S")

    if (choice(T, F)) println("$x true!")
    else global {
        println("atomic=${atomicInt.getAndIncrement()}, normal=${normal++}")
    }
    // No success() call to prevent other branches being cancelled when 1 reaches the end
}

val atomicInt = AtomicInteger(0)
var normal = 0
