package com.atom.chat.wallpaper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WallpaperViewportTest {
 @Test void physicalSizeWaitsForStableBucketsAndNeverDropsBelowCoverage() {
  var v=new WallpaperViewport();var initial=v.update(960,1800,0);
  assertEquals(960,initial.width());assertEquals(1856,initial.height());
  assertNull(v.update(970,1800,10));assertNull(v.update(975,1800,200));
  assertEquals(1024,v.update(975,1800,260).width());
  for(int i=0;i<10000;i++)assertNull(v.update(975,1800,300+i));
 }
 @Test void extremeDimensionsAreExplicitlyBudgetLimited() {
  var v=new WallpaperViewport();var size=v.update(20000,10000,0);
  assertTrue(size.limited());assertTrue(size.width()<=4096);assertTrue(size.height()<=4096);
  assertTrue((long)size.width()*size.height()<=16777216);
 }
}
