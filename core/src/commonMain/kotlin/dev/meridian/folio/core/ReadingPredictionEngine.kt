package dev.meridian.folio.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Adaptive reading telemetry and dynamic finish-time prediction engine.
 * Continuously recalculates how much time the reader needs to complete a PDF or EPUB
 * by blending observed per-page dwell time and words-per-minute (WPM) velocity via EWMA.
 */
object ReadingPredictionEngine {
    private const val DEFAULT_WPM = 215.0
    private const val EWMA_ALPHA = 0.35

    fun recordReadingTick(
        telemetry: BookReadingTelemetry,
        book: BookDocument,
        pageNumber: Int,
        deltaSeconds: Double,
        nowMs: Long,
    ): BookReadingTelemetry {
        if (deltaSeconds <= 0.0) return telemetry
        val page = book.pages.find { it.pageNumber == pageNumber }
        val words = page?.wordCount ?: 180
        val existingStat = telemetry.pagesVisited[pageNumber] ?: PageReadingStat(
            pageNumber = pageNumber,
            secondsSpent = 0.0,
            visitCount = 1,
            wordsOnPage = words,
        )
        val updatedSeconds = existingStat.secondsSpent + deltaSeconds
        val updatedStat = existingStat.copy(
            secondsSpent = updatedSeconds,
            wordsOnPage = words,
        )

        val updatedMap = telemetry.pagesVisited.toMutableMap()
        updatedMap[pageNumber] = updatedStat

        // If user has read this page for at least 4 seconds, update dynamic WPM and pace samples
        val updatedPaceList = telemetry.recentPagePaceSeconds.toMutableList()
        val updatedWpmList = telemetry.recentWpmSamples.toMutableList()

        // Estimate full-page reading pace from active reading velocity
        val baselineSecondsForPage = (words / DEFAULT_WPM) * 60.0
        val blendedPagePace = if (updatedSeconds >= 4.0) {
            // Extrapolate smoothly as the user reads the page
            val observedWeight = min(0.85, updatedSeconds / max(12.0, baselineSecondsForPage))
            (updatedSeconds / max(0.35, min(1.25, updatedSeconds / baselineSecondsForPage))) * observedWeight +
                baselineSecondsForPage * (1.0 - observedWeight)
        } else {
            baselineSecondsForPage
        }

        if (updatedPaceList.isEmpty()) {
            updatedPaceList.add(blendedPagePace)
        } else {
            val prev = updatedPaceList.last()
            val ewma = EWMA_ALPHA * blendedPagePace + (1.0 - EWMA_ALPHA) * prev
            if (updatedPaceList.size >= 24) updatedPaceList.removeAt(0)
            updatedPaceList.add(ewma)
        }

        val currentWpm = (words.toDouble() / max(8.0, blendedPagePace)) * 60.0
        if (updatedWpmList.isEmpty()) {
            updatedWpmList.add(currentWpm.coerceIn(80.0, 550.0))
        } else {
            val prevWpm = updatedWpmList.last()
            val ewmaWpm = (EWMA_ALPHA * currentWpm + (1.0 - EWMA_ALPHA) * prevWpm).coerceIn(80.0, 550.0)
            if (updatedWpmList.size >= 24) updatedWpmList.removeAt(0)
            updatedWpmList.add(ewmaWpm)
        }

        return telemetry.copy(
            totalActiveSeconds = telemetry.totalActiveSeconds + deltaSeconds,
            pagesVisited = updatedMap,
            recentPagePaceSeconds = updatedPaceList,
            recentWpmSamples = updatedWpmList,
            recalculationCount = telemetry.recalculationCount + 1,
            lastReadEpochMs = nowMs,
        )
    }

    fun recordPageTurn(
        telemetry: BookReadingTelemetry,
        book: BookDocument,
        fromPage: Int,
        toPage: Int,
        nowMs: Long,
    ): BookReadingTelemetry {
        val fromStat = telemetry.pagesVisited[fromPage]
        val updatedPaceList = telemetry.recentPagePaceSeconds.toMutableList()
        val updatedWpmList = telemetry.recentWpmSamples.toMutableList()

        if (fromStat != null && fromStat.secondsSpent >= 3.0) {
            val actualSec = fromStat.secondsSpent.coerceIn(5.0, 300.0)
            val prevPace = updatedPaceList.lastOrNull() ?: actualSec
            val ewmaPace = EWMA_ALPHA * actualSec + (1.0 - EWMA_ALPHA) * prevPace
            if (updatedPaceList.size >= 24) updatedPaceList.removeAt(0)
            updatedPaceList.add(ewmaPace)

            val words = max(40, fromStat.wordsOnPage)
            val observedWpm = ((words / actualSec) * 60.0).coerceIn(80.0, 550.0)
            val prevWpm = updatedWpmList.lastOrNull() ?: DEFAULT_WPM
            val ewmaWpm = EWMA_ALPHA * observedWpm + (1.0 - EWMA_ALPHA) * prevWpm
            if (updatedWpmList.size >= 24) updatedWpmList.removeAt(0)
            updatedWpmList.add(ewmaWpm)
        }

        val toWords = book.pages.find { it.pageNumber == toPage }?.wordCount ?: 180
        val toStat = telemetry.pagesVisited[toPage]
        val updatedMap = telemetry.pagesVisited.toMutableMap()
        updatedMap[toPage] = if (toStat == null) {
            PageReadingStat(pageNumber = toPage, secondsSpent = 0.0, visitCount = 1, wordsOnPage = toWords)
        } else {
            toStat.copy(visitCount = toStat.visitCount + 1, wordsOnPage = toWords)
        }

        return telemetry.copy(
            pagesVisited = updatedMap,
            recentPagePaceSeconds = updatedPaceList,
            recentWpmSamples = updatedWpmList,
            recalculationCount = telemetry.recalculationCount + 1,
            lastReadEpochMs = nowMs,
        )
    }

