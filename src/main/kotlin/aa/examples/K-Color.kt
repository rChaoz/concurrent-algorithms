package aa.examples

import aa.*

@Suppress("LocalVariableName")
fun kColor(multiThreaded: Boolean = true) = launch(multiThreaded) {
    val G = G(
        4, oriented = false,
        1 to 2, 1 to 4, 2 to 4, 2 to 3
    )
    val k = 3

    val S = D()
    for (x in G.V) {
        print("$x ")
        val c: I = choice(1..k)
        S[x] = c
    }
    println()
    for ((x, y) in G.E) {
        if (eq(S[x], S[y])) {
            fail()
        }
    }
    success()
}
