package com.github.g0rger.kommands.core

sealed class Node<S, N: Node<S, N>> {
    internal val literals: MutableMap<String, LiteralNode<S>> = mutableMapOf()

    abstract val usage: String
    internal abstract val id: Int

    abstract fun mergeNode(other: N)
    abstract suspend fun canUse(source: S): Boolean

    fun addLiteral(child: LiteralNode<S>) {
        val other = literals[child.literal]
        if (other !== null)
            other.mergeNode(child)
        else
            literals[child.literal] = child
    }

    override fun toString(): String {
        return "Node(id=$id)"
    }
}

class RootNode<S>: Node<S, RootNode<S>>() {
    override val usage: String = ""
    override val id: Int = 0

    override fun mergeNode(other: RootNode<S>) {
        for (literal in other.literals.values)
            addLiteral(literal)
    }

    override suspend fun canUse(source: S): Boolean = true

    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("UNUSED")
    fun resolveRedirects() {
        val nodeMap: MutableMap<Int, Node<S, *>> = mutableMapOf(0 to this)
        val nodes: MutableList<NestedNode<S, *>> = mutableListOf()
        nodes.addAll(literals.values)
        while (nodes.isNotEmpty()) {
            val node = nodes.removeLast()
            nodeMap[node.id] = node
            nodes.addAll(node.literals.values)
            nodes.addAll(node.arguments.values)
        }
        for (node in nodeMap.values)
            if (node is NestedNode<S, *>)
                node.redirect = nodeMap[node.redirectID]
    }

    override fun toString(): String {
        return "RootNode(id=$id)"
    }
}

sealed class NestedNode<S, N: NestedNode<S, N>>(
    command: Command<S>?,
    requirement: Requirement<S>?,
    internal val redirectID: Int?,
    val redirectModifier: RedirectModifier<S>?,
    val forks: Boolean,
    override val id: Int
): Node<S, N>() {
    var command: Command<S>? = command
        internal set
    var requirement: Requirement<S>? = requirement
        internal set
    var redirect: Node<S, *>? = null
        internal set

    internal val arguments: MutableMap<Int, ArgumentNode<S, *>> = mutableMapOf()

    internal fun addArgument(child: ArgumentNode<S, *>) {
        val other = arguments[child.id]
        if (other !== null) {
            other.mergeNode(child)
        } else {
            arguments[child.id] = child
        }
    }

    override fun mergeNode(other: N) {
        if (other.command != null)
            this.command = other.command
        if (other.requirement !== null)
            this.requirement = other.requirement
        for (literal in other.literals.values)
            addLiteral(literal)
        for (argument in other.arguments.values)
            addArgument(argument)
    }

    override fun toString(): String {
        return "NestedNode(redirectID=$redirectID, forks=$forks, id=$id)"
    }
}

class LiteralNode<S>(val literal: String, id: Int,
                     command: Command<S>?,
                     requirement: Requirement<S>?,
                     redirectID: Int?,
                     redirectModifier: RedirectModifier<S>?,
                     forks: Boolean
): NestedNode<S, LiteralNode<S>>(command, requirement, redirectID, redirectModifier, forks, id) {
    override val usage: String = literal

    override suspend fun canUse(source: S): Boolean = requirement?.let { it(source) }?: true
    override fun toString(): String {
        return "LiteralNode(literal=$literal) ${super.toString()}"
    }
}

class ArgumentNode<S, T>(name: String, id: Int, internal val argument: Argument<S, T>,
                         command: Command<S>?,
                         requirement: Requirement<S>?,
                         redirectID: Int?,
                         redirectModifier: RedirectModifier<S>?,
                         forks: Boolean
): NestedNode<S, ArgumentNode<S, *>>(command, requirement, redirectID, redirectModifier, forks, id) {
    override val usage: String = "<$name>"

    override suspend fun canUse(source: S): Boolean = requirement?.let { it(source) }?: true
    override fun toString(): String {
        return "ArgumentNode() ${super.toString()}"
    }
}
