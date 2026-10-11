package com.atom.chat.chat;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.util.ImageFiles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.StringHelper;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;

/**
 * Outgoing messages: normalize, quote, route, echo-suppress, echo locally, and
 * clear the send state.
 *
 * <p>The order in here is the protocol. Three things must not move:
 * <ul>
 *   <li>the hardcoded quote prefix ({@code 「引用 @name: text」}) and the
 *       {@code /msg} template — both travel on the wire, so a byte is a
 *       compatibility break;</li>
 *   <li>{@link PublicEchoTracker#markSent} / {@link PrivateEchoTracker#markOutgoing}
 *       and the local echo into {@link ChatStore} / {@link PrivateChatStore}:
 *       the echo-suppression contract in the platform's chat-hud mixin is held
 *       up by those calls happening here, in this order, with these arguments;</li>
 *   <li>the clear chain at the end (field → reply slot → focus → scroll).</li>
 * </ul>
 *
 * <p>Cross-domain state comes in through {@link Host}: the screen owns the
 * client handle, the navigation stack, D1's reply slots, D21's composer and
 * D11's scroll controller, and this class must not reach for any of them
 * itself. Everything else it touches is shared or same-layer
 * ({@link ChatStore}, {@link PublicEchoTracker}, {@link LocalEcho},
 * {@link Cicodes}, …).
 *
 * <p>Both mappings: this file is the official-name copy and
 * {@code platforms/1.21.1-fabric/…/SendPipeline.java} is its yarn twin — the
 * bodies differ only in Minecraft names (sendChat/sendCommand, getChat/
 * getChatHud, StringUtil/StringHelper), never in what they do.
 */
public final class SendPipeline {
    /**
     * What the screen hands over: three facts and three actions, each one owned
     * by another domain (see the class comment). Nothing here is D3 state.
     */
    public interface Host {
        /**
         * The screen's client handle — deliberately not {@code MinecraftClient.getInstance()}:
         * the screen's field is null before {@code init()}, and the send paths keep
         * their exact behaviour only if they see the same null (and the same
         * exception) they saw before.
         */
        MinecraftClient client();

        /**
         * Top of the navigation stack (D24). Supplies both facts the send path
         * needs — whether the page is a private chat, and which partner it is —
         * from one read, exactly like {@code topPage()} + {@code activePrivateTarget()}
         * did.
         */
        NavPage topNav();

        /** D1's reply slots: the armed reply is read, then consumed, then synced. */
        ReplySlotStore replySlots();

        /** D21: empties the shared composer field. */
        void clearComposer();

        /** D21: marks the composer as focused, the way a send leaves it. */
        void focusComposer();

        /** D11: pins the list to the newest message after a send. */
        void stickToBottom();
    }

    private final Host host;

    public SendPipeline(Host host) {
        this.host = host;
    }

