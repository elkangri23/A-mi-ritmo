package com.teleprompter.phone.storage

import android.content.Context
import com.teleprompter.shared.position.PositionAnchor
import org.json.JSONObject

/** Stores only local text anchors, never document text or screen coordinates. */
class ReadingPositionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(scriptId: String): PositionAnchor? {
        val encoded = preferences.getString(keyFor(scriptId), null) ?: return null
        return runCatching {
            val json = JSONObject(encoded)
            PositionAnchor(
                scriptId = json.getString(FIELD_SCRIPT_ID),
                sectionId = json.getString(FIELD_SECTION_ID),
                blockId = json.getString(FIELD_BLOCK_ID),
                textOffset = json.getInt(FIELD_TEXT_OFFSET),
                surroundingText = json.optString(FIELD_SURROUNDING_TEXT),
            ).takeIf { it.scriptId == scriptId }
        }.getOrNull()
    }

    fun save(anchor: PositionAnchor) {
        val encoded = JSONObject()
            .put(FIELD_SCRIPT_ID, anchor.scriptId)
            .put(FIELD_SECTION_ID, anchor.sectionId)
            .put(FIELD_BLOCK_ID, anchor.blockId)
            .put(FIELD_TEXT_OFFSET, anchor.textOffset)
            .put(FIELD_SURROUNDING_TEXT, anchor.surroundingText)
            .toString()
        preferences.edit().putString(keyFor(anchor.scriptId), encoded).apply()
    }

    fun delete(scriptId: String) {
        preferences.edit().remove(keyFor(scriptId)).apply()
    }

    private fun keyFor(scriptId: String): String = "$POSITION_KEY_PREFIX$scriptId"

    private companion object {
        const val PREFERENCES_NAME = "reading_positions"
        const val POSITION_KEY_PREFIX = "position_"
        const val FIELD_SCRIPT_ID = "scriptId"
        const val FIELD_SECTION_ID = "sectionId"
        const val FIELD_BLOCK_ID = "blockId"
        const val FIELD_TEXT_OFFSET = "textOffset"
        const val FIELD_SURROUNDING_TEXT = "surroundingText"
    }
}
