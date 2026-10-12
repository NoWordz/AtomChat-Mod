package com.atom.chat.net;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvatarCompanionServerTest {
    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void pngHeaderWithSafeDimensionsIsAccepted() {
        assertTrue(AvatarCompanionServer.isPng(pngHeader(128,128)));
    }

    private static byte[] pngHeader(int width,int height) {
        byte[] data=new byte[24];
        System.arraycopy(PNG_HEADER,0,data,0,PNG_HEADER.length);
        java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(data);
        buffer.putInt(8,13); buffer.putInt(12,0x49484452);
        buffer.putInt(16,width); buffer.putInt(20,height);
        return data;
    }

    @Test
    void hugeOrInvalidDimensionsAreRejectedBeforeDecode() {
        assertFalse(AvatarCompanionServer.isPng(pngHeader(Integer.MAX_VALUE,Integer.MAX_VALUE)));
        assertFalse(AvatarCompanionServer.isPng(pngHeader(0,128)));
        assertFalse(AvatarCompanionServer.isPng(pngHeader(-1,128)));
    }

    @Test
    void signatureWithoutIhdrIsRejected() {
        byte[] data=new byte[24];
        System.arraycopy(PNG_HEADER,0,data,0,PNG_HEADER.length);
        assertFalse(AvatarCompanionServer.isPng(data));
    }

    @Test
    void nonPngPayloadsAreRejected() {
        assertFalse(AvatarCompanionServer.isPng("GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(AvatarCompanionServer.isPng(new byte[0]));
        assertFalse(AvatarCompanionServer.isPng("<html>".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void truncatedMagicIsRejected() {
        byte[] data = new byte[PNG_HEADER.length - 1];
        System.arraycopy(PNG_HEADER, 0, data, 0, data.length);
        assertFalse(AvatarCompanionServer.isPng(data));
    }

    @Test
    void payloadIdsAreStable() {
        // Forge packs every packet into one SimpleChannel, so the wire contract is
        // the channel name plus the packet ids: never rename or reorder these.
        assertTrue(AvatarPayloads.CHANNEL_NAME.getNamespace().equals("atomchat"));
        assertTrue(AvatarPayloads.CHANNEL_NAME.getPath().equals("avatar"));
        assertEquals(0, AvatarPayloads.ID_UPLOAD);
        assertEquals(1, AvatarPayloads.ID_REQUEST);
        assertEquals(2, AvatarPayloads.ID_DATA);
        assertEquals(3, AvatarPayloads.ID_CHANGED);
    }

    @Test
    void maxAvatarBytesIsLargeEnoughForCroppedPng() {
        // The cropper emits a 256px PNG, typically well under the cap.
        assertTrue(AvatarPayloads.MAX_AVATAR_BYTES >= 256 * 1024);
        assertTrue(UUID.fromString("00000000-0000-0000-0000-000000000000") != null);
    }
}
