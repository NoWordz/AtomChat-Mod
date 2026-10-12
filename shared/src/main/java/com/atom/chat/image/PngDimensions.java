package com.atom.chat.image;
/** PNG IHDR preflight without image decoding or client/native libraries. */
public final class PngDimensions {
    private PngDimensions() {}
    private static int integer(byte[] bytes,int offset) {
        return (bytes[offset]&255)<<24 | (bytes[offset+1]&255)<<16 | (bytes[offset+2]&255)<<8 | bytes[offset+3]&255;
    }
    public static boolean within(byte[] bytes,long pixels) {
        if(bytes==null || bytes.length<24 || integer(bytes,8)!=13 || integer(bytes,12)!=0x49484452) return false;
        int w=integer(bytes,16),h=integer(bytes,20);
        return w>0 && h>0 && (long)w*h<=pixels;
    }
}
