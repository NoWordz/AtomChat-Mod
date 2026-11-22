package com.atom.chat.image;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

public final class ChatHudGifFixture {
    private ChatHudGifFixture() {}

    // A completely opaque frame, then a transparent canvas with a blue block.
    static final byte[] MIXED_ALPHA_GIF = Base64.getDecoder().decode(
            "R0lGODlhEgAKAIEAAAAAACiCvtJzLS2WaSH/C05FVFNDQVBFMi4wAwEAAAAh+QQJCgAAACwAAAAAEgAKAAAIJgAHCBxIsKDBgwgTDhDAsKFCgQ0dPozI8OFCihYpCsiI0aJHiwEBACH5BAkKAAAALAIAAgAGAAYAgQAAACiCvtJzLS2WaQgMAAMIHEiwoMGDCAMCADs=");

    public static BiFunction<String, Boolean, MessageImage> frames(AtomicLong clock) throws IOException {
        try (var input = ChatHudGifFixture.class.getResourceAsStream("/atomchat/animated_3frames.gif")) {
            return frames(clock, input.readAllBytes());
        }
    }

    public static BiFunction<String, Boolean, MessageImage> mixedAlphaFrames(AtomicLong clock) {
        return frames(clock, MIXED_ALPHA_GIF);
    }

    private static BiFunction<String, Boolean, MessageImage> frames(AtomicLong clock, byte[] gif) {
        ImageLoader loader = ImageLoader.forTest(url -> gif, clock::get, Runnable::run);
        loader.rememberLocallySent("http://test/hud.gif");
        return loader::getHudMessageImage;
    }
}
