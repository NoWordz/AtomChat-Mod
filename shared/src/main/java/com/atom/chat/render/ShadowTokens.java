package com.atom.chat.render;

import com.atom.lib.design.atomchat.UiTokens;

/** Contact and ambient profiles in canvas drawing units. */
final class ShadowTokens {
    record Layer(float sigma, float drop, int color) { }

    record Face(Layer contact, Layer ambient) {
        float pad() {
            float contactTail = 4F * contact.sigma() + contact.drop();
            float ambientTail = ambient == null ? 0F : 3F * ambient.sigma() + ambient.drop();
            return (float) Math.ceil(Math.max(contactTail, ambientTail) + 1F);
        }

        float wideSigma() {
            return ambient == null ? contact.sigma() : ambient.sigma();
        }
    }

    static final Face HUD = new Face(new Layer(2F, 1.5F, 0x70000000), null);
    static final Face HUD_AVATAR = new Face(new Layer(2F, 1.5F, 0x90000000), null);
    static final Face MESSAGE = new Face(layer(2.8F, 1.6F, 61), layer(4.3F, 1.8F, 13));
    static final Face CARD = new Face(layer(3.4F, 1.9F, 49), layer(5.5F, 2.1F, 13));
    static final Face CHROME = new Face(layer(4.1F, 2.4F, 61), layer(7F, 2.6F, 14));
    static final Face FLOAT = new Face(layer(4.1F, 2.4F, 61), layer(8F, 3F, 26));
    static final Face PANEL = new Face(layer(4.4F, 2.6F, 64), layer(10F, 4F, 36));
    private static final float FAB_PAD_LOGICAL = 36F;
    static final float FAB_PAD = UiTokens.s(FAB_PAD_LOGICAL);

    private static Layer layer(float sigma, float drop, int alpha) {
        return new Layer(UiTokens.s(sigma), UiTokens.s(drop), alpha << 24);
    }

    private ShadowTokens() { }
}
