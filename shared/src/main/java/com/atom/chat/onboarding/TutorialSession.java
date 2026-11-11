package com.atom.chat.onboarding;

/** Step state and isolated demonstration text, with no business-store or network dependencies. */
public final class TutorialSession {
    public enum Step {
        NAVIGATION, CONVERSATIONS, COMPOSE, MESSAGE_ACTIONS, SEARCH_FILTER,
        PLAYERS_BLOCK, PROFILE, EXPRESSIONS, IMAGES, QUICK_PHRASES,
        SETTINGS_HOME, CHAT_SETTINGS, APPEARANCE, PRIVACY, NOTIFICATIONS, ABOUT;
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
        public boolean requiresAction() {
            return this == NAVIGATION || this == COMPOSE || this == MESSAGE_ACTIONS
                    || this == PLAYERS_BLOCK || this == EXPRESSIONS || this == IMAGES || this == QUICK_PHRASES || this == SEARCH_FILTER;
        }
    }
    private int index = -1;
    private String demoText = "";
    private boolean performed;
    public boolean active() { return index >= 0; }
    public int index() { return index; }
    public Step step() { return active() ? Step.values()[index] : null; }
    public String demoText() { return demoText; }
    public boolean demoPerformed() { return performed; }
    public void start() { index = 0; clearDemo(); }
    public void next() { if (active()) { if (++index >= Step.values().length) exit(); else clearDemo(); } }
    public void previous() { if (active() && index > 0) { --index; clearDemo(); } }
    public void type(char c) { if (active() && !Character.isISOControl(c) && demoText.length() < 160) demoText += c; }
    public void backspace() { if (!demoText.isEmpty()) demoText = demoText.substring(0, demoText.offsetByCodePoints(demoText.length(), -1)); }
    public void performDemo() { if (active()) performed = true; }
    public void exit() { index = -1; clearDemo(); }
    private void clearDemo() { demoText = ""; performed = false; }
}
