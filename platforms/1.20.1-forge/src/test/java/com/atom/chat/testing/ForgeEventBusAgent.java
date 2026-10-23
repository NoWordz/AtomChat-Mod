package com.atom.chat.testing;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import net.minecraftforge.eventbus.EventSubclassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/** Plain JUnit needs Forge's normal event transformation before vanilla bootstrap's network hooks. */
public final class ForgeEventBusAgent {
    private ForgeEventBusAgent() {}

    public static void premain(String arguments, Instrumentation instrumentation) {
        EventSubclassTransformer transformer = new EventSubclassTransformer();
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type,
                                               ProtectionDomain domain, byte[] bytes) {
                if (name == null || !name.startsWith("net/minecraftforge/")) return null;
                if (name.startsWith("net/minecraftforge/eventbus/")
                        && !name.equals("net/minecraftforge/eventbus/api/Event")
                        && !name.equals("net/minecraftforge/eventbus/api/GenericEvent")) return null;
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                var transformed = transformer.transform(node, Type.getObjectType(name));
                if (transformed.isEmpty()) return null;
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                transformed.get().accept(writer);
                return writer.toByteArray();
            }
        });
    }
}
