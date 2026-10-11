package com.atom.chat.mixin;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.ChatClassifier;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatPipeline;
import com.atom.chat.chat.ChatStore;
import com.atom.chat.chat.ChatTemplates;
import com.atom.chat.chat.EasyBotParser;
import com.atom.chat.chat.MentionDetector;
import com.atom.chat.chat.MentionObserver;
import com.atom.chat.chat.MessageCapture;
import com.atom.chat.chat.OwnIdentity;
import com.atom.chat.chat.OwnNameMatcher;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.chat.PrivateChatParser;
import com.atom.chat.chat.PrivateEchoTracker;
import com.atom.chat.chat.PublicEchoTracker;
import com.atom.chat.chat.QuoteParser;
import com.atom.chat.chat.PrivateChatStore;
import com.atom.chat.chat.SeenPlayers;
import com.atom.chat.chat.SenderMeta;
import com.atom.chat.chat.TeleportCommands;
import com.atom.chat.chat.TellClickDetector;
import com.atom.chat.chat.WhisperTextParser;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.notification.NotificationController;
import com.atom.chat.text.ChatTextRewriter;
import com.atom.chat.text.RichText;
import com.atom.chat.screen.AtomChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = ChatComponent.class, priority = 500)
public class ChatHudMixin {
    @Unique
    private boolean atomchat$reposting;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void atomchat$hideVanillaChatHud(GuiGraphics context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (atomchat$panelOwnsInput()) {
            ci.cancel();
        }
    }

    /** True while the AtomChat panel is the open screen. */
    @Unique
    private static boolean atomchat$panelOwnsInput() {
        return Minecraft.getInstance().screen instanceof AtomChatScreen;
    }

    /*
     * Hiding the vanilla HUD is not enough for input: ChatScreen still counts
     * this screen as "chat focused" (ChatComponent#isChatFocused is a bare
     * `screen instanceof ChatScreen` check) and keeps hit-testing the invisible
     * chat lines that sit underneath the panel. A click in the lower part of the
     * panel therefore reached a hidden line: its ClickEvent could fire (open a
     * link, suggest a tell command) or the click was swallowed to flush
     * unprocessed messages, and a wheel over a non-list part scrolled the hidden
     * history. All three seams are muted while our screen owns the mouse.
     * ChatScreen's other two click steps are untouched on purpose: the chat field
     * IS the AtomChat composer (init() lays the vanilla EditBox out inside the
     * panel) and the suggestor is AtomChat's own.
     */

