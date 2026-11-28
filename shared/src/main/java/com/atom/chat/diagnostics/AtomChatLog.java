package com.atom.chat.diagnostics;

import com.atom.chat.AtomChat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** AtomChat log prefixes and the asynchronous output used by diagnostic mode. */
public final class AtomChatLog {
    private static final AtomicLong RECEIPTS = new AtomicLong();

    private AtomChatLog() {}

    private static final class Diagnostics {
        private static final AsyncDiagnosticOutput OUTPUT = new AsyncDiagnosticOutput(512, AtomChat.LOGGER::info);
    }

    public static void diagnostic(String category, String message) {
        Diagnostics.OUTPUT.offer(category, message);
    }

    public static long nextReceipt() {
        return RECEIPTS.incrementAndGet();
    }

    public static void finishDiagnostics() {
        try {
            if (Diagnostics.OUTPUT.finish(Duration.ofSeconds(2))) return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        long discarded = Diagnostics.OUTPUT.discardPending();
        // A blocked sink must not hold up closing the client.
        Thread report = new Thread(() -> AtomChat.LOGGER.warn(AtomChatLog.message(
                "diagnostics", "shutdown_incomplete discarded=" + discarded)), "AtomChat diagnostic exit report");
        report.setDaemon(true);
        report.start();
    }

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
