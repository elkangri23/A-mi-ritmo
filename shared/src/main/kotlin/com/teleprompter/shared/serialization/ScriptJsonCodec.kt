package com.teleprompter.shared.serialization

import com.teleprompter.shared.model.ContentBlock
import com.teleprompter.shared.model.PreparedScript
import com.teleprompter.shared.model.ReaderAppearance
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.encodeToString
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

class ScriptFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class UnsupportedScriptSchemaException(val foundVersion: Int) :
    IllegalArgumentException("Script schema version $foundVersion is not supported; the file was not changed")

object ScriptJsonCodec {
    const val SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        isLenient = false
        prettyPrint = true
    }

    fun encode(script: PreparedScript): String {
        validate(script)
        return json.encodeToString(script)
    }

    fun decode(source: String): PreparedScript {
        val root = try {
            json.parseToJsonElement(source.removePrefix("\uFEFF")) as? JsonObject
                ?: throw ScriptFormatException("The script JSON root must be an object")
        } catch (error: ScriptFormatException) {
            throw error
        } catch (error: SerializationException) {
            throw ScriptFormatException("The script file is not valid JSON", error)
        }

        val version = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull
            ?: throw ScriptFormatException("The script is missing a valid schemaVersion")
        if (version != SUPPORTED_SCHEMA_VERSION) {
            throw UnsupportedScriptSchemaException(version)
        }

        val script = try {
            json.decodeFromJsonElement<PreparedScript>(root)
        } catch (error: SerializationException) {
            throw ScriptFormatException("The script does not match schema version 1", error)
        } catch (error: IllegalArgumentException) {
            throw ScriptFormatException("The script does not match schema version 1", error)
        }

        try {
            validate(script)
        } catch (error: IllegalArgumentException) {
            throw ScriptFormatException("The script does not match schema version 1", error)
        }
        return script
    }

    fun validate(script: PreparedScript) {
        require(script.schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Only schema version $SUPPORTED_SCHEMA_VERSION can be written"
        }
        require(isCanonicalUuid(script.scriptId)) { "scriptId must be a canonical UUID" }
        require(script.title.isNotBlank()) { "title must not be empty" }
        require(script.sections.isNotEmpty()) { "A script must contain at least one section" }
        require(script.speedMultiplier in 0.5..2.0 && isTenthStep(script.speedMultiplier)) {
            "speedMultiplier must be between 0.5 and 2.0 in 0.1 steps"
        }
        require(isArgb(script.appearance.textColorArgb)) { "textColorArgb must use #AARRGGBB" }
        require(isArgb(script.appearance.backgroundColorArgb)) { "backgroundColorArgb must use #AARRGGBB" }
        require(
            script.appearance.fontSizeSp.isFinite() &&
                script.appearance.fontSizeSp in ReaderAppearance.MIN_FONT_SIZE_SP..ReaderAppearance.MAX_FONT_SIZE_SP &&
                script.appearance.fontSizeSp == script.appearance.fontSizeSp.roundToInt().toDouble(),
        ) {
            "fontSizeSp must be a whole number between 20 and 64"
        }
        require(
            script.appearance.lineSpacingMultiplier.isFinite() &&
                script.appearance.lineSpacingMultiplier in ReaderAppearance.MIN_LINE_SPACING..ReaderAppearance.MAX_LINE_SPACING &&
                isTenthStep(script.appearance.lineSpacingMultiplier),
        ) {
            "lineSpacingMultiplier must be between 1.0 and 2.5 in 0.1 steps"
        }

        val seenIds = mutableSetOf(script.scriptId)
        var hasSpokenText = false
        script.sections.forEach { section ->
            require(isCanonicalUuid(section.id)) { "section id must be a canonical UUID" }
            require(seenIds.add(section.id)) { "IDs must be unique across the script" }
            section.blocks.forEach { block ->
                require(isCanonicalUuid(block.id)) { "block id must be a canonical UUID" }
                require(seenIds.add(block.id)) { "IDs must be unique across the script" }
                when (block) {
                    is ContentBlock.Paragraph -> {
                        require(block.runs.isNotEmpty()) { "A paragraph must contain at least one run" }
                        hasSpokenText = hasSpokenText || block.runs.any { it.text.isNotBlank() }
                    }
                    is ContentBlock.ListBlock -> {
                        require(block.items.isNotEmpty()) { "A list must contain at least one item" }
                        block.items.forEach { item ->
                            require(item.isNotEmpty()) { "A list item must contain at least one run" }
                            hasSpokenText = hasSpokenText || item.any { it.text.isNotBlank() }
                        }
                    }
                    is ContentBlock.Table -> {
                        require(block.headerCells.isNotEmpty()) { "A table must contain at least one header cell" }
                        require(block.rows.all { it.size == block.headerCells.size }) {
                            "Every table row must have the same number of cells as the header"
                        }
                        hasSpokenText = hasSpokenText ||
                            (block.headerCells.flatten() + block.rows.flatten().flatten()).any { it.text.isNotBlank() }
                    }
                    is ContentBlock.Cue -> require(block.label.isNotBlank()) { "A cue label must not be empty" }
                }
            }
        }
        require(hasSpokenText) { "A script must contain at least one non-cue text run" }
    }

    private fun isCanonicalUuid(value: String): Boolean = try {
        UUID.fromString(value).toString().equals(value, ignoreCase = true)
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun isArgb(value: String): Boolean = value.matches(Regex("#[0-9A-Fa-f]{8}"))

    private fun isTenthStep(value: Double): Boolean {
        val scaled = value * 10.0
        return abs(scaled - scaled.roundToInt()) < 1e-8
    }
}
