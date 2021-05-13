package com.github.g0rger.kommands.kord

import com.github.g0rger.kommands.core.Dispatcher
import com.github.g0rger.kommands.core.RootNodeBuilder
import com.gitlab.kordlib.core.Kord
import com.gitlab.kordlib.core.entity.Message
import com.gitlab.kordlib.core.event.message.MessageCreateEvent
import com.gitlab.kordlib.core.on

@Suppress("unused", "MemberVisibilityCanBePrivate")
object Kommands {
    private val root = RootNodeBuilder<Message>().build()
    private val dispatcher = Dispatcher(root)
    private var first = true

    private var prefix: String? = null

    suspend fun Kord.commands(builder: RootNodeBuilder<Message>.() -> Unit) {
        root.mergeNode(RootNodeBuilder<Message>().apply(builder).build())
        if (prefix == null)
            prefix = "<@!${this.getSelf().id.longValue}> "
        if (first) {
            first = false
            MemberArgument(this)
            on<MessageCreateEvent> {
                if (message.author == this@commands.getSelf() || member == null)
                    return@on

                if (!message.content.startsWith(prefix!!))
                    return@on

                dispatcher.dispatch(message.content.substring(prefix!!.length), message)
            }
        }
    }
}