    @Inject(method = "handleChatQueueClicked(DD)Z", at = @At("HEAD"), cancellable = true)
    private void atomchat$muteHiddenChatClick(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (atomchat$panelOwnsInput()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getClickedComponentStyleAt(DD)Lnet/minecraft/network/chat/Style;", at = @At("HEAD"), cancellable = true)
    private void atomchat$muteHiddenChatStyle(double mouseX, double mouseY, CallbackInfoReturnable<Style> cir) {
        if (atomchat$panelOwnsInput()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "scrollChat(I)V", at = @At("HEAD"), cancellable = true)
    private void atomchat$muteHiddenChatScroll(int amount, CallbackInfo ci) {
        if (atomchat$panelOwnsInput()) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void atomchat$captureMessage(Component message, MessageSignature signatureData, GuiMessageTag indicator, CallbackInfo ci) {
        if (atomchat$reposting) {
            return;
        }

        // Store the original message first; the vanilla HUD copy is rewritten
        // below only after capture has already decided what belongs in ChatStore.
        atomchat$captureAndStore(message, signatureData, indicator);

        Minecraft client = Minecraft.getInstance();
        String ownName = client.player != null ? client.player.getName().getString() : null;
        Component rewritten = ChatTextRewriter.rewritePrivate(message, ownName);
        if (rewritten != null) {
            // Keep compacting image codes / quote prefixes inside the rewritten
            // private line (e.g. "<name>[Whisper] [Quote] body").
            Component compacted = ChatTextRewriter.rewrite(rewritten);
            if (compacted != null) {
                rewritten = compacted;
            }
        } else {
            rewritten = ChatTextRewriter.rewrite(message);
        }
        if (rewritten != null) {
            ci.cancel();
            atomchat$reposting = true;
            try {
                ((ChatComponent) (Object) this).addMessage(rewritten, signatureData, indicator);
            } finally {
                atomchat$reposting = false;
            }
        }
    }

    @Unique
    private void atomchat$captureAndStore(Component message, MessageSignature signatureData, GuiMessageTag indicator) {
        String raw = message.getString();
        Minecraft client = Minecraft.getInstance();
        // Teleport failure watch: a "unknown command/no permission" reply right
        // after a menu teleport flips the session command (0.1.11 auto mode).
        TeleportCommands.checkFailure(raw);

        SenderMeta meta = MessageCapture.consume();
        if (AtomChatConfig.get().debug) {
            // Whether the channel-level capture landed at all: a missing meta is
            // why an own line falls through to the text heuristics (2026-09-11).
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("capture", "[capture] meta={} line={}"), meta != null, raw);
        }
        // Machine-to-machine chat protocols must never be claimed as player chat
        // or suppressed as an own echo. e33chat routes them to the system channel
        // ("宁可不杀不可错杀"): when in doubt, let the line through.
        if (ChatClassifier.isXaeroWaypointData(raw)
                || (meta != null && meta.contentText() != null
                && ChatClassifier.isXaeroWaypointData(meta.contentText()))) {
            addSystemMessage(message);
            return;
        }
        if (meta != null && meta.whisper()) {
            atomchat$routePrivate(message, meta, client);
            return;
        }
        if (meta == null) {
            // No channel-level identity. Private /msg lines are deterministic by
            // translation key even when the MessageHandler capture missed them.
            SenderMeta fallbackPrivate = PrivateChatParser.tryParse(message);
            if (fallbackPrivate != null) {
                atomchat$routePrivate(message, fallbackPrivate, client);
                return;
            }
            // No channel-level identity: translation-key system lines are
            // authoritative and must stay system even if their rendered text
            // happens to look like a player line. Other routes may only claim
            // a player when the text parser resolves the line to an online
            // player; otherwise they are system messages.
            if (ChatClassifier.classifyByKey(message) == ChatClassifier.Route.SYSTEM) {
                addSystemMessage(message);
                return;
            }
            // Component-layer whisper fallback (plugin-reformatted /msg, bot relays).
            // Must run BEFORE the player-line guard: its separator skipping
            // would mis-claim "[Steve -> me] hi" as a public bubble from Steve.
            if (atomchat$tryTextWhisper(raw, message, client)) {
                return;
            }
            // Layer 2 (e33chat parity): tell-click attribution. A SUGGEST_COMMAND
            // "/tell <name>" on the sender's display name carries the real
            // profile name — deterministic on nickname servers.
            SenderMeta parsed = TellClickDetector.detectByTellClick(message, raw);
            if (parsed == null) {
                parsed = ChatPipeline.tryParsePlayerLine(raw);
            }
            if (parsed == null) {
                // EasyBot QQ-relay shapes ("<nick(123)> hi", "[群] <nick> hi")
                // with no locally known player name behind them.
                parsed = EasyBotParser.tryParse(raw);
            }
            if (parsed == null) {
                // Guard failed: user-configured templates get the last claim
                // before the line degrades to a gray system bubble.
                parsed = atomchat$tryChatTemplate(raw);
            }
            if (parsed == null) {
                atomchat$logParseMiss(raw);
                addSystemMessage(message);
                return;
            }
            // Deliberately no own-echo suppression here. This branch has no
            // channel-level identity, so parsing the line as "looks like me" is
            // not proof it is our own echo — mods (Xaero path analysis, server
            // waypoints, etc.) can emit own-name-shaped system lines. Suppressing
            // them makes real messages vanish from the panel; showing a possible
            // duplicate is the safer failure per e33chat's "宁可不杀" rule.
            String displayName = parsed.senderName() != null ? parsed.senderName() : parsed.profileName();
            if (AtomChatConfig.get().hideBlockedMessages
                    && displayName != null && BlockList.isBlocked(displayName)) {
                return;
            }
            String parsedContent = parsed.contentText() != null ? parsed.contentText() : raw;
            QuoteParser.Quote quote = atomchat$quoteOf(parsedContent);
            String body = quote != null ? quote.body() : parsedContent;
            var sliced = ChatPipeline.sliceRichText(message, parsed);
            RichText senderRichParsed = sliced.isPresent()
                    ? atomchat$ensureSenderColor(sliced.get().sender(), parsed.profileName())
                    : atomchat$ensureSenderColor(
                            displayName != null ? RichText.literal(displayName) : RichText.empty(),
                            parsed.profileName());
            // Own echo on this identity-less path (NCR-style relays). Two
            // independent signals, either one is enough:
            //  - the wire name is one of our own renderings (team prefix/suffix
            //    included), or
            //  - the text is exactly a message we just sent and no other player is
            //    named in the line (e33chat's EchoTracker contract: content first,
            //    never a time-only guess).
            boolean ownIdentity = atomchat$isOwnIdentity(parsed, client);
            boolean ownEchoText = false;
            if (!ownIdentity && !atomchat$otherPlayerNamed(raw, client)) {
                ownEchoText = PublicEchoTracker.consumeIfEcho(body, raw);
            }
            if (ownIdentity || ownEchoText) {
                // The local bubble already exists; only keep the server-decorated
                // label as the self-name cache source (e33chat parity).
                if (AtomChatConfig.get().debug) {
                    AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("echo", "[echo] own line dropped (identity={} text={}) name={} line={}"),
                            ownIdentity, ownEchoText, displayName, raw);
                }
                OwnIdentity.cache(senderRichParsed);
                return;
            }
            if (sliced.isPresent()) {
                RichText richContent = quote != null
                        ? ChatPipeline.quoteBodyRich(sliced.get().content(), body)
                        : sliced.get().content().linkifyUrls();
                atomchat$addPublic(new ChatMessage(message, false, parsed.system(),
                        quote != null ? quote.quoteName() : null,
                        quote != null ? quote.quoteText() : null,
                        parsed.senderUuid(), displayName, parsed.profileName(), body,
                        senderRichParsed, richContent), body, parsed.system());
            } else {
                RichText richContent = quote != null
                        ? ChatPipeline.quoteBodyRich(RichText.of(message), body)
                        : RichText.literal(body).linkifyUrls();
                atomchat$addPublic(new ChatMessage(message, false, parsed.system(),
                        quote != null ? quote.quoteName() : null,
                        quote != null ? quote.quoteText() : null,
                        parsed.senderUuid(), displayName, parsed.profileName(), body,
                        senderRichParsed, richContent), body, parsed.system());
            }
            SeenPlayers.remember(parsed.senderUuid(), parsed.profileName(), displayName);
            return;
        }

        String content = ChatPipeline.extractContent(raw, meta);
        boolean own = isOwn(meta, raw, client);
        boolean ownEchoText = false;
        if (!own && !atomchat$otherPlayerNamed(raw, client)) {
            // Identity-less relay of our own line: the decorated wire name is not
            // proof, the text we just sent is (e33chat parity).
            ownEchoText = PublicEchoTracker.consumeIfEcho(content, raw);
            own = ownEchoText;
        }
        if (AtomChatConfig.get().debug) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("route", "[route] own={} textEcho={} uuid={} sender={} profile={} text={}"),
                    own, ownEchoText, meta.senderUuid(), meta.senderName(), meta.profileName(), raw);
        }
        if (own) {
            // Own message echo: already added locally by AtomChatScreen. The
            // server-decorated component is the best self-name source — cache
            // it so the local echo bubbles can show "[Title]Name" too. NCR-style
            // relays arrive as system-channel lines parsed from text only, so
            // meta has no sender component: slice the styled label off the
            // final HUD line exactly like the meta==null branch above.
            RichText decorated = meta.senderComponent() != null
                    ? RichText.of(meta.senderComponent()) : null;
            if (decorated == null) {
                var sliced = ChatPipeline.sliceRichText(message, meta);
                if (sliced.isPresent()) {
                    decorated = sliced.get().sender();
                }
            }
            OwnIdentity.cache(decorated != null ? decorated.stripInteractions() : null);
            return;
        }
        String blockName = meta.profileName() != null ? meta.profileName() : meta.senderName();
        if (AtomChatConfig.get().hideBlockedMessages
                && blockName != null && BlockList.isBlocked(blockName)) {
            return;
        }

        String displayName = ChatPipeline.decoratedDisplayName(raw, meta);
        if (displayName == null) {
            displayName = meta.senderName();
        }
        RichText senderRich;
        RichText contentRich;
        if (meta.senderComponent() == null && meta.contentComponent() == null) {
            var sliced = ChatPipeline.sliceRichText(message, meta);
            if (sliced.isPresent()) {
                senderRich = sliced.get().sender();
                contentRich = sliced.get().content().linkifyUrls();
            } else {
                senderRich = RichText.empty();
                contentRich = RichText.literal(content);
            }
            SeenPlayers.remember(meta.senderUuid(),
                    meta.profileName() != null ? meta.profileName() : displayName, displayName);
        } else {
            senderRich = meta.senderComponent() != null
                    ? RichText.of(meta.senderComponent())
                    : RichText.empty();
            contentRich = meta.contentComponent() != null
                    ? RichText.of(meta.contentComponent()).linkifyUrls()
                    : RichText.literal(content);
        }
        QuoteParser.Quote quote = atomchat$quoteOf(content);
        String body = quote != null ? quote.body() : content;
        if (quote != null) {
            contentRich = ChatPipeline.quoteBodyRich(contentRich, body);
        }
        senderRich = atomchat$ensureSenderColor(senderRich, meta.profileName());
        atomchat$addPublic(new ChatMessage(message, false, meta.system(),
                quote != null ? quote.quoteName() : null,
                quote != null ? quote.quoteText() : null,
                meta.senderUuid(), displayName, meta.profileName(), body,
                senderRich, contentRich), body, meta.system());
    }

