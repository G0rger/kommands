package com.github.g0rger.kommands.core

import kotlin.jvm.JvmSynthetic
import kotlin.reflect.KClass

interface IStringReader {
    val string: String
    val remainingLength: Int
    val totalLength: Int
    var cursor: Int
    val read: String
    val remaining: String

    fun readChar(): Char
    fun read(length: Int = 1): String
    fun canRead(length: Int = 1): Boolean
    fun peek(offset: Int = 0): Char
    fun skip(num: Int = 1)
    fun skipWhitespace()
    fun readUnquoted(): String
    fun readQuoted(): String
    fun readString(): String
}

class StringReader(override val string: String): IStringReader {
    companion object {
        private const val SYNTAX_ESCAPE       = '\\'
        private const val SYNTAX_DOUBLE_QUOTE = '"'
        private const val SYNTAX_SINGLE_QUOTE = '\''

        fun isQuotedStringStart(c: Char) = c == SYNTAX_DOUBLE_QUOTE || c == SYNTAX_SINGLE_QUOTE
        fun isAllowedInUnquotedString(c: Char) = c in '0'..'9' || c == '.' || c == '-' || c in 'A'..'Z' || c in 'a'..'z' || c == '_' || c == '+'
    }

    override var cursor: Int = 0

    override val remainingLength: Int get() = string.length - cursor
    override val totalLength: Int = string.length
    override val read: String get() = string.substring(0, cursor)
    override val remaining: String get() = string.substring(cursor)

    override fun readChar(): Char = string[cursor++]
    override fun read(length: Int): String {
        val start = cursor
        if (canRead(length))
            cursor += length
        else
            cursor = totalLength
        return string.substring(start, cursor)
    }
    override fun canRead(length: Int): Boolean = cursor + length <= string.length
    override fun peek(offset: Int): Char = string[cursor + offset]

    override fun skip(num: Int) { cursor += num }
    override fun skipWhitespace() { while(canRead() && peek().isWhitespace()) skip() }

    override fun readUnquoted(): String {
        val start = cursor
        while (canRead() && isAllowedInUnquotedString(peek()))
            skip()
        return string.substring(start, cursor)
    }

    override fun readQuoted(): String {
        if (!canRead())
            return ""
        val next = peek()
        require(isQuotedStringStart(next)) { "Unquoted String" }
        skip()
        return readUntil(next)
    }

    private fun readUntil(terminator: Char): String = buildString {
        while (canRead()) {
            when (val c = readChar()) {
                SYNTAX_ESCAPE -> {
                    val next = readChar()
                    require(next == terminator || next == SYNTAX_ESCAPE) { "Invalid escape" }
                    append(next)
                }
                terminator -> return@buildString
                else -> append(c)
            }
        }
        require(false) { "Unexpected end of quoted string" }
    }

    override fun readString(): String = buildString {
        skipWhitespace()
        while (canRead()) {
            val firstEscape = remaining.indexOfFirst { c -> isQuotedStringStart(c) }
            val firstWhitespace = remaining.indexOfFirst(Char::isWhitespace)
            if (firstEscape == -1 && firstWhitespace == -1) {
                append(read(remainingLength))
                return@buildString
            } else if (firstWhitespace == -1 || (firstEscape < firstWhitespace && firstEscape != -1)) {
                if (firstEscape > 0) {
                    append(read(firstEscape - 1))
                    skip()
                }
                if (peek(-1) == SYNTAX_ESCAPE) {
                    append(readChar())
                } else {
                    if (firstEscape > 0)
                        append(peek(-1))
                    append(readUntil(readChar()))
                }
            } else if (firstEscape == -1 || firstWhitespace < firstEscape) {
                append(read(firstWhitespace))
                return@buildString
            }
        }
    }
}

object ParserRegistry {
    @JvmSynthetic @PublishedApi
    internal val defaults: MutableMap<KClass<*>, Argument<*, *>> = mutableMapOf()

    init {
        default(StringArgument.PHRASE)
        default(BoolArgument)
        default(IntArgument)
        default(LongArgument)
        default(DoubleArgument)
        default(FloatArgument)
    }

    @Suppress("UNCHECKED_CAST") // This is safe as long as there is no reflection
    inline fun <reified T> get(): Argument<*, T>? = defaults[T::class] as Argument<*, T>?

    inline fun <reified T> default(default: Argument<*, T>) {
        defaults[T::class] = default
    }
}

abstract class Argument<S, out T> {
    abstract suspend fun parse(reader: StringReader, context: CommandContextBuilder<S>): T
}

abstract class StringArgument: Argument<Any?, String>() {
    @Suppress("unused")
    companion object {
        val WORD: StringArgument = object: StringArgument() {
            override suspend fun parse(reader: StringReader, context: CommandContextBuilder<Any?>): String = reader.readUnquoted()
        }
        val PHRASE: StringArgument = object: StringArgument() {
            override suspend fun parse(reader: StringReader, context: CommandContextBuilder<Any?>): String {
                val start = reader.cursor
                try {
                    return reader.readString()
                } catch (ex: IllegalArgumentException) {
                    reader.cursor = start
                    throw ParserException(ex)
                }
            }
        }
        val GREEDY: StringArgument = object: StringArgument() {
            override suspend fun parse(reader: StringReader, context: CommandContextBuilder<Any?>): String {
                val text = reader.remaining
                reader.cursor = reader.totalLength
                return text
            }
        }
    }
}

object BoolArgument: Argument<Any?, Boolean>() {
    override suspend fun parse(reader: StringReader, context: CommandContextBuilder<Any?>): Boolean {
        return reader.readUnquoted().toBoolean()
    }
}

object IntArgument: Argument<Any?, Int>() {
    override suspend fun parse(reader: StringReader, context: CommandContextBuilder<Any?>): Int {
        val start = reader.cursor
        try {
            return reader.readUnquoted().toInt()
        } catch (ex: NumberFormatException) {
            reader.cursor = start
            throw ParserException(ex)
        }
    }
}

object LongArgument: Argument<Any?, Long>() {
    override suspend fun parse(reader: StringReader, context: CommandContextBuilder<Any?>): Long {
        val start = reader.cursor
        try {
            return reader.readUnquoted().toLong()
        } catch (ex: NumberFormatException) {
            reader.cursor = start
            throw ParserException(ex)
        }
    }
}

object DoubleArgument: Argument<Any?, Double>() {
    override suspend fun parse(reader: StringReader, context: CommandContextBuilder<Any?>): Double {
        val start = reader.cursor
        try {
            return reader.readUnquoted().toDouble()
        } catch (ex: NumberFormatException) {
            reader.cursor = start
            throw ParserException(ex)
        }
    }
}

object FloatArgument: Argument<Any?, Float>() {
    override suspend fun parse(reader: StringReader, context: CommandContextBuilder<Any?>): Float {
        val start = reader.cursor
        try {
            return reader.readUnquoted().toFloat()
        } catch (ex: NumberFormatException) {
            reader.cursor = start
            throw ParserException(ex)
        }
    }
}
