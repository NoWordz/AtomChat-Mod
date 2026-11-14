package com.atom.chat.onboarding;

import com.atom.chat.ui.UiLayout;
import com.atom.chat.nav.AppPage;
import java.util.List;

/** Tutorial pointers are a whitelist of currently painted controls; navigation bands are never implicit. */
public final class TutorialPointerPolicy {
    private TutorialPointerPolicy() { }
    /** Steps that can open a child page must always retain a route back to their teaching page. */
    public static AppPage returnPage(TutorialSession.Step step) {
        if (step == null) return null;
        return switch (step) {
            case COMPOSE, MESSAGE_ACTIONS, PLAYERS_BLOCK, AVATAR_PROFILE, SEARCH_FILTER, FILTER,
                 EXPRESSIONS, QUICK_PHRASES, IMAGES -> AppPage.WORLD_CHAT;
            case SETTINGS_HOME -> AppPage.SETTINGS;
            default -> null;
        };
    }
    public static boolean buttonAllowed(TutorialSession.Step step, int button, boolean popup) {
        if (popup) return button == 0;
        if (step == TutorialSession.Step.PLAYERS_BLOCK || step == TutorialSession.Step.MESSAGE_ACTIONS)
            return button == 1;
        return button == 0;
    }
    public static boolean allows(UiLayout.Rect target,UiLayout.Rect header,UiLayout.Rect chips,List<UiLayout.Rect> extensions,float x,float y) {
        for(var rect:extensions)if(rect!=null&&rect.contains(x,y))return true;
        if(header.contains(x,y)||chips.contains(x,y))return false;
        if(target!=null&&target.contains(x,y))return true;
        return false;
    }
}
