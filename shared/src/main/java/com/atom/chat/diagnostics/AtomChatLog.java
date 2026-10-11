package com.atom.chat.diagnostics;
/** Formats templates only; logger arguments and Throwable handling remain intact. */
public final class AtomChatLog {
    private AtomChatLog() {}
    public static java.util.function.Supplier<String> message(String chain,java.util.function.Supplier<String> template) {
        return () -> message(chain,template.get());
    }
    public static String message(String chain,String template) {
        String prefix="[AtomChat]["+chain+"] ";
        String[] lines=String.valueOf(template).split("\\R",-1);
        for(int i=0;i<lines.length;i++) {
            String line=lines[i];
            while (line.startsWith("[AtomChat][")) {
                String stripped=line.replaceFirst("^\\[AtomChat\\]\\[[^]\\r\\n]+\\]\\s*", "");
                if (stripped.equals(line)) break;
                line=stripped;
            }
            line=line.replaceFirst("^\\[(?:atomchat|notify|test|avatar|capture|echo|route|sender|media)\\]\\s*", "");
            lines[i]=prefix+line;
        }
        return String.join("\n",lines);
    }
}
