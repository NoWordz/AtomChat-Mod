package com.atom.chat.reaction;

/** One connection sees each receipt and its chat packet as an indivisible ordered submission. */
public final class ReactionTransport {
    private ReactionTransport() { }
    public static void sendPair(Object connection,Runnable receipt,Runnable message) {
        synchronized(connection) { receipt.run();message.run(); }
    }
}
