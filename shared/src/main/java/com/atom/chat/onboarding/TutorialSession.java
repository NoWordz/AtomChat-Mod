package com.atom.chat.onboarding;

/** Step state and isolated demonstration text, with no business-store or network dependencies. */
public final class TutorialSession {
    public enum Step {
        NAVIGATION, CONVERSATIONS, COMPOSE, MESSAGE_ACTIONS, SEARCH_FILTER,
        PLAYERS_BLOCK, AVATAR_PROFILE, FILTER, PROFILE, EXPRESSIONS, IMAGES, QUICK_PHRASES,
        SETTINGS_HOME, CHAT_SETTINGS, APPEARANCE, PRIVACY, NOTIFICATIONS, ABOUT;
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
        public boolean requiresAction() {
            return this == NAVIGATION || this == COMPOSE || this == MESSAGE_ACTIONS
                    || this == PLAYERS_BLOCK || this == AVATAR_PROFILE || this == FILTER || this == EXPRESSIONS || this == IMAGES || this == QUICK_PHRASES || this == SEARCH_FILTER;
        }
    }
    private int index = -1;
    private java.util.List<TutorialGuide.Entry> itinerary=java.util.Arrays.stream(Step.values()).map(TutorialGuide.Entry::page).toList();
    public TutorialGuide.Entry entry(){return active()?itinerary.get(index):null;}
    public int count(){return itinerary.size();}
    public void itinerary(java.util.List<TutorialGuide.Entry> entries){if(entries.isEmpty())throw new IllegalArgumentException("Empty tutorial");itinerary=java.util.List.copyOf(entries);}

    private String demoText = "";
    private boolean performed;
    public boolean active() { return index >= 0; }
    public int index() { return index; }
    public Step step() { return active() ? itinerary.get(index).step() : null; }
    public String demoText() { return demoText; }
    public boolean demoPerformed() { return performed; }
    public void start() { index = 0; clearDemo(); }
    public void next() { if (active()) { if (++index >= itinerary.size()) exit(); else clearDemo(); } }
    public void previous() { if (active() && index > 0) { --index; clearDemo(); } }
    public void type(char c) { if (active() && !Character.isISOControl(c) && demoText.length() < 160) demoText += c; }
    public void backspace() { if (!demoText.isEmpty()) demoText = demoText.substring(0, demoText.offsetByCodePoints(demoText.length(), -1)); }
    public void performDemo() { if (active()) performed = true; }
    public void exit() { index = -1; clearDemo(); }
    private void clearDemo() { demoText = ""; performed = false; }
}
