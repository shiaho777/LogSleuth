package io.github.shiaho777.logsleuth.app.core.text

/**
 * The clipboard is a Binder transaction: the whole process shares a ~1 MiB
 * buffer and chars cost two bytes each, so a large multi-select copy can
 * throw TransactionTooLargeException and take the app down. Callers check
 * the text here first and fall back to saving it as a file/session.
 *
 * The cap matches the text-share threshold in SessionExporter — well under
 * the Binder limit even with the chooser copying the payload a second time.
 */
object ClipboardGuard {
    const val MAX_CLIPBOARD_CHARS = 100_000

    fun fits(text: String): Boolean = text.length <= MAX_CLIPBOARD_CHARS
}