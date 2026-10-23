package com.atom.chat.testing;

/** Fresh-JVM child-first regression: an earlier test must not mask missing instrumentation. */
public final class ForgeEventBusAgentProbe {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = ForgeEventBusAgentProbe.class.getClassLoader();
        Class.forName("net.minecraftforge.network.NetworkEvent$ClientCustomPayloadEvent", false, loader);
        Class<?> root = Class.forName("net.minecraftforge.network.NetworkEvent", false, loader);
        root.getConstructor();
        System.out.println("Forge event agent child-first initialization passed");
    }
}
