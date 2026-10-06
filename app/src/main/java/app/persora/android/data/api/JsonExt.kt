package app.persora.android.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Defensive accessors so the Android client is tolerant of the same loosely typed rows cloud.ts handles. */
internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()
internal fun JsonElement?.str(default: String = ""): String = when (this) {
    null, JsonNull -> default
    is JsonPrimitive -> if (isString) content else contentOrNull ?: default
    else -> default
}
internal fun JsonElement?.strOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
internal fun JsonElement?.bool(default: Boolean = false): Boolean = (this as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.equals("true", true) } ?: default
internal fun JsonElement?.long(default: Long = 0): Long = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: default
internal fun JsonElement?.double(default: Double = 0.0): Double = (this as? JsonPrimitive)?.doubleOrNull ?: default
internal fun JsonElement?.int(default: Int = 0): Int = long(default.toLong()).toInt()
internal operator fun JsonObject?.get(key: String): JsonElement? = this?.get(key)
internal fun JsonObject?.stringMap(key: String): Map<String, String> =
    this.get(key).obj()?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.let { p -> k to (p.contentOrNull ?: "") } }?.toMap() ?: emptyMap()
