package com.atom.chat.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;
class ServerResourcesTest {
    @TempDir Path temp;
    private byte[] png()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB),"png",out);return out.toByteArray();}
    @Test void failedPersistenceRestoresExistingDirectoryAndImportsStayUnpublished()throws Exception{
        Path dir=temp.resolve("emotes");Files.createDirectory(dir);byte[] old=png();Files.write(dir.resolve("old.png"),old);
        var base=ServerResources.list(dir);var next=List.of(AdminAsset.imported("new.png",png()));
        assertFalse(Files.exists(dir.resolve("new.png")));
        assertThrows(IOException.class,()->ServerResources.commit(dir,ServerResources.fingerprint(base),next,200,ServerResources.MAX_BYTES,()->false));
        assertArrayEquals(old,Files.readAllBytes(dir.resolve("old.png")));assertFalse(Files.exists(dir.resolve("new.png")));
        ServerResources.commit(dir,ServerResources.fingerprint(base),next,200,ServerResources.MAX_BYTES,()->true);
        assertFalse(Files.exists(dir.resolve("old.png")));assertTrue(Files.exists(dir.resolve("new.png")));
    }
    @Test void staleExternalEditsAndUnsafeDuplicateNamesAreRefused()throws Exception{
        Path dir=temp.resolve("emotes");Files.createDirectory(dir);String before=ServerResources.fingerprint(ServerResources.list(dir));
        Files.write(dir.resolve("other.png"),png());
        assertThrows(IOException.class,()->ServerResources.commit(dir,before,List.of(),200,ServerResources.MAX_BYTES,()->true));
        String current=ServerResources.fingerprint(ServerResources.list(dir));var file=AdminAsset.imported("new.png",png());
        assertThrows(IOException.class,()->ServerResources.commit(dir,current,List.of(file,file),200,ServerResources.MAX_BYTES,()->true));
        assertTrue(Files.exists(dir.resolve("other.png")));
    }
    @Test void invalidImageBytesNeverPublish()throws Exception{
        Path dir=temp.resolve("emotes");String before=ServerResources.fingerprint(List.of());
        assertThrows(IOException.class,()->ServerResources.commit(dir,before,List.of(AdminAsset.imported("bad.png",new byte[]{1,2,3})),200,ServerResources.MAX_BYTES,()->true));
        assertFalse(Files.exists(dir.resolve("bad.png")));
    }
}
