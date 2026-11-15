package com.atom.chat.reaction;

import java.util.UUID;
import java.util.function.Function;

/** Carries one broadcast invocation's identity without global or thread-local broadcast state. */
public final class ReactionBroadcastFunction<T,R> implements Function<T,R> {
    private final UUID id=UUID.randomUUID();
    private final Function<T,R> delegate;
    public ReactionBroadcastFunction(Function<T,R> delegate) { this.delegate=delegate; }
    public UUID id() { return id; }
    @Override public R apply(T receiver) { return delegate.apply(receiver); }
}
