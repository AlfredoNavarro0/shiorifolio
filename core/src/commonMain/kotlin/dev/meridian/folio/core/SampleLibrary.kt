package dev.meridian.folio.core

object SampleLibrary {

    fun defaultBooks(nowMs: Long = 1_760_500_000_000L): List<BookDocument> = listOf(
        BookDocument(
            id = "meridian_viaducts_epub",
            title = "On the Viaducts of Meridian",
            subtitle = "The Scenic Route to Shipped Work — Town to Town",
            author = "Clara Vance & Julian Thorne",
            format = DocumentFormat.EPUB,
            currentPage = 2,
            createdAtMs = nowMs - 86_400_000L * 5,
            updatedAtMs = nowMs - 3_600_000L,
            pages = listOf(
                BookPage(
                    pageNumber = 1,
                    chapterTitle = "I. The Stone Arched Viaduct",
                    blocks = listOf(
                        ContentBlock(
                            id = "mv_1_1",
                            style = BlockStyle.HEADING,
                            text = "Chapter I — Across the Pale Sage Valley",
                        ),
                        ContentBlock(
                            id = "mv_1_2",
                            style = BlockStyle.PARAGRAPH,
                            text = "At dawn the limestone piers of the Meridian Viaduct emerge from the valley mist like an engraving pressed into damp cotton rag paper. Every arch was laid by masons who measured span and keystone load by compass rose and plumb line.",
                        ),
                        ContentBlock(
                            id = "mv_1_3",
                            style = BlockStyle.QUOTE,
                            text = "The scenic route is never the slowest when every milestone is carved in stone and every relay of horses knows the grade of the bridge.",
                        ),
                        ContentBlock(
                            id = "mv_1_4",
                            style = BlockStyle.PARAGRAPH,
                            text = "When the morning stagecoach enters from the western abutment pulled by three trotting horses, its iron-shod wheels strike a steady cadence across the masonry deck. Dust trails behind the rear axle, rising briefly before settling back into the pale sage grass below.",
                        ),
                    ),
                ),
                BookPage(
                    pageNumber = 2,
                    chapterTitle = "II. Instruments of the Dispatch",
                    blocks = listOf(
                        ContentBlock(
                            id = "mv_2_1",
                            style = BlockStyle.HEADING,
                            text = "Chapter II — The Eight-Point Compass Rose",
                        ),
                        ContentBlock(
                            id = "mv_2_2",
                            style = BlockStyle.PARAGRAPH,
                            text = "In the dispatch ledger of Meridian Works, each town along the route is indexed by bearing and league. The cardinal points of the brass compass are split half-burnished and half-shaded so the surveyor can read azimuth even under overcast autumn skies.",
                        ),
                        ContentBlock(
                            id = "mv_2_3",
                            style = BlockStyle.PARAGRAPH,
                            text = "Waybills carried inside the coach leather satchel record not only cargo weight, but the exact minute the team cleared each toll-house. From these timestamps, the stationmaster recalculates the arrival prediction for the terminal depot.",
                        ),
                        ContentBlock(
                            id = "mv_2_4",
                            style = BlockStyle.CALLOUT,
                            text = "Archival Note: Click any word or phrase on this page to mark and save it to your Meridian Lexicon, or switch to the Editor tab to revise the manuscript directly.",
                        ),
                    ),
                ),
                BookPage(
                    pageNumber = 3,
                    chapterTitle = "III. Copperplate & Green Ink",
                    blocks = listOf(
                        ContentBlock(
                            id = "mv_3_1",
                            style = BlockStyle.HEADING,
                            text = "Chapter III — Intaglio Plates and Paper Grain",
                        ),
                        ContentBlock(
                            id = "mv_3_2",
                            style = BlockStyle.PARAGRAPH,
                            text = "Unlike black lamp-soot ink that sits harshly on white parchment, the deep forest pigment of Meridian (#2b3a30) is ground from verdigris, roasted umber, and linseed oil. Pressed onto warm sage sheets (#ebeee7), line and paper fuse into a calm, tireless surface for long hours of study.",
                        ),
                        ContentBlock(
                            id = "mv_3_3",
                            style = BlockStyle.PARAGRAPH,
                            text = "Engravers cross-hatch the undersides of the viaduct vaults with fine burin strokes spaced less than a millimeter apart, creating shadow without losing transparency.",
                        ),
                        ContentBlock(
                            id = "mv_3_4",
                            style = BlockStyle.QUOTE,
                            text = "True legibility is achieved not by glare, but by the quiet harmony between mineral pigment and unbleached fiber.",
                        ),
                    ),
                ),
                BookPage(
                    pageNumber = 4,
                    chapterTitle = "IV. The Night Relay",
                    blocks = listOf(
                        ContentBlock(
                            id = "mv_4_1",
                            style = BlockStyle.HEADING,
                            text = "Chapter IV — Lanterns on the High Span",
                        ),
                        ContentBlock(
                            id = "mv_4_2",
                            style = BlockStyle.PARAGRAPH,
                            text = "When twilight descends over the river gorge, the coachman lights the twin bevel-glass carriage lamps. In Nocturne Dark mode, the stone parapet reflects a soft celadon glow while the valley floor recedes into deep pine shadow.",
                        ),
                        ContentBlock(
                            id = "mv_4_3",
                            style = BlockStyle.PARAGRAPH,
                            text = "Inside the cabin, passengers consult their pocket folios in two-column spread or wide panorama view, marking memorable passages with brass page clips so they can resume at the exact leaf on tomorrow's journey.",
                        ),
                    ),
                ),
                BookPage(
                    pageNumber = 5,
                    chapterTitle = "V. Arrival at the Citadel",
                    blocks = listOf(
                        ContentBlock(
                            id = "mv_5_1",
                            style = BlockStyle.HEADING,
                            text = "Chapter V — Delivering Work from Town to Town",
                        ),
                        ContentBlock(
                            id = "mv_5_2",
                            style = BlockStyle.PARAGRAPH,
                            text = "As the final arch gives way to the cobbled plaza of the eastern terminus, the chronometer hand aligns with the stationmaster's revised forecast. Every parcel, folio, and architectural proof arrives intact.",
                        ),
                        ContentBlock(
                            id = "mv_5_3",
                            style = BlockStyle.PARAGRAPH,
                            text = "Here the ledger is signed, exported in both archival PDF and portable EPUB format, and shelved for the next expedition across the Meridian.",
                        ),
                    ),
                ),
            ),
        ),
        BookDocument(
            id = "stone_and_light_pdf",
            title = "An Archive of Light Cast in Stone",
            subtitle = "Brutalist & Masonry Monographs — Autumn 2026",
            author = "Atrium & Meridian Press",
            format = DocumentFormat.PDF,
            currentPage = 1,
            createdAtMs = nowMs - 86_400_000L * 3,
            updatedAtMs = nowMs - 7_200_000L,
            pages = listOf(
                BookPage(
                    pageNumber = 1,
                    chapterTitle = "Monograph 01 — Concrete & Limestone",
                    blocks = listOf(
                        ContentBlock(
                            id = "sl_1_1",
                            style = BlockStyle.HEADING,
                            text = "Monograph I — Shadow Geometry in Civic Architecture",
                        ),
                        ContentBlock(
                            id = "sl_1_2",
                            style = BlockStyle.PARAGRAPH,
                            text = "Board-formed concrete and rough-dressed ashlar share an unexpected kinship: both record the texture of their formwork and tooling in permanent relief. When low autumn sunlight rakes across a coffered vault, time itself becomes visible along the arris of every stone.",
                        ),
                        ContentBlock(
                            id = "sl_1_3",
                            style = BlockStyle.PARAGRAPH,
                            text = "This PDF folio catalogs forty-two structures photographed between dawn and civil twilight, preserving their proportions before urban redevelopment alters the skyline.",
                        ),
                    ),
                ),
                BookPage(
                    pageNumber = 2,
                    chapterTitle = "Monograph 02 — Acoustic Chambers",
                    blocks = listOf(
                        ContentBlock(
                            id = "sl_2_1",
                            style = BlockStyle.HEADING,
                            text = "Monograph II — Reverberation and Volume",
                        ),
                        ContentBlock(
                            id = "sl_2_2",
                            style = BlockStyle.PARAGRAPH,
                            text = "In high-ceilinged reading halls, the decay time of a turned page measures nearly two seconds. Architects proportioned the clerestory bays so natural daylight illuminates every reading desk without casting the reader's own hand in shadow.",
                        ),
                        ContentBlock(
                            id = "sl_2_3",
                            style = BlockStyle.QUOTE,
                            text = "Architecture is frozen cadence; a book is portable architecture.",
                        ),
                    ),
                ),
                BookPage(
                    pageNumber = 3,
                    chapterTitle = "Monograph 03 — Preservation Index",
                    blocks = listOf(
                        ContentBlock(
                            id = "sl_3_1",
                            style = BlockStyle.HEADING,
                            text = "Monograph III — Field Notes & Restoration",
                        ),
                        ContentBlock(
                            id = "sl_3_2",
                            style = BlockStyle.PARAGRAPH,
                            text = "Use the built-in PDF Editor to append restoration notes, insert new survey pages, or export updated monographs as a clean ISO-32000 PDF file.",
                        ),
                    ),
                ),
            ),
        ),
    )

