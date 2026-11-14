package com.atom.chat.ui;
import com.atom.chat.onboarding.TutorialPointerPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TutorialPointerPolicyTest {
    @Test void overviewAndChatTasksHaveAnExplicitReturnDestination() {
        assertEquals(com.atom.chat.nav.AppPage.SETTINGS,TutorialPointerPolicy.returnPage(com.atom.chat.onboarding.TutorialSession.Step.SETTINGS_HOME));
        assertEquals(com.atom.chat.nav.AppPage.WORLD_CHAT,TutorialPointerPolicy.returnPage(com.atom.chat.onboarding.TutorialSession.Step.AVATAR_PROFILE));
        assertNull(TutorialPointerPolicy.returnPage(com.atom.chat.onboarding.TutorialSession.Step.APPEARANCE));
    }
    @Test void explicitHeaderFilterIsAllowedWithoutOpeningTheRestOfTheHeader() {
        var header=new UiLayout.Rect(0,0,500,60);var chips=new UiLayout.Rect(0,60,500,40);
        var filter=new UiLayout.Rect(440,10,40,40);
        assertTrue(TutorialPointerPolicy.allows(null,header,chips,List.of(filter),460,30));
        assertFalse(TutorialPointerPolicy.allows(null,header,chips,List.of(filter),20,30));
        assertFalse(TutorialPointerPolicy.allows(null,header,chips,List.of(filter),20,80));
    }
    @Test void onlyCurrentGroupAndItsActivePopupCanReceivePointer() {
        var header=new UiLayout.Rect(0,0,500,60);var chips=new UiLayout.Rect(0,60,500,40);
        var group=new UiLayout.Rect(20,200,450,180);var popup=new UiLayout.Rect(100,120,300,300);
        assertTrue(TutorialPointerPolicy.allows(group,header,chips,List.of(popup),30,230));
        assertTrue(TutorialPointerPolicy.allows(group,header,chips,List.of(popup),150,150));
        assertFalse(TutorialPointerPolicy.allows(group,header,chips,List.of(popup),30,30));
        assertFalse(TutorialPointerPolicy.allows(group,header,chips,List.of(popup),30,75));
        assertFalse(TutorialPointerPolicy.allows(group,header,chips,List.of(popup),480,700));
    }
}
