package com.atom.chat.chat;

/** Server-authored interpretation rule, never a rewrite of the server's chat output. */
public record MessageTemplate(String id, String name, Kind kind, String format, boolean enabled) {
    public enum Kind { PUBLIC, INCOMING, OUTGOING }
    public MessageTemplate {
        id=id==null?"":id; name=name==null?"":name; format=format==null?"":format;
    }
    public String validate() {
        if(id.isBlank()||id.length()>64||name.isBlank()||name.length()>48||kind==null
                ||!com.atom.chat.util.TextLimits.wellFormed(name)||!com.atom.chat.util.TextLimits.wellFormed(format)) return "bad_template";
        return TemplateParser.validate(this);
    }
}