    fun defaultReminders(nowMs: Long = 1_760_500_000_000L): Map<String, PageReminder> = mapOf(
        "meridian_viaducts_epub" to PageReminder(
            bookId = "meridian_viaducts_epub",
            bookTitle = "On the Viaducts of Meridian",
            pageNumber = 2,
            note = "Continue at Chapter II (The Eight-Point Compass Rose) — check the stationmaster's arrival prediction formula.",
            targetLabel = "Tonight's Reading Session",
            createdAtMs = nowMs - 1_800_000L,
        ),
    )

    fun defaultSavedPhrases(nowMs: Long = 1_760_500_000_000L): List<SavedPhrase> = listOf(
        SavedPhrase(
            id = "phrase_1",
            bookId = "meridian_viaducts_epub",
            bookTitle = "On the Viaducts of Meridian",
            pageNumber = 1,
            phrase = "limestone piers of the Meridian Viaduct",
            contextSentence = "At dawn the limestone piers of the Meridian Viaduct emerge from the valley mist like an engraving pressed into damp cotton rag paper.",
            note = "Opening architectural imagery of the arched stone bridge.",
            tone = HighlightTone.SAGE,
            createdAtMs = nowMs - 3_600_000L,
        ),
        SavedPhrase(
            id = "phrase_2",
            bookId = "meridian_viaducts_epub",
            bookTitle = "On the Viaducts of Meridian",
            pageNumber = 2,
            phrase = "eight-point compass rose",
            contextSentence = "The cardinal points of the brass compass are split half-burnished and half-shaded so the surveyor can read azimuth even under overcast autumn skies.",
            note = "Key symbol of Meridian Works Ltd.",
            tone = HighlightTone.AMBER,
            createdAtMs = nowMs - 2_400_000L,
        ),
    )

    fun defaultTelemetry(nowMs: Long = 1_760_500_000_000L): Map<String, BookReadingTelemetry> = mapOf(
        "meridian_viaducts_epub" to BookReadingTelemetry(
            bookId = "meridian_viaducts_epub",
            totalActiveSeconds = 48.0,
            pagesVisited = mapOf(
                1 to PageReadingStat(pageNumber = 1, secondsSpent = 34.0, visitCount = 1, wordsOnPage = 94),
                2 to PageReadingStat(pageNumber = 2, secondsSpent = 14.0, visitCount = 1, wordsOnPage = 102),
            ),
            recentPagePaceSeconds = listOf(36.0, 34.5),
            recentWpmSamples = listOf(210.0, 218.0),
            recalculationCount = 3,
            lastReadEpochMs = nowMs - 600_000L,
        ),
    )
}
