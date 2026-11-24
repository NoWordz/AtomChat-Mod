package com.atom.chat.chat;
import com.atom.chat.text.RichText;
import java.util.*;
import java.util.function.Predicate;

/** Client-thread deletion coordination for AC's retained rows beyond vanilla's 100 entries. */
public final class NativeMessageSync {
    private static final Map<String,Integer> PENDING=new LinkedHashMap<>();
    private static final List<java.lang.ref.WeakReference<java.util.function.Consumer<String>>> LISTENERS=new ArrayList<>();
    private static long session=1,revision;
    public static void listen(java.util.function.Consumer<String> listener){
        LISTENERS.removeIf(reference->reference.get()==null || reference.get()==listener);
        LISTENERS.add(new java.lang.ref.WeakReference<>(listener));
    }
    public static void unlisten(java.util.function.Consumer<String> listener){LISTENERS.removeIf(reference->reference.get()==null || reference.get()==listener);}
    private NativeMessageSync(){}
    public static long session(){return session;}
    public static long revision(){return revision;}
    public static void start(){PENDING.clear();session++;revision++;}
    public static ChatMessage knownDeleted(String signature){
        if(signature==null)return null;
        for(var message:held())if(message.isDeleted() && signature.equals(message.nativeMeta().signature()))return message;
        return null;
    }
    public static boolean deleted(ChatMessage message){return message!=null && (message.isDeleted() || knownDeleted(message.nativeMeta().signature())!=null);}
    public static ChatMessage presentation(ChatMessage message){
        return message!=null && !message.isDeleted() && knownDeleted(message.nativeMeta().signature())!=null?message.asDeleted(NativeMessageBridge.deletedMarker()):message;
    }
    private static List<ChatMessage> held(){
        var result=new ArrayList<>(ChatStore.get().rawSnapshot());
        for(var partner:PrivateChatStore.knownPartners())result.addAll(PrivateChatStore.rawMessages(partner));
        return result;
    }
    public static ChatMessage loaded(ChatMessage message,int tick){
        var meta=message.nativeMeta();
        return meta.signature()==null?message:message.withNativeMeta(meta.inSession(tick,session));
    }
    public static void request(String signature,int tick,boolean nativePresent){
        signature=NativeMessageMeta.validSignature(signature);
        if(signature==null || nativePresent || knownDeleted(signature)!=null)return;
        int deadline=Integer.MAX_VALUE;
        for(var message:held())if(!message.isDeleted() && signature.equals(message.nativeMeta().signature())) {
            var meta=message.nativeMeta();
            int added=meta.session()==session && meta.addedTick()>=0?meta.addedTick():tick;
            deadline=Math.min(deadline,added+60);
        }
        if(deadline==Integer.MAX_VALUE)return;
        PENDING.merge(signature,deadline,Math::min);
        if(tick>=deadline){PENDING.remove(signature);applied(signature,NativeMessageBridge.deletedMarker());}
    }
    public static void tick(int tick,Predicate<String> nativePresent){
        if(PENDING.isEmpty())return;
        Set<String> held=new HashSet<>();for(var message:held())if(!message.isDeleted() && message.nativeMeta().signature()!=null)held.add(message.nativeMeta().signature());
        var due=new ArrayList<String>();
        var iterator=PENDING.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(!held.contains(entry.getKey())){iterator.remove();continue;}
            if(tick>=entry.getValue() && !nativePresent.test(entry.getKey())){due.add(entry.getKey());iterator.remove();}
        }
        for(var signature:due)applied(signature,NativeMessageBridge.deletedMarker());
    }
    /** Invoked only after the real iterator.set succeeds, or after the missing-entry deadline. */
    public static int applied(String signature,RichText marker){
        signature=NativeMessageMeta.validSignature(signature);if(signature==null)return 0;
        PENDING.remove(signature);
        int count=ChatStore.get().deleteSignature(signature,marker)+PrivateChatStore.deleteSignature(signature,marker);
        if(count>0) {
            revision++;
            com.atom.chat.notification.NotificationBanner.INSTANCE.deleted(signature,marker);
            com.atom.chat.history.ChatHistory.redactNow(signature);
            LISTENERS.removeIf(reference->reference.get()==null);
            for(var reference:List.copyOf(LISTENERS)) {
                var listener=reference.get();
                if(listener!=null)try{listener.accept(signature);}catch(RuntimeException error){com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("chat","Failed to invalidate a deleted message view"),error);}
            }
        }
        return count;
    }
    static int pendingCount(){return PENDING.size();}
}
