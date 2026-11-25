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
import com.atom.chat.notification.NotificationHudOverlay;
import com.atom.chat.render.ChatHudHistory;
import com.atom.chat.render.ChatHudRenderer;
import com.atom.chat.render.ItemIconRenderer;
import com.atom.chat.text.ChatTextRewriter;
import com.atom.chat.text.RichText;
import com.atom.chat.ui.Animations;
import com.atom.chat.screen.AtomChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.atom.chat.chat.NativeMessageMeta;
import com.atom.chat.chat.NativeMessageBridge;
import com.atom.chat.chat.NativeMessageSync;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Mixin(value = ChatComponent.class, priority = 500)
public class ChatHudMixin implements NotificationHudOverlay.PanelChatHud {
    @Unique
    private boolean atomchat$reposting;

    @Shadow @Final private List<GuiMessage> allMessages;

    @Shadow private boolean isChatHidden() {
        throw new AssertionError();
    }

    @Unique private final ChatHudHistory<GuiMessage, Component, ChatMessage> atomchat$hudHistory = new ChatHudHistory<>();
    @Unique private final ChatHudRenderer atomchat$hudRenderer = new ChatHudRenderer();
    @Unique private final java.util.function.Consumer<String> atomchat$deletionListener=signature->{
        atomchat$hudHistory.invalidate(message->signature.equals(message.nativeMeta().signature()));
        atomchat$hudRenderer.clear();
    };
    @Inject(method="<init>",at=@At("RETURN"))
    private void atomchat$listenDeletions(CallbackInfo info){NativeMessageSync.listen(atomchat$deletionListener);}
    @Unique private static final Function<GuiMessage, ChatMessage> atomchat$hudFallback =
            row -> atomchat$systemPresentation(row.content()).withNativeMeta(NativeMessageBridge.capture(row.signature(),row.tag()));

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void atomchat$renderChatHud(GuiGraphics context, int currentTick, int mouseX, int mouseY, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof AtomChatScreen) {
            // The HUD overlay paints the panel transition behind Screen.render.
            ci.cancel();
            return;
        }
        AtomChatConfig config = AtomChatConfig.get();
        // The real vanilla chat screen retains its interactive history and hit testing.
        if (client.screen instanceof ChatScreen || client.options.hideGui
                || client.player == null || isChatHidden() || !config.enabled || !config.chatHudEnabled) {
            atomchat$hudRenderer.suspend();
            return;
        }

