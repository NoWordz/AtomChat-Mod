package com.atom.chat.audio;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FeedbackSoundPolicyTest {
 @Test void masterTypeAndFiniteVolume() {
  assertEquals(0,FeedbackSoundPolicy.volume(false,true,1)); assertEquals(0,FeedbackSoundPolicy.volume(true,false,1));
  assertEquals(0,FeedbackSoundPolicy.volume(true,true,Float.NaN)); assertEquals(0,FeedbackSoundPolicy.volume(true,true,Float.POSITIVE_INFINITY));
  assertEquals(1,FeedbackSoundPolicy.volume(true,true,2)); assertEquals(0,FeedbackSoundPolicy.volume(true,true,-1)); assertEquals(.25f,FeedbackSoundPolicy.volume(true,true,.25f));
 }
 @Test void mostSpecificActionWins() {
  assertEquals(FeedbackCue.SEND,FeedbackSoundPolicy.choose(FeedbackCue.BUTTON,FeedbackCue.SEND));
  assertEquals(FeedbackCue.SEND,FeedbackSoundPolicy.choose(FeedbackCue.SEND,FeedbackCue.BUTTON));
  assertEquals(FeedbackCue.PANEL_OPEN,FeedbackSoundPolicy.choose(FeedbackCue.BUTTON,FeedbackCue.PANEL_OPEN));
  assertNull(FeedbackSoundPolicy.choose(null,null));
 }
}

