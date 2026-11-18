package com.atom.chat.chat;

import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Structured identity captured at the MessageHandler channel level, before
 * Minecraft decorates the line for the chat HUD. It travels through a short
 * TTL handoff to {@link ChatHudMixin}, which builds the final {@link ChatMessage}
 * or routes it into {@link PrivateChatStore}.
 *
 * @param senderUuid       real player UUID when known, else {@code null} (nil UUID is
 *                         stored as null to keep "unknown" explicit)
 * @param senderName       display name to show in the bubble (tab/nick/decorated name
 *                         when known, otherwise profile/raw name)
 * @param profileName      real profile name used for skin/identity lookups
 * @param contentText      message body without sender decorations (may be null when
 *                         the consumer must fall back to the full decorated line)
 * @param system           true when the line is a system/broadcast message
 * @param whisper          true when this line is a private /msg message
 * @param whisperPartner   partner's real profile name for private messages
 * @param senderComponent  optional decorated sender {@link Component} captured before the
 *                         final HUD line is built; {@code null} when only text parsing
 *                         is available (system/text-guard paths)
 * @param contentComponent optional message-body {@link Component} captured before the final
 *                         HUD line is built; {@code null} when only text parsing is
 *                         available
 */
public record SenderMeta(UUID senderUuid, String senderName, String profileName,
                         String contentText, boolean system, boolean whisper,
                         String whisperPartner, Component senderComponent,
                         Component contentComponent, IdentityEvidence evidence, String originalContent,
                         UUID whisperPartnerUuid) {
    public SenderMeta(UUID uuid, String sender, String profile, String content, boolean system,
                      boolean whisper, String partner, Component senderComponent, Component contentComponent,
                      IdentityEvidence evidence, String originalContent) {
        this(uuid, sender, profile, content, system, whisper, partner, senderComponent, contentComponent,
                evidence, originalContent, null);
    }
    public SenderMeta(UUID uuid, String sender, String profile, String content, boolean system,
                      boolean whisper, String partner, Component senderComponent, Component contentComponent) {
        this(uuid, sender, profile, content, system, whisper, partner, senderComponent, contentComponent, IdentityEvidence.TEXT, null);
    }
    public SenderMeta withOriginalContent(String original) {
        return new SenderMeta(senderUuid, senderName, profileName, contentText, system, whisper,
                whisperPartner, senderComponent, contentComponent, evidence, original, whisperPartnerUuid);
    }
    public SenderMeta withEvidence(IdentityEvidence source) {
        return new SenderMeta(senderUuid, senderName, profileName, contentText, system, whisper,
                whisperPartner, senderComponent, contentComponent, source, originalContent, whisperPartnerUuid);
    }
    public SenderMeta {
        evidence = evidence == null ? IdentityEvidence.TEXT : evidence;
        senderUuid = senderUuid != null && senderUuid.equals(NIL_UUID) ? null : senderUuid;
        senderName = clean(senderName);
        profileName = clean(profileName);
        contentText = contentText == null || contentText.isBlank() ? null : TemplateParser.visibleText(contentText);
        whisperPartner = clean(whisperPartner);
    }

    public SenderMeta(UUID senderUuid, String senderName, String profileName,
                      String contentText, boolean system) {
        this(senderUuid, senderName, profileName, contentText, system, false, null, null, null);
    }

    public SenderMeta(UUID senderUuid, String senderName, String profileName,
                      String contentText, boolean system, Component senderComponent,
                      Component contentComponent) {
        this(senderUuid, senderName, profileName, contentText, system, false, null,
                senderComponent, contentComponent);
    }

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public static SenderMeta systemMeta() {
        return new SenderMeta(null, null, null, null, true);
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "");
        return stripped.isBlank() ? null : stripped.trim();
    }
}
