package aa

/**
 * Tests the functionality of a Set
 */
fun main() {
    fun getLine() = print("> ").run { readln() }

    val set = M()
    var line = getLine()
    // Note: <num> = xx.xx or .xx or xx (negative too - can start with unary +/-)
    // <word> = only a-z, A-Z, 0-9, _
    val regex = Regex(
        """(?:\s+|^)(?:""" +
                // ^
                // Match whitespace(es) or beginning of line before every option

                // = OPTION 1
                //  ( / [       <num>         ,         <num>          ) / ]
                """([(\[])([+-]?(?:\d*\.)?\d+), ?([+-]?(?:\d*\.)?\d+)([)\]])""" +
                //   -{        <num>         ",   <num>  "  0 or more  }
                """(?:-\{([+-]?(?:\d*\.)?\d+(?:, ?[+-]?(?:\d*\.)?\d+)+)})?""" +

                // = OPTION 2
                //        <num>
                """|([+-]?(?:\d*\.)?\d+)""" +

                // = OPTION 3
                // <word>
                """|(\w+))"""
    )
    while (line != "exit") {
        if (line == "clear") {
            set.clear()
            line = getLine()
            continue
        }
        val r = line.startsWith("~")
        val q = line.startsWith("?")
        if (r || q) line = line.drop(1)

        fun f(v: Any) = when {
            r -> set.remove(v)
            q -> println(set.contains(v))
            else -> set.add(v)
        }

        var result = regex.find(line)
        while (result != null) {
            // openI/closeI = whether to include first/last number (inclusive)
            val (openI, n1, n2, closeI, exclusions, num, string) = result.destructured
            if (num.isNotEmpty()) {
                val n = num.toLongOrNull() ?: num.toDoubleOrNull()
                if (n == null) println("Invalid/too large number: $num")
                else f(n)
            } else if (string.isNotEmpty()) f(string)
            else {
                try {
                    val range = IExclusiveRange(n1.toLong()..n2.toLong())
                    if (openI == "(") range.exclude(n1.toLong())
                    if (closeI == ")") range.exclude(n2.toLong())
                    if (exclusions.isNotEmpty())
                        for (n in exclusions.filterNot { c -> c == ' ' }.split(',')) range.exclude(n.toLong())
                    f(range)
                } catch (e: NumberFormatException) {
                    try {
                        val range = RExclusiveRange(n1.toDouble()..n2.toDouble())
                        if (openI == "(") range except n1.toDouble()
                        if (closeI == ")") range except n2.toDouble()
                        if (exclusions.isNotEmpty())
                            for (n in exclusions.filterNot { c -> c == ' ' }.split(',')) range.exclude(n.toDouble())
                        f(range)
                    } catch (e2: NumberFormatException) {
                        println("Cannot parse input: \"${result.value}\"")
                    }
                }
            }
            result = result.next()
        }

        if (!q) {
            print("${set.size}: ")
            set.printAll()
        }
        line = getLine()
    }
}
