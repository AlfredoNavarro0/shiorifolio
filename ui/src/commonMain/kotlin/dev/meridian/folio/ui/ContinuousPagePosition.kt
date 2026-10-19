package dev.meridian.folio.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*

private class ContinuousPageSync(var page: Int) {
    var navigating = false
    var position: Pair<Int, Int>? = null
}

/** Scroll reports must not be mistaken for commands to jump back to the start of that page. */
@Composable
internal fun rememberSourcePageListState(
    bookId: String,
    sourcePages: List<Int>,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
): LazyListState {
    fun indexOfPage(page: Int): Int = sourcePages.indexOfFirst { it >= page }
        .let { if (it < 0) sourcePages.lastIndex.coerceAtLeast(0) else it }
    val listState = key(bookId, sourcePages) {
        rememberLazyListState(initialFirstVisibleItemIndex = indexOfPage(currentPage))
    }
    val sync = remember(bookId, sourcePages) { ContinuousPageSync(currentPage) }
    val latestCallback by rememberUpdatedState(onPageChange)
    val latestPage by rememberUpdatedState(currentPage)
    LaunchedEffect(bookId, sourcePages, currentPage) {
        if (currentPage != sync.page) {
            sync.page = currentPage
            sync.navigating = true
            try {
                listState.scrollToItem(indexOfPage(currentPage))
                sync.position = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
            finally { sync.navigating = false }
        }
    }
    LaunchedEffect(bookId, listState, sourcePages) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val coverage = linkedMapOf<Int, Int>()
            layout.visibleItemsInfo.forEach { item ->
                sourcePages.getOrNull(item.index)?.let { page ->
                    val visible = (minOf(item.offset + item.size, layout.viewportEndOffset) -
                        maxOf(item.offset, layout.viewportStartOffset)).coerceAtLeast(0)
                    coverage[page] = (coverage[page] ?: 0) + visible
                }
            }
            coverage.maxByOrNull { it.value }?.key to
                (listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
        }.collect { (page, position) ->
            val moved = sync.position != null && sync.position != position
            sync.position = position
            if (moved && !sync.navigating && latestPage == sync.page && page != null && page != sync.page) {
                sync.page = page
                latestCallback(page)
            }
        }
    }
    return listState
}
