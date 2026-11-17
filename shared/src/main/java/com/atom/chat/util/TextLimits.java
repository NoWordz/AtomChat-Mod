package com.atom.chat.util;

/** UTF-16 limits used by the existing Minecraft string codecs. */
public final class TextLimits {
    private TextLimits(){}
    public static boolean wellFormed(String text){
        if(text==null)return false;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(Character.isHighSurrogate(c)){if(i+1>=text.length()||!Character.isLowSurrogate(text.charAt(++i)))return false;}
            else if(Character.isLowSurrogate(c))return false;
        }
        return true;
    }
    public static String limit(String text,int max){
        if(text.length()<=max)return text;
        int end=max;if(end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;
        return text.substring(0,end);
    }
}
