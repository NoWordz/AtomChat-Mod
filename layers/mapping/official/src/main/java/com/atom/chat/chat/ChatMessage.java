package com.atom.chat.chat;

import com.atom.chat.text.RichText;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class ChatMessage {
    private final Component component;
    private final String rawText;
    private final long timestamp;
    private final boolean own;
    private final boolean system;
    private final String quoteName;
    private final String quoteText;
    private final UUID senderUuid;
    private final String senderName;
    private final String profileName;
    private final String contentText;
    private final RichText senderRich;
    private final RichText contentRich;
    /**
     * Quote capsule parts sliced out of the original component ({@code null} when
     * the line carried no quote, when the text drifted, or when the capsule stays
     * a plain string pill). See {@link ChatPipeline#quotePartsRich}.
     */
    private final RichText quoteNameRich;
    private final RichText quoteTextRich;
    /** 1 = ordinary message; >1 = anti-spam merged consecutive identical messages. */
    private final int duplicateCount;
    /**
     * Stable identity for UI state (selection anchors, jump highlight). An
     * anti-spam merge replaces the stored row object, so identity-by-reference
     * silently drops any state held on the message; copies made for a merge
     * carry this id over.
     */
    private final long id;

    private static final java.util.concurrent.atomic.AtomicLong ID_SEQUENCE =
            new java.util.concurrent.atomic.AtomicLong();

    public ChatMessage(Component component, boolean own) {
        this(component, own, false);
    }

    public ChatMessage(Component component, boolean own, boolean system) {
        this(component, own, system, null, null, null, null, null, null);
    }

    public ChatMessage(Component component, boolean own, String quoteName, String quoteText) {
        this(component, own, false, quoteName, quoteText, null, null, null, null);
    }

    public ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                       UUID senderUuid, String senderName, String profileName, String contentText) {
        this(component, own, system, quoteName, quoteText, senderUuid, senderName, profileName, contentText,
                System.currentTimeMillis());
    }

    /**
     * Variant used when rehydrating persisted history: keeps the original time.
     *
     * <p>No rich parts are supplied, so the tail slices the sender/content (and
     * the quote capsule) out of the restored component instead of rebuilding them
     * as plain literals — that is what keeps a reloaded line's explicit wire
     * colours. The component is restored from the persisted JSON, so it is the
     * original rich line even here.
     */
    public ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                       UUID senderUuid, String senderName, String profileName, String contentText, long timestamp) {
        this(component, own, system, quoteName, quoteText, senderUuid, senderName, profileName, contentText,
                null, null, timestamp);
    }

    public ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                       UUID senderUuid, String senderName, String profileName, String contentText,
                       RichText senderRich, RichText contentRich) {
        this(component, own, system, quoteName, quoteText, senderUuid, senderName, profileName, contentText,
                senderRich, contentRich, System.currentTimeMillis());
    }

    public ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                       UUID senderUuid, String senderName, String profileName, String contentText,
                       RichText senderRich, RichText contentRich, long timestamp) {
        this(component, own, system, quoteName, quoteText, senderUuid, senderName, profileName, contentText,
                senderRich, contentRich, timestamp, 1);
    }

    /** Full constructor with anti-spam merge count ({@code duplicateCount} 1 = normal). */
    public ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                       UUID senderUuid, String senderName, String profileName, String contentText,
                       RichText senderRich, RichText contentRich, long timestamp, int duplicateCount) {
        this(component, own, system, quoteName, quoteText, senderUuid, senderName, profileName, contentText,
                senderRich, contentRich, timestamp, duplicateCount, ID_SEQUENCE.incrementAndGet());
    }

    /** Private tail: {@code id} lets merge copies keep the original identity. */
    private ChatMessage(Component component, boolean own, boolean system, String quoteName, String quoteText,
                        UUID senderUuid, String senderName, String profileName, String contentText,
                        RichText senderRich, RichText contentRich, long timestamp, int duplicateCount, long id) {
        this.component = component;
        this.rawText = component.getString();
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.own = own;
        this.system = system;
        this.quoteName = quoteName;
        this.quoteText = quoteText;
        this.senderUuid = senderUuid;
        this.senderName = clean(senderName);
        this.profileName = clean(profileName);
        this.contentText = contentText != null && !contentText.isBlank() ? clean(contentText) : null;
        // One flattening of the original component serves both fallbacks (legacy
        // constructors and history reload) and the quote capsule: all three must
        // read the wire's runs, never just its plain text.
        RichText full = null;
        if (component != null
                && ((!system && senderRich == null) || contentRich == null || quoteName != null)) {
            full = RichText.of(component);
        }
        this.senderRich = !system && senderRich != null ? senderRich
                : legacySenderRich(full, system, this.senderName, this.profileName);
        this.contentRich = contentRich != null ? contentRich
                : legacyContentRich(full, rawText, quoteName, this.contentText);
        ChatPipeline.QuoteRichParts quoteParts =
                quoteName != null ? ChatPipeline.quotePartsRich(full, quoteName, quoteText) : null;
        this.quoteNameRich = quoteParts != null ? quoteParts.name() : null;
        this.quoteTextRich = quoteParts != null ? quoteParts.text() : null;
        this.duplicateCount = Math.max(1, duplicateCount);
        this.id = id;
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.replaceAll("§.", "");
        return stripped.isBlank() ? null : stripped.trim();
    }

    private static String cleanContent(String s) {
        return s == null || s.isBlank() ? null : clean(s);
    }

    /**
     * Rich text for the sender name shown in the bubble; empty for system lines.
     *
     * <p>The name is sliced out of the original line when it sits there exactly
     * once, so a pre-rich constructor — a reloaded history row in particular —
     * keeps the name run's explicit colour. A missing or ambiguous match falls
     * back to a plain literal: the data layer must not borrow a colour from
     * another region, and the render layer then paints AtomChat's name colour.
     */
    private static RichText legacySenderRich(RichText full, boolean system, String senderName, String profileName) {
        if (system) {
            return RichText.empty();
        }
        String name = clean(senderName);
        if (name == null) {
            name = clean(profileName);
        }
        if (name == null) {
            return RichText.empty();
        }
        RichText sliced = sliceUnique(full, name, 0);
        return sliced != null ? sliced : RichText.literal(name);
    }

    /**
     * Rich content for the pre-rich constructors, sliced out of the original line
     * under the same uniqueness rule as {@link #legacySenderRich} so a reloaded
     * row keeps its explicit run colours. When the line carries a quote prefix the
     * search starts after its closing bracket, which is where the reply body
     * begins; otherwise a plain literal backs the display text and the render
     * layer's bubble colour applies.
     */
    private static RichText legacyContentRich(RichText full, String rawText, String quoteName, String contentText) {
        String want = legacyDisplayText(rawText, quoteName, contentText);
        int from = 0;
        if (quoteName != null && full != null && rawText != null && rawText.startsWith("「引用")) {
            int close = full.getString().indexOf('」');
            if (close >= 0) {
                from = close + 1;
            }
        }
        RichText sliced = sliceUnique(full, want, from);
        return (sliced != null ? sliced : RichText.literal(want)).linkifyUrls();
    }

    /**
     * Slices {@code want} out of the original line, but only when it occurs
     * exactly once from {@code from} onwards. An ambiguous match could attach
     * another region's colour to this part, which is worse than the plain text
     * plus the render default; the same rule covers a missing or empty match.
     */
    private static RichText sliceUnique(RichText full, String want, int from) {
        if (full == null || want == null || want.isEmpty()) {
            return null;
        }
        String plain = full.getString();
        int at = plain.indexOf(want, Math.max(0, from));
        if (at < 0 || plain.indexOf(want, at + 1) >= 0) {
            return null;
        }
        return full.slice(at, at + want.length());
    }

    /** Plain display string used by pre-rich constructors, matching legacy display behavior. */
    private static String legacyDisplayText(String rawText, String quoteName, String contentText) {
        if (quoteName != null && rawText.startsWith("「引用")) {
            int end = rawText.indexOf('」');
            if (end >= 0) {
                return rawText.substring(end + 1);
            }
        }
        return legacyContentText(rawText, contentText);
    }

    /** Plain content string used by pre-rich constructors, matching {@link #getContentText()}. */
    private static String legacyContentText(String rawText, String contentText) {
        String cleaned = cleanContent(contentText);
        String text = cleaned != null ? cleaned : rawText;
        // The vanilla "<sender> " prefix only exists on the final HUD line.
        // contentText is captured before decoration, so a message that really
        // starts with "<Alice> hi" must keep that text — only the raw fallback
        // needs the prefix stripped.
        if (cleaned == null) {
            while (text.startsWith("<")) {
                int end = text.indexOf("> ");
                if (end > 0 && end + 2 < text.length()) {
                    text = text.substring(end + 2);
                } else {
                    break;
                }
            }
        }
        while (text.startsWith("「引用")) {
            int end = text.indexOf('」');
            if (end >= 0 && end + 1 < text.length()) {
                text = text.substring(end + 1);
            } else {
                break;
            }
        }
        return text;
    }

    public Component getComponent() {
        return component;
    }

    public String getRawText() {
        return rawText;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    /** Display name to show in the bubble; null for pure system lines. */
    public String getSenderName() {
        if (senderRich != null && !senderRich.isEmpty()) {
            String rich = senderRich.getString();
            if (rich != null && !rich.isBlank()) {
                return rich;
            }
        }
        return senderName != null ? senderName : profileName;
    }

    /** Real profile name used for skin/identity lookups. */
    public String getProfileName() {
        return profileName != null ? profileName : senderName;
    }

    /**
     * Message content without the vanilla "&lt;sender&gt; " prefix, for copy/quote.
     * Nested quote prefixes ("「引用 @x: 「引用 @y: ...」 text」") are stripped
     * recursively so a quoted quote shows only the original message.
     */
    public String getContentText() {
        return legacyContentText(rawText, contentText);
    }

    /** Rich sender part, or empty for system messages. */
    public RichText getSenderRich() {
        return senderRich;
    }

    /** Rich content part backing display text and future styled rendering. */
    public RichText getContentRich() {
        return contentRich;
    }

    /**
     * Styled quote-capsule name sliced from the original line (without the
     * {@code @}), or empty when the capsule has no rich source and must be drawn
     * from {@link #getQuoteName()} with the capsule colour.
     */
    public RichText getQuoteNameRich() {
        return quoteNameRich != null ? quoteNameRich : RichText.empty();
    }

    /** Styled quoted text sliced from the original line, or empty like the name. */
    public RichText getQuoteTextRich() {
        return quoteTextRich != null ? quoteTextRich : RichText.empty();
    }

    /** Anti-spam merge count: 1 for a normal message, N for N identical sends. */
    public int getDuplicateCount() {
        return duplicateCount;
    }

    /** Copy with a different anti-spam merge count (used when a duplicate lands). */
    public ChatMessage withDuplicateCount(int count) {
        return new ChatMessage(component, own, system, quoteName, quoteText,
                senderUuid, senderName, profileName, contentText,
                senderRich, contentRich, timestamp, count, id);
    }

    /** Copy with a different anti-spam merge count and an updated timestamp. */
    public ChatMessage withDuplicateCount(int count, long newTimestamp) {
        return new ChatMessage(component, own, system, quoteName, quoteText,
                senderUuid, senderName, profileName, contentText,
                senderRich, contentRich, newTimestamp, count, id);
    }

    public long getId() {
        return id;
    }

    /** Identity comparison that survives anti-spam merges replacing the row. */
    public boolean sameAs(ChatMessage other) {
        return other != null && (other == this || other.id == this.id);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isOwn() {
        return own;
    }

    /** Server/System line without a player sender (join, death, command feedback...). */
    public boolean isSystem() {
        return system;
    }

    public String getQuoteName() {
        return quoteName;
    }

    public String getQuoteText() {
        return quoteText;
    }

    /**
     * Component to draw inside the bubble. Rich-content aware callers supply the final
     * content through {@link #getContentRich()}; legacy constructors populate that
     * rich part from the plain text after stripping quote/prefix decorations.
     */
    public String getDisplayText() {
        return contentRich.getString();
    }
}
