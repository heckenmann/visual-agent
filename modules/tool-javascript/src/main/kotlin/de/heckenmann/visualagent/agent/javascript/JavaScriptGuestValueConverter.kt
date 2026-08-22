package de.heckenmann.visualagent.agent.javascript

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.graalvm.polyglot.Value

/** Bounds conversion of JavaScript values before they become host data. */
internal class JavaScriptGuestValueConverter(
    private val limits: JavaScriptExecutionLimits,
) {
    /** Convert a guest object into bounded JSON-compatible tool arguments. */
    fun toJsonObject(value: Value): JsonObject =
        toJsonObject(
            value,
            JavaScriptResultBudget(limits.maxToolArgumentCharacters, TOOL_ARGUMENT_LIMIT_MESSAGE),
        )

    /** Render bounded console values without stringifying arbitrary guest objects. */
    fun logText(
        arguments: Array<out Value>,
        maxCharacters: Int,
    ): String {
        val budget = JavaScriptResultBudget(maxCharacters, CONSOLE_LIMIT_MESSAGE)
        return arguments.take(MAX_LOG_ARGUMENTS).joinToString(" ") { value -> logValue(value, budget) }
    }

    private fun logValue(
        value: Value,
        budget: JavaScriptResultBudget,
    ): String {
        val text =
            when {
                value.isNull -> "null"
                value.isBoolean -> value.asBoolean().toString()
                value.isNumber -> value.asDouble().toString()
                value.isString -> value.asString()
                else -> "[${value.metaObject?.metaSimpleName ?: "value"}]"
            }
        val bounded = text.take(budget.remainingElements())
        budget.consume(bounded.length)
        return bounded
    }

    private fun toJsonObject(
        value: Value,
        budget: JavaScriptResultBudget,
    ): JsonObject {
        if (!value.hasMembers()) throw argumentsFailure("Tool arguments must be an object")
        return buildJsonObject {
            value.memberKeys.forEach { key ->
                budget.consume(key.length + 3)
                put(key, toJson(value.getMember(key), budget))
            }
        }
    }

    private fun toJson(
        value: Value,
        budget: JavaScriptResultBudget,
    ): JsonElement {
        if (value.isNull) return JsonNull
        if (value.isBoolean) return JsonPrimitive(value.asBoolean()).also { budget.consume(it.content.length) }
        if (value.isNumber) return JsonPrimitive(value.asDouble()).also { budget.consume(it.content.length) }
        if (value.isString) return JsonPrimitive(value.asString()).also { budget.consume(it.content.length) }
        if (value.hasArrayElements()) {
            val arraySize = value.arraySize
            if (arraySize > budget.remainingElements().toLong()) {
                throw JavaScriptExecutionException(JavaScriptErrorCategory.LIMIT_EXCEEDED, TOOL_ARGUMENT_LIMIT_MESSAGE)
            }
            return buildJsonArray {
                repeat(arraySize.toInt()) { index ->
                    budget.consume(1)
                    add(toJson(value.getArrayElement(index.toLong()), budget))
                }
            }
        }
        if (value.hasMembers()) return toJsonObject(value, budget)
        throw argumentsFailure("Tool arguments must contain JSON-compatible values")
    }

    private fun argumentsFailure(message: String): JavaScriptExecutionException =
        JavaScriptExecutionException(JavaScriptErrorCategory.TOOL_ARGUMENTS, message.take(MAX_ERROR_CHARACTERS))

    private companion object {
        const val CONSOLE_LIMIT_MESSAGE = "JavaScript console output limit exceeded"
        const val MAX_ERROR_CHARACTERS = 500
        const val MAX_LOG_ARGUMENTS = 16
        const val TOOL_ARGUMENT_LIMIT_MESSAGE = "JavaScript tool arguments size limit exceeded"
    }
}
