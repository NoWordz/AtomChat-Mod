package com.atom.chat.ui;

import com.atom.chat.render.Easing;

import java.util.Objects;

/** One header slot carries at most one visible value through a navigation change. */
final class HeaderFade<K, V> {
    record Layer<K, V>(K identity, V value, float opacity) {}

    private static final float HIT_OPACITY = 0.4F;
    private K destination;
    private Layer<K, V> drawn;
    private Layer<K, V> departure;
    private long startedAt;
    private boolean transitioning;

    Layer<K, V> draw(K identity, V value, long now, long duration) {
        if (drawn == null || duration <= 0) {
            destination = identity;
            transitioning = false;
            departure = null;
        } else if (!Objects.equals(destination, identity)) {
            destination = identity;
            departure = drawn;
            startedAt = now;
            transitioning = true;
        }

        if (transitioning) {
            long elapsed = Math.max(0, now - startedAt);
            float half = duration / 2.0F;
            if (identity != null && identity.equals(departure.identity())) {
                if (elapsed < half) {
                    float fraction = Easing.easeOutQuad(elapsed / half);
                    return remember(identity, value, departure.opacity() + (1 - departure.opacity()) * fraction);
                }
            } else if (elapsed < half) {
                return remember(departure.identity(), departure.value(),
                        departure.opacity() * (1 - Easing.easeOutQuad(elapsed / half)));
            } else if (identity != null && elapsed < duration) {
                return remember(identity, value, Easing.easeOutQuad((elapsed - half) / half));
            }
            transitioning = false;
            departure = null;
        }
        return remember(identity, value, identity == null ? 0 : 1);
    }

    boolean accepts(K identity) {
        return identity != null && drawn != null && identity.equals(drawn.identity()) && drawn.opacity() >= HIT_OPACITY;
    }

    boolean acceptsVisible(K identity) {
        return identity != null && identity.equals(destination) && drawn != null
                && identity.equals(drawn.identity()) && drawn.opacity() > 0;
    }

    private Layer<K, V> remember(K identity, V value, float opacity) {
        if (drawn == null || !Objects.equals(drawn.identity(), identity)
                || !Objects.equals(drawn.value(), value) || drawn.opacity() != opacity) {
            drawn = new Layer<>(identity, value, opacity);
        }
        return drawn;
    }
}
