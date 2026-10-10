package io.github.shiaho777.logsleuth.app.core.root

/**
 * One shell word. `logcat -T` takes a stamp that contains a space
 * (`MM-dd HH:mm:ss.SSS`); without quotes the shell splits it into two
 * arguments and logcat rejects the command.
 */
fun shellQuote(arg: String): String = buildString(arg.length + 2) {
    append('\'')
    for (ch in arg) {
        if (ch == '\'') append("'\\''") else append(ch)
    }
    append('\'')
}

/** A command line libsu can hand to a root shell. */
fun toShellCommand(args: List<String>): String =
    args.joinToString(" ") { shellQuote(it) }
