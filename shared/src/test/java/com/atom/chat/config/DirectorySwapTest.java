package com.atom.chat.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.atom.chat.util.DirectorySwap;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class DirectorySwapTest {
    @TempDir Path temp;
    @Test void failedFinalRenameRestoresWorkingContent()throws Exception{
        Path target=temp.resolve("live");Files.createDirectory(target);Files.writeString(target.resolve("old"),"usable");
        assertThrows(IOException.class,()->DirectorySwap.replace(temp.resolve("missing-stage"),target));
        assertEquals("usable",Files.readString(target.resolve("old")));
        Path stage=temp.resolve("stage");Files.createDirectory(stage);Files.writeString(stage.resolve("new"),"updated");
        DirectorySwap.replace(stage,target);assertFalse(Files.exists(target.resolve("old")));assertEquals("updated",Files.readString(target.resolve("new")));
    }
}
