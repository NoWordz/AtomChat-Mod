package com.atom.chat.api.card;

import java.lang.System.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Preview API for client-side actions on item cards. Register during client
 * initialization. Labels, visibility and actions are evaluated on the client
 * thread. These registrations are independent of card wire actions.
 */
public final class ItemCardActions {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Logger LOGGER = System.getLogger(ItemCardActions.class.getName());
    private static final ConcurrentSkipListMap<String, Action> ACTIONS = new ConcurrentSkipListMap<>();

    /** The callback returns whether it handled the action. */
    public record Action(String id, Function<ShareCard, String> label,
                         Predicate<ShareCard> available, Predicate<ShareCard> activate) {
        public Action {
            if (!ID.matcher(Objects.requireNonNull(id)).matches()) {
                throw new IllegalArgumentException("Expected a namespaced action ID: " + id);
            }
            Objects.requireNonNull(label);
            Objects.requireNonNull(available);
            Objects.requireNonNull(activate);
        }
    }

    public record Button(String id, String label) {}

    private ItemCardActions() {}

    /** Registers a locally provided action. IDs must be unique across installed add-ons. */
    public static void register(Action action) {
        if (ACTIONS.putIfAbsent(action.id(), action) != null) {
            throw new IllegalArgumentException("Duplicate item card action: " + action.id());
        }
    }

    /** Returns an immutable snapshot, ordered by the registered IDs. */
    public static List<Button> visible(ShareCard card) {
        if (!eligible(card) || ACTIONS.isEmpty()) return List.of();
        List<Button> buttons = new ArrayList<>();
        for (Action action : ACTIONS.values()) {
            Button button = button(action, card);
            if (button != null) buttons.add(button);
        }
        return List.copyOf(buttons);
    }

    /** Revalidates the action still belongs to the given card before activating it. */
    public static boolean activate(ShareCard card, String id) {
        if (!eligible(card) || id == null) return false;
        Action action = ACTIONS.get(id);
        if (action == null || button(action, card) == null) return false;
        try {
            return action.activate().test(card);
        } catch (RuntimeException exception) {
            LOGGER.log(Logger.Level.WARNING, "Item card action failed: " + id, exception);
            return false;
        }
    }

    private static boolean eligible(ShareCard card) {
        return card != null && card.template() == ShareCard.Template.ITEM && card.item() != null
                && !card.item().itemId().isBlank();
    }

    private static Button button(Action action, ShareCard card) {
        try {
            if (!action.available().test(card)) return null;
            String label = action.label().apply(card);
            return label == null || label.isBlank() ? null : new Button(action.id(), label);
        } catch (RuntimeException exception) {
            LOGGER.log(Logger.Level.WARNING, "Could not display item card action: " + action.id(), exception);
            return null;
        }
    }
}
