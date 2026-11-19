package com.atom.chat.util;

import java.util.Locale;
import org.junit.jupiter.api.Assumptions;

public final class GlTestEnvironment {
    private GlTestEnvironment() {}

    public static void requireWindowContext() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            Assumptions.assumeFalse("true".equals(System.getenv("GITHUB_ACTIONS")),
                    "Hosted Windows runners lack a GLFW OpenGL context");
            return;
        }
        Assumptions.assumeTrue(os.startsWith("linux") && System.getenv("DISPLAY") != null,
                "A Linux X11 display is required for the GLFW OpenGL tests");
    }
}
