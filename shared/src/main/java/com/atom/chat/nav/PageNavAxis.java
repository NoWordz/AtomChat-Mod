package com.atom.chat.nav;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.ui.UiStagger;

/**
 * The page-nav style as a motion axis — the one place the setting turns into
 * motion.
 *
 * <p><b>Why this exists.</b> The entrance work gave every nav surface (four
 * list pages, the tab bar) its own per-item motion, and each of them needs to
 * know which channels it may drive: SLIDE is horizontal, ZOOM is vertical.
 * The first cut duplicated that mapping as a private {@code axis()} in all
 * five classes. That is the exact shape of an older defect — a style resolved
 * in more than one place lets the surfaces disagree, and the field report was
 * "全局 zoom 和 slide 变得没区别了". One function, so a surface can only ask
 * what the axis <em>is</em>, never decide for itself.</p>
 *
 * <p>{@link UiStagger} itself stays config-free (pure arithmetic), which is why
 * this mapping lives beside the nav flow rather than in the stagger helper.</p>
 *
 * <p><b>Bucket.</b> Shared single source, no Minecraft names — only the shared
 * config and the stagger helper.</p>
 */
public final class PageNavAxis {

    private PageNavAxis() {
    }

    /** The axis a nav style drives; unknown styles fall back to the horizontal one. */
    public static UiStagger.Axis of(AtomChatConfig.PageNavStyle style) {
        return style == AtomChatConfig.PageNavStyle.ZOOM
                ? UiStagger.Axis.VERTICAL
                : UiStagger.Axis.HORIZONTAL;
    }

    /** The axis the configured nav style drives — the entry point surfaces use. */
    public static UiStagger.Axis current() {
        return of(AtomChatConfig.get().pageNavStyle);
    }
}
