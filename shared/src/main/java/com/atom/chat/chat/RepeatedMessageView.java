package com.atom.chat.chat;

import com.atom.chat.reaction.ReactionClient;

import java.util.ArrayList;
import java.util.List;

/** Display-only collapse for messages whose separate delivery IDs must stay in the store. */
public final class RepeatedMessageView {
    private List<ChatMessage> source;
    private List<ChatMessage> displayed;
    private long reactionRevision;

    public List<ChatMessage> rows(List<ChatMessage> messages) {
        if (!MessageMerge.antiSpamEnabled() || messages.size() < 2) return messages;
        long revision = ReactionClient.revision();
        if (source == messages && reactionRevision == revision) return displayed;

        ArrayList<ChatMessage> result = null;
        ChatMessage previous = messages.get(0);
        for (int i = 1; i < messages.size(); i++) {
            ChatMessage row = messages.get(i);
            if (MessageMerge.canMergeForDisplay(previous, row)) {
                if (result == null) result = new ArrayList<>(messages.subList(0, i));
                int last = result.size() - 1;
                result.set(last, MessageMerge.mergeForDisplay(result.get(last), row));
            } else if (result != null) {
                result.add(row);
            }
            previous = row;
        }

        source = messages;
        displayed = result == null ? messages : List.copyOf(result);
        reactionRevision = revision;
        return displayed;
    }
}
