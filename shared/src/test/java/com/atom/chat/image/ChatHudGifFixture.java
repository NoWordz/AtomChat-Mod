package com.atom.chat.image;

import io.github.humbleui.skija.Image;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

public final class ChatHudGifFixture {
    private ChatHudGifFixture() {}

    public static BiFunction<String, Boolean, Image> frames(AtomicLong clock) throws IOException {
        try (var input = ChatHudGifFixture.class.getResourceAsStream("/atomchat/animated_3frames.gif")) {
            byte[] gif = input.readAllBytes();
            ImageLoader loader = ImageLoader.forTest(url -> gif, clock::get, Runnable::run);
            loader.rememberLocallySent("http://test/hud.gif");
            return loader::getHudImage;
        }
    }
}
