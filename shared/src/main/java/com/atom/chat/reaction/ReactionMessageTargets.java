package com.atom.chat.reaction;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

/** A command invocation owns this iterator; each target gets its own paired outgoing/incoming ID. */
public final class ReactionMessageTargets<T> extends AbstractCollection<T> {
    private final Collection<T> targets;
    private UUID currentId;
    public ReactionMessageTargets(Collection<T> targets) { this.targets=targets; }
    public UUID currentId() { return currentId; }
    @Override public int size() { return targets.size(); }
    @Override public Iterator<T> iterator() {
        Iterator<T> iterator=targets.iterator();
        return new Iterator<>() {
            public boolean hasNext() { return iterator.hasNext(); }
            public T next() { T target=iterator.next(); currentId=UUID.randomUUID(); return target; }
        };
    }
}