        long now = System.nanoTime();
        float visibility = NotificationHudOverlay.gameplayChatVisibility(client, now);
        boolean animate = client.screen == null && visibility >= 1F && Animations.messageEntry();
        if (atomchat$paintGameplayChat(context, client, config, currentTick, visibility, false, animate, now)) {
            ci.cancel();
        }
    }

    @Override
    public void atomchat$renderPanelHud(GuiGraphics context, float visibility, long now) {
        Minecraft client = Minecraft.getInstance();
        AtomChatConfig config = AtomChatConfig.get();
        if (!config.enabled || !config.chatHudEnabled || client.player == null || client.options.hideGui || isChatHidden()) {
            atomchat$hudRenderer.suspend();
            return;
        }
        atomchat$paintGameplayChat(context, client, config, client.gui.getGuiTicks(), visibility, true, false, now);
    }

    @Unique
    private boolean atomchat$paintGameplayChat(GuiGraphics context, Minecraft client, AtomChatConfig config,
                                              int currentTick, float visibility, boolean panel, boolean animate, long now) {
        int limit = config.effectiveChatHudMaxMessages();
        atomchat$hudRenderer.select(client.level, atomchat$hudHistory, allMessages, limit, atomchat$hudFallback,
                panel, config.chatHudTimedExit ? GuiMessage::addedTime : null, currentTick);
        if (limit == 0 || visibility <= 0F) return true;

        ChatComponent hud = (ChatComponent) (Object) this;
        var viewport = ChatHudRenderer.Viewport.of(context.pose().last().pose(),
                context.guiWidth(), context.guiHeight(), client.getWindow().getGuiScale(), hud.getScale(), hud.getWidth(),
                !client.player.getOffhandItem().isEmpty() && client.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
        int opacity = (int) (255D * (client.options.chatOpacity().get() * 0.9D + 0.1D));
        return atomchat$hudRenderer.render(viewport, client.player.getUUID(), OwnIdentity.bareName(),
                net.minecraft.client.resources.language.I18n.get("atomchat.sender.player"), net.minecraft.client.resources.language.I18n.get("atomchat.hud.image"),
                client.getMainRenderTarget().frameBufferId,
                context::flush, graphics -> ItemIconRenderer.prepare(context, graphics),
                limit, opacity, visibility, animate, now / 1_000_000L);
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V", at = @At("RETURN"))
    private void atomchat$bindChatHudPresentation(Component content, MessageSignature signature, int addedTick, GuiMessageTag tag, boolean refresh, CallbackInfo ci) {
        if (refresh || allMessages.isEmpty()) return;
        GuiMessage entry = allMessages.get(0);
        if (entry.content() != content || entry.addedTime() != addedTick) return;
        atomchat$hudHistory.bind(entry, entry.content(), allMessages);
    }

    @Inject(method = "clearMessages(Z)V", at = @At("HEAD"))
    private void atomchat$clearChatHudPresentation(boolean clearHistory, CallbackInfo ci) {
        atomchat$hudHistory.clear();
        atomchat$hudRenderer.clear();
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
            atomchat$hudHistory.stage(null, null);
            MessageCapture.consume();
            return;
        }

        ChatMessage staged = com.atom.chat.card.CardHudBridge.consumeStaged();
        if (staged != null) {
            atomchat$hudHistory.stage(message, staged);
            return;
        }

        // Store the original message first; the vanilla HUD copy is rewritten
        // below only after capture has already decided what belongs in ChatStore.
        ChatMessage presentation = atomchat$captureAndStore(message, NativeMessageBridge.capture(signatureData,indicator));

        Minecraft client = Minecraft.getInstance();
        String ownName = client.player != null ? client.player.getName().getString() : null;
        boolean protectedRedPacket = presentation != null && presentation.hasCard()
                && presentation.getCard().hasPrivateRedPacketPassword();
        Component rewritten = protectedRedPacket ? presentation.getComponent() : ChatTextRewriter.rewritePrivate(message, ownName);
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
        // Bind the presentation to the component vanilla actually queues, after rewriting.
        atomchat$hudHistory.stage(rewritten != null ? rewritten : message, presentation);
        if (rewritten != null) {
            ci.cancel();
            atomchat$reposting = true;
            try {
                ((ChatComponent) (Object) this).addMessage(rewritten, signatureData, indicator);
            } finally {
                atomchat$reposting = false;
                atomchat$hudHistory.stage(null, null);
            }
        }
    }

    @Unique
    private ChatMessage atomchat$captureAndStore(Component message, NativeMessageMeta nativeMeta) {
        ChatMessage deleted=NativeMessageSync.knownDeleted(nativeMeta.signature());
        if(deleted!=null){MessageCapture.consume();return deleted;}
        String raw = message.getString();
        Minecraft client = Minecraft.getInstance();
        // Teleport failure watch: a "unknown command/no permission" reply right
        // after a menu teleport flips the session command (0.1.11 auto mode).
        TeleportCommands.checkFailure(raw);

        boolean configured = false;
        SenderMeta meta = MessageCapture.consume();
        var convertedShare = com.atom.chat.net.CardCompanionClient.takeConvertedShare(message);
        if (convertedShare != null) {
            return atomchat$storeConvertedShare(message, nativeMeta, client, convertedShare);
        }
        if (meta == null) meta = ChatPipeline.structuredPlayerLine(message);
        if (AtomChatConfig.get().debug) {
            // Whether the channel-level capture landed at all: a missing meta is
            // why an own line falls through to the text heuristics (2026-09-11).
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("capture", "[capture] meta={} line={}"), meta != null,
                    com.atom.chat.card.BuiltInCardAdapter.isLegacyCredentialAnnouncement(message, raw)
                            ? "[private red packet]" : raw);
        }
        // Machine-to-machine chat protocols must never be claimed as player chat
        // or suppressed as an own echo. e33chat routes them to the system channel
        // ("宁可不杀不可错杀"): when in doubt, let the line through.
        if (ChatClassifier.isXaeroWaypointData(raw)
                || (meta != null && meta.contentText() != null
                && ChatClassifier.isXaeroWaypointData(meta.contentText()))) {
            if (meta != null && !meta.system()) {
                if (sourceBlocked(client, meta)) return null;
                return addSystemMessage(message, nativeMeta, meta.senderName());
            }
            return addSystemMessage(message,nativeMeta);
        }
        if (meta != null && meta.system()) { return addSystemMessage(message,nativeMeta); }
        if (meta != null && meta.evidence() == IdentityEvidence.TRANSLATION
                && (meta.senderUuid() == null || meta.whisper() && meta.whisperPartner() == null)
                && meta.senderComponent() != null && meta.contentComponent() != null) {
            meta = ServerTemplateRecognition.unknown(meta.senderComponent(), meta.contentComponent());
        }
        if (meta != null && meta.whisper()) {
            return atomchat$routePrivate(message, meta, client, nativeMeta);
        }
        if (meta == null) {
            meta = PrivateChatParser.tryParse(message);
            if (meta != null) {
                if (meta.system()) { return addSystemMessage(message,nativeMeta); }
                if (meta.senderUuid() != null && meta.whisperPartner() != null) { return atomchat$routePrivate(message, meta, client, nativeMeta); }
                meta = ServerTemplateRecognition.unknown(meta.senderComponent(), meta.contentComponent());
            }
            if (meta == null) {
                String ownName = client.player == null ? null : client.player.getName().getString();
                if (ChatClassifier.classifyByKey(message) == ChatClassifier.Route.SYSTEM
                        || WhisperTextParser.isSpyEnvelope(raw)) {
                    return addSystemMessage(message,nativeMeta);
                }
                var inspected = com.atom.chat.chat.ComponentIdentity.inspect(message);
                if (inspected.conflict()) { return addSystemMessage(message,nativeMeta); }
                meta = ServerTemplateRecognition.parseConfigured(message, inspected);
                if (meta != null && meta.system()) { return addSystemMessage(message,nativeMeta); }
                configured = meta != null;
                if (meta == null && WhisperTextParser.isThirdPartyEnvelope(raw, ownName)) {
                    return addSystemMessage(message,nativeMeta);
                }
                if (meta == null) meta = inspected.meta();
                if (meta == null) {
                    var learned = ChatPipeline.learnedPlayerLine(message);
                    if (learned != null && !learned.whisper()) {
                        meta = ServerTemplateRecognition.unknown(learned.senderComponent(), learned.contentComponent());
                    }
                }
                if (meta == null && ChatPipeline.hasUnmappedTranslation(message)) { return addSystemMessage(message,nativeMeta); }
                if (meta == null) {
                    SenderMeta whisper = atomchat$parseTextWhisper(message, client, null);
                    if (whisper != null) return atomchat$storeTextWhisper(message, whisper, client, nativeMeta);
                }
                if (meta == null) meta = TellClickDetector.detectByTellClick(message, raw);
                if (meta == null) meta = ChatPipeline.tryParsePlayerLine(raw);
                if (meta == null) meta = EasyBotParser.tryParse(raw);
                if (meta == null) { atomchat$logParseMiss(raw); return addSystemMessage(message,nativeMeta); }
            }
        }

        if (meta.whisper()) { return atomchat$routePrivate(message, meta, client, configured, nativeMeta); }
        // A click/hover attributes a name, but does not declare a public channel.
        if (!configured && (meta.evidence() == IdentityEvidence.ENTITY
                || meta.evidence() == IdentityEvidence.CLICK)) {
            SenderMeta whisper = atomchat$parseTextWhisper(message, client, meta);
            if (whisper != null) return atomchat$storeTextWhisper(message, whisper, client, nativeMeta);
        }
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
        if (sourceBlocked(client, meta)) return null;

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
        senderRich = ChatPipeline.senderPresentation(senderRich);
        ChatMessage presentation = new ChatMessage(message, own, meta.system(),
                quote != null ? quote.quoteName() : null,
                quote != null ? quote.quoteText() : null,
                meta.senderUuid(), displayName, unknown ? "" : meta.profileName(), body,
                senderRich, contentRich).retainPresentation(configured || unknown).withNativeMeta(nativeMeta);
        var pairedCard = meta.evidence() == IdentityEvidence.NATIVE
                ? com.atom.chat.net.CardCompanionClient.takeNativeShare(meta.senderUuid(), meta.originalContent()) : null;
        presentation = pairedCard == null ? atomchat$attachCard(presentation, message, displayName, meta.system())
                : presentation.withCard(pairedCard);
        atomchat$addPublic(presentation, body, meta.system() || unknown,
                meta.evidence().confirmsIdentity(), meta.originalContent());
        return presentation;
    }

    /**
     * Public-channel entry + @-mention bookkeeping (0.1.11): the mention check
     * uses the final body text and fires the observer seam; unread counters
     * live in ChatStore. System capsules never count as mentions.
     */
    @Unique
    private static ChatMessage atomchat$storeConvertedShare(Component message, NativeMessageMeta nativeMeta,
            Minecraft client, com.atom.chat.net.ConvertedSharePairing.Share share) {
        if (client.isBlocked(share.author()) || client.getPlayerSocialManager().isHidden(share.author())
                || AtomChatConfig.get().hideBlockedMessages && BlockList.isBlocked(share.name())) return null;
        boolean own = client.player != null && share.author().equals(client.player.getUUID());
        var row = new ChatMessage(message, own, false, null, null, share.author(), share.name(), share.name(),
                share.body(), RichText.literal(share.name()), RichText.literal(share.body()))
                .withNativeMeta(nativeMeta).withCard(share.card());
        atomchat$addPublic(row, share.body(), false, true, share.body());
        return row;
    }

    @Unique
    private static void atomchat$addPublic(ChatMessage message, String body, boolean system, boolean provenSender, String originalBody) {
        if (message.isOwn() && provenSender) OwnIdentity.cache(ChatPipeline.senderPresentation(message.getSenderRich()));
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

    @Unique
    private static SenderMeta atomchat$parseTextWhisper(Component message, Minecraft client, SenderMeta identity) {
        String ownName = client.player == null ? null : client.player.getName().getString();
        return com.atom.chat.chat.PrivateMessageRecognition.parse(message,
                client.player == null ? null : client.player.getUUID(), ownName,
                ChatPipeline.onlineNameCandidates(), identity);
    }

    @Unique
    private static ChatMessage atomchat$storeTextWhisper(Component message, SenderMeta parsed, Minecraft client, NativeMessageMeta nativeMeta) {
        return parsed.system() ? addSystemMessage(message,nativeMeta) : atomchat$routePrivate(message, parsed, client, nativeMeta);
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
    private static ChatMessage atomchat$routePrivate(Component message, SenderMeta meta, Minecraft client, NativeMessageMeta nativeMeta) {
        return atomchat$routePrivate(message, meta, client, false, nativeMeta);
    }

    @Unique
    private static ChatMessage atomchat$routePrivate(Component message, SenderMeta meta, Minecraft client, boolean configured, NativeMessageMeta nativeMeta) {
        // Suppressed outgoing echo sentinel: the local bubble already exists.
        if (meta.system()) {
            return null;
        }
        String partnerName = meta.whisperPartner();
        if (partnerName == null) {
            return null;
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
                ? ChatPipeline.senderPresentation(RichText.of(meta.senderComponent()))
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
                meta.senderUuid(), displayName, meta.profileName(), body, senderRich, contentRich).retainPresentation(configured).withNativeMeta(nativeMeta);
        if (own) {
            if (!com.atom.chat.chat.OutgoingMessages.reconcile(privateMessage, partner,
                    meta.whisperPartnerUuid() != null && meta.evidence().confirmsIdentity(),
                    meta.originalContent()))
                PrivateChatStore.addOutgoing(partner, privateMessage);
        } else {
            if (BlockList.isBlocked(partner)) return null;
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
        return privateMessage;
    }

    @Unique
    private static boolean sourceBlocked(Minecraft client, SenderMeta meta) {
        if (meta.evidence() == IdentityEvidence.UNKNOWN_TEMPLATE) return false;
        if (client != null && meta.senderUuid() != null && client.isBlocked(meta.senderUuid())) return true;
        String profile = meta.profileName() != null ? meta.profileName() : meta.senderName();
        return AtomChatConfig.get().hideBlockedMessages && profile != null && BlockList.isBlocked(profile);
    }

    @Unique
    private static ChatMessage atomchat$attachCard(ChatMessage presentation, Component original, String sender, boolean system) {
        if (presentation.isDeleted()) return presentation;
        var card = com.atom.chat.card.BuiltInCardAdapter.adapt(original, sender, system);
        return card == null ? presentation : presentation.withCard(card);
    }

    private static ChatMessage addSystemMessage(Component message, NativeMessageMeta nativeMeta) {
        return addSystemMessage(message, nativeMeta, null);
    }

    @Unique
    private static ChatMessage addSystemMessage(Component message, NativeMessageMeta nativeMeta, String sender) {
        ChatMessage presentation = atomchat$systemPresentation(message).withNativeMeta(nativeMeta);
        presentation = atomchat$attachCard(presentation, message, sender, true);
        ChatStore.get().add(presentation);
        return presentation;
    }

    @Unique
    private static ChatMessage atomchat$systemPresentation(Component message) {
        return new ChatMessage(message, false, true, null, null,
                null, null, null, null, RichText.empty(), RichText.of(message).linkifyUrls());
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

    @Redirect(method="deleteMessageOrDelay",at=@At(value="INVOKE",target="Ljava/util/ListIterator;set(Ljava/lang/Object;)V"))
    private void atomchat$afterNativeSet(java.util.ListIterator<net.minecraft.client.GuiMessage> iterator,Object replacement,MessageSignature signature){
        var marker=(net.minecraft.client.GuiMessage)replacement;
        iterator.set(marker);
        NativeMessageSync.applied(NativeMessageMeta.signature(signature.bytes()),RichText.of(marker.content()));
        String key=NativeMessageMeta.signature(signature.bytes());
        atomchat$hudHistory.invalidate(message->key.equals(message.nativeMeta().signature()));
        atomchat$hudRenderer.clear();
    }
    @Inject(method="deleteMessageOrDelay",at=@At("RETURN"))
    private void atomchat$missingRetainedEntry(MessageSignature signature,CallbackInfoReturnable<Object> info){
        boolean present=allMessages.stream().anyMatch(entry->signature.equals(entry.signature()));
        NativeMessageSync.request(NativeMessageMeta.signature(signature.bytes()),NativeMessageBridge.tick(),present);
    }
    @Inject(method="tick",at=@At("RETURN"))
    private void atomchat$retainedDeletionTick(CallbackInfo info){
        NativeMessageSync.tick(NativeMessageBridge.tick(),signature->allMessages.stream()
                .anyMatch(entry->entry.signature()!=null && signature.equals(NativeMessageMeta.signature(entry.signature().bytes()))));
    }

}
