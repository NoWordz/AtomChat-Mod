package com.atom.lib.frontend.probe;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import java.time.LocalTime;
import org.objectweb.asm.*;

/** Probe-only clocks for production rendering. Never included in mod artifacts. */
public final class FixedHeaderClockAgent {
    private static int headerClocks, caretClocks, scrollClocks, springClocks, pulseClocks;
    private FixedHeaderClockAgent(){}
    public static LocalTime now(){return LocalTime.of(17,5);}
    public static long caretMillis() { return Long.getLong("fcl.historyCaretMillis", 1000L); }
    public static long frameNanos() { return caretMillis() * 1_000_000; }
    public static void requireSceneClocks() {
        if (headerClocks != 1 || caretClocks != 1 || scrollClocks != 1 || springClocks != 1 || pulseClocks != 1) {
            throw new AssertionError("FCL_SCENE_CLOCKS header=" + headerClocks + " caret=" + caretClocks
                    + " scroll=" + scrollClocks + " spring=" + springClocks + " pulse=" + pulseClocks);
        }
    }

    public static void premain(String argument,Instrumentation instrumentation){
        if ("interaction".equals(argument)) installForgeBootstrapAgent(instrumentation);
        instrumentation.addTransformer(new ClassFileTransformer(){
            @Override public byte[] transform(ClassLoader loader,String name,Class<?> redefining,ProtectionDomain domain,byte[] input)throws IllegalClassFormatException{
                boolean header = "com/atom/chat/ui/ShellHeader".equals(name);
                boolean historyPage = "com/atom/chat/settings/SettingsSectionPage".equals(name);
                boolean historyRow = "com/atom/chat/settings/HistoryNumberRow".equals(name);
                boolean caret = "interaction".equals(argument) && (historyPage || historyRow);
                boolean scene = Boolean.getBoolean("atomchat.fcl.scene");
                boolean scroll = scene && "com/atom/chat/ui/ScrollController".equals(name);
                boolean spring = scene && "com/atom/chat/ui/SpringAnim".equals(name);
                boolean pulse = scene && "com/atom/chat/ui/ToggleSwitch".equals(name);
                if (!header && !caret && !scroll && !spring && !pulse) return null;
                var reader=new ClassReader(input);var writer=new ClassWriter(reader,0);int[] replaced={0};
                reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                    @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                        return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,name,descriptor,signature,exceptions)){
                            @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean isInterface){
                                boolean headerClock = header && owner.equals("java/time/LocalTime")
                                        && method.equals("now") && descriptor.equals("()Ljava/time/LocalTime;");
                                boolean caretClock = caret && ((historyPage && name.equals("drawInlineNumberSlider"))
                                        || (historyRow && name.equals("caretMillis")))
                                        && owner.equals("java/lang/System") && method.equals("currentTimeMillis")
                                        && descriptor.equals("()J");
                                boolean scrollClock = scroll && name.equals("startScrollAnim")
                                        && owner.equals("java/lang/System") && method.equals("currentTimeMillis")
                                        && descriptor.equals("()J");
                                boolean springClock = spring && name.equals("tick") && owner.equals("java/lang/System")
                                        && method.equals("nanoTime") && descriptor.equals("()J");
                                boolean pulseClock = pulse && (name.equals("pulse") || name.equals("pulsing"))
                                        && owner.equals("java/lang/System") && method.equals("currentTimeMillis")
                                        && descriptor.equals("()J");
                                if(opcode==Opcodes.INVOKESTATIC && (headerClock || caretClock || scrollClock || springClock || pulseClock)){
                                    replaced[0]++;super.visitMethodInsn(opcode,"com/atom/lib/frontend/probe/FixedHeaderClockAgent",
                                            headerClock ? "now" : springClock ? "frameNanos" : "caretMillis",descriptor,false);
                                }else super.visitMethodInsn(opcode,owner,method,descriptor,isInterface);
                            }
                        };
                    }
                },0);
                // The reference owns the clock on the page; current builds share it at the number row.
                if (caret && replaced[0] == 0 && historyPage) return null;
                String role = scroll ? "SCROLL" : spring ? "SPRING" : pulse ? "TOGGLE_PULSE" : caret ? "HISTORY_CARET" : "HEADER";
                if (replaced[0] != (pulse ? 2 : 1)) throw new IllegalClassFormatException("FCL_" + role + "_CLOCK_UNBOUND count=" + replaced[0]);
                if (header) headerClocks++;
                if (caret) caretClocks++;
                if (scroll) scrollClocks++;
                if (spring) springClocks++;
                if (pulse) pulseClocks++;
                System.out.println("FCL_" + role + "_CLOCK_BOUND count=" + replaced[0] + (header ? " value=17:05" : ""));
                return writer.toByteArray();
            }
        });
    }

    private static void installForgeBootstrapAgent(Instrumentation instrumentation) {
        final Class<?> forge;
        try {
            forge = Class.forName("com.atom.chat.testing.ForgeEventBusAgent");
        } catch (ClassNotFoundException absentOutsideForge) {
            return;
        }
        try {
            forge.getMethod("premain", String.class, Instrumentation.class).invoke(null, null, instrumentation);
            System.setProperty("atomchat.fcl.forge", "true");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not install Forge's test bootstrap agent", e);
        }
    }
}
