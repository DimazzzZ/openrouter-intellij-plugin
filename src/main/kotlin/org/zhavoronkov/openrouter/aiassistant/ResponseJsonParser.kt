package org.zhavoronkov.openrouter.aiassistant

import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal object ResponseJsonParser {
    fun getAsJsonObjectOrNull(jsonObject: JsonObject, memberName: String): JsonObject? {
        return jsonObject.get(memberName)?.takeIf { it.isJsonObject }?.asJsonObject
    }

    fun getAsJsonArrayOrNull(jsonObject: JsonObject, memberName: String): JsonArray? {
        return jsonObject.get(memberName)?.takeIf { it.isJsonArray }?.asJsonArray
    }

    fun getAsStringOrNull(jsonObject: JsonObject, memberName: String): String? {
        return jsonObject.get(memberName)?.takeIf { it.isJsonPrimitive }?.asString
    }
}
