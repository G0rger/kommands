package com.github.g0rger.kommands.core

class Dispatcher<S>(private val root: RootNode<S>) {
    private suspend fun parseRoot(start: Int = 0, reader: StringReader, source: S): CommandContext<S>? {
        reader.cursor = start
        val literal = reader.readString()
        val node = root.literals[literal] as NestedNode<S, *>?
        require(node != null && node.canUse(source)) { "Unknown command $literal" }
        val context = CommandContextBuilder(source, start, root)
        context.withNode(node, StringRange(start, reader.cursor))
        reader.skipWhitespace()
        return parseNodes(node, context, reader.cursor, reader, source)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun parseNodes(startNode: NestedNode<S, *>, empty: CommandContextBuilder<S>, start: Int, reader: StringReader, source: S): CommandContext<S>? {
        val contexts: MutableList<Triple<NestedNode<S, *>, CommandContextBuilder<S>, Int>> = mutableListOf(Triple(startNode, empty, start))

        while (contexts.isNotEmpty()) {
            val (node, context, cursor) = contexts.removeLast()
            reader.cursor = cursor

            if (!reader.canRead())
                return context.build(reader.string)

            val redirect = node.redirect
            if (redirect != null) {
                val child = if (redirect is NestedNode<S, *>) {
                    parseNodes(redirect, CommandContextBuilder(source, reader.cursor, redirect), reader.cursor, reader, source)
                } else {
                    parseRoot(reader.cursor, reader, source)
                }
                if (child != null) {
                    context.withChild(child)
                    return context.build(reader.string)
                }
                continue
            }

            val literal = node.literals[reader.readString()]
            if (literal != null && literal.canUse(source)) {
                context.withNode(literal, StringRange.between(cursor, reader.cursor))
                reader.skipWhitespace()
                contexts.add(Triple(literal, context, reader.cursor))
                continue
            }
            for (arg in node.arguments.values.reversed().filter { it.canUse(source) }) {
                reader.cursor = cursor
                val parsed = try {
                    arg.argument.parse(reader, context)
                } catch (ex: ParserException) {
                    continue
                }

                if (reader.canRead() && !reader.peek().isWhitespace())
                    continue

                val c = context.copy()
                val range = StringRange.between(cursor, reader.cursor)
                c.withNode(arg, range)
                c.withArgument(arg.id, ParsedArgument(range, parsed))
                reader.skipWhitespace()
                contexts.add(Triple(arg, c, reader.cursor))
            }
        }
        return null
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("UNUSED")
    suspend fun dispatch(input: String, source: S): Int {
        val contexts = run {
            val reader = StringReader(input)
            val context = parseRoot(0, reader, source)
            checkNotNull(context) { "Invalid command: $input" }
            mutableListOf(context)
        }

        var forked = false
        var foundCommand = false
        var result = 0
        var forks = 0

        while (contexts.isNotEmpty()) {
            val context = contexts.removeLast()
            val child = context.child
            if (child != null) {
                forked = forked || context.forks
                if (child.hasNodes) {
                    val modifier = context.redirectModifier
                    if (modifier == null) {
                        contexts.add(child.copyFor(context.source))
                    } else {
                        try {
                            contexts.addAll(modifier(context).map { child.copyFor(it) })
                        } catch (ex: Exception) {
                            if (!forked)
                                throw ex
                        }
                    }
                }
            } else if (context.command != null) {
                foundCommand = true
                try {
                    result += (context.command)(context)
                    forks++
                } catch (ex: Exception) {
                    if (!forked)
                        throw ex
                }
            }
        }

        check(foundCommand) { "Unknown command" }

        return if (forked) forks else result
    }
}
