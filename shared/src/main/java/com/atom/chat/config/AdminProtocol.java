package com.atom.chat.config;

import com.atom.chat.chat.MessageTemplate;
import com.google.gson.Gson;
import java.util.*;

/** Optional management channel; legacy config/pack packet layouts remain unchanged. */
public final class AdminProtocol {
    private AdminProtocol() {}
    public static final Gson JSON=new Gson();
    public record Request(String action,int version,String fingerprint,ServerConfigValues values,List<AdminAsset> assets,String name,String requestId) {
        public Request(String action,int version,String fingerprint,ServerConfigValues values,List<AdminAsset> assets,String name){this(action,version,fingerprint,values,assets,name,UUID.randomUUID().toString());}
    }
    public record Snapshot(int version,ServerConfigValues values,String fingerprint,List<AdminAsset> assets,
                           String publicAvatarRevision) {
        public Snapshot(int version, ServerConfigValues values, String fingerprint, List<AdminAsset> assets) {
            this(version, values, fingerprint, assets, null);
        }
    }
    public record Reply(String action,boolean ok,String error,Snapshot snapshot,AdminAsset asset,List<MessageTemplate> templates,String requestId) {
        public Reply(String action,boolean ok,String error,Snapshot snapshot,AdminAsset asset,List<MessageTemplate> templates){this(action,ok,error,snapshot,asset,templates,null);}
        public Reply correlate(String id){return new Reply(action,ok,error,snapshot,asset,templates,id);}
    }
    public record Chunk(int id,int index,int count,String text) {}
    public static List<Chunk> chunks(String json) {
        if(json.length()>24*1024*1024)throw new IllegalArgumentException("too_large");
        int id=java.util.concurrent.ThreadLocalRandom.current().nextInt();var pieces=new ArrayList<String>();
        for(int at=0;at<json.length();){int end=Math.min(json.length(),at+8000);if(end<json.length()&&Character.isLowSurrogate(json.charAt(end)))end--;pieces.add(json.substring(at,end));at=end;}
        if(pieces.isEmpty())pieces.add("");
        var chunks=new ArrayList<Chunk>();for(int i=0;i<pieces.size();i++)chunks.add(new Chunk(id,i,pieces.size(),pieces.get(i)));return chunks;
    }
    public static final class Receiver {
        private int id,next,count;private long until;private StringBuilder buffer;
        public void expire(long now){if(buffer!=null&&now>until)buffer=null;}
        public String offer(Chunk chunk,long now) {
            if(chunk.count()<1||chunk.count()>4096||chunk.text()==null||chunk.text().length()>8000)throw new IllegalArgumentException("bad_chunk");
            if(chunk.index()==0){buffer=new StringBuilder();id=chunk.id();next=0;count=chunk.count();until=now+45_000;}
            if(buffer==null||now>until||id!=chunk.id()||count!=chunk.count()||chunk.index()!=next)throw new IllegalArgumentException("bad_chunk");
            if(buffer.length()+chunk.text().length()>24*1024*1024){buffer=null;throw new IllegalArgumentException("too_large");}
            buffer.append(chunk.text());next++;
            if(next!=count)return null;String json=buffer.toString();buffer=null;return json;
        }
    }
}
