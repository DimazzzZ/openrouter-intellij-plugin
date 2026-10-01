package org.zhavoronkov.openrouter.utils

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** This element as an object, or null when it is anything else. */
fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

/** This element as a string, or null when it is not a string primitive. */
fun JsonElement.asStringOrNull(): String? = takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

/** This element as a boolean, or null when it is not a boolean primitive. */
fun JsonElement.asBooleanOrNull(): Boolean? = takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
