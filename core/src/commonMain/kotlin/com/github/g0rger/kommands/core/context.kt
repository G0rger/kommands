package com.github.g0rger.kommands.core

import com.github.g0rger.kommands.core.StringRange.Companion.including
import kotlin.jvm.JvmSynthetic
import kotlin.math.max
import kotlin.math.min

class CommandContext<S>(val source: S,
                        private val input: String,
                        private val args: Map<Int, ParsedArgument<*>>,
                        internal val command: Command<S>?,
                        private val root: Node<S, *>,
                        private val nodes: List<ParsedNode<S>>,
                        private val range: StringRange,
                        internal val child: CommandContext<S>?,
                        internal val redirectModifier: RedirectModifier<S>?,
                        internal val forks: Boolean
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(arg: ArgumentContext<T>): T = args[arg.id]?.result as T

    internal val hasNodes get() = nodes.isNotEmpty()

    fun copyFor(source: S) = CommandContext(source, input, args, command, root, nodes, range, child, redirectModifier, forks)
}

data class ParsedArgument<T>(val range: StringRange, val result: T)

data class ParsedNode<S>(val node: NestedNode<S, *>?, val range: StringRange)

data class StringRange(val start: Int, val end: Int) {
    companion object {
        fun at(pos: Int) = StringRange(pos, pos)
        fun between(start: Int, end: Int) = StringRange(start, end)
        infix fun StringRange.including(b: StringRange) = StringRange(min(start, b.start), max(end, b.end))
    }
}

class CommandContextBuilder<S>(
    val source: S, start: Int,
    private val root: Node<S, *>
) {
    private val args: MutableMap<Int, ParsedArgument<*>> = mutableMapOf()
    private val nodes: MutableList<ParsedNode<S>> = mutableListOf()
    private var command: Command<S>? = null
    private var child: CommandContext<S>? = null
    private var range: StringRange = StringRange.at(start)
    private var modifier: RedirectModifier<S>? = null
    private var forks: Boolean = false

    fun withArgument(id: Int, arg: ParsedArgument<*>) {
        args[id] = arg
    }

    fun withNode(node: NestedNode<S, *>, range: StringRange) {
        nodes.add(ParsedNode(node, range))
        this.range = this.range including range
        this.modifier = node.redirectModifier
        this.forks = node.forks
        this.command = node.command
    }

    fun withChild(child: CommandContext<S>) {
        this.child = child
    }

    fun copy(): CommandContextBuilder<S> {
        val copy = CommandContextBuilder(source, range.start, root)
        copy.command = command
        copy.args.putAll(args)
        copy.nodes.addAll(nodes)
        copy.child = child
        copy.range = range
        copy.forks = forks
        return copy
    }

    fun build(input: String): CommandContext<S> {
        return CommandContext(source, input, args, command, root, nodes, range, child, modifier, forks)
    }
}

@Suppress("unused")
class ArgumentContext<T> private constructor(internal val id: Int) {
    companion object {
        @PublishedApi @JvmSynthetic
        internal operator fun <T> invoke(id: Int) = ArgumentContext<T>(id)
    }
}
