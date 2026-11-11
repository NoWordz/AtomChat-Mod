from pathlib import Path
import subprocess, tempfile, os, sys, shutil
root=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[2]
java_home=os.environ.get('JAVA_HOME')
javac=str(Path(java_home)/'bin'/('javac.exe' if os.name=='nt' else 'javac')) if java_home else shutil.which('javac')
java=str(Path(java_home)/'bin'/('java.exe' if os.name=='nt' else 'java')) if java_home else shutil.which('java')
if not javac or not java: raise RuntimeError('JDK java and javac are required')
print('JDK_HOME='+(java_home or '<PATH>'), flush=True)
stubs={
'com/mojang/blaze3d/systems/RenderSystem.java':'''package com.mojang.blaze3d.systems; public class RenderSystem { public static void assertOnRenderThread(){} }''',
'com/atom/chat/AtomChat.java':'''package com.atom.chat; public class AtomChat { public static final Logger LOGGER=new Logger(); public static class Logger { public void info(String s,Object... a){} public void warn(String s,Object... a){} public void debug(String s,Object... a){} } }''',
'com/atom/chat/diagnostics/AtomChatLog.java':'''package com.atom.chat.diagnostics; public class AtomChatLog { public static String message(String a,String b){return b;} }''',
'com/atom/chat/config/AtomChatConfig.java':'''package com.atom.chat.config; public class AtomChatConfig { public boolean enabled=true,debug=false; private static final AtomChatConfig C=new AtomChatConfig();public static AtomChatConfig get(){return C;} }''',
'com/atom/chat/render/UiPixelSnap.java':'''package com.atom.chat.render; public class UiPixelSnap { public static void beginFrame(float f){} }''',
'com/atom/chat/render/PanelBlurRenderer.java':'''package com.atom.chat.render; public class PanelBlurRenderer { public static boolean isAvailable(){return true;} public static boolean render(org.joml.Matrix4f m,float x,float y,float w,float h,float r,float a){com.atom.chat.diagnostics.FrameSegments.count(com.atom.chat.diagnostics.FrameSegments.Counter.BLUR_RESOURCE); return true;} }''',
'com/atom/chat/render/ItemIconRenderer.java':'''package com.atom.chat.render; public class ItemIconRenderer { public static void prepare(Object gui,SkiaGraphics g){com.atom.chat.diagnostics.FrameSegments.count(com.atom.chat.diagnostics.FrameSegments.Counter.ICON_COLD);} }''',
'com/atom/chat/screen/ScreenMetrics.java':'''package com.atom.chat.screen; public class ScreenMetrics { public static float uiDensity(Object w){return 1;} }''',
'com/atom/lib/design/atomchat/UiTokens.java':'''package com.atom.lib.design.atomchat; public class UiTokens { public static float cardRadius(){return 1;} }''',
'com/atom/lib/design/atomchat/SkiaDraw.java':'''package com.atom.lib.design.atomchat; public class SkiaDraw {}''',
'org/joml/Matrix4f.java':'''package org.joml; public class Matrix4f {}''',
'io/github/humbleui/skija/BackendRenderTarget.java':'''package io.github.humbleui.skija; public class BackendRenderTarget {}''',
'io/github/humbleui/skija/Image.java':'''package io.github.humbleui.skija; public class Image {}''',
'io/github/humbleui/skija/Surface.java':'''package io.github.humbleui.skija; public class Surface {}''',
'io/github/humbleui/skija/DirectContext.java':'''package io.github.humbleui.skija; public class DirectContext {}''',
'io/github/humbleui/skija/impl/Native.java':'''package io.github.humbleui.skija.impl; public class Native { public static long getPtr(Object value){return value==null?0:1;} }''',
'io/github/humbleui/skija/Canvas.java':'''package io.github.humbleui.skija; public class Canvas {private int n=1;public int getSaveCount(){return n;} public void save(){n++;}public void scale(float x,float y){}public void restoreToCount(int v){n=v;} }''',
'com/atom/lib/client/render/MinecraftRenderBridge.java':'''package com.atom.lib.client.render; import io.github.humbleui.skija.*; import java.util.function.*; public class MinecraftRenderBridge { private static final MinecraftRenderBridge B=new MinecraftRenderBridge(); public static MinecraftRenderBridge get(){return B;} public void prepare(){} public void vanillaPass(Runnable r){r.run();} public void drawFrame(BiConsumer<Canvas,Frame> a){a.accept(new Canvas(),new Frame());} public <T>T withContext(Function<DirectContext,T> f){return f.apply(new DirectContext());} public static class Frame {public DirectContext context(){return new DirectContext();}public Surface surface(){return new Surface();}public BackendRenderTarget backendRenderTarget(){return new BackendRenderTarget();}} }''',
'com/atom/chat/notification/GnetumHudCompat.java':'''package com.atom.chat.notification; public class GnetumHudCompat {public static void keepOutOfHudCache(){}}''',
'com/atom/chat/notification/NotificationHudVisibility.java':'''package com.atom.chat.notification; public class NotificationHudVisibility {public static boolean visible(){return true;}}''',
'com/atom/chat/notification/NotificationBanner.java':'''package com.atom.chat.notification; import io.github.humbleui.skija.Canvas; public class NotificationBanner {public static final NotificationBanner INSTANCE=new NotificationBanner();public void tick(){}public boolean hasActive(){return true;}public void renderInHud(Canvas c,float w,float h){}public static Placement hudPlacement(float w,float h){return new Placement();} public static float hudBackdropAlpha(){return 1;} public static void hudBackdropPreDrawn(){} public static class Placement { public Rect rect(){return new Rect();} }public static class Rect {public float x(){return 0;}public float y(){return 0;}public float w(){return 10;}public float h(){return 10;}}}''',
'net/minecraft/client/gui/GuiGraphics.java':'''package net.minecraft.client.gui; public class GuiGraphics { public void flush(){} }''',
'net/minecraft/client/gui/DrawContext.java':'''package net.minecraft.client.gui; public class DrawContext { public void draw(){} }''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client; public class Minecraft {public static Minecraft getInstance(){return new Minecraft();}public Target getMainRenderTarget(){return new Target();}public Object getWindow(){return null;}public static class Target {public int width=800,height=500;}}''',
'net/minecraft/client/MinecraftClient.java':'''package net.minecraft.client; public class MinecraftClient {public static MinecraftClient getInstance(){return new MinecraftClient();}public Target getFramebuffer(){return new Target();}public Object getWindow(){return null;}public static class Target {public int textureWidth=800,textureHeight=500;}}''',
# P1 diagnostics: FrameSegments now owns a bounded GPU timer ring. The wiring
# harness only checks segment/surface/frameId semantics, so provide no-op
# stand-ins for the two new types (no GL, no timing) instead of pulling in
# LWJGL. Harness assertions themselves are unchanged.
'com/atom/chat/diagnostics/GpuTimerQuery.java':'''package com.atom.chat.diagnostics; import java.util.*; public final class GpuTimerQuery implements AutoCloseable { public interface Gl { boolean timerQuerySupported(); boolean contextStillValid(); boolean otherQueryActive(); int createQuery(); void deleteQuery(int q); void beginQuery(int q); void endQuery(); boolean resultAvailable(int q); long resultNanos(int q); } public record Sample(long frameId,long gpuNanos,long latencyNanos){} public GpuTimerQuery(Gl gl,int capacity){} public boolean beginFrame(long id){return false;} public void endFrame(){} public List<Sample> poll(){return List.of();} public void reset(){} public long completed(){return 0;} public long dropped(){return 0;} public long expired(){return 0;} public long skipped(){return 0;} public long stalled(){return 0;} public int pending(){return 0;} public void close(){} }''',
'com/atom/chat/diagnostics/GlGpuTimer.java':'''package com.atom.chat.diagnostics; public final class GlGpuTimer implements GpuTimerQuery.Gl { public boolean timerQuerySupported(){return false;} public boolean contextStillValid(){return false;} public boolean otherQueryActive(){return false;} public int createQuery(){return 0;} public void deleteQuery(int q){} public void beginQuery(int q){} public void endQuery(){} public boolean resultAvailable(int q){return false;} public long resultNanos(int q){return 0L;} }''',
}
harness='''package com.atom.chat.diagnostics;
import com.atom.chat.render.SkiaGraphics; import com.atom.chat.notification.NotificationHudOverlay;
public class ProfileWiringHarness {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m+"\\n"+FrameSegments.summary()+"\\n"+FrameProfile.summary());}
 public static void main(String[] args) throws Exception {
  var recording = new jdk.jfr.Recording();
  recording.enable("com.atomchat.UiFrame"); recording.enable("com.atomchat.PanelSegments"); recording.start();
  FrameProfile.setEnabled(true); NotificationHudOverlay.render(new GUI());
  check(FrameSegments.summary().contains("frames=0"),"HUD polluted segments");
  check(FrameProfile.summary().contains("panel  no samples"),"HUD polluted panel bucket");
  SkiaGraphics g=new SkiaGraphics(); final int[] paints={0};
  java.util.function.BiConsumer<io.github.humbleui.skija.Canvas,io.github.humbleui.skija.Image> paint=(c,i)->{paints[0]++;FrameProfile.surface(FrameProfile.Surface.CHAT);FrameSegments.count(FrameSegments.Counter.ICON_COLD);NotificationHudOverlay.render(new GUI());g.drawPanel(null,1f,(nestedCanvas,nestedImage)->{FrameProfile.surface(FrameProfile.Surface.EMOJI);FrameSegments.count(FrameSegments.Counter.ICON_COLD);});try{g.drawPanel(null,1f,(x,y)->{FrameProfile.surface(FrameProfile.Surface.EMOJI);throw new IllegalStateException("nested");});throw new AssertionError("nested failure missing");}catch(IllegalStateException expected){};};
  try {g.getClass().getMethod("drawPanel",Runnable.class,float.class,java.util.function.BiConsumer.class).invoke(g,(Runnable)()->FrameSegments.count(FrameSegments.Counter.BLUR_RESOURCE),1f,paint);}
  catch(NoSuchMethodException e){g.draw(()->FrameSegments.count(FrameSegments.Counter.BLUR_RESOURCE),1f,paint);}
  check(paints[0]==1,"panel renderer not called");check(FrameSegments.summary().contains("frames=1"),"panel missing or HUD included");
  check(FrameProfile.summary().contains("panel  n=1"),"nested drawPanel produced an extra FrameProfile sample");
  check(FrameProfile.summary().contains("chat_render n=1") && !FrameProfile.summary().contains("emoji_render"),"nested panel changed outer surface label");
  check(FrameSegments.summary().contains("ICON_COLD=1"),"nested HUD polluted outer panel");
  check(FrameSegments.summary().contains("BLUR_RESOURCE=1"),"panel prepass missing");
  check(FrameSegments.summary().contains("BRIDGE_ENTER n=1") && FrameSegments.summary().contains("BRIDGE_EXIT n=1"),"bridge boundaries missing or nested frame leaked");
  try { g.drawPanel(null,1f,(c,i)->{ throw new IllegalStateException("outer"); }); throw new AssertionError("outer failure missing"); }
  catch (IllegalStateException expected) {}
  g.drawPanel(null,1f,(c,i)->FrameProfile.surface(FrameProfile.Surface.CHAT));
  recording.stop(); var file=java.nio.file.Files.createTempFile("atomchat-pair-", ".jfr");
  try {
   recording.dump(file);
   var all=jdk.jfr.consumer.RecordingFile.readAllEvents(file);
   var ui=all.stream().filter(e->e.getEventType().getName().equals("com.atomchat.UiFrame")).toList();
   var seg=all.stream().filter(e->e.getEventType().getName().equals("com.atomchat.PanelSegments")).toList();
   check(ui.size()==2 && seg.size()==2,"failed or nested draw generated an event; ui="+ui.size()+" seg="+seg.size());
   long first=ui.get(0).getLong("frameId"), second=ui.get(1).getLong("frameId");
   check(first>0 && second>first,"invalid or duplicate frame IDs");
   check(first==seg.get(0).getLong("frameId") && second==seg.get(1).getLong("frameId"),"UiFrame/PanelSegments mismatch");
  } finally { recording.close(); java.nio.file.Files.deleteIfExists(file); }
  System.out.println("PASS actual NotificationHudOverlay.render + SkiaGraphics HUD and panel entry points, nested exclusion");
 }
}'''
screens=['platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java','platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java','platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java']
for screen in screens:
 text=(root/screen).read_text(encoding='utf-8')
 assert text.count('graphics.drawPanel(() -> {')==1, f'{screen} must route the chat panel through drawPanel'