    /**
     * Public-channel entry + @-mention bookkeeping (0.1.11): the mention check
     * uses the final body text and fires the observer seam; unread counters
     * live in ChatStore. System capsules never count as mentions.
     */
    @Unique
    private static void atomchat$addPublic(ChatMessage message, String body, boolean system) {
        boolean merged = ChatStore.get().add(message);
        if (system || merged) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        String own = client.player != null ? client.player.getName().getString() : null;
        if (own != null && MentionDetector.isMentioned(body, own,
                AtomChatConfig.get().mentionRequireAt, null)) {
            ChatStore.noteMention();
            MentionObserver.fire(message);
            NotificationController.onMention(message);
            // A line that both @mentions and quotes us notifies once.
            return;
        }
        if (own != null && quoteTargetsLocalPlayer(message, own)) {
            NotificationController.onQuote(message);
        }
    }

    /** Whether a quote pill names the local player (someone replied to us). */
    @Unique
    private static boolean quoteTargetsLocalPlayer(ChatMessage message, String ownName) {
        String quote = message.getQuoteName();
        if (quote == null || ownName == null) {
            return false;
        }
        String cleaned = quote.startsWith("@") ? quote.substring(1) : quote;
        cleaned = cleaned.replaceAll("§.", "").trim();
        if (cleaned.equalsIgnoreCase(ownName)) {
            return true;
        }
        // Decorated labels ([VIP] Steve) and profile-vs-display-name differences:
        // accept the local name as a trailing token, not as a substring of a
        // longer name (NotSteve must not match).
        String lower = cleaned.toLowerCase(java.util.Locale.ROOT);
        String needle = ownName.toLowerCase(java.util.Locale.ROOT);
        int idx = lower.lastIndexOf(needle);
        if (idx >= 0 && (idx == 0 || !isNameCharacter(cleaned.charAt(idx - 1)))) {
            return true;
        }
        PlayerInfo entry = ChatClassifier.resolveOnlinePlayer(cleaned);
        return entry != null && ownName.equalsIgnoreCase(entry.getProfile().getName());
    }

