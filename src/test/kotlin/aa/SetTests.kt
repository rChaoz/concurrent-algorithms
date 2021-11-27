package aa

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream

class SetTests {
    @Test
    fun `contains(LongRange) - Complex`() = test(
        input = """
            [1,5] (5.5,6.5) [7,10] [11.0,15.0)
            ?[1, 15]
            15
            ?[1, 15]
            clear
            exit
        """.trimIndent(),
        expectedOutput = """
            > [1,5] (5.5,6.5) [7,10] [11.0,15.0)
            9: 1, 2, 3, 4, 5, (5.5, 6.5), 7, 8, 9, 10, [11.0, 15.0)

            > ?[1, 15]
            false
            > 15
            9: 1, 2, 3, 4, 5, (5.5, 6.5), 7, 8, 9, 10, [11.0, 15.0]

            > ?[1, 15]
            true
            > clear
            > exit
        """.trimIndent()
    )
}

fun test(input: String, expectedOutput: String) {
    val outputStream = ByteArrayOutputStream()
    val printStream = PrintStream(outputStream).also { System.setOut(it) }
    // Each time a line is requested, it's also added to outputStream to obtain a pretty outout which simulates
    // the program console
    val inputIt = sequence { input.lineSequence().forEach { yield(it) } }.onEach { printStream.println(it) }.iterator()
    System.setIn(object : InputStream() {
        var buf: ByteArray = byteArrayOf() // empty initially
        var index = 0

        @Synchronized
        override fun read(): Int {
            if (index >= buf.size) {
                buf = "${inputIt.next()}\n".toByteArray()
                index = 0
            }
            return buf[index++].toUByte().toInt()
        }
    })
    // Call program, which has the contents if 'input' String as stdin, and stdout is set to outputStream
    main()
    // Compare program output and expected output, allowing any line separators as well as removing
    // leading and trailing empty lines.
    assert(
        expectedOutput.lines().dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
                == outputStream.toString().lines().dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
    ) { "Program output does not match expected output" }
}

