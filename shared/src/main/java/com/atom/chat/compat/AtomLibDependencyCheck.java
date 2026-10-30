package com.atom.chat.compat;

import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

/** Client bootstrap check. No AtomLib/Skija/client types in this class's signatures. */
public final class AtomLibDependencyCheck {
    private AtomLibDependencyCheck() {}
    public static String requiredVersion() {
        try(var input=AtomLibDependencyCheck.class.getResourceAsStream("/atomchat-atomlib.properties")){
            if(input==null)throw new IllegalStateException("AtomChat is missing its AtomLib requirement metadata");
            var properties=new Properties();properties.load(input);String version=properties.getProperty("version");
            if(version==null||version.isBlank())throw new IllegalStateException("AtomChat has invalid AtomLib requirement metadata");
            return version;
        }catch(IOException error){throw new IllegalStateException("Cannot read AtomChat's AtomLib requirement",error);}
    }
    public static void require(String installedVersion){validate(installedVersion,requiredVersion(),System.getProperty("os.name"),System.getProperty("os.arch"));}
    static void validate(String installed,String required,String os,String arch){
        if(!required.equals(installed))throw new IllegalStateException("AtomChat client requires AtomLib "+required+" for the same Minecraft version and loader; installed: "+(installed==null?"missing":installed)+". Install both matching jars. Dedicated servers do not need AtomLib.");
        String architecture=arch.toLowerCase(Locale.ROOT);
        if(!os.toLowerCase(Locale.ROOT).startsWith("windows")||!java.util.Set.of("amd64","x86_64","aarch64","arm64").contains(architecture))
            throw new IllegalStateException("This AtomLib test build supports Windows x64/ARM64 clients only; JVM platform is "+os+" / "+arch+". Previous Linux AtomChat artifacts are unchanged.");
    }
}
