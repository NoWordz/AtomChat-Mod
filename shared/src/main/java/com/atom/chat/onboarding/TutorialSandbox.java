package com.atom.chat.onboarding;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.image.ImageLoader;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import java.util.*;
import java.util.function.BiFunction;

/** A private message source consumed by the production list, search and send path. */
public final class TutorialSandbox implements AutoCloseable {
    public static final String IMAGE_URL="atomchat-tutorial:sample-image";
    private static TutorialSandbox active;
    private final BiFunction<String,Boolean,ChatMessage> factory;
    private List<ChatMessage> messages;
    private long revision;
    private final Set<String> blocked=new HashSet<>();
    private boolean imageShown;
    private boolean imageActionPerformed;
    public TutorialSandbox(BiFunction<String,Boolean,ChatMessage> factory,String greeting,String reply) {
        if(active!=null)throw new IllegalStateException("Tutorial sandbox already active");
        this.factory=factory;messages=List.of(factory.apply(greeting,false),factory.apply(reply,true));active=this;
    }
    public static boolean active(){return active!=null;}
    public static List<ChatMessage> messages(){return active==null?List.of():active.messages;}
    public static long revision(){return active==null?0:active.revision;}
    public static boolean blocked(String name){return active!=null&&name!=null&&active.blocked.contains(name.toLowerCase(Locale.ROOT));}
    public static void block(String name,boolean value){if(active==null||name==null)return;if(value)active.blocked.add(name.toLowerCase(Locale.ROOT));else active.blocked.remove(name.toLowerCase(Locale.ROOT));active.revision++;}
    public static void action(){if(active!=null)active.revision++;}
    public static void imageAction(){if(active!=null&&active.imageShown){active.imageActionPerformed=true;active.revision++;}}
    public static boolean imageActionPerformed(){return active!=null&&active.imageActionPerformed;}
    public static boolean send(String text) {
        if(active==null)return false;
        if(text!=null&&!text.isBlank()) {
            var next=new ArrayList<>(active.messages);next.add(active.factory.apply(text,true));active.messages=List.copyOf(next);active.revision++;
        }
        return true;
    }
    /** Adds the normal image CICode path backed by a generated in-memory bitmap. */
    public static void showImage() {
        if(active==null||active.imageShown)return;
        try(var surface=Surface.makeRasterN32Premul(320,180)) {
            var canvas=surface.getCanvas();
            try(Paint sky=new Paint().setColor(0xFFDDEEFF)){canvas.drawRect(Rect.makeXYWH(0,0,320,180),sky);}
            try(Paint sun=new Paint().setColor(0xFFFFC857)){canvas.drawCircle(252,52,28,sun);}
            try(Paint hill=new Paint().setColor(0xFF69A978)){canvas.drawRect(Rect.makeXYWH(0,115,320,65),hill);}
            try(Paint card=new Paint().setColor(0xFFF7FAFC)){canvas.drawRect(Rect.makeXYWH(22,24,175,55),card);}
            try(Paint line=new Paint().setColor(0xFF446B8A).setStrokeWidth(4)){canvas.drawLine(38,45,170,45,line);canvas.drawLine(38,58,132,58,line);}
            ImageLoader.get().putMemoryImage(IMAGE_URL,surface.makeImageSnapshot());
        }
        var code="[[CICode,url="+IMAGE_URL+",name=tutorial-sample,w=320,h=180]]";
        var next=new ArrayList<>(active.messages);next.add(active.factory.apply(code,true));
        active.messages=List.copyOf(next);active.imageShown=true;active.revision++;
    }
    @Override public void close(){
        ImageLoader.get().removeMemoryImage(IMAGE_URL);
        if(active==this)active=null;messages=List.of();
    }
}