for target in ['official','fabric']:
 with tempfile.TemporaryDirectory(prefix='atomchat-profile-wiring-') as tmp:
  base=Path(tmp); src=base/'src';out=base/'classes';out.mkdir()
  for name,body in stubs.items():
   p=src/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,encoding='utf-8')
  gui='net.minecraft.client.gui.GuiGraphics' if target=='official' else 'net.minecraft.client.gui.DrawContext'
  p=src/'com/atom/chat/diagnostics/ProfileWiringHarness.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(harness.replace('new GUI()',f'new {gui}()'),encoding='utf-8')
  source=(root/'layers/mapping/official/src/main/java' if target=='official' else root/'platforms/1.21.1-fabric/src/main/java')
  files=[source/'com/atom/chat/render/SkiaGraphics.java',source/'com/atom/chat/notification/NotificationHudOverlay.java',root/'shared/src/main/java/com/atom/chat/diagnostics/FrameProfile.java',root/'shared/src/main/java/com/atom/chat/diagnostics/FrameSegments.java',*src.rglob('*.java')]
  subprocess.run([javac,'-encoding','UTF-8','-d',str(out),*[str(f) for f in files]],check=True)
  subprocess.run([java,'-cp',str(out),'com.atom.chat.diagnostics.ProfileWiringHarness'],check=True)
 print(target,'passed')
