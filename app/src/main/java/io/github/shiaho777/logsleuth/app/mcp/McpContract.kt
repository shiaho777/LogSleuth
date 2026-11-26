package io.github.shiaho777.logsleuth.app.mcp

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
internal class McpContract @Inject constructor(@ApplicationContext context: Context) {
    private val schemas by lazy {
        val catalog = context.assets.open("tools.json").bufferedReader().use { JSONObject(it.readText()) }
        check(catalog.getInt("version") == MCP_VERSION)
        val tools = catalog.getJSONArray("tools")
        (0 until tools.length()).associate {
            val tool = tools.getJSONObject(it)
            tool.getString("name") to (tool.getJSONObject("inputSchema") to (tool.getString("kind") == "read"))
        }
    }

    fun validate(operation: String, arguments: JSONObject): Boolean {
        val (schema, readOnly) = schemas[operation] ?: refuse("UNKNOWN_TOOL", "Unknown LogSleuth tool.")
        checkValue(schema, arguments, "arguments")
        return readOnly
    }

    private fun checkValue(schema: JSONObject, value: Any, path: String) {
        fun invalid(): Nothing = refuse("INVALID_ARGUMENT", "Invalid value at $path.")
        when (schema.getString("type")) {
            "object" -> {
                if (value !is JSONObject) invalid()
                val properties = schema.getJSONObject("properties")
                val required = schema.getJSONArray("required")
                repeat(required.length()) { if (!value.has(required.getString(it))) invalid() }
                for (key in value.keys()) {
                    val property = properties.optJSONObject(key) ?: invalid()
                    checkValue(property, value.get(key), "$path.$key")
                }
            }
            "array" -> {
                if (value !is JSONArray || value.length() > schema.getInt("maxItems")) invalid()
                repeat(value.length()) { checkValue(schema.getJSONObject("items"), value.get(it), "$path[$it]") }
            }
            "string" -> {
                if (value !is String || value.length !in schema.optInt("minLength", 0)..schema.optInt("maxLength", Int.MAX_VALUE)) invalid()
                val allowed = schema.optJSONArray("enum")
                if (allowed != null && (0 until allowed.length()).none { allowed.getString(it) == value }) invalid()
                if (schema.has("pattern") && !Regex(schema.getString("pattern")).matches(value)) invalid()
            }
            "boolean" -> if (value !is Boolean) invalid()
            "integer", "number" -> {
                if (value !is Number) invalid()
                val number = value.toDouble()
                if (!number.isFinite() || number !in schema.getDouble("minimum")..schema.getDouble("maximum")) invalid()
                if (schema.getString("type") == "integer" && number % 1 != 0.0) invalid()
            }
            else -> invalid()
        }
    }
}

internal fun JSONObject.id(key: String): Long =
    getString(key).toLongOrNull()?.takeIf { it > 0 } ?: refuse("INVALID_ARGUMENT", "Invalid $key.")

internal fun JSONObject.optionalId(key: String): Long? = if (has(key)) id(key) else null
internal fun JSONObject.string(key: String): String? = if (has(key)) getString(key) else null