    @Unique
    private static boolean isNameCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Whether a text-parsed line belongs to the local player: UUID when the
     * resolver found one, otherwise the bare profile name (the parse stores
     * the real name, never the decorated label).
     */
    @Unique
    private static boolean atomchat$isOwnIdentity(SenderMeta parsed, Minecraft client) {
        if (client.player == null) {
            return false;
        }
        if (parsed.senderUuid() != null && parsed.senderUuid().equals(client.player.getUUID())) {
            return true;
        }
        // Wire names are decorated (team prefix/suffix, tab display name), so the
        // bare profile name is not the right thing to compare against.
        java.util.List<String> own = OwnIdentity.wireNameCandidates();
        return OwnNameMatcher.matches(parsed.profileName(), own)
                || OwnNameMatcher.matches(parsed.senderName(), own);
    }

    /**
     * True when the line names a different online player. That makes it their
     * message rather than our relayed echo, however well the text matches
     * something we just sent (e33chat's EchoSuppressor guard).
     */
    @Unique
    private static boolean atomchat$otherPlayerNamed(String raw, Minecraft client) {
        if (raw == null || client == null || client.player == null || client.getConnection() == null) {
            return false;
        }
        java.util.List<String> own = OwnIdentity.wireNameCandidates();
        String bare = client.player.getName().getString();
        for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
            if (info.getProfile().getId().equals(client.player.getUUID())) {
                continue;
            }
            for (String cand : ChatClassifier.nameCandidates(info)) {
                if (cand == null || cand.isBlank()) {
                    continue;
                }
                if (cand.equals(bare) || OwnNameMatcher.matches(cand, own)) {
                    continue;
                }
                if (raw.contains(cand)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Team-colour fallback for sender names: when a captured/sliced sender
     * carries no colour at all — the server stripped the run styles, or the
     * label only exists as text — recolour it with the sender's scoreboard
     * team colour so titled/coloured names don't collapse into the plain text
     * colour. Senders that already have colour are left untouched.
     */
    @Unique
    private static RichText atomchat$ensureSenderColor(RichText sender, String profileName) {
        if (sender == null || sender.isEmpty() || sender.hasColor()) {
            return sender;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return sender;
        }
        var team = client.level.getScoreboard().getPlayersTeam(
                profileName != null ? profileName : "");
        if (team == null || team.getColor() == null) {
            return sender;
        }
        var color = net.minecraft.network.chat.TextColor.fromLegacyFormat(team.getColor());
        if (color == null) {
            return sender;
        }
        if (AtomChatConfig.get().debug) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("sender", "[sender] team colour fallback applied ({})"), team.getName());
        }
        return sender.mapStyles(style -> style.getColor() != null ? style : style.withColor(color));
    }

    /**
     * G1 text-layer whisper fallback + user-configured whisper templates.
     * Returns true when the line was claimed (routed into the private panel).
     */
    @Unique
    private static boolean atomchat$tryTextWhisper(String raw, Component message, Minecraft client) {
        String ownName = client.player != null ? client.player.getName().getString() : null;
        WhisperTextParser.WhisperHit hit = WhisperTextParser.tryParse(raw, ownName);
        if (hit == null) {
            // Anchored family (e33chat WhisperDetector port): a known player
            // name near the line start + whisper keyword before the colon.
            hit = WhisperTextParser.tryParseAnchored(raw, ChatPipeline.onlineNameCandidates());
        }
        if (hit == null) {
            var cfg = AtomChatConfig.get();
            if (cfg.whisperTemplates.isEmpty()) {
                return false;
            }
            var match = ChatTemplates.matchWhisper(
                    raw, cfg.whisperTemplates, ChatPipeline.onlineNameCandidates());
            if (match.isEmpty()) {
                return false;
            }
            var tm = match.get();
            hit = new WhisperTextParser.WhisperHit(true, tm.displayLabel(), tm.content());
        }
        PlayerInfo info = ChatClassifier.resolveOnlinePlayer(hit.partnerDisplay());
        String profile = info != null ? info.getProfile().getName() : hit.partnerDisplay();
        UUID uuid = info != null ? info.getProfile().getId()
                : ChatClassifier.resolveUuid(hit.partnerDisplay());
        SenderMeta whisperMeta;
        if (hit.incoming()) {
            whisperMeta = new SenderMeta(uuid, hit.partnerDisplay(), profile,
                    hit.content(), false, true, profile, null, null);
        } else {
            // Outgoing echo: for sends made from the panel the local bubble
            // already exists — drop only when the tracker confirms, otherwise
            // store the line as our outgoing message (same semantics as the
            // vanilla-key outgoing branch in PrivateChatParser).
            PlayerRef partner = PlayerRef.of(uuid, profile);
            if (PrivateEchoTracker.consumeIfMatch(partner)) {
                return true;
            }
            if (client.player == null) {
                return false;
            }
            String own = client.player.getName().getString();
            whisperMeta = new SenderMeta(client.player.getUUID(), own, own,
                    hit.content(), false, true, profile, null, null);
        }
        atomchat$routePrivate(message, whisperMeta, client);
        return true;
    }

    /** Client-side template claim for chat lines the guards cannot express. */
    @Unique
    private static SenderMeta atomchat$tryChatTemplate(String raw) {
        var cfg = AtomChatConfig.get();
        if (cfg.chatTemplates.isEmpty()) {
            return null;
        }
        var match = ChatTemplates.match(
                raw, cfg.chatTemplates, ChatPipeline.onlineNameCandidates());
        if (match.isEmpty()) {
            return null;
        }
        var tm = match.get();
        UUID uuid = ChatClassifier.resolveUuid(tm.playerName());
        return new SenderMeta(uuid, tm.displayLabel(), tm.playerName(), tm.content(), false);
    }

    /**
     * G4 (e33chat parity) miss diagnostics: when the whole claim chain fails
     * and templates are configured, log the raw line so real-server formats
     * that slip through can be fixed from the log alone. Gated behind the
     * debug flag to keep public-chat log volume at zero.
     */
    @Unique
    private static void atomchat$logParseMiss(String raw) {
        var cfg = AtomChatConfig.get();
        if (!cfg.debug) {
            return;
        }
        boolean hasTemplates = !cfg.chatTemplates.isEmpty() || !cfg.whisperTemplates.isEmpty();
        AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("capture", "[atomchat] chat parse miss (guard{} matched nothing): {}"),
                hasTemplates ? "+templates" : "", raw);
    }

