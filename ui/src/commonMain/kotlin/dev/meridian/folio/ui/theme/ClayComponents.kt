package dev.meridian.folio.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meridian.folio.core.ReaderTheme

/** Soft pill for the application header: rounded, lightly raised, without a hard outline. */
@Composable
fun ClayHeaderAction(
    label: String,
    palette: ClayPalette,
    tag: String,
    selected: Boolean = false,
    quiet: Boolean = false,
    description: String = label,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(contentAlignment = Alignment.Center) {
        if (!quiet || selected) {
            ClayShadowBox(
                shadow = if (selected) ClayShadows.button else ClayShadows.card,
                shape = shape, modifier = Modifier.matchParentSize(),
            ) {
                Box(Modifier.matchParentSize().background(when {
                    selected && quiet -> palette.primary
                    selected -> palette.primaryContainer
                    else -> palette.surfaceContainerLow
                }, shape))
            }
        }
        Box(
            Modifier.heightIn(min = 40.dp).widthIn(min = 40.dp)
                .clayFocusableClickable(palette, shape, onClick = onClick)
                .testTag(tag).semantics { contentDescription = description }
                .padding(horizontal = ClaySpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = when {
                selected && quiet -> palette.onPrimary
                selected -> palette.onPrimaryContainer
                else -> palette.onSurfaceVariant
            },
                style = LocalClayTypography.current.labelMd,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Soft clay card: multi-layer clay shadow ([ClayShadows.card]) around an opaque
 * `cardSurface` fill and the shared card radius.
 *
 * The card is sized by its caller: the shadow and frosted layers are `matchParentSize`,
 * so pass explicit constraints (e.g. `Modifier.fillMaxWidth()` plus a height) — with
 * loose constraints the card renders 0×0.
 */
@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    palette: ClayPalette = LocalClayPalette.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(ClayRadius.md)
    ClayShadowBox(shadow = ClayShadows.card, shape = shape, modifier = modifier) {
        Box(Modifier.matchParentSize().clayFrosted(shape, surface = palette.cardSurface))
        content()
    }
}

/**
 * Primary action button: `primary → primaryDeep` gradient, white label, clay button
 * shadow ([ClayShadows.button]), shared button radius and a minimum height of 48.dp.
 *
 * The label drives the intrinsic size: the surface is a sibling above the shadow box
 * (whose layers are `matchParentSize` and therefore cannot wrap content).
 */
@Composable
fun ClayPrimaryButton(
    label: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    tag: String? = null,
    enabled: Boolean = true,
    palette: ClayPalette = LocalClayPalette.current,
) {
    val shape = RoundedCornerShape(ClayRadius.base)
    val background: Brush = if (enabled) {
        Brush.horizontalGradient(listOf(palette.primary, palette.primaryDeep))
    } else {
        SolidColor(palette.disabledContainer)
    }
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        ClayShadowBox(
            shadow = ClayShadows.button,
            shape = shape,
            modifier = Modifier.matchParentSize(),
        ) {
            // The gradient surface is a sibling above; the base stays transparent.
        }
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(background)
                // The opaque gradient hides the shadow box's behind-content inset
                // layers, so they are repainted over the surface, clipped to it.
                .clayInsetOverlay(shape, ClayShadows.button.inner)
                .clayFocusableClickable(palette, shape = shape, enabled = enabled, onClick = onClick)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .padding(horizontal = ClaySpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (enabled) palette.onPrimary else palette.disabledContent,
                style = LocalClayTypography.current.labelLg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Secondary action button: quiet `surfaceSecondary` surface with a subtle border,
 * shared button radius and a minimum height of 48.dp.
 */
@Composable
fun ClaySecondaryButton(
    label: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    tag: String? = null,
    enabled: Boolean = true,
    palette: ClayPalette = LocalClayPalette.current,
) {
    val shape = RoundedCornerShape(ClayRadius.base)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (enabled) palette.surfaceSecondary else palette.disabledContainer)
            .border(1.dp, palette.outlineVariant.copy(alpha = 0.6f), shape)
            .clayFocusableClickable(palette, shape = shape, enabled = enabled, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = ClaySpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) palette.onSurface else palette.disabledContent,
            style = LocalClayTypography.current.labelLg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Compact toolbar action with a soft rectangular outline and a solid selected accent.
 * Keeps a 40.dp target, the user typography settings, tags and accessible description.
 */
@Composable
fun ClayChromeAction(
    label: String,
    palette: ClayPalette,
    tag: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    description: String = label,
    onClick: () -> Unit,
) {
    val appearance = LocalClayUiAppearance.current
    val shape = RoundedCornerShape(ClayRadius.sm)
    val contentColor = when {
        !enabled -> palette.disabledContent
        selected -> palette.onPrimary
        else -> palette.onSurface
    }
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) palette.primary else palette.surfaceContainerLow)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, palette.outlineVariant.copy(alpha = 0.6f), shape)
                },
            )
            .clayFocusableClickable(palette, shape = shape, enabled = enabled, onClick = onClick)
            .testTag(tag)
            .semantics { contentDescription = description }
            .padding(horizontal = ClaySpacing.sm, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = contentColor,
            fontFamily = appearance.fontFamily.toComposeFontFamily(),
            fontWeight = if (selected) FontWeight.SemiBold else appearance.fontWeight.toComposeFontWeight(),
            fontSize = appearance.fontSizeSp.coerceIn(11f, 15f).sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Selectable filter chip: soft rectangle with `primary`/`onPrimary` when [selected], otherwise
 * `surfaceContainerHigh`/`onSurface` with a subtle border.
 */
@Composable
fun ClayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    enabled: Boolean = true,
    palette: ClayPalette = LocalClayPalette.current,
) {
    val shape = RoundedCornerShape(ClayRadius.sm)
    val contentColor = when {
        !enabled -> palette.disabledContent
        selected -> palette.onPrimary
        else -> palette.onSurface
    }
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) palette.primary else palette.surfaceContainerLow)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, palette.outlineVariant.copy(alpha = 0.6f), shape)
                },
            )
            .clayFocusableClickable(palette, shape = shape, enabled = enabled, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = ClaySpacing.sm, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = contentColor,
            style = LocalClayTypography.current.labelMd,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Segmented tab strip: `surfaceSecondary` container pill with a `primary` indicator
 * behind the selected item. Tab tags are `"${tagPrefix}${item}"`.
 */
@Composable
fun ClaySegmentedTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    tagPrefix: String = "segmented_tab_",
    modifier: Modifier = Modifier,
    palette: ClayPalette = LocalClayPalette.current,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(ClayRadius.sm))
            .background(palette.surfaceSecondary)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val itemShape = RoundedCornerShape(ClayRadius.sm)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(itemShape)
                    .background(if (selected) palette.primary else palette.surfaceSecondary)
                    .clayFocusableClickable(palette, shape = itemShape, onClick = { onSelect(index) })
                    .testTag("${tagPrefix}${item}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item,
                    color = if (selected) palette.onPrimary else palette.onSurfaceVariant,
                    style = LocalClayTypography.current.labelMd,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Progress coerced to the [0f, 1f] range used to size the fill. */
fun clayProgressFraction(progress: Float): Float = progress.coerceIn(0f, 1f)

/**
 * Reading progress bar: `surfaceContainerHigh` track with a `primary → primaryDeep`
 * gradient fill, fully rounded and 8.dp tall. [progress] is clamped to [0f, 1f].
 */
@Composable
fun ClayProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    tag: String? = null,
    palette: ClayPalette = LocalClayPalette.current,
) {
    val shape = RoundedCornerShape(ClayRadius.sm)
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(shape)
            .background(palette.surfaceContainerHigh)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        Box(
            Modifier
                .fillMaxWidth(clayProgressFraction(progress))
                .fillMaxHeight()
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(palette.primary, palette.primaryDeep))),
        )
    }
}

/**
 * Reading-tone swatches: one dot per [ReaderTheme] sheet with a `primary` ring on the
 * selected tone. Swatch tags are `"${tagPrefix}${theme.name}"` (default
 * `reader_tone_WHITE`, …).
 */
@Composable
fun ClayToneSwatches(
    selected: ReaderTheme,
    onSelect: (ReaderTheme) -> Unit,
    palette: ClayPalette = LocalClayPalette.current,
    tagPrefix: String = "reader_tone_",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderTheme.entries.forEach { theme ->
            val isSelected = theme == selected
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, palette.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clayFocusableClickable(palette, shape = CircleShape, onClick = { onSelect(theme) })
                    .testTag("${tagPrefix}${theme.name}")
                    .semantics { contentDescription = theme.stitchLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ClayTokens.readingTone(theme).sheet),
                )
            }
        }
    }
}
