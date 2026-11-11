package com.atom.chat.ui;
import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.ui.*;
import io.github.humbleui.skija.*;
import java.nio.file.Files;
import java.nio.file.Path;
class BottomChromeTransitionTest {
 private java.lang.reflect.Field provider,instance;
 private Object oldProvider,oldConfig;
 private boolean oldAnimations;
 @org.junit.jupiter.api.BeforeEach void capture()throws Exception {
  provider=Platform.class.getDeclaredField("provider");provider.setAccessible(true);oldProvider=provider.get(null);
  instance=AtomChatConfig.class.getDeclaredField("instance");instance.setAccessible(true);oldConfig=instance.get(null);
  if(oldConfig!=null)oldAnimations=((AtomChatConfig)oldConfig).animationEnabled;
 }
 @org.junit.jupiter.api.AfterEach void restore()throws Exception {
  if(oldConfig!=null)((AtomChatConfig)oldConfig).animationEnabled=oldAnimations;
  provider.set(null,oldProvider);instance.set(null,oldConfig);
 }

 @org.junit.jupiter.api.Test void rapidReverseRetainsShapeAndMovingButtonHits()throws Exception {
  Path root=Files.createTempDirectory("atomchat-bottom-probe");
  Platform.install(new Platform.Provider(){public Path configDir(){return root.resolve("config");}public Path gameDir(){return root;}public boolean isModLoaded(String id){return false;}});
  AtomChatConfig.get().animationEnabled=true;
  UiLayout.Rect input=new UiLayout.Rect(20,600,480,110),tabs=new UiLayout.Rect(20,640,480,70);
  var nav=new BottomTabBar();int samples=0;
  try(var chrome=new BottomChromeTransition();var surface=Surface.makeRasterN32Premul(560,780)) {
   for(int i=0;i<500;i++) {
    long now=1000+i*16;
    var mode=i%3==0?BottomChromeTransition.Mode.CHAT:i%3==1?BottomChromeTransition.Mode.NAV:BottomChromeTransition.Mode.HIDDEN;
    chrome.beginFrame(mode,input,tabs,now,true);
    chrome.draw(surface.getCanvas(),c->{if(mode==BottomChromeTransition.Mode.NAV)nav.renderContents(c,tabs,0,0xFFFFFFFF,0xFF3B82F6);});
    var shape=chrome.paintedShape();
    if(mode==BottomChromeTransition.Mode.NAV&&shape.w()>1) {
     for(int cell=0;cell<3;cell++) {
      float x=shape.x()+shape.w()*(cell+.5f)/3,y=shape.y()+shape.h()/2;
      int hit=BottomTabBar.hitTest(chrome.inverseX(x),chrome.inverseY(y),tabs);
      if(hit!=cell)throw new AssertionError("wrong moving cell "+hit+" expected "+cell);
      samples++;
     }
    }
    var before=chrome.paintedShape();
    var reverse=mode==BottomChromeTransition.Mode.NAV?BottomChromeTransition.Mode.HIDDEN:BottomChromeTransition.Mode.NAV;
    chrome.beginFrame(reverse,input,tabs,now,true);
    if(!before.equals(chrome.paintedShape()))throw new AssertionError("shape jumped on same-frame reverse");
   }
   chrome.beginFrame(BottomChromeTransition.Mode.HIDDEN,input,tabs,10000,false);
   if(chrome.paintedShape().alpha()!=0||chrome.paintedShape().w()!=0)throw new AssertionError("disabled not immediate");
  }
  System.out.println("PASS BottomPointerProbe: 500 rapid reversals, "+samples+" actual moving icon hits; disabled immediate");
 }
}
