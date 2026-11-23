package com.teleprompter.shared.serialization

import com.teleprompter.shared.model.ContentBlock
import com.teleprompter.shared.model.InlineRun
import com.teleprompter.shared.model.PreparedScript
import com.teleprompter.shared.model.ReaderAppearance
import com.teleprompter.shared.model.ScriptSection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ScriptJsonCodecTest {
    @Test
    fun roundTripPreservesSectionsBlocksFormattingAndReadingSettings() {
        val script = sampleScript()

        val decoded = ScriptJsonCodec.decode(ScriptJsonCodec.encode(script))

        assertEquals(script, decoded)
    }

    @Test
    fun decodingUsesOptionalFieldDefaults() {
        val decoded = ScriptJsonCodec.decode(minimalScriptJson())
        val section = decoded.sections.single()
        val paragraph = section.blocks[0] as ContentBlock.Paragraph
        val cue = section.blocks[1] as ContentBlock.Cue

        assertEquals(null, section.subtitle)
        assertFalse(paragraph.runs.single().bold)
        assertFalse(paragraph.runs.single().italic)
        assertEquals("PAUSA", cue.label)
        assertEquals("#FFFFFFFF", decoded.appearance.textColorArgb)
        assertEquals("#FF000000", decoded.appearance.backgroundColorArgb)
        assertEquals(32.0, decoded.appearance.fontSizeSp, 0.0)
        assertEquals(1.5, decoded.appearance.lineSpacingMultiplier, 0.0)
        assertEquals(1.0, decoded.speedMultiplier, 0.0)
    }

    @Test
    fun encodingIncludesSchemaVersionAndDefaultValues() {
        val encoded = ScriptJsonCodec.encode(sampleScript().copy(
            sections = listOf(
                ScriptSection(
                    id = SECTION_ID,
                    blocks = listOf(ContentBlock.Paragraph(BLOCK_ID, listOf(InlineRun("Hello")))),
                ),
            ),
        ))
        val json = Json.parseToJsonElement(encoded).jsonObject
        val paragraph = json["sections"]!!.jsonArray.single().jsonObject["blocks"]!!
            .jsonArray.single().jsonObject
        val run = paragraph["runs"]!!.jsonArray.single().jsonObject

        assertEquals("1", json["schemaVersion"]!!.jsonPrimitive.content)
        assertEquals("paragraph", paragraph["type"]!!.jsonPrimitive.content)
        assertEquals("false", run["bold"]!!.jsonPrimitive.content)
        assertEquals("false", run["italic"]!!.jsonPrimitive.content)
    }

    @Test
    fun futureSchemaVersionIsRejectedAndReported() {
        val futureJson = minimalScriptJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")

        val error = assertThrows(UnsupportedScriptSchemaException::class.java) {
            ScriptJsonCodec.decode(futureJson)
        }

        assertEquals(2, error.foundVersion)
    }

    @Test
    fun unknownFieldsAndUnknownBlockTypesAreRejected() {
        val extraField = minimalScriptJson().replace("\"title\": \"Example\"", "\"title\": \"Example\", \"extra\": true")
        val unknownBlock = minimalScriptJson().replace("\"type\": \"paragraph\"", "\"type\": \"futureBlock\"")

        assertThrows(ScriptFormatException::class.java) { ScriptJsonCodec.decode(extraField) }
        assertThrows(ScriptFormatException::class.java) { ScriptJsonCodec.decode(unknownBlock) }
    }

    @Test
    fun malformedJsonAndInvalidScriptValuesAreRejected() {
        assertThrows(ScriptFormatException::class.java) { ScriptJsonCodec.decode("{") }

        val invalidSpeed = minimalScriptJson().replace("\"speedMultiplier\": 1.0", "\"speedMultiplier\": 1.25")
        val duplicateIds = minimalScriptJson().replace(SECTION_ID, SCRIPT_ID)
        val invalidColor = minimalScriptJson().replace("\"appearance\": {}", "\"appearance\": {\"textColorArgb\": \"white\"}")

        assertThrows(IllegalArgumentException::class.java) { ScriptJsonCodec.decode(invalidSpeed) }
        assertThrows(IllegalArgumentException::class.java) { ScriptJsonCodec.decode(duplicateIds) }
        assertThrows(IllegalArgumentException::class.java) { ScriptJsonCodec.decode(invalidColor) }
    }

    @Test
    fun validScriptWithEveryBlockKindPassesValidation() {
        val script = sampleScript()

        ScriptJsonCodec.validate(script)

        assertTrue(script.sections.single().blocks.any { it is ContentBlock.Cue })
    }

    private fun sampleScript() = PreparedScript(
        schemaVersion = 1,
        scriptId = SCRIPT_ID,
        title = "Opening remarks",
        sections = listOf(
            ScriptSection(
                id = SECTION_ID,
                subtitle = "Welcome",
                blocks = listOf(
                    ContentBlock.Paragraph(
                        id = BLOCK_ID,
                        runs = listOf(
                            InlineRun("Good morning, "),
                            InlineRun("everyone", bold = true, italic = true),
                        ),
                    ),
                    ContentBlock.ListBlock(
                        id = LIST_ID,
                        ordered = false,
                        items = listOf(listOf(InlineRun("First point")), listOf(InlineRun("Second point"))),
                    ),
                    ContentBlock.Table(
                        id = TABLE_ID,
                        headerCells = listOf(listOf(InlineRun("Topic")), listOf(InlineRun("Speaker"))),
                        rows = listOf(listOf(listOf(InlineRun("Opening")), listOf(InlineRun("Alex")))),
                    ),
                    ContentBlock.Cue(id = CUE_ID),
                ),
            ),
        ),
        appearance = ReaderAppearance(
            textColorArgb = "#FFF0F0F0",
            backgroundColorArgb = "#FF101010",
            fontSizeSp = 40.0,
            lineSpacingMultiplier = 1.75,
        ),
        speedMultiplier = 1.3,
    )

    private fun minimalScriptJson() = """
        {
          "schemaVersion": 1,
          "scriptId": "$SCRIPT_ID",
          "title": "Example",
          "sections": [{
            "id": "$SECTION_ID",
            "blocks": [
              {"id": "$BLOCK_ID", "type": "paragraph", "runs": [{"text": "Hello"}]},
              {"id": "$CUE_ID", "type": "cue"}
            ]
          }],
          "appearance": {},
          "speedMultiplier": 1.0
        }
    """.trimIndent()

    private companion object {
        const val SCRIPT_ID = "550e8400-e29b-41d4-a716-446655440000"
        const val SECTION_ID = "550e8400-e29b-41d4-a716-446655440001"
        const val BLOCK_ID = "550e8400-e29b-41d4-a716-446655440002"
        const val LIST_ID = "550e8400-e29b-41d4-a716-446655440003"
        const val TABLE_ID = "550e8400-e29b-41d4-a716-446655440004"
        const val CUE_ID = "550e8400-e29b-41d4-a716-446655440005"
    }
}
