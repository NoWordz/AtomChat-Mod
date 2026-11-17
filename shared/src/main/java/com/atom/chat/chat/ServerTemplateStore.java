package com.atom.chat.chat;

import java.util.List;

/** Connection-scoped templates; disconnect clears them before another server can use them. */
public final class ServerTemplateStore {
    private ServerTemplateStore() {}
    private static volatile List<MessageTemplate> rules=List.of();
    public static List<MessageTemplate> rules(){return rules;}
    public static boolean install(List<MessageTemplate> next){
        if(next==null||next.size()>64||next.stream().anyMatch(t->t==null||t.validate()!=null))return false;
        rules=List.copyOf(next);return true;
    }
    public static void clear(){rules=List.of();}
}
