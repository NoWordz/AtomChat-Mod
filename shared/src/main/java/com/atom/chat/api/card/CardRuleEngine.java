package com.atom.chat.api.card;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data-driven JSON rule engine ({@code config/atomchat/card_rules.json}) allowing server
 * owners and modpack authors to map any custom plugin/mod chat message to a {@link ShareCard}
 * without writing Java code.
 *
 * <p>Security invariants:
 * <ul>
 *   <li>Rules can ONLY bind {@code RUN_COMMAND}s that existed verbatim as native {@code ClickEvent}s
 *       on the incoming server message and pass {@link UniversalCardMatcher#isDangerousCommand(String)}.</li>
 *   <li>Regex evaluation is wrapped in a bounded {@link CharSequence} (max 10,000 char reads)
 *       so catastrophic backtracking (ReDoS) can never freeze the client thread.</li>
 *   <li>Capture-group expansion ({@code $1..$9}) runs in a single pass with no recursive re-expansion.</li>
 * </ul>
 */
public final class CardRuleEngine {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_RULES = 128;
    private static final int MAX_REGEX_LEN = 256;
    private static final long MAX_FILE_BYTES = 262_144L;
    private static final int MAX_REGEX_STEPS = 10_000;
    private static final Pattern GROUP_REF = Pattern.compile("\\$([1-9])");
    private static final AtomicLong RULE_CARD_SEQ = new AtomicLong();

    private static volatile List<CompiledRule> activeRules = List.of();
    private static volatile Path boundPath;

    private CardRuleEngine() {
    }

    public static final class RuleFileDto {
        public int version = 1;
        public boolean enabled = true;
        public List<RuleDto> rules = new ArrayList<>();
    }

    public static final class RuleDto {
        public String id = "";
        public boolean enabled = true;
        public int priority = 0;
        public boolean systemOnly = true;
        public String textRegex = "";
        public String commandPrefix = "";
        public String translationKeyPrefix = "";
        public String template = "GENERAL";
        public String title = "";
        public String subtitle = "";
        public String source = "";
        public String accentColor = "#3B82F6";
        public String buttonLabel = "";
        public int avatarGroup = 0;
    }

    private record CompiledRule(
            RuleDto dto,
            Pattern textPattern,
            int accentArgb,
            ShareCard.Template template
    ) {
    }

    /** Loads rules from {@code path}, creating a clean empty template file if absent. */
    public static void loadFrom(Path path) {
        if (path == null) return;
        boundPath = path;
        try {
            if (!Files.exists(path)) {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                RuleFileDto empty = new RuleFileDto();
                Files.writeString(path, GSON.toJson(empty), StandardCharsets.UTF_8);
                activeRules = List.of();
                return;
            }
            if (Files.size(path) > MAX_FILE_BYTES) {
                activeRules = List.of();
                return;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            loadFromJson(json);
        } catch (Throwable ignored) {
            activeRules = List.of();
        }
    }

    public static void reload() {
        Path p = boundPath;
        if (p != null) {
            loadFrom(p);
        }
    }

    /** Parses and activates rules from raw JSON (used both by disk loader and unit tests). */
    public static int loadFromJson(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_FILE_BYTES) {
            activeRules = List.of();
            return 0;
        }
        try {
            RuleFileDto file = GSON.fromJson(json, RuleFileDto.class);
            if (file == null || !file.enabled || file.rules == null || file.rules.isEmpty()) {
                activeRules = List.of();
                return 0;
            }
            List<CompiledRule> compiled = new ArrayList<>();
            for (RuleDto dto : file.rules) {
                if (compiled.size() >= MAX_RULES) break;
                CompiledRule cr = compile(dto);
                if (cr != null) {
                    compiled.add(cr);
                }
            }
            compiled.sort(Comparator.comparingInt((CompiledRule r) -> r.dto().priority).reversed());
            activeRules = List.copyOf(compiled);
            return activeRules.size();
        } catch (Throwable ignored) {
            activeRules = List.of();
            return 0;
        }
    }

    public static void clear() {
        activeRules = List.of();
    }

    public static int activeRuleCount() {
        return activeRules.size();
    }

    /** Evaluates active rules in descending priority order against incoming message signals. */
    public static ShareCard match(UniversalCardMatcher.MessageSignals signals) {
        List<CompiledRule> rules = activeRules;
        if (signals == null || rules.isEmpty()) return null;
        String cleanText = signals.rawText().replaceAll("§.", "").trim();
        if (cleanText.isEmpty() || cleanText.length() > 400) return null;

        for (CompiledRule rule : rules) {
            try {
                ShareCard card = tryRule(rule, signals, cleanText);
                if (card != null) return card;
            } catch (RegexBudgetExceeded ignored) {
                // Step-bounded ReDoS protection: skip runaway rule safely without stalling client thread.
            }
        }
        return null;
    }

    private static ShareCard tryRule(CompiledRule rule, UniversalCardMatcher.MessageSignals signals, String cleanText) {
        RuleDto dto = rule.dto();
        if (dto.systemOnly && !signals.system()) return null;

        if (dto.translationKeyPrefix != null && !dto.translationKeyPrefix.isBlank()) {
            boolean keyMatched = false;
            for (String k : signals.translationKeys()) {
                if (k.startsWith(dto.translationKeyPrefix)) {
                    keyMatched = true;
                    break;
                }
            }
            if (!keyMatched) return null;
        }

        boolean hasCmdPrefix = dto.commandPrefix != null && !dto.commandPrefix.isBlank();
        List<UniversalCardMatcher.ClickActionSpec> matchedClicks = new ArrayList<>();
        if (hasCmdPrefix) {
            String pref = dto.commandPrefix.toLowerCase(Locale.ROOT);
            for (UniversalCardMatcher.ClickActionSpec c : signals.clicks()) {
                if (c.isExecutableCommand() && c.value().toLowerCase(Locale.ROOT).startsWith(pref)
                        && !UniversalCardMatcher.isDangerousCommand(c.value())) {
                    matchedClicks.add(c);
                }
            }
            if (matchedClicks.isEmpty()) return null;
        } else {
            for (UniversalCardMatcher.ClickActionSpec c : signals.clicks()) {
                if (c.isExecutableCommand() && !UniversalCardMatcher.isDangerousCommand(c.value())) {
                    matchedClicks.add(c);
                }
            }
        }

        Matcher matcher = null;
        if (rule.textPattern() != null) {
            matcher = rule.textPattern().matcher(new BoundedCharSequence(cleanText, MAX_REGEX_STEPS));
            if (!matcher.find()) return null;
        }

        String title = expandGroupsSinglePass(dto.title, matcher);
        if (title.isBlank()) title = BuiltInCardParsers.sanitizeLabel(cleanText, 48);
        String subtitle = expandGroupsSinglePass(dto.subtitle, matcher);
        String source = BuiltInCardParsers.sanitizeLabel(expandGroupsSinglePass(dto.source, matcher), 20);
        String avatar = "";
        if (dto.avatarGroup > 0 && matcher != null && dto.avatarGroup <= matcher.groupCount()) {
            String g = matcher.group(dto.avatarGroup);
            if (g != null) avatar = BuiltInCardParsers.sanitizeLabel(g, 20);
        }

        String cardId = "rule:" + BuiltInCardParsers.sanitizeLabel(dto.id, 20) + ":" + RULE_CARD_SEQ.incrementAndGet();
        ShareCard.Builder b;
        if (rule.template() == ShareCard.Template.RED_PACKET) {
            b = ShareCard.redPacket(cardId)
                    .title(title)
                    .subtitle(subtitle)
                    .source(source.isEmpty() ? "模块商店" : source)
                    .accentColor(rule.accentArgb())
                    .redPacketPayload(new ShareCard.RedPacketPayload(
                            cardId, title, subtitle, true, false, "", 0, 0, "", false));
            if (!matchedClicks.isEmpty()) {
                UniversalCardMatcher.ClickActionSpec primary = matchedClicks.get(0);
                b.bodyAction(new ShareCard.CardAction("body", "", primary.kind(), primary.value(), true));
            }
        } else {
            b = ShareCard.general(title)
                    .cardId(cardId)
                    .title(title)
                    .subtitle(subtitle)
                    .source(source)
                    .accentColor(rule.accentArgb())
                    .generalPayload(new ShareCard.GeneralPayload(title, subtitle, "", "", avatar, rule.accentArgb()));
        }

        List<String> nativeRunCmds = new ArrayList<>();
        if (rule.template() == ShareCard.Template.RED_PACKET) {
            if (!matchedClicks.isEmpty() && matchedClicks.get(0).kind() == ShareCard.ActionKind.RUN_COMMAND) {
                nativeRunCmds.add(matchedClicks.get(0).value());
            }
        } else {
            int maxBtns = Math.min(2, matchedClicks.size());
            for (int i = 0; i < maxBtns; i++) {
                UniversalCardMatcher.ClickActionSpec c = matchedClicks.get(i);
                // Only allow custom buttonLabel relabeling when rule explicitly pinned commandPrefix
                String label = (i == 0 && hasCmdPrefix && dto.buttonLabel != null && !dto.buttonLabel.isBlank())
                        ? BuiltInCardParsers.sanitizeLabel(dto.buttonLabel, 10)
                        : BuiltInCardParsers.sanitizeLabel(c.label().replaceAll("[\\[\\]【】]", ""), 10);
                if (label.isEmpty()) label = i == 0 ? "确认" : "取消";
                b.addAction("rule_btn_" + i, label, c.kind(), c.value(), i == 0);
                if (c.kind() == ShareCard.ActionKind.RUN_COMMAND) {
                    nativeRunCmds.add(c.value());
                }
            }
        }
        ShareCard card = b.build();
        return nativeRunCmds.isEmpty() ? card : card.withNativeClickCommands(nativeRunCmds);
    }

    private static CompiledRule compile(RuleDto dto) {
        if (dto == null || !dto.enabled) return null;
        boolean hasMatcher = (dto.textRegex != null && !dto.textRegex.isBlank())
                || (dto.commandPrefix != null && !dto.commandPrefix.isBlank())
                || (dto.translationKeyPrefix != null && !dto.translationKeyPrefix.isBlank());
        if (!hasMatcher) return null;

        Pattern pat = null;
        if (dto.textRegex != null && !dto.textRegex.isBlank()) {
            if (dto.textRegex.length() > MAX_REGEX_LEN) return null;
            try {
                pat = Pattern.compile(dto.textRegex);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        int color = parseHexColor(dto.accentColor, 0xFF3B82F6);
        ShareCard.Template tpl = ShareCard.Template.GENERAL;
        if (dto.template != null && !dto.template.isBlank()) {
            try {
                tpl = ShareCard.Template.valueOf(dto.template.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                tpl = ShareCard.Template.GENERAL;
            }
        }
        return new CompiledRule(dto, pat, color, tpl);
    }

    /** Single-pass $1..$9 expansion (immune to cross-injection when group $1 contains "$2"). */
    private static String expandGroupsSinglePass(String template, Matcher sourceMatcher) {
        if (template == null || template.isBlank()) return "";
        if (sourceMatcher == null) return BuiltInCardParsers.sanitizeLabel(template, 64);
        Matcher ref = GROUP_REF.matcher(template);
        StringBuilder sb = new StringBuilder(template.length() + 16);
        int maxGroup = sourceMatcher.groupCount();
        while (ref.find()) {
            int idx = ref.group(1).charAt(0) - '0';
            String val = (idx >= 1 && idx <= maxGroup) ? sourceMatcher.group(idx) : "";
            ref.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : val));
        }
        ref.appendTail(sb);
        return BuiltInCardParsers.sanitizeLabel(sb.toString(), 64);
    }

    private static int parseHexColor(String hex, int fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        try {
            if (s.length() == 6) {
                return 0xFF000000 | Integer.parseInt(s, 16);
            }
            if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }

    private static final class RegexBudgetExceeded extends RuntimeException {
    }

    /** Step-bounded CharSequence protecting the client render thread against catastrophic regex backtracking. */
    private static final class BoundedCharSequence implements CharSequence {
        private final CharSequence inner;
        private int remaining;

        BoundedCharSequence(CharSequence inner, int maxSteps) {
            this.inner = inner;
            this.remaining = maxSteps;
        }

        @Override
        public int length() {
            return inner.length();
        }

        @Override
        public char charAt(int index) {
            if (--remaining < 0) {
                throw new RegexBudgetExceeded();
            }
            return inner.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new BoundedCharSequence(inner.subSequence(start, end), Math.max(0, remaining));
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }
}
