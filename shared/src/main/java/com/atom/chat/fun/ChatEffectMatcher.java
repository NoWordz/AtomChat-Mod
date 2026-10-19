package com.atom.chat.fun;
import java.util.Optional;
import java.util.regex.Pattern;
/** Fixed phrase whitelist. Masking preserves offsets so earliest text wins. */
public final class ChatEffectMatcher {
    private static final Pattern EXCLUDED = Pattern.compile("https?://\\S+|www\\.\\S+|(?<!\\S)(?:[\\p{L}\\p{N}-]+\\.)+[\\p{L}]{2,}(?:/\\S*)?|```[\\s\\S]*?(?:```|$)|`[^`]*(?:`|$)|「引用[^」]*(?:」|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern[] RULES = {
        Pattern.compile("(?:我喜欢你|我爱你)(?!\\p{IsHan})|(?<![\\p{L}\\p{N}_/@.:-])(?:i\\s+(?:like|love)\\s+(?:you|u)|love\\s+(?:you|u))(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("生日快乐(?!\\p{IsHan})|(?<![\\p{L}\\p{N}_/@.:-])(?:happy\\s+(?:birthday|bday)|hbd)(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("新年快乐(?!\\p{IsHan})|(?<![\\p{L}\\p{N}_/@.:-])(?:happy\\s+new\\s+year|hny)(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
        Pattern.compile("晚安(?!\\p{IsHan})|(?<![\\p{L}\\p{N}_/@.:-])(?:good\\s+night|goodnight)(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    };
    private static final ChatEffectType[] TYPES = {
        ChatEffectType.HEART, ChatEffectType.CAKE,
        ChatEffectType.FIREWORK, ChatEffectType.STAR
    };
    public Optional<ChatEffectType> match(String body) {
        if (body == null || body.isBlank() || body.stripLeading().startsWith("/") || body.contains("CICode")) return Optional.empty();
        StringBuilder text = new StringBuilder(body);
        var excluded = EXCLUDED.matcher(body);
        while (excluded.find()) for (int i=excluded.start();i<excluded.end();i++) text.setCharAt(i, ' ');
        int earliest=Integer.MAX_VALUE;
        ChatEffectType found=null;
        for(int i=0;i<RULES.length;i++) {
            var m=RULES[i].matcher(text);
            if(m.find() && m.start()<earliest) { earliest=m.start(); found=TYPES[i]; }
        }
        return Optional.ofNullable(found);
    }
}
