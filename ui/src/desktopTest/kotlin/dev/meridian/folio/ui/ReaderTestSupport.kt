package dev.meridian.folio.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/** Secondary reader actions are reached through the visible overflow control. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.readerMenuItem(tag: String): SemanticsNodeInteraction {
    if (onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
        onNodeWithTag("reader_tools_menu").performClick()
        waitForIdle()
    }
    return onNodeWithTag(tag)
}

/** Layout, sorting and bulk controls are available without filling the shelf toolbar. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.libraryMenuItem(tag: String): SemanticsNodeInteraction {
    if (onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) {
        val menu = when {
            tag.startsWith("library_layout_") || tag.startsWith("library_density_") -> "library_view_menu"
            tag.startsWith("library_sort_") -> "library_sort_menu"
            else -> "library_more_menu"
        }
        onNodeWithTag(menu).performScrollTo().performClick()
        waitForIdle()
    }
    return onNodeWithTag(tag)
}