    /**
     * Sends whatever the composer holds: commands go as commands, chat goes as
     * chat (with a quote prefix when a reply is armed and an image URL wrapped
     * into a CICode), and a private page routes into {@link #sendPrivateMessage}.
     */
    public void sendMessage(String text) {
        String normalized = normalizeInput(text);
        if (normalized.isEmpty()) {
            return;
        }
        MinecraftClient client = host.client();
        if (client.player == null) {
            return;
        }
        String quoteName = null;
        String quoteText = null;
        // The typed body stays separate from the wire text: the quote prefix
        // travels with the message, but the local bubble must not show it.
        String body = normalized;
        ChatMessage reply = host.replySlots().replyTarget();
        if (reply != null) {
            quoteName = messageSenderName(reply);
            quoteText = quoteTextFor(reply);
            // Quote travels with the message so other players can see it too.
            normalized = "「引用 @" + quoteName + ": " + quoteText + "」" + body;
        }

        NavPage top = host.topNav();
        boolean privateChat = top.page() == AppPage.PRIVATE_CHAT;
        PlayerRef privateTarget = privateChat ? top.target() : null;
        if (privateChat) {
            sendPrivateMessage(normalized, body, privateTarget);
            return;
        }

        boolean command = normalized.startsWith("/");
        if (command) {
            client.player.networkHandler.sendChatCommand(normalized.substring(1));
        } else {
            if (!normalized.startsWith("「引用")
                    && (normalized.startsWith("http://") || normalized.startsWith("https://"))
                    && !normalized.contains("CICode")
                    && ImageFiles.isImageUrl(normalized)) {
                normalized = "[[CICode,url=" + normalized + ",name=图片]]";
            }
            client.player.networkHandler.sendChatMessage(normalized);
            // Arm echo suppression: the server relays this line back through the
            // identity-less path on NCR-style servers (e33chat parity).
            PublicEchoTracker.markSent(normalized);
        }
        client.inGameHud.getChatHud().addToMessageHistory(normalized);
        // Vanilla never echoes commands back into the chat feed as your own
        // message, so do not manufacture a local bubble for them either.
        // Non-command chat still gets an immediate local echo so the UI feels
        // like a phone messenger even before the server relays the message.
        if (!command) {
            UUID ownUuid = client.player.getUuid();
            String ownProfile = client.player.getName().getString();
            // Own bubbles show the decorated self name (titles/team prefix),
            // e33chat parity — the bare profile name stays for identity fields.
            ChatStore.get().add(LocalEcho.build(normalized, body, quoteName, quoteText,
                    ownUuid, ownProfile, OwnIdentity.displayNameRich()));
        }
        com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.SEND);
        host.clearComposer();
        host.replySlots().disarm();
        // The armed reply is consumed with the send: drop its slot too, or
        // navigating away and back would re-arm a reply that was already sent.
        syncReplySlot();
        host.focusComposer();
        host.stickToBottom();
    }

    /**
     * The private half: {@code /msg} to the partner, then the same local echo and
     * the same clear chain as the world path. Blocked partners are dropped here
     * (D3's third consumer of {@link BlockList}).
     */
    public void sendPrivateMessage(String normalized, String body, PlayerRef target) {
        ChatMessage reply = host.replySlots().replyTarget();
        if (target == null || BlockList.isBlocked(target)) {
            return;
        }
        MinecraftClient client = host.client();
        boolean command = normalized.startsWith("/");
        String historyText = normalized;
        String sendText = command ? normalized
                : "/msg " + target.realName() + " " + normalized;
        client.player.networkHandler.sendChatCommand(sendText.startsWith("/")
                ? sendText.substring(1) : sendText);
        client.inGameHud.getChatHud().addToMessageHistory(historyText);

        if (!command) {
            UUID ownUuid = client.player.getUuid();
            String ownProfile = client.player.getName().getString();
            // Decorated self name on the outgoing bubble, e33chat parity.
            PrivateChatStore.addOutgoing(target,
                    LocalEcho.build(historyText, body,
                            reply != null ? messageSenderName(reply) : null,
                            reply != null ? quoteTextFor(reply) : null,
                            ownUuid, ownProfile, OwnIdentity.displayNameRich()));
            PrivateEchoTracker.markOutgoing(target);
        }
        com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.SEND);
        host.clearComposer();
        host.replySlots().disarm();
        // Consumed with the send — see sendMessage's matching call.
        syncReplySlot();
        host.focusComposer();
        host.stickToBottom();
    }

    /**
     * Writes the armed reply back to the page on top of the stack. The stack is
     * read here rather than passed in, so this stays the same one-liner the
     * screen's own {@code syncReplySlot()} was.
     */
    private void syncReplySlot() {
        NavPage top = host.topNav();
        host.replySlots().syncReplySlot(top.page(), top);
    }

    /** Trims and collapses whitespace the way vanilla chat does. */
    public static String normalizeInput(String text) {
        return StringHelper.truncateChat(StringUtils.normalizeSpace(text.trim()));
    }

    /** Quote preview for a message: image messages become the "[image]" stand-in. */
    public static String quoteTextFor(ChatMessage message) {
        if (message == null) {
            return null;
        }
        String contentText = message.getContentText();
        if (Cicodes.extractImageUrl(message.getRawText()) != null
                || (contentText != null && Cicodes.extractImageUrl(contentText) != null)) {
            return tr("atomchat.hud.image");
        }
        return abbreviate(message.getContentText(), 30);
    }

    /**
     * Display name for a message row (quote labels and toasts). Own messages use
     * the local player; other messages prefer the structured sender name from the
     * capture pipeline and only fall back to the old hard-coded label when
     * identity is unavailable.
     */
    public String messageSenderName(ChatMessage msg) {
        if (msg.isOwn()) {
            return ownName();
        }
        String name = msg.getSenderName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return tr("atomchat.sender.player");
    }

    /** The local player's name, or the "(me)" stand-in outside a world. */
    public String ownName() {
        MinecraftClient client = host.client();
        return client.player != null ? client.player.getName().getString() : tr("atomchat.sender.me");
    }

    /** Head-truncates to {@code maxChars} with an ellipsis (quote labels). */
    public static String abbreviate(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "…";
    }

    /** Minecraft language lookup for the copy this class owns. */
    private static String tr(String key) {
        return Text.translatable(key).getString();
    }
}
