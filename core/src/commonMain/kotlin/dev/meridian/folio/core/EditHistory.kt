package dev.meridian.folio.core

/** Observable undo/redo stack state (session-only, never persisted). */
data class EditHistoryState(
    val undoBooks: Set<String> = emptySet(),
    val redoBooks: Set<String> = emptySet(),
)

/**
 * Per-book Undo/Redo stack, bounded to the current editing session.
 * Keeps snapshots of the document and its annotations so every editing
 * operation is reversible without touching the original file.
 */
class EditHistory {
    internal data class Entry(
        val bookId: String,
        val label: String,
        val beforeDocument: BookDocument,
        val afterDocument: BookDocument,
        val beforePhrases: List<SavedPhrase>,
        val afterPhrases: List<SavedPhrase>,
        val beforeReminder: PageReminder?,
        val afterReminder: PageReminder?,
    )

    private val maxDepth = 80
    private val undoStacks = mutableMapOf<String, ArrayDeque<Entry>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<Entry>>()

    val undoBooks: Set<String> get() = undoStacks.filterValues { it.isNotEmpty() }.keys.toSet()
    val redoBooks: Set<String> get() = redoStacks.filterValues { it.isNotEmpty() }.keys.toSet()

    fun canUndo(bookId: String): Boolean = undoStacks[bookId]?.isNotEmpty() == true

    fun canRedo(bookId: String): Boolean = redoStacks[bookId]?.isNotEmpty() == true

    internal fun record(entry: Entry) {
        val stack = undoStacks.getOrPut(entry.bookId) { ArrayDeque() }
        stack.addLast(entry)
        while (stack.size > maxDepth) stack.removeFirst()
        redoStacks.remove(entry.bookId)
    }

    internal fun undoEntry(bookId: String): Entry? {
        val stack = undoStacks[bookId] ?: return null
        val entry = stack.removeLastOrNull() ?: return null
        redoStacks.getOrPut(bookId) { ArrayDeque() }.addLast(entry)
        return entry
    }

    internal fun redoEntry(bookId: String): Entry? {
        val stack = redoStacks[bookId] ?: return null
        val entry = stack.removeLastOrNull() ?: return null
        undoStacks.getOrPut(bookId) { ArrayDeque() }.addLast(entry)
        return entry
    }

    fun purge(bookIds: Collection<String>) {
        bookIds.forEach {
            undoStacks.remove(it)
            redoStacks.remove(it)
        }
    }

    fun clear() {
        undoStacks.clear()
        redoStacks.clear()
    }
}