    @Unique
    private static QuoteParser.Quote atomchat$quoteOf(String content) {
        return content == null ? null : QuoteParser.parse(content);
    }

    @Unique
    private static void atomchat$routePrivate(Component message, SenderMeta meta, Minecraft client) {
        // Suppressed outgoing echo sentinel: the local bubble already exists.
        if (meta.system()) {
            return;
        }
        String partnerName = meta.whisperPartner();
        if (partnerName == null) {
            return;
        }
        boolean own = isOwn(meta, message.getString(), client);
        UUID partnerUuid = own ? null : meta.senderUuid();
        PlayerRef partner = PlayerRef.of(partnerUuid, partnerName);

        String content = meta.contentText() != null
                ? meta.contentText()
                : ChatPipeline.extractContent(message.getString(), meta);
        String displayName = meta.profileName() != null ? meta.profileName() : meta.senderName();
        if (displayName == null) {
            displayName = partnerName;
        }
        QuoteParser.Quote quote = atomchat$quoteOf(content);
        String body = quote != null ? quote.body() : content;
        RichText senderRich = meta.senderComponent() != null
                ? RichText.of(meta.senderComponent()).stripInteractions()
                : RichText.literal(displayName);
        RichText contentRich;
        if (quote != null) {
            RichText richSource = meta.contentComponent() != null
                    ? RichText.of(meta.contentComponent())
                    : RichText.of(message);
            contentRich = ChatPipeline.quoteBodyRich(richSource, body);
        } else if (meta.contentComponent() != null) {
            contentRich = RichText.of(meta.contentComponent()).linkifyUrls();
        } else {
            contentRich = RichText.literal(body != null ? body : message.getString()).linkifyUrls();
        }
        ChatMessage privateMessage = new ChatMessage(message, own, false,
                quote != null ? quote.quoteName() : null,
                quote != null ? quote.quoteText() : null,
                own ? meta.senderUuid() : meta.senderUuid(), displayName,
                own ? meta.profileName() : meta.profileName(), body, senderRich, contentRich);
        if (own) {
            PrivateChatStore.addOutgoing(partner, privateMessage);
        } else if (!BlockList.isBlocked(partner)) {
            boolean merged = PrivateChatStore.addIncoming(partner, privateMessage);
            SeenPlayers.remember(partnerUuid, meta.profileName(), displayName);
            if (!merged) {
                NotificationController.onWhisper(privateMessage);
            }
        }
    }

