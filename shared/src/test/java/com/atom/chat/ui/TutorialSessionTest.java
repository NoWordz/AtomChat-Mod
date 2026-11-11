package com.atom.chat.ui;
import com.atom.chat.onboarding.TutorialSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TutorialSessionTest {
    @Test void replayAlwaysStartsAtFirstStepAndDropsDemoContent() {
        var session=new TutorialSession();
        session.start(); session.next(); session.type('x'); session.performDemo();
        session.exit();
        assertFalse(session.active());
        assertEquals("",session.demoText());
        session.start();
        assertEquals(0,session.index());
        assertFalse(session.demoPerformed());
        session.previous(); assertEquals(0,session.index());
    }
    @Test void everyStepIsReachableAndCompletionDoesNotResume() {
        var session=new TutorialSession(); session.start();
        int count=TutorialSession.Step.values().length;
        assertEquals(16,count,"main chat actions must not be omitted");
        for(int i=0;i<count;i++) {assertTrue(session.active());assertEquals(i,session.index());session.next();}
        assertFalse(session.active());
        session.start(); assertEquals(0,session.index());
    }
}
