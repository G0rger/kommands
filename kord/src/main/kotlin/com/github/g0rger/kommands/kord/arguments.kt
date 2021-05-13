@file:Suppress("unused")

package com.github.g0rger.kommands.kord

import com.github.g0rger.kommands.core.*
import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.Kord
import com.gitlab.kordlib.core.entity.Member
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.exception.EntityNotFoundException

class MemberArgument(val kord: Kord): Argument<Message, Member>() {
    init {
        ParserRegistry.default(this)
    }

    override suspend fun parse(reader: StringReader, context: CommandContextBuilder<Message>): Member {
        val start = reader.cursor
        try {
            check(reader.read(3) == "<@!") { "Misformed mention" }
            val id: Snowflake = try {
                Snowflake(reader.readUnquoted())
            } catch (ex: NumberFormatException) {
                reader.cursor = start
                throw ParserException(ex)
            }
            val member = try {
                context.source.getGuild().getMember(id)
            } catch (ex: EntityNotFoundException) {
                reader.cursor = start
                throw ParserException(ex)
            }
            check(reader.readChar() == '>') { "Misformed mention" }
            return member
        } catch (ex: IllegalStateException) {
            reader.cursor = start
            throw ParserException(ex)
        }
    }
}
