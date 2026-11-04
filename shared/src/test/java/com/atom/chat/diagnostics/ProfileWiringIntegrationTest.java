package com.atom.chat.diagnostics;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class ProfileWiringIntegrationTest {
    private static final List<List<String>> PYTHON_CANDIDATES = List.of(
            List.of("python3"), List.of("python"), List.of("py", "-3"));

    static List<String> choosePython(List<List<String>> candidates, Predicate<List<String>> usable) {
        return candidates.stream().filter(usable).findFirst()
                .orElseThrow(() -> new IllegalStateException("No usable Python: python3 / python / py -3"));
    }

    private static boolean usablePython(List<String> candidate) {
        var command = new ArrayList<>(candidate);
        command.add("--version");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) return false;
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.exitValue() == 0 && output.contains("Python");
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Python discovery interrupted", e);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    @Test void pythonCandidatesPreservePriorityAndMultiArgumentFallback() {
        var seen = new ArrayList<List<String>>();
        assertEquals(List.of("py", "-3"), choosePython(PYTHON_CANDIDATES, candidate -> {
            seen.add(candidate); return candidate.equals(List.of("py", "-3"));
        }));
        assertEquals(PYTHON_CANDIDATES, seen);
        assertEquals(List.of("python3"), choosePython(PYTHON_CANDIDATES, candidate -> true));
        assertThrows(IllegalStateException.class, () -> choosePython(PYTHON_CANDIDATES, candidate -> false));
    }

    @Test void realHudAndGraphicsFacadeKeepPanelSamplesSeparate() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("tools/verify_targets.py"))) root = root.getParent();
        assertNotNull(root, "profile wiring probe is required");
        assertTrue(Files.isRegularFile(root.resolve("tools/probes/verify_profile_wiring.py")), "profile wiring probe is required in this checkout");
        List<String> python = choosePython(PYTHON_CANDIDATES, ProfileWiringIntegrationTest::usablePython);
        var command = new ArrayList<>(python);
        command.add(root.resolve("tools/probes/verify_profile_wiring.py").toString());
        command.add(root.toString());
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "profile wiring probe timed out");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("official passed") && output.contains("fabric passed"), output);
            assertTrue(output.contains("JDK_HOME=" + System.getProperty("java.home")), output);
            System.out.println("Profile wiring Python: " + String.join(" ", python) + "; " + "JDK_HOME=" + System.getProperty("java.home"));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