    private static void addSystemMessage(Component message) {
        ChatStore.get().add(new ChatMessage(message, false, true, null, null,
                null, null, null, null, RichText.empty(), RichText.of(message).linkifyUrls()));
    }

    private static boolean isOwn(SenderMeta meta, String raw, Minecraft client) {
        if (client.player == null) {
            return false;
        }
        if (meta.senderUuid() != null) {
            return meta.senderUuid().equals(client.player.getUUID());
        }
        // Wire names arrive decorated (team prefix/suffix, tab display name), so a
        // bare-name comparison misses our own echo; compare against every
        // rendering of ourselves instead.
        java.util.List<String> ownCandidates = OwnIdentity.wireNameCandidates();
        if (OwnNameMatcher.matches(meta.profileName(), ownCandidates)
                || OwnNameMatcher.matches(meta.senderName(), ownCandidates)) {
            return true;
        }
        FallbackIdentity fb = parseAngleFallback(raw);
        return fb != null && OwnNameMatcher.matches(fb.name(), ownCandidates);
    }

    private record FallbackIdentity(String name) {
    }

    private static FallbackIdentity parseAngleFallback(String raw) {
        if (raw != null && raw.startsWith("<")) {
            int end = raw.indexOf("> ");
            if (end > 0) {
                String sender = raw.substring(1, end);
                if (!sender.isBlank()) {
                    return new FallbackIdentity(sender);
                }
            }
        }
        return null;
    }
}
