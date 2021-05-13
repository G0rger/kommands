package com.github.g0rger.kommands.core

typealias Command<S> = suspend (CommandContext<S>) -> Int
typealias Requirement<S> = suspend (S) -> Boolean
typealias RedirectModifier<S> = (CommandContext<S>) -> Collection<S>
typealias SingleRedirectModifier<S> = (CommandContext<S>) -> S
