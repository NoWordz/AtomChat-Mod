package com.atom.chat.settings;

import com.atom.chat.avatar.AvatarStore;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.page.ProfilePage;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.ScrollSyncService;
import com.atom.chat.ui.UiLayout;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ProfilePageWiring}'s signature half, offline: the parking ticket, the
 * abort paths, the role row and the per-frame draft feed.
 *
 * <p><b>Scope, stated up front.</b> A recording composer stands in for D21's
 * field and a real {@link ProfilePage} for the page, so "abort never commits"
 * and "the draft is only fed while editing" are asserted on the real objects.
 * NOT covered: the IME carrier itself (a real input method needs a running game)
 * and anything the page draws — those are the D18a probe list: IME composition
 * in the signature field, and the page's own render path.</p>
 */
class ProfilePageWiringTest {
    @BeforeAll
    static void installTemporaryPlatform(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            @Override
            public Path configDir() {
                return root.resolve("config");
            }

            @Override
            public Path gameDir() {
                return root.resolve("game");
            }

            @Override
            public boolean isModLoaded(String modId) {
                return false;
            }
        });
    }

    @TempDir
    Path tmp;

    /** Records what the wiring asked of the shell, in order. */
    private static final class Composer implements ProfilePageWiring.Host {
        final List<String> calls = new ArrayList<>();
        final ProfilePage page;
        String text = "";
        boolean focused = true;
        UUID localId;
        boolean isOperator;

        Composer(Path avatarDir) {
            page = new ProfilePage(new ProfilePage.Handler() {
                @Override
                public void openAvatarPicker() {
                }

                @Override
                public void clearAvatar() {
                }

                @Override
                public void openBannerPicker() {
                }

                @Override
                public void clearBanner() {
                }

                @Override
                public boolean copyText(String value) {
                    return true;
                }

                @Override
                public void beginSignatureEdit() {
                    calls.add("pageBegin");
                }

                @Override
                public void endSignatureEdit(boolean commit) {
                    calls.add("pageEnd:" + commit);
                }
            }, new AvatarStore(avatarDir));
        }

        @Override
        public ProfilePage profilePage() {
            return page;
        }

        @Override
        public String composerText() {
            calls.add("read");
            return text;
        }

        @Override
        public void setComposerText(String value) {
            calls.add("set:" + value);
            text = value;
        }

        @Override
        public void focusComposer() {
            calls.add("focus");
        }

        @Override
        public void caretToEnd() {
            calls.add("caret");
        }

        @Override
        public UiLayout detailLayout() {
            return UiLayout.ofDetail(10.0F, 10.0F, 400.0F, 780.0F);
        }

        @Override
        public ScrollSyncService scrollSync() {
            throw new AssertionError("not needed by the signature half's tests");
        }

        @Override
        public float toVirtualX(double guiX) {
            return (float) guiX;
        }

        @Override
        public float toVirtualY(double guiY) {
            return (float) guiY;
        }

        @Override
        public UUID localPlayerId() {
            return localId;
        }

        @Override
        public boolean localPlayerHasPermissions(int level) {
            calls.add("perm:" + level);
            return isOperator;
        }

        @Override
        public boolean composerFocused() {
            return focused;
        }
    }

    private ProfilePageWiring wiring(Composer composer) {
        return new ProfilePageWiring(composer);
    }

    @Test
    void beginningParksTheDraftLoadsTheSignatureAndFocusesInOrder() {
        Composer composer = new Composer(tmp.resolve("avatar"));
        composer.text = "draft in progress";
        AtomChatConfig cfg = AtomChatConfig.get();
        String original = cfg.playerSignature;
        try {
            cfg.playerSignature = "my signature";
            wiring(composer).beginSignatureEdit();

            assertEquals(List.of("read", "set:my signature", "focus", "caret"), composer.calls,
                    "park the draft, load the signature, focus, then caret");
            assertEquals("my signature", composer.text);
            assertTrue(composer.page.isSignatureEditing(), "the page flips into its editing draw");
        } finally {
            AtomChatConfig.get().playerSignature = original;
        }
    }

    @Test
    void committingCapsAt256AndStoresItWhileAbortingOnlyPutsTheDraftBack() {
        Composer composer = new Composer(tmp.resolve("avatar"));
        AtomChatConfig cfg = AtomChatConfig.get();
        String original = cfg.playerSignature;
        try {
            ProfilePageWiring wiring = wiring(composer);
            composer.text = "draft";
            wiring.beginSignatureEdit();
            composer.text = "x".repeat(300);
            composer.calls.clear();

            wiring.endSignatureEdit(true);

            assertEquals(256, AtomChatConfig.get().playerSignature.length(), "the signature is capped");
            assertEquals("draft", composer.text, "and the parked draft comes back");

            composer.calls.clear();
            wiring.beginSignatureEdit();
            composer.text = "throw this away";
            composer.calls.clear();
            wiring.endSignatureEdit(false);

            assertEquals("x".repeat(256), AtomChatConfig.get().playerSignature,
                    "aborting must not touch the stored signature");
            assertEquals("draft", composer.text);
            assertFalse(composer.calls.contains("pageEnd:true"), composer.calls.toString());
        } finally {
            AtomChatConfig.get().playerSignature = original;
        }
    }

    @Test
    void theRoleRowAnswersOnlyForTheLocalPlayer() {
        Composer composer = new Composer(tmp.resolve("avatar"));
        ProfilePage.RoleResolver resolver = wiring(composer).roleResolver();
        UUID local = UUID.randomUUID();
        composer.localId = local;
        composer.isOperator = true;

        assertEquals(Boolean.TRUE, resolver.isOperator(local, "me"));
        assertTrue(composer.calls.contains("perm:2"), "the local player's own level is asked for");
        assertNull(resolver.isOperator(UUID.randomUUID(), "someone else"),
                "everyone else stays unknown, which hides the row");
        assertNull(resolver.isOperator(null, "nobody"));

        composer.localId = null;
        assertNull(resolver.isOperator(local, "me"), "no player means no answer");
    }

    @Test
    void theDraftIsOnlyFedWhileThePageIsEditing() {
        Composer composer = new Composer(tmp.resolve("avatar"));
        ProfilePageWiring wiring = wiring(composer);
        composer.text = "typed";

        composer.calls.clear();
        wiring.syncProfileSignatureDraft();
        assertEquals(List.of(), composer.calls, "a page that is not editing gets nothing");

        composer.page.beginSignatureEdit();
        wiring.syncProfileSignatureDraft();
        assertTrue(composer.calls.contains("read"), composer.calls.toString());
    }
}