    fun computePrediction(
        book: BookDocument,
        telemetry: BookReadingTelemetry?,
    ): FinishTimePrediction {
        val totalPages = book.totalPages
        val currentPage = book.currentPage.coerceIn(1, totalPages)
        val remainingPagesList = book.pages.filter { it.pageNumber >= currentPage }
        val remainingPagesCount = (totalPages - currentPage).coerceAtLeast(0)
        val remainingWords = remainingPagesList.sumOf { it.wordCount }

        val activeSeconds = telemetry?.totalActiveSeconds ?: 0.0
        val avgWordsPerPage = if (totalPages > 0) (book.totalWords.toDouble() / totalPages).coerceAtLeast(60.0) else 160.0

        val wpm = telemetry?.recentWpmSamples?.lastOrNull() ?: DEFAULT_WPM
        val defaultSecPerPage = (avgWordsPerPage / DEFAULT_WPM) * 60.0
        val secPerPage = telemetry?.recentPagePaceSeconds?.lastOrNull() ?: defaultSecPerPage

        // Subtract time already spent on the current page from remaining estimate
        val currentPageSpent = telemetry?.pagesVisited?.get(currentPage)?.secondsSpent ?: 0.0
        val currentPageEstTotal = ((book.pages.find { it.pageNumber == currentPage }?.wordCount ?: 160) / wpm) * 60.0
        val currentPageRemaining = (currentPageEstTotal - currentPageSpent).coerceAtLeast(2.0)

        val futurePagesWords = book.pages.filter { it.pageNumber > currentPage }.sumOf { it.wordCount }
        val futurePagesByWpm = (futurePagesWords / wpm) * 60.0
        val futurePagesByPagePace = remainingPagesCount * secPerPage

        // Blend word-density estimate (65%) with page-turn velocity estimate (35%)
        val totalRemainingSeconds = if (currentPage >= totalPages && currentPageSpent >= currentPageEstTotal * 0.85) {
            0.0
        } else {
            currentPageRemaining + (0.65 * futurePagesByWpm + 0.35 * futurePagesByPagePace)
        }

        val samples = telemetry?.recentWpmSamples ?: emptyList()
        val trendDelta = if (samples.size >= 2) {
            val first = samples.first()
            val last = samples.last()
            if (first > 0.0) ((last - first) / first) * 100.0 else 0.0
        } else {
            0.0
        }

        val recalcCount = telemetry?.recalculationCount ?: 0
        val confidence = when {
            recalcCount >= 15 -> "High (Calibrated to your pace)"
            recalcCount >= 5 -> "Medium (Adapting live)"
            else -> "Baseline (Calibrating...)"
        }

        val completion = if (totalPages <= 1) {
            1f
        } else {
            val pageBase = (currentPage - 1).toFloat() / totalPages.toFloat()
            val withinPage = (currentPageSpent / max(10.0, currentPageEstTotal)).toFloat().coerceIn(0f, 1f) / totalPages.toFloat()
            (pageBase + withinPage).coerceIn(0f, 1f)
        }

        return FinishTimePrediction(
            bookId = book.id,
            currentPage = currentPage,
            totalPages = totalPages,
            remainingPages = remainingPagesCount,
            remainingWords = remainingWords,
            activeReadingSeconds = activeSeconds,
            estimatedSecondsPerPage = (secPerPage * 10.0).roundToInt() / 10.0,
            estimatedWordsPerMinute = (wpm * 10.0).roundToInt() / 10.0,
            estimatedRemainingSeconds = max(0.0, totalRemainingSeconds),
            completionFraction = completion,
            recalculationCount = recalcCount,
            paceTrendDeltaPercent = (trendDelta * 10.0).roundToInt() / 10.0,
            confidenceLabel = confidence,
        )
    }
}
