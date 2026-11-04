package com.atom.chat.diagnostics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FrameSegmentsTest {
 @Test void disabledAndFramePairing() {
  var r=new FrameSegments.Recorder(3);
  assertEquals(0,r.begin(false)); assertEquals(0,r.size());
  long id=r.begin(true);assertTrue(id>0);r.add(id,FrameSegments.Stage.BLUR,40);r.add(id,FrameSegments.Stage.BLUR,2);
  r.add(id+1,FrameSegments.Stage.BLUR,1000);r.finish(id+1,true);assertEquals(0,r.size());r.finish(id,true);
  assertEquals(1,r.size());assertEquals(id,r.frameId(0));assertEquals(42,r.value(0,FrameSegments.Stage.BLUR));
  assertEquals(-1,r.value(0,FrameSegments.Stage.DOTS));
 }
 @Test void liveRecorderRecordsOneFrameAndIgnoresDisabled() {
  assertEquals(0,FrameSegments.begin(false)); assertEquals(0,FrameSegments.current());
  long id=FrameSegments.begin(true); assertTrue(id>0); long start=FrameSegments.start(id);
  assertTrue(start!=0); FrameSegments.end(id,FrameSegments.Stage.DOTS,start);
  FrameSegments.count(FrameSegments.Counter.ICON_COLD); FrameSegments.finish(id,true);
  assertEquals(0,FrameSegments.current()); assertTrue(FrameSegments.summary().contains("ICON_COLD=1"));
 }
 @Test void nestedFrameCannotWriteToOuterEvenWhenDisabled() {
  var r=new FrameSegments.Recorder(3); long outer=r.begin(true);r.add(outer,FrameSegments.Stage.DRAW,5);
  assertEquals(0,r.begin(false));assertEquals(0,r.current());r.count(FrameSegments.Counter.ICON_COLD);
  r.add(outer,FrameSegments.Stage.DRAW,100);assertEquals(0,r.begin(true));assertEquals(0,r.current());
  r.finish(0,true);assertEquals(0,r.current());r.finish(0,false);assertEquals(outer,r.current());
  r.add(outer,FrameSegments.Stage.DRAW,7);r.finish(outer,true);
  assertEquals(12,r.value(0,FrameSegments.Stage.DRAW));assertTrue(r.summary().contains("ICON_COLD=0"));
 }
 @Test void ringIsBoundedAndFailedFrameDoesNotLeak() {
  var r=new FrameSegments.Recorder(2);long a=r.begin(true);r.add(a,FrameSegments.Stage.DRAW,7);r.finish(a,false);assertEquals(0,r.size());
  long b=r.begin(true);r.add(b,FrameSegments.Stage.DRAW,11);r.finish(b,true);
  long c=r.begin(true);r.add(c,FrameSegments.Stage.DRAW,22);r.finish(c,true);
  long d=r.begin(true);r.add(d,FrameSegments.Stage.DRAW,33);r.finish(d,true);
  assertEquals(2,r.size());assertEquals(c,r.frameId(0));assertEquals(22,r.value(0,FrameSegments.Stage.DRAW));assertEquals(d,r.frameId(1));assertEquals(33,r.value(1,FrameSegments.Stage.DRAW));
  assertTrue(r.summary().contains("DRAW"));assertTrue(r.summary().contains("n=2"));
 }
}
