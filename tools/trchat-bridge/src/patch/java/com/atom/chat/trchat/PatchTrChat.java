package com.atom.chat.trchat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.*;

public final class PatchTrChat {
    private static final String BASE = "me/arasple/mc/trchat/";
    private static final String HOOK = "com/atom/chat/trchat/ChannelBridge";
    private static final String CHANNEL = BASE + "module/display/channel/Channel";
    private static final String COMPONENT = BASE + "taboolib/module/chat/ComponentText";
    private static final String PROXY = BASE + "api/impl/BukkitProxyManager";
    private static final String PROCESSOR = BASE + "module/internal/proxy/BukkitProxyProcessor";
    private static final String PLUGIN = BASE + "taboolib/platform/BukkitPlugin";

    private PatchTrChat() {}

    public static void main(String[] args) throws IOException {
        var changes = Map.<String, Consumer<ClassNode>>of(
                CHANNEL + ".class", PatchTrChat::channel, PROCESSOR + ".class", PatchTrChat::processor,
                PROXY + ".class", PatchTrChat::manager, PLUGIN + ".class", PatchTrChat::lifecycle);
        Map<String, byte[]> patched = new HashMap<>();
        try (var input = new JarFile(args[0]);
             var output = new JarOutputStream(Files.newOutputStream(Path.of(args[2])))) {
            for (var patch : changes.entrySet()) {
                var node = new ClassNode();
                new ClassReader(input.getInputStream(input.getJarEntry(patch.getKey()))).accept(node, 0);
                patch.getValue().accept(node);
                var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                patched.put(patch.getKey(), writer.toByteArray());
            }
            for (var entry : Collections.list(input.entries())) {
                if (entry.getName().matches("META-INF/[^/]+\\.(SF|RSA|DSA)")) {
                    throw new IllegalArgumentException("Signed input is outside the reviewed release");
                }
                output.putNextEntry(new JarEntry(entry.getName()));
                byte[] replacement = patched.get(entry.getName());
                if (replacement == null) input.getInputStream(entry).transferTo(output);
                else output.write(replacement);
                output.closeEntry();
            }
            Path classes = Path.of(args[1]);
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    String name = classes.relativize(file).toString().replace('\\', '/');
                    if (!name.endsWith(".class")) continue;
                    output.putNextEntry(new JarEntry(name));
                    Files.copy(file, output);
                    output.closeEntry();
                }
            }
        }
    }

    private static void channel(ClassNode clazz) {
        var execute = method(clazz, "execute", "(Lorg/bukkit/entity/Player;L" + COMPONENT + ";Z)L"
                + BASE + "module/display/channel/obj/ChannelExecuteResult;");
        var sends = calls(execute, BASE + "util/BukkitUtilKt", "sendComponent", 7);
        for (int index : new int[]{2, 3, 4, 5}) {
            var send = sends.get(index);
            execute.instructions.insertBefore(send, new VarInsnNode(ALOAD, 0));
            replace(send, "local", "L" + CHANNEL + ";");
        }
        var proxy = calls(execute, PROXY, "sendBroadcastRaw$default", 1).get(0);
        execute.instructions.insertBefore(proxy, new VarInsnNode(ALOAD, 0));
        replace(proxy, "forward", "L" + CHANNEL + ";");
        System.out.println("Channel.execute: four final local player sends and one forwarding call");
    }

    private static void processor(ClassNode clazz) {
        var execute = method(clazz, "execute", "([Ljava/lang/String;)V");
        var send = calls(execute, BASE + "util/BukkitUtilKt", "sendComponent", 3).get(1);
        var input = new InsnList();
        input.add(new VarInsnNode(ALOAD, 1));
        input.add(new VarInsnNode(ALOAD, 0));
        input.add(new TypeInsnNode(INSTANCEOF, PROCESSOR + "$VelocitySide"));
        execute.instructions.insertBefore(send, input);
        replace(send, "remote", "[Ljava/lang/String;Z");
        System.out.println("BukkitProxyProcessor.execute: one final BroadcastRaw player send, with Velocity type check");
    }

    private static void manager(ClassNode clazz) {
        var broadcast = method(clazz, "sendBroadcastRaw", "(Ljava/lang/Object;Ljava/util/UUID;L" + COMPONENT
                + ";Ljava/lang/String;ZLjava/util/List;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        var send = calls(broadcast, PROXY, "sendMessage", 1).get(0);
        broadcast.instructions.insertBefore(send, new MethodInsnNode(INVOKESTATIC, HOOK, "seal",
                "([Ljava/lang/String;)[Ljava/lang/String;", false));
        System.out.println("BukkitProxyManager: one BroadcastRaw array before forwarding");
    }

    private static void lifecycle(ClassNode clazz) {
        var enable = method(clazz, "onEnable", "()V");
        int exits = 0;
        for (var insn : enable.instructions.toArray()) {
            if (insn.getOpcode() == RETURN) {
                enable.instructions.insertBefore(insn, new MethodInsnNode(INVOKESTATIC, HOOK, "install", "()V"));
                exits++;
            }
        }
        if (exits != 1) throw new IllegalStateException("Unexpected BukkitPlugin initialization");
        var disable = method(clazz, "onDisable", "()V");
        disable.instructions.insert(new MethodInsnNode(INVOKESTATIC, HOOK, "stop", "()V"));
    }

    private static MethodNode method(ClassNode cls, String name, String desc) {
        return cls.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc))
                .findFirst().orElseThrow(() -> new IllegalStateException(cls.name + "." + name + desc));
    }

    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name, int expected) {
        var calls = Arrays.stream(method.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).filter(i -> i.owner.equals(owner) && i.name.equals(name)).toList();
        if (calls.size() != expected) throw new IllegalStateException(method.name + ": unexpected " + name + " calls");
        return calls;
    }

    private static void replace(MethodInsnNode call, String replacement, String args) {
        if (call.getOpcode() != INVOKESTATIC || !Type.getReturnType(call.desc).equals(Type.VOID_TYPE)) {
            throw new IllegalStateException("Unexpected call " + call.name + call.desc);
        }
        call.owner = HOOK;
        call.name = replacement;
        call.desc = call.desc.substring(0, call.desc.indexOf(')')) + args + ")V";
    }
}
