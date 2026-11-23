package com.teleprompter.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PreparedScript(
    val schemaVersion: Int,
    val scriptId: String,
    val title: String,
    val sections: List<ScriptSection>,
    val appearance: ReaderAppearance,
    val speedMultiplier: Double = 1.0,
)

@Serializable
data class ScriptSection(
    val id: String,
    val subtitle: String? = null,
    val blocks: List<ContentBlock>,
)

@Serializable
sealed interface ContentBlock {
    val id: String

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        override val id: String,
        val runs: List<InlineRun>,
    ) : ContentBlock

    @Serializable
    @SerialName("list")
    data class ListBlock(
        override val id: String,
        val ordered: Boolean,
        val items: List<List<InlineRun>>,
    ) : ContentBlock

    @Serializable
    @SerialName("table")
    data class Table(
        override val id: String,
        val headerCells: List<List<InlineRun>>,
        val rows: List<List<List<InlineRun>>>,
    ) : ContentBlock

    @Serializable
    @SerialName("cue")
    data class Cue(
        override val id: String,
        val label: String = "PAUSA",
    ) : ContentBlock
}

@Serializable
data class InlineRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

@Serializable
data class ReaderAppearance(
    val textColorArgb: String = "#FFFFFFFF",
    val backgroundColorArgb: String = "#FF000000",
    val fontSizeSp: Double = 32.0,
    val lineSpacingMultiplier: Double = 1.5,
) {
    companion object {
        const val MIN_FONT_SIZE_SP = 20.0
        const val MAX_FONT_SIZE_SP = 64.0
        const val MIN_LINE_SPACING = 1.0
        const val MAX_LINE_SPACING = 2.5
    }
}
