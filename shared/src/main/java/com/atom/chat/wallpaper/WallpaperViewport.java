package com.atom.chat.wallpaper;

/** Render-thread geometry gate. No locks, files, shader compilation or executor work on a hot frame. */
final class WallpaperViewport {
    static final int MAX_SIDE=4096;
    static final long MAX_PIXELS=16_777_216;
    private static final int BUCKET=64;
    private static final long STABLE_MS=250;
    record Size(int width,int height,boolean limited) { }
    private Size candidate,applied;
    private long candidateSince;

    Size update(int width,int height,long now) {
        if(width<1||height<1)return null;
        double scale=Math.min(1,Math.min((double)MAX_SIDE/width,(double)MAX_SIDE/height));
        Size next=new Size(bucket(width*scale),bucket(height*scale),scale<1);
        if(next.equals(applied))return null;
        if(applied==null){candidate=applied=next;candidateSince=now;return next;}
        if(!next.equals(candidate)){candidate=next;candidateSince=now;return null;}
        if(now-candidateSince<STABLE_MS)return null;
        applied=next;return next;
    }
    private static int bucket(double side){return Math.min(MAX_SIDE,Math.max(BUCKET,(int)Math.ceil(side/BUCKET)*BUCKET));}
}
