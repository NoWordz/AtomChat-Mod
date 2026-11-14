package com.atom.chat.config;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Client-thread preview: mutate the existing live object, suppress writes, deep-restore on exit. */
public final class ConfigPreviewTransaction implements AutoCloseable {
    private static ConfigPreviewTransaction active;
    private final AtomChatConfig target;
    private final AtomChatConfig before;
    private boolean finished;
    private ConfigPreviewTransaction(AtomChatConfig target) {
        this.target=target;
        Gson gson=new Gson();before=gson.fromJson(gson.toJson(target),AtomChatConfig.class);
    }
    public static ConfigPreviewTransaction begin(AtomChatConfig config) {
        if(active!=null)throw new IllegalStateException("A configuration preview is already active");
        return active=new ConfigPreviewTransaction(config);
    }
    public static boolean interceptSave(AtomChatConfig config) { return active!=null&&active.target==config; }
    public static boolean active() { return active!=null; }
    public boolean keep() {
        if(finished)return true;
        // Keeping visual/settings adjustments never commits tutorial-authored personal data.
        target.quickPhrases=before.quickPhrases;target.blockedPlayers=before.blockedPlayers;
        active=null;
        if(!AtomChatConfig.saveChecked(target)){active=this;return false;}
        finished=true;return true;
    }
    @Override public void close() {
        if(finished)return;
        for(Field field:AtomChatConfig.class.getFields()) {
            if(Modifier.isStatic(field.getModifiers())||Modifier.isFinal(field.getModifiers()))continue;
            try {field.set(target,field.get(before));}
            catch(IllegalAccessException e){throw new IllegalStateException("Cannot restore preview field "+field.getName(),e);}
        }
        active=null;finished=true;
    }
}
