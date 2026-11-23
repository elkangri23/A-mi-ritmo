package com.teleprompter.shared.position

/** A local reading position that can be remapped after text layout or content changes. */
data class PositionAnchor(
    val scriptId: String,
    val sectionId: String,
    val blockId: String,
    val textOffset: Int,
    val surroundingText: String,
) {
    init {
        require(textOffset >= 0) { "textOffset must not be negative" }
    }
}
