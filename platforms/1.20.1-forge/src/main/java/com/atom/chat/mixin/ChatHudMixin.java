package com.atom.chat.mixin;

import com.atom.chat.AtomChat;
import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.ChatClassifier;
import com.atom.chat.chat.IdentityEvidence;
import com.atom.chat.chat.ServerTemplateRecognition;
import com.atom.chat.chat.WhisperTextParser;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.ChatPipeline;
import com.atom.chat.chat.ChatStore;
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

    // 1.20.1 ChatComponent#render(GuiGraphics, int, int, int) has no trailing
    // boolean; the extra parameter asked for in 1.21.1 makes Mixin reject the
    // handler with InvalidInjectionException at class-load time.
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void atomchat$hideVanillaChatHud(GuiGraphics context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
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
        if (!AtomChatConfig.get().enabled) {
            MessageCapture.consume();
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

        boolean configured = false;
        SenderMeta meta = MessageCapture.consume();
        if (meta == null) meta = ChatPipeline.structuredPlayerLine(message);
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
        if (meta != null && meta.system()) { addSystemMessage(message); return; }
        if (meta != null && meta.evidence() == IdentityEvidence.TRANSLATION
                && (meta.senderUuid() == null || meta.whisper() && meta.whisperPartner() == null)
                && meta.senderComponent() != null && meta.contentComponent() != null) {
            meta = ServerTemplateRecognition.unknown(meta.senderComponent(), meta.contentComponent());
        }
        if (meta != null && meta.whisper()) {
            atomchat$routePrivate(message, meta, client);
            return;
        }
        if (meta == null) {
            meta = PrivateChatParser.tryParse(message);
            if (meta != null) {
                if (meta.system()) { addSystemMessage(message); return; }
                if (meta.senderUuid() != null && meta.whisperPartner() != null) { atomchat$routePrivate(message, meta, client); return; }
                meta = ServerTemplateRecognition.unknown(meta.senderComponent(), meta.contentComponent());
            }
            if (meta == null) {
                String ownName = client.player == null ? null : client.player.getName().getString();
                if (ChatClassifier.classifyByKey(message) == ChatClassifier.Route.SYSTEM
                        || WhisperTextParser.isSpyEnvelope(raw)) {
                    addSystemMessage(message);
                    return;
                }
                var inspected = com.atom.chat.chat.ComponentIdentity.inspect(message);
                if (inspected.conflict()) { addSystemMessage(message); return; }
                meta = ServerTemplateRecognition.parseConfigured(message, inspected);
                if (meta != null && meta.system()) { addSystemMessage(message); return; }
                configured = meta != null;
                if (meta == null && WhisperTextParser.isThirdPartyEnvelope(raw, ownName)) {
                    addSystemMessage(message);
                    return;
                }
                if (meta == null) meta = inspected.meta();
                if (meta == null) {
                    var learned = ChatPipeline.learnedPlayerLine(message);
                    if (learned != null && !learned.whisper()) {
                        meta = ServerTemplateRecognition.unknown(learned.senderComponent(), learned.contentComponent());
                    }
                }
                if (meta == null && ChatPipeline.hasUnmappedTranslation(message)) { addSystemMessage(message); return; }
                if (meta == null && atomchat$tryTextWhisper(raw, message, client)) return;
                if (meta == null) meta = TellClickDetector.detectByTellClick(message, raw);
                if (meta == null) meta = ChatPipeline.tryParsePlayerLine(raw);
                if (meta == null) meta = EasyBotParser.tryParse(raw);
                if (meta == null) { atomchat$logParseMiss(raw); addSystemMessage(message); return; }
            }
        }

        if (meta.whisper()) { atomchat$routePrivate(message, meta, client, configured); return; }
        // A click/hover attributes a name, but does not declare a public channel.
        if (!configured && (meta.evidence() == IdentityEvidence.ENTITY
                || meta.evidence() == IdentityEvidence.CLICK)
                && atomchat$tryTextWhisper(raw, message, client, meta)) return;
        String content = ChatPipeline.extractContent(raw, meta);
        boolean own = isOwn(meta, raw, client);
        if (meta.evidence().confirmsIdentity() && meta.senderUuid() != null) {
            String confirmedLabel = meta.evidence() == com.atom.chat.chat.IdentityEvidence.NATIVE ? meta.senderName()
                    : meta.senderComponent() != null ? meta.senderComponent().getString() : meta.senderName();
            SeenPlayers.remember(meta.senderUuid(), meta.profileName(), confirmedLabel);
            if (confirmedLabel != null && meta.contentComponent() != null)
                com.atom.chat.chat.LearnedChatFormats.observe(raw, confirmedLabel, meta.contentComponent().getString());
        }
        boolean unknown = meta.evidence() == IdentityEvidence.UNKNOWN_TEMPLATE;
        String blockName = unknown ? null : meta.profileName() != null ? meta.profileName() : meta.senderName();
        if (AtomChatConfig.get().hideBlockedMessages
                && blockName != null && BlockList.isBlocked(blockName)) {
            return;
        }

        String displayName = meta.senderComponent() != null ? meta.senderComponent().getString() : ChatPipeline.decoratedDisplayName(raw, meta);
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
                senderRich = RichText.literal(displayName != null ? displayName : "");
                contentRich = ChatPipeline.preserveRichBody(message, content);
            }
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
        atomchat$addPublic(new ChatMessage(message, own, meta.system(),
                quote != null ? quote.quoteName() : null,
                quote != null ? quote.quoteText() : null,
                meta.senderUuid(), displayName, unknown ? "" : meta.profileName(), body,
                senderRich, contentRich).retainPresentation(configured || unknown), body, meta.system() || unknown,
                meta.evidence().confirmsIdentity(), meta.originalContent());
    }

    /**
     * Public-channel entry + @-mention bookkeeping (0.1.11): the mention check
     * uses the final body text and fires the observer seam; unread counters
     * live in ChatStore. System capsules never count as mentions.
     */
    @Unique
    private static void atomchat$addPublic(ChatMessage message, String body, boolean system, boolean provenSender, String originalBody) {
        if (message.isOwn() && provenSender) OwnIdentity.cache(message.getSenderRich().stripInteractions());
        if (com.atom.chat.chat.OutgoingMessages.reconcile(message, null, provenSender, originalBody)) return;
        boolean merged = ChatStore.get().add(message);
        if (system || merged || message.isOwn()) {
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

    @Unique
    private static boolean atomchat$tryTextWhisper(String raw, Component message, Minecraft client) {
        return atomchat$tryTextWhisper(raw, message, client, null);
    }

    @Unique
    private static boolean atomchat$tryTextWhisper(String raw, Component message, Minecraft client, SenderMeta identity) {
        String ownName = client.player == null ? null : client.player.getName().getString();
        SenderMeta parsed = com.atom.chat.chat.PrivateMessageRecognition.parse(message,
                client.player == null ? null : client.player.getUUID(), ownName,
                ChatPipeline.onlineNameCandidates(), identity);
        if (parsed == null) return false;
        if (parsed.system()) addSystemMessage(message); else atomchat$routePrivate(message, parsed, client);
        return true;
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
        atomchat$routePrivate(message, meta, client, false);
    }

    @Unique
    private static void atomchat$routePrivate(Component message, SenderMeta meta, Minecraft client, boolean configured) {
        // Suppressed outgoing echo sentinel: the local bubble already exists.
        if (meta.system()) {
            return;
        }
        String partnerName = meta.whisperPartner();
        if (partnerName == null) {
            return;
        }
        boolean own = isOwn(meta, message.getString(), client);
        UUID partnerUuid = own ? meta.whisperPartnerUuid() : meta.senderUuid();
        if (own && partnerUuid == null) partnerUuid = ChatClassifier.resolveAccountUuid(partnerName);
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
            contentRich = ChatPipeline.preserveRichBody(message, body != null ? body : message.getString());
        }
        ChatMessage privateMessage = new ChatMessage(message, own, false,
                quote != null ? quote.quoteName() : null,
                quote != null ? quote.quoteText() : null,
                meta.senderUuid(), displayName, meta.profileName(), body, senderRich, contentRich).retainPresentation(configured);
        if (own) {
            if (!com.atom.chat.chat.OutgoingMessages.reconcile(privateMessage, partner,
                    meta.whisperPartnerUuid() != null && meta.evidence().confirmsIdentity(),
                    meta.originalContent()))
                PrivateChatStore.addOutgoing(partner, privateMessage);
        } else if (!BlockList.isBlocked(partner)) {
            boolean merged = PrivateChatStore.addIncoming(partner, privateMessage);
            if (meta.evidence().confirmsIdentity() && partnerUuid != null) {
                SeenPlayers.remember(partnerUuid, meta.profileName(), meta.senderName());
                if (meta.senderComponent() != null && meta.contentComponent() != null)
                    com.atom.chat.chat.LearnedChatFormats.observe(message.getString(), meta.senderComponent().getString(),
                            meta.contentComponent().getString(), true);
            }
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
        if (meta.evidence() == IdentityEvidence.UNKNOWN_TEMPLATE) return false;
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
