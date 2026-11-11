package com.atom.chat.render;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PaintContextIdentityTest {
    private static Path root(){Path p=Path.of("").toAbsolutePath();while(p!=null&&!Files.isDirectory(p.resolve("platforms")))p=p.getParent();return java.util.Objects.requireNonNull(p);}
    @Test void contextIdentityIsUnavailableOutsideAnActualPaintBorrow()throws Exception {
        var graphics=new SkiaGraphics();
        var method=SkiaGraphics.class.getMethod("contextIdentity");
        var failure=assertThrows(InvocationTargetException.class,()->method.invoke(graphics));
        assertInstanceOf(IllegalStateException.class,failure.getCause());
    }
    @Test void identityComesFromTheExistingPaintAndRareCompactionAloneBorrowsAgain()throws Exception {
        for(String file:List.of("layers/mapping/official/src/main/java/com/atom/chat/render/SkiaGraphics.java",
                "platforms/1.21.1-fabric/src/main/java/com/atom/chat/render/SkiaGraphics.java")){
            String source=Files.readString(root().resolve(file));
            int identity=source.indexOf("borrowedContextIdentity.set(io.github.humbleui.skija.impl.Native.getPtr(frame.context()))");
            int paint=source.indexOf("renderer.accept(canvas, null)");
            int expired=source.indexOf("borrowedContextIdentity.remove()");
            assertTrue(identity>=0&&identity<paint&&paint<expired,file);
        }
        for(String file:List.of("platforms/1.20.1-forge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-neoforge/src/main/java/com/atom/chat/screen/AtomChatScreen.java",
                "platforms/1.21.1-fabric/src/main/java/net/minecraft/client/gui/screen/AtomChatScreen.java")){
            String source=Files.readString(root().resolve(file));
            int rare=source.indexOf("if (composite.needsCompaction())");
            int borrow=source.indexOf("graphics.withContext(");
            assertTrue(rare>=0&&rare<borrow,file+": stable frames must not capture/restore another GL state scope");
            int arrival=source.indexOf("composite.beginFrame(");
            assertTrue(arrival>=0&&arrival<rare,file+": arm this frame's interruption before testing its snapshot budget");
            int paint=source.indexOf("syncAnimationContext(graphics.contextIdentity())");
            int draw=source.indexOf("drawPhone(canvas, worldSnapshot, mouseX, mouseY, delta)");
            assertTrue(paint>=0&&paint<draw,file+": context replacement must clear pictures before replay");
            int helper=source.indexOf("private void syncAnimationContext(long contextId)");
            assertTrue(helper>=0&&source.substring(helper,helper+360).contains("composite.reset(); bottomChrome.reset();"),file);
        }
    }
}
