package com.atom.chat.ui;

import com.atom.chat.nav.AppPage;
import com.atom.chat.nav.NavPage;
import com.atom.chat.settings.SettingsSection;
import io.github.humbleui.skija.Canvas;

/**
 * The header's own chrome (D20): the back button's rect and hover, the fixed
 * header a pushed page draws, and every title the shell bar can carry.
 *
 * <p><b>The back button's size is not this class's to choose.</b> Its side is
 * {@link UiTokens#ACTION_BUTTON_SIZE}, the same token the composer keys use, so
 * the header affordance and those keys cannot drift apart; the rect is pinned to
 * the header card's left edge ({@link UiTokens#EDGE_CONTROL_INSET}) and centred
 * vertically. A second hand-rolled size here is exactly the bug this comment
 * exists to prevent.</p>
 *
 * <p><b>The pushed header is fixed chrome, not part of the sliding page.</b>
 * {@link #drawPushedHeader} draws it through the shell's detail header rect and
 * never through anything that moves with the transition — during a push the
 * header is the "arriving" copy, in place from frame one. The hover spring it
 * steps is the same one {@link #stepBackButtonHover} advances on the settled
 * frames, so the two paths share one value instead of each keeping a copy.</p>
 *
 * <p><b>Titles say what the page is.</b> A private page names the partner by
 * <em>real</em> profile name (never a decorated label — that would leak display
 * formatting into navigation), a settings sub-page names its section, and the
 * root pages name their tabs. Missing pieces fall back exactly as they did: a
 * private page with no partner, a settings page with no section yet.</p>
 *
 * <p>Ports on {@link Host}: the language lookup, the current private partner (the
 * shell owns that state), the two header rects (the settled chat layout's and the
 * detail layout's), this frame's dt and the header's text colour. The ports exist
 * so this class — and its test — name no Minecraft API; the header itself is
 * drawn by {@link ShellHeader}, which already lives here.</p>
 *
 * <p>One copy, in the shared layer: every type it touches ({@link ShellHeader},
 * {@link NavPage}, {@link AppPage}, {@link SettingsSection}, {@link UiLayout},
 * {@link UiTokens}, {@link UiMotion}) already lives at this same name on every
 * target. A twin pair would be byte-identical, which {@code verify_targets}
 * rejects.</p>
 */
public final class HeaderChrome {
    /** What the screen hands over: labels, the partner, the rects and the colours. */
    public interface Host {
        /** D7: the language lookup for a title key. */
        String label(String key);

        /**
         * The current private partner's real profile name, or null when the
         * private page has no partner (the tab falls back to its own label).
         */
        String activePrivateName();

        /** The settled chat layout's header rect — where the back button lives. */
        UiLayout.Rect headerRect();

        /** The detail layout's header rect — where a pushed page's header lives. */
        UiLayout.Rect pushedHeaderRect();

        /** This frame's dt, the clock the hover spring runs on. */
        float frameDt();

        /** The header's text colour (the theme's, D9). */
        int textPrimary();
    }

    private final Host host;

    /** Back-button hover wash, stepped by the shell's frame and by a pushed header. */
    private float backButtonHover;

    public HeaderChrome(Host host) {
        this.host = host;
    }

    /**
     * Shared hit rect for the world-chat header's SVG back button. It is fixed
     * to the left edge of the header card and vertically centered. The side comes
     * from ACTION_BUTTON_SIZE, the same token the composer keys use, so the
     * header button and those keys stay one size instead of drifting apart.
     */
    public UiLayout.Rect backButton() {
        float size = UiTokens.ACTION_BUTTON_SIZE;
        UiLayout.Rect header = host.headerRect();
        float y = header.y() + (header.h() - size) / 2.0F;
        return new UiLayout.Rect(header.x() + UiTokens.EDGE_CONTROL_INSET, y, size, size);
    }

    public boolean isBackButtonHit(float vmx, float vmy) {
        return backButton().contains(vmx, vmy);
    }

    /**
     * The hover spring, one step per frame: the shell calls this on its settled
     * frames, {@link #drawPushedHeader} calls it before it paints. Both keep the
     * original position — this only moved the arithmetic, not the call order.
     */
    public void stepBackButtonHover(float vmx, float vmy) {
        backButtonHover = UiMotion.approach(backButtonHover,
                isBackButtonHit(vmx, vmy) ? 1.0F : 0.0F, host.frameDt(), UiMotion.HOVER_MS);
    }

    /** The stepped hover value, for the header renders the shell keeps. */
    public float backButtonHover() {
        return backButtonHover;
    }

    /** Fixed header (with back affordance) for a pushed settings sub-page. */
    public void drawPushedHeader(Canvas canvas, float vmx, float vmy, NavPage nav) {
        stepBackButtonHover(vmx, vmy);
        ShellHeader.render(canvas, host.pushedHeaderRect(), shellTitleFor(nav), true,
                backButton(), backButtonHover, host.textPrimary(), null);
    }

    public String shellTitleFor(NavPage nav) {
        if (nav.page() == AppPage.PRIVATE_CHAT && nav.target() != null) {
            return nav.target().realName();
        }
        if (nav.page() == AppPage.SETTINGS_SECTION && nav.section() != null) {
            return settingsTitle(nav.section());
        }
        return shellTitleFor(nav.page());
    }

    public String settingsTitle(SettingsSection section) {
        return switch (section) {
            case APPEARANCE -> host.label("atomchat.settings.appearance");
            case CHAT -> host.label("atomchat.settings.chat");
            case PRIVACY -> host.label("atomchat.settings.privacy");
            case ABOUT -> host.label("atomchat.settings.about");
        };
    }

    public String shellTitleFor(AppPage page) {
        return switch (page) {
            case CHAT_LIST -> host.label("atomchat.tab.chat");
            case PROFILE -> host.label("atomchat.page.profile.title");
            case SETTINGS -> host.label("atomchat.tab.settings");
            case WORLD_CHAT -> host.label("atomchat.channel.world");
            case PRIVATE_CHAT -> host.activePrivateName() != null
                    ? host.activePrivateName() : host.label("atomchat.channel.private");
            case SETTINGS_SECTION -> host.label("atomchat.tab.settings");
            case PROFILE_DETAIL -> host.label("atomchat.page.profile.title");
        };
    }
}
