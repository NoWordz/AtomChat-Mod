package com.atom.chat.settings;

import com.atom.chat.avatar.AvatarImage;
import com.atom.chat.banner.BannerImage;
import com.atom.chat.banner.BannerStore;
import com.atom.chat.chat.BlockList;
import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.PlayerRef;
import com.atom.chat.net.PokeCompanionClient;
import com.atom.chat.page.MessageListView;
import com.atom.chat.page.ProfilePage;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The profile page's avatar-and-banner half, the avatar click competition window
 * and poking (D18b): the page a click opens, the picker a card row opens, and
 * who is allowed to learn that they were poked.
 *
 * <p><b>The competition window is a draw-driven state machine.</b> A single
 * avatar click may still turn out to be the first half of a double click, so it
 * parks itself as <em>pending</em> and the profile only opens once the window
 * has elapsed. That verdict is reached inside a rendered frame, from the
 * panel's own {@code nowMs}: {@link #settlePendingAvatarClick(long, boolean)}
 * is called by the shell at the exact spot the inline code used to sit in
 * {@code drawPanel}. Moving it to a tick or to an input path would change both
 * the window's semantics and the frame order — a closing panel drops the
 * pending click, and a pending click past {@link #AVATAR_CLICK_WINDOW_MS} opens
 * the profile.</p>
 *
 * <p><b>The click's three branches are the contract</b> (they were a fix, not a
 * default, so their order is load-bearing): a double click <em>pokes and
 * cancels the pending single click</em>; a single click with poke available
 * <em>parks</em> and waits out the window; with poke off there is nothing to
 * compete with, so the profile opens at once.</p>
 *
 * <p><b>Poking is a nudge at somebody else.</b> A poke aimed at yourself is
 * refused; a blocked player's incoming poke is dropped silently — like their
 * chat, no cue and no toast; the outgoing toast is truthful about delivery
 * ({@code poked} when the server negotiated the companion channel, {@code
 * pokedLocal} when only the local wobble played); and {@link #onTestPoked} is
 * deliberately <em>apart</em> from {@link #onPoked}, because the test poke is
 * always addressed to another player while {@code onPoked} rejects a poke from
 * yourself — routing the test through it would drop every test poke.</p>
 *
 * <p>Ports on {@link Host}: D18's page plus the push, the poke config and the
 * platform's poke client, the incoming poke's route, the shell's labels (D14's
 * clipboard, a player's display name, D7's language lookup) and its sinks
 * (feedback, cue, wobble), plus the three profile-card actions that never leave
 * the shell. A message is not a port but {@link MessageFacts} — a neutral shape
 * this class names, because the mapping layer's own message type is spelled
 * {@code Component} on the official builds and {@code Text} on the yarn one.
 * Nothing here is D18 state except the competition-window fields.</p>
 *
 * <p>One copy, in the shared layer: the mapping-layer types it drives
 * ({@link ProfilePage}, {@link MessageListView}) and the platform-net type
 * ({@link PokeCompanionClient}) exist at this same name on every target — the
 * seam manifest's 甲-group case. A twin pair here would be byte-identical,
 * which {@code verify_targets} rejects.</p>
 */
public final class AvatarClickWiring {
    /** Single-click→profile competition window (QQ standard), in ms. */
    public static final long AVATAR_CLICK_WINDOW_MS = 300;

    /** What the screen hands over: D18's page, the poke config and the shell's sinks. */
    public interface Host {
        /** D18: the profile page this domain fills in and pushes. */
        ProfilePage profilePage();

        /** D18: opens another player's profile as a pushed detail page. */
        void openProfileDetailPage();

        /** The local player's id, or null when there is no player — read live. */
        UUID localPlayerId();

        /** The poke feature's config switch. */
        boolean pokeConfigEnabled();

        /** Whether decorative motion is on (poke is off without it). */
        boolean animationsEnabled();

        /**
         * Clears the owned avatar file; true when it is gone (the page and the
         * own bubbles fall back). The decode is released here either way.
         */
        boolean clearAvatar();

        /**
         * The poke companion's send: true when the server negotiated the channel
         * (the toast then says "sent"), false when only the local wobble played.
         */
        boolean pokeSend(UUID target);

        /** The poke companion's handler slot: the screen's incoming-poke route. */
        void installPokeHandler(Consumer<UUID> handler);

        /** A player's display name from the tab list, falling back to the raw uuid. */
        String displayNameFor(UUID uuid);

        /** Whether this player's chat is blocked (their poke is dropped too). */
        boolean isBlocked(String name);

        /** The wobble on a player's avatar row. */
        void pokeWobble(UUID uuid, long nowMs);

        /** The poke cue. */
        void playPokeSound();

        /** Raises an acknowledgement with no argument. */
        void feedback(String key, boolean success);

        /** D7: language lookup for a key with one argument; null when unknown. */
        String label(String key, String arg);

        /** Raises an acknowledgement whose text carries a player name. */
        void feedback(String key, String label, boolean success);

        /** Opens the system picker for the avatar file. */
        void pickAvatarFile();

        /** Opens the system picker for the banner file. */
        void pickBannerFile();

        /** Deletes the custom banner file; true when the file is gone. */
        boolean clearBanner();

        /** D14: the clipboard write, as the colour picker and the menus use it. */
        boolean copyToClipboard(String text);

        /** D18a: the signature borrow in (the page has one handler, D18b fills it). */
        void beginSignatureEdit();

        /** D18a: the signature borrow out, {@code commit} deciding the write-back. */
        void endSignatureEdit(boolean commit);
    }

    /**
     * One message row's facts, and the only way this domain reads a message.
     *
     * <p>{@link ChatMessage} is a mapping-layer type: the official builds name it
     * {@code Component} and the yarn build names it {@code Text}, so nothing in
     * the shared layer — this class's test included — may spell it out. The
     * adapter is the mapping layer's own one-liner; everything above it speaks
     * these five facts. Without a uuid there is nobody to poke and no profile to
     * open, which is why both are nullable.</p>
     */
    public interface MessageFacts {
        /** The sender's uuid, or null when identity never arrived. */
        UUID senderUuid();

        /** The sender's real profile name from the capture pipeline. */
        String profileName();

        /** The sender's display name for the poke toast. */
        String senderName();

        /** True for the local player's own messages. */
        boolean own();

        /** True for server lines rather than a player's. */
        boolean system();

        /** The mapping layer's adapter: a real message becomes these five facts. */
        static MessageFacts of(UUID senderUuid, String profileName, String senderName,
                              boolean own, boolean system) {
            return new MessageFacts() {
                @Override
                public UUID senderUuid() {
                    return senderUuid;
                }

                @Override
                public String profileName() {
                    return profileName == null ? senderName : profileName;
                }

                @Override
                public String senderName() {
                    return senderName;
                }

                @Override
                public boolean own() {
                    return own;
                }

                @Override
                public boolean system() {
                    return system;
                }
            };
        }
    }

    /**
     * The poke paths' own slice of {@link Host}: the lifecycle (install and
     * uninstall a handler), the incoming poke and the test poke. Kept as one
     * object so the shell can hand the domain to the command layer without
     * exposing its whole surface.
     */
    public final class Poke {
        private final AvatarClickWiring owner;
        private final Host host;

        Poke(AvatarClickWiring owner, Host host) {
            this.owner = owner;
            this.host = host;
        }

        /** Routes incoming remote pokes to this screen while it is open. */
        public void install() {
            host.installPokeHandler(owner::onPoked);
        }

        /** Gives the poke companion's handler slot back when the screen closes. */
        public void uninstall() {
            host.installPokeHandler(null);
        }

        /** {@link AvatarClickWiring#onPoked}. */
        public void received(UUID from) {
            owner.onPoked(from);
        }

        /** {@link AvatarClickWiring#onTestPoked}. */
        public void tested(UUID target, String targetName) {
            owner.onTestPoked(target, targetName);
        }
    }

    private final Host host;

    private final Poke poke;

    private long lastAvatarClickTime;
    private int lastAvatarClickIndex = -1;
    private long pendingAvatarClickTime;
    private int pendingAvatarClickIndex = -1;
    private MessageFacts pendingAvatarClickMessage;

    public AvatarClickWiring(Host host) {
        this.host = host;
        this.poke = new Poke(this, host);
    }

    /** The poke half: what the screen's lifecycle and command layer call. */
    public Poke poke() {
        return poke;
    }

    /**
     * The profile page's handler half that is not the signature: the two card
     * pickers, the two clears and the clipboard row. The signature borrow is
     * D18a's half — the page has one handler, so those two stay with the screen.
     */
    public ProfilePage.Handler handler() {
        return new ProfilePage.Handler() {
            @Override
            public void openAvatarPicker() {
                host.pickAvatarFile();
            }

            @Override
            public void clearAvatar() {
                boolean removed = host.clearAvatar();
                AvatarImage.release();
                if (removed || com.atom.chat.profile.ProfileSync.remoteOnlyImage(com.atom.chat.profile.ProfileField.AVATAR)) {
                    com.atom.chat.profile.ProfileSync.imageCleared(com.atom.chat.profile.ProfileField.AVATAR);
                    removed = true;
                }
                host.feedback(removed ? "atomchat.feedback.avatarCleared"
                        : "atomchat.feedback.avatarClearFailed", removed);
            }

            @Override
            public void openBannerPicker() {
                host.pickBannerFile();
            }

            @Override
            public void clearBanner() {
                // Delete image = off: the file goes, the accent gradient returns.
                boolean removed = host.clearBanner();
                BannerImage.release();
                if (removed || com.atom.chat.profile.ProfileSync.remoteOnlyImage(com.atom.chat.profile.ProfileField.BANNER)) {
                    com.atom.chat.profile.ProfileSync.imageCleared(com.atom.chat.profile.ProfileField.BANNER);
                    removed = true;
                }
                host.feedback(removed ? "atomchat.feedback.bannerCleared"
                        : "atomchat.feedback.bannerClearFailed", removed);
            }

            @Override
            public boolean copyText(String text) {
                return host.copyToClipboard(text);
            }

            @Override
            public void beginSignatureEdit() {
                // The page has one handler: D18b owns it, D18a's borrow passes through.
                host.beginSignatureEdit();
            }

            @Override
            public void endSignatureEdit(boolean commit) {
                host.endSignatureEdit(commit);
            }
        };
    }

    /**
     * Opens another player's profile as a pushed detail page. Public because a
     * player card and the notification jump open one the same way.
     */
    public void openProfileDetail(PlayerRef player) {
        if (player == null || player.realName() == null) {
            return;
        }
        host.profilePage().setSubject(player);
        host.openProfileDetailPage();
    }

    /** Opens the profile detail for a message's sender (avatar single click). */
    public void openProfileFor(MessageFacts msg) {
        if (msg == null || msg.system() || msg.own()) {
            return;
        }
        PlayerRef player = PlayerRef.of(msg.senderUuid(), msg.profileName());
        if (player == null || player.realName() == null) {
            return;
        }
        openProfileDetail(player);
    }

    /**
     * The avatar click's competition window. {@code nowMs} is the frame's clock,
     * exactly as the inline code took {@code System.currentTimeMillis()} once;
     * {@code msg} and {@code index} are the hit's own facts.
     */
    public void handleAvatarClick(long nowMs, MessageFacts msg, int index) {
        UUID uuid = msg == null ? null : msg.senderUuid();
        // Poke is a nudge at *someone else*: your own avatar has no one to
        // notify, so it falls straight through to the profile.
        boolean pokeEnabled = host.pokeConfigEnabled() && host.animationsEnabled()
                && msg != null && !msg.own() && uuid != null;
        boolean doubleClick = pokeEnabled
                && lastAvatarClickIndex == index
                && nowMs - lastAvatarClickTime < AVATAR_CLICK_WINDOW_MS;
        if (doubleClick) {
            // Double click → poke; cancels the pending single click.
            lastAvatarClickTime = 0;
            pendingAvatarClickMessage = null;
            sendPoke(msg);
        } else if (pokeEnabled) {
            // QQ-style competition window: the single click waits for
            // the double-click threshold before opening the profile.
            lastAvatarClickTime = nowMs;
            lastAvatarClickIndex = index;
            pendingAvatarClickTime = nowMs;
            pendingAvatarClickIndex = index;
            pendingAvatarClickMessage = msg;
        } else {
            // With poke (or decorative motion) off a double click does
            // nothing, so there is nothing to compete with: jump at once.
            lastAvatarClickTime = 0;
            openProfileFor(msg);
        }
    }

    /**
     * The per-frame verdict on a parked single click, called from the panel's
     * draw at the spot the inline code sat: a closing panel drops it, and once
     * the window has elapsed the profile opens. The side effect stays in the
     * frame — moving it to a tick or an input path would change the window.
     */
    public void settlePendingAvatarClick(long nowMs, boolean closing) {
        if (pendingAvatarClickMessage == null) {
            return;
        }
        if (closing) {
            pendingAvatarClickMessage = null;
        } else if (nowMs - pendingAvatarClickTime >= AVATAR_CLICK_WINDOW_MS) {
            MessageFacts pending = pendingAvatarClickMessage;
            pendingAvatarClickMessage = null;
            pendingAvatarClickIndex = -1;
            openProfileFor(pending);
        }
    }

    /** True while a single click is waiting out the competition window. */
    public boolean hasPendingAvatarClick() {
        return pendingAvatarClickMessage != null;
    }

    /**
     * Pokes {@code target}: the local wobble and cue play either way, and a
     * real poke is sent when the server negotiated the companion channel. The
     * wobble plays for the sender immediately; the target's client wobbles when
     * the server relays it.
     */
    private void sendPoke(MessageFacts target) {
        UUID uuid = target == null ? null : target.senderUuid();
        if (uuid == null) {
            return;
        }
        host.pokeWobble(uuid, System.currentTimeMillis());
        host.playPokeSound();
        // The toast is truthful about delivery: without the companion channel the
        // poke is local only (the wobble still plays), so the label says so.
        boolean sent = host.pokeSend(uuid);
        host.feedback(sent ? "atomchat.feedback.poked" : "atomchat.feedback.pokedLocal",
                host.label(sent ? "atomchat.feedback.poked" : "atomchat.feedback.pokedLocal",
                        target.senderName()), true);
    }

    /**
     * Incoming poke from {@code from}: wobble that player's row, cue and toast.
     *
     * <p>Reachable only for another player, because a poke to yourself is
     * rejected everywhere upstream — which holds for the real relay and for the
     * ping-pong poke in the test round-trip alike.</p>
     */
    public void onPoked(UUID from) {
        if (from == null || from.equals(host.localPlayerId())) {
            return;
        }
        // A blocked player's poke is dropped like their chat: no cue, no toast.
        if (BlockList.isBlocked(host.displayNameFor(from))) {
            return;
        }
        host.pokeWobble(from, System.currentTimeMillis());
        host.playPokeSound();
        host.feedback("atomchat.feedback.pokeReceived",
                host.label("atomchat.feedback.pokeReceived", host.displayNameFor(from)), true);
    }

    /**
     * A poke simulated by {@code /atomchat test poke}, addressed to somebody
     * else. The cue already played on the client side; the wobble and the toast
     * are what this adds. Kept apart from {@link #onPoked} on purpose: that path
     * rejects a poke from yourself, and the test is always addressed to another
     * player, so routing it there would drop every test poke silently.
     */
    public void onTestPoked(UUID target, String targetName) {
        host.pokeWobble(target, System.currentTimeMillis());
        host.feedback("atomchat.feedback.pokeReceived",
                host.label("atomchat.feedback.pokeReceived", targetName), true);
    }
}
