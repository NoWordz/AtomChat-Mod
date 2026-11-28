import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Adds scene markers and interaction to the historical, archived CCB driver. */
public final class ExtendReplayRoute {
    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]);
        var reader = new ClassReader(Files.readAllBytes(path));
        var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                var next = super.visitMethod(access, name, desc, signature, exceptions);
                if (!name.equals("bench$enter") && !name.equals("bench$onFrame")) return next;
                return new MethodVisitor(Opcodes.ASM9, next) {
                    @Override public void visitCode() {
                        super.visitCode();
                        if (name.equals("bench$enter")) {
                            visitVarInsn(Opcodes.ALOAD, 2);
                            visitMethodInsn(Opcodes.INVOKESTATIC, "atomchatprobe/ReplayActions", "scene", "(Ljava/lang/String;)V", false);
                        } else {
                            visitVarInsn(Opcodes.ALOAD, 0);
                            visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/class_310");
                            visitMethodInsn(Opcodes.INVOKESTATIC, "atomchatprobe/ReplayActions", "tick", "(Lnet/minecraft/class_310;)V", false);
                        }
                    }

                    private int durationConstants;
                    private boolean sawPanelConstructor;

                    @Override public void visitTypeInsn(int opcode, String type) {
                        if (name.equals("bench$enter") && opcode == Opcodes.NEW
                                && type.equals("net/minecraft/client/gui/screen/AtomChatScreen")) {
                            sawPanelConstructor = true;
                            openMarker("beginPanelConstructor");
                        }
                        super.visitTypeInsn(opcode, type);
                    }

                    @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                          String descriptor, boolean isInterface) {
                        String stage = null;
                        if (name.equals("bench$enter")) {
                            if (owner.equals("net/minecraft/client/gui/screen/AtomChatScreen")) {
                                stage = switch (called) {
                                    case "<init>" -> "constructor";
                                    case "switchRoot" -> "switchRoot";
                                    case "openSettingsSection", "openWorldChat" -> "navigate";
                                    default -> null;
                                };
                            } else if (owner.equals("net/minecraft/class_310") && called.equals("method_1507")) {
                                stage = sawPanelConstructor ? "setScreen" : "closePrevious";
                            }
                        }
                        if (stage != null && !stage.equals("constructor")) openMarker("startOpenStage");
                        if (name.equals("bench$enter") && owner.equals("com/atom/chat/chat/ChatStore")
                                && called.equals("reset") && descriptor.equals("()V")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "atomchatprobe/ReplayActions",
                                    "resetChatForScene", "()V", false);
                        } else if (name.equals("bench$enter") && owner.equals("net/minecraft/client/gui/screen/AtomChatScreen")
                                && (called.equals("switchRoot") || called.equals("openSettingsSection"))) {
                            String callback = called.equals("switchRoot") ? "switchReplayRoot" : "openSettingsOrHistory";
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "atomchatprobe/ReplayActions", callback,
                                    "(Lnet/minecraft/client/gui/screen/AtomChatScreen;" + descriptor.substring(1), false);
                        } else {
                            super.visitMethodInsn(opcode, owner, called, descriptor, isInterface);
                        }
                        if (stage != null) {
                            super.visitLdcInsn(stage);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "atomchatprobe/ReplayActions",
                                    "endOpenStage", "(Ljava/lang/String;)V", false);
                        }
                    }

                    private void openMarker(String called) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, "atomchatprobe/ReplayActions", called, "()V", false);
                    }

                    @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                        if (name.equals("bench$onFrame") && opcode == Opcodes.GETSTATIC
                                && owner.equals("com/atom/chat/nav/AppPage") && field.equals("SETTINGS_SECTION")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "atomchatprobe/ReplayActions",
                                    "expectedAppearancePage", "()" + descriptor, false);
                        } else {
                            super.visitFieldInsn(opcode, owner, field, descriptor);
                        }
                    }

                    @Override public void visitLdcInsn(Object value) {
                        super.visitLdcInsn(value);
                        if (name.equals("bench$onFrame") && Long.valueOf(12_000_000_000L).equals(value)
                                && ++durationConstants == 2) {
                            visitMethodInsn(Opcodes.INVOKESTATIC, "atomchatprobe/ReplayActions",
                                    "sceneDuration", "(J)J", false);
                        }
                    }

                    @Override public void visitIntInsn(int opcode, int operand) {
                        if (name.equals("bench$enter") && opcode == Opcodes.BIPUSH && operand == 32) {
                            super.visitIntInsn(Opcodes.SIPUSH, 160);
                        } else {
                            super.visitIntInsn(opcode, operand);
                        }
                    }
                };
            }
        }, 0);
        Files.write(path, writer.toByteArray());
    }
}
