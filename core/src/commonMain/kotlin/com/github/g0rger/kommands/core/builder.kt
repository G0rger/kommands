@file:OptIn(ExperimentalContracts::class)

package com.github.g0rger.kommands.core

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmSynthetic
import kotlin.reflect.KClass

@DslMarker
private annotation class CommandsDSL

@PublishedApi @JvmSynthetic
internal var ID = 1

@CommandsDSL
sealed class NodeBuilder<S, N: Node<S, *>>(@PublishedApi @JvmSynthetic internal val id: Int) {
    companion object {
        @PublishedApi @JvmSynthetic
        internal val litIDs: MutableMap<Pair<Int, String>, Int> = mutableMapOf()
    }

    protected val literals: MutableMap<Int, LiteralNode<S>> = mutableMapOf()

    abstract fun build(): N

    @Suppress("unused")
    fun literal(literal: String, builder: LiteralNodeBuilder<S>.() -> Unit = {}): LiteralNode<S> {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val id = litIDs.getOrPut(Pair(id, literal)) { ID++ }
        val node = LiteralNodeBuilder<S>(id, literal).apply(builder).build()
        val other = literals[id]
        return if (other !== null) {
            other.mergeNode(node)
            other
        } else {
            literals[id] = node
            node
        }
    }
}

class RootNodeBuilder<S>: NodeBuilder<S, RootNode<S>>(0) {
    override fun build(): RootNode<S> {
        val root = RootNode<S>()
        for (node in literals.values) {
            root.addLiteral(node)
        }
        return root
    }
}

sealed class NestedNodeBuilder<S, N: NestedNode<S, *>>(id: Int): NodeBuilder<S, N>(id) {
    companion object {
        @PublishedApi @JvmSynthetic
        internal val argIDs: MutableMap<Triple<Int, String, KClass<*>>, Int> = mutableMapOf()
    }

    @PublishedApi @JvmSynthetic
    internal val arguments: MutableMap<Int, ArgumentNode<S, *>> = mutableMapOf()

    var command: Command<S>? = null
        protected set
    var requirement: Requirement<S>? = null
        protected set
    var redirect: Node<S, *>? = null
        protected set
    var redirectModifier: RedirectModifier<S>? = null
        protected set
    var forks: Boolean = false
        protected set

    @Suppress("unused")
    inline fun <reified T> argument(name: String, parser: Argument<*, T>? = null, builder: ArgumentNodeBuilder<S, T>.(ArgumentContext<T>) -> Unit = {}): ArgumentNode<S, T> {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        check(redirect == null) { "Cannot add children to a redirected node" }
        val id = argIDs.getOrPut(Triple(id, name, T::class)) { ID++ }
        val node = ArgumentNodeBuilder<S, T>(id, name, parser?: ParserRegistry.get()!!).apply {
            this.builder(ArgumentContext(id))
        }.build()
        val other = arguments[id]
        return if (other !== null) {
            @Suppress("UNCHECKED_CAST")
            (other as ArgumentNode<S, T>).mergeNode(node)
            other
        } else {
            arguments[id] = node
            node
        }
    }

    @Suppress("unused")
    fun executes(command: Command<S>) {
        this.command = command
    }

    @Suppress("unused")
    fun requires(requirement: Requirement<S>) {
        this.requirement = requirement
    }

    @Suppress("unused")
    fun redirect (target: Node<S, *>, modifier: SingleRedirectModifier<S>? = null) = forward(target, modifier?.let { m -> { o -> listOf(m(o))} }, false)

    @Suppress("unused")
    fun fork(target: Node<S, *>, modifier: RedirectModifier<S>?) = forward(target, modifier, true)

    private fun forward(target: Node<S, *>, modifier: RedirectModifier<S>?, forks: Boolean) {
        check(!(literals.isNotEmpty() || arguments.isNotEmpty())) { "Cannot redirect a node with children" }
        this.redirect = target
        this.redirectModifier = modifier
        this.forks = forks
    }
}

class LiteralNodeBuilder<S>(id: Int, private val literal: String): NestedNodeBuilder<S, LiteralNode<S>>(id) {
    override fun build(): LiteralNode<S> {
        val node = LiteralNode(literal, id, command, requirement, redirect?.id, redirectModifier, forks)
        for (literal in literals.values)
            node.addLiteral(literal)
        for (argument in arguments.values)
            node.addArgument(argument)
        return node
    }
}

class ArgumentNodeBuilder<S, T> @PublishedApi internal constructor(id: Int, private val name: String, private val parser: Argument<*, T>): NestedNodeBuilder<S, ArgumentNode<S, T>>
    (id) {
    override fun build(): ArgumentNode<S, T> {
        @Suppress("UNCHECKED_CAST")
        val node = ArgumentNode(name, id, parser as Argument<S, T>, command, requirement, redirect?.id, redirectModifier, forks)
        for (literal in literals.values)
            node.addLiteral(literal)
        for (argument in arguments.values)
            node.addArgument(argument)
        return node
    }
}
