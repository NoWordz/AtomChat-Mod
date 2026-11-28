package com.atom.chat.util;

import com.atom.chat.AtomChat;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.PointerByReference;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** NFDe backend using the game's existing JNA runtime on Windows x64. */
final class WindowsFileDialog {
    private static final int OK = 1;
    private static final int CANCEL = 2;
    private static final long ABI_VERSION = 2;
    private static final long WIN32_WINDOW = 1;
    private static final Result FALLBACK = new Result(false, null, false);
    private static final Result CANCELLED = new Result(true, null, false);
    private static final Result INVALID_IMAGE = new Result(false, null, true);
    private static boolean loggedFailure;

    record Result(boolean handled, Path path, boolean invalidImage) {}

    private WindowsFileDialog() {}

    static Result choose(long glfwWindow, File directory, String title, String suggestedName,
                         String filterLabel, Predicate<String> accept) {
        if (glfwWindow == 0 || !com.sun.jna.Platform.isWindows() || !"x86-64".equals(com.sun.jna.Platform.ARCH)
                || Boolean.getBoolean("atomchat.filePicker.swing")) return FALLBACK;
        try {
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(glfwWindow);
            if (hwnd == 0) return FALLBACK;
            Api api = ApiLoader.API;
            if (api.NFD_Init() != OK) return failed(api.NFD_GetError());
            try {
                var pointer = new PointerByReference();
                int status;
                if (suggestedName == null) {
                    var filter = new FilterItem();
                    filter.name = filterLabel;
                    filter.spec = ImageFiles.extensions().stream().filter(ext -> accept.test("image" + ext))
                            .map(ext -> ext.substring(1)).collect(Collectors.joining(","));
                    if (filter.spec.isEmpty()) return FALLBACK;
                    filter.write();
                    var args = new OpenArgs();
                    args.filters = filter.getPointer();
                    args.filterCount = 1;
                    args.directory = directory.getAbsolutePath();
                    args.owner = Pointer.createConstant(hwnd);
                    args.title = title;
                    status = api.NFD_OpenDialogU8_With_Impl(ABI_VERSION, pointer, args);
                    Reference.reachabilityFence(filter);
                } else {
                    var args = new SaveArgs();
                    args.directory = directory.getAbsolutePath();
                    args.defaultName = suggestedName;
                    args.owner = Pointer.createConstant(hwnd);
                    args.title = title;
                    status = api.NFD_SaveDialogU8_With_Impl(ABI_VERSION, pointer, args);
                }
                if (status == CANCEL) return CANCELLED;
                if (status != OK || pointer.getValue() == null) return failed(api.NFD_GetError());
                Pointer value = pointer.getValue();
                try {
                    Path path = Path.of(value.getString(0, "UTF-8"));
                    if (suggestedName == null && (!path.toFile().isFile() || !accept.test(path.getFileName().toString()))) {
                        return INVALID_IMAGE;
                    }
                    return new Result(true, path, false);
                } finally {
                    api.NFD_FreePathU8(value);
                }
            } finally {
                api.NFD_Quit();
            }
        } catch (LinkageError | RuntimeException failure) {
            return failed(failure.toString());
        }
    }

    private static Result failed(String reason) {
        if (!loggedFailure) {
            loggedFailure = true;
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("compat", "Windows file dialog unavailable, using the desktop picker: {}"), reason);
        }
        return FALLBACK;
    }

    private static final class ApiLoader {
        static final Api API = load();

        private static Api load() {
            try {
                File binary = Native.extractFromResourcePath("/win32-x86-64/atomchat-nfde.dll",
                        WindowsFileDialog.class.getClassLoader());
                return Native.load(binary.getAbsolutePath(), Api.class, Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    public interface Api extends Library {
        int NFD_Init();
        void NFD_Quit();
        String NFD_GetError();
        int NFD_OpenDialogU8_With_Impl(long version, PointerByReference path, OpenArgs args);
        int NFD_SaveDialogU8_With_Impl(long version, PointerByReference path, SaveArgs args);
        void NFD_FreePathU8(Pointer path);
    }

    @Structure.FieldOrder({"name", "spec"})
    public static final class FilterItem extends Structure {
        public String name;
        public String spec;

        public FilterItem() {
            setStringEncoding("UTF-8");
        }
    }

    @Structure.FieldOrder({"filters", "filterCount", "directory", "ownerType", "owner", "title", "accept", "cancel"})
    public static final class OpenArgs extends Structure {
        public Pointer filters;
        public int filterCount;
        public String directory;
        public long ownerType = WIN32_WINDOW;
        public Pointer owner;
        public String title;
        public String accept;
        public String cancel;

        public OpenArgs() {
            setStringEncoding("UTF-8");
        }
    }

    @Structure.FieldOrder({"filters", "filterCount", "directory", "defaultName", "ownerType", "owner", "title", "accept", "cancel"})
    public static final class SaveArgs extends Structure {
        public Pointer filters;
        public int filterCount;
        public String directory;
        public String defaultName;
        public long ownerType = WIN32_WINDOW;
        public Pointer owner;
        public String title;
        public String accept;
        public String cancel;

        public SaveArgs() {
            setStringEncoding("UTF-8");
        }
    }
}
