package com.atom.chat.page;

import com.atom.chat.channel.ChannelInbox;
import com.atom.chat.channel.ChannelWire;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationChannelRowsTest {
    @Test void anAvailableChannelReplacesThePlayerEmptyNotice() {
        var publicRow = new ConversationListPage.Row(ConversationListPage.RowKind.PUBLIC, null, null, 0, true, false, null);
        var channel = new ChannelInbox.Row(new ChannelWire.Entry(new ChannelWire.Key("lobby", "team"), "Team"), 1);
        var channelRow = new ConversationListPage.Row(ConversationListPage.RowKind.CHANNEL, null, null, 1, true, false, channel);
        assertTrue(ConversationListPage.showEmptyPlayerNotice(List.of(publicRow)));
        assertFalse(ConversationListPage.showEmptyPlayerNotice(List.of(publicRow, channelRow)));
    }
}
