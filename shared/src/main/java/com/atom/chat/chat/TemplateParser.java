package com.atom.chat.chat;

import java.util.*;

/** Bounded literal matching. Enumerates two possible field splits rather than picking a lazy regex result. */
public final class TemplateParser {
    private TemplateParser() {}
    public enum State { MATCH, NO_MATCH, AMBIGUOUS, INVALID }
    public record Span(int start,int end) {}
    public record Match(MessageTemplate.Kind kind,Map<String,String> fields,Map<String,Span> spans) {}
    public record Preview(State state,Match match,String error) {}
    private record Part(String literal,String field) {}
    private static final Set<String> FIELDS=Set.of("sender","display","uuid","target","message","channel");
    private static List<Part> parts(String format) {
        if(format==null||format.isBlank()||format.length()>512) throw new IllegalArgumentException();
        var out=new ArrayList<Part>(); var seen=new HashSet<String>(); int at=0;
        while(at<format.length()) {
            int open=format.indexOf('{',at);
            if(open<0) { if(format.substring(at).contains("}"))throw new IllegalArgumentException();out.add(new Part(format.substring(at),null));break; }
            String literal=format.substring(at,open);
            if(literal.contains("}"))throw new IllegalArgumentException();
            if(!literal.isEmpty())out.add(new Part(literal,null));
            int close=format.indexOf('}',open); if(close<0)throw new IllegalArgumentException();
            String field=format.substring(open+1,close);
            if(!FIELDS.contains(field)||!seen.add(field))throw new IllegalArgumentException();
            out.add(new Part(null,field)); at=close+1;
        }
        return out;
    }
    public static String validate(MessageTemplate template) {
        try {
            var p=parts(template.format()); var fields=new HashSet<String>();
            for(var item:p)if(item.field()!=null)fields.add(item.field());
            if(!fields.contains("message")||!fields.contains(template.kind()==MessageTemplate.Kind.OUTGOING?"target":"sender"))return "bad_template";
            if(fields.contains("uuid")&&!fields.contains("sender"))return "bad_template";
            return null;
        } catch(RuntimeException invalid) {return "bad_template";}
    }
    public static Preview preview(MessageTemplate rule,String text) {
        if(rule==null||rule.validate()!=null)return new Preview(State.INVALID,null,"bad_template");
        if(text==null||text.length()>8192)return new Preview(State.NO_MATCH,null,"");
        List<Match> found=new ArrayList<>(); int[] budget={20_000};
        walk(parts(rule.format()),0,text,0,rule.kind(),new LinkedHashMap<>(),new LinkedHashMap<>(),found,budget);
        if(found.size()>1||budget[0]<=0)return new Preview(State.AMBIGUOUS,null,"");
        return found.isEmpty()?new Preview(State.NO_MATCH,null,""):new Preview(State.MATCH,found.get(0),"");
    }
    private static void walk(List<Part> p,int i,String text,int at,MessageTemplate.Kind kind,
                             Map<String,String> fields,Map<String,Span> spans,List<Match> out,int[] budget) {
        if(out.size()>1||--budget[0]<=0)return;
        if(i==p.size()) {if(at==text.length())out.add(new Match(kind,Map.copyOf(fields),Map.copyOf(spans)));return;}
        Part part=p.get(i);
        if(part.literal()!=null) {if(text.startsWith(part.literal(),at))walk(p,i+1,text,at+part.literal().length(),kind,fields,spans,out,budget);return;}
        int max=part.field().equals("message")?8192:part.field().endsWith("uuid")?36:part.field().equals("sender")||part.field().equals("target")?32:64;
        int end=Math.min(text.length(),at+max);
        for(int to=at+1;to<=end&&out.size()<2&&budget[0]>0;to++) {
            if(to<text.length()&&Character.isLowSurrogate(text.charAt(to)))continue;
            if(i+1==p.size()&&to!=text.length())continue;
            if(i+1<p.size()&&p.get(i+1).literal()!=null&&!text.startsWith(p.get(i+1).literal(),to))continue;
            String value=text.substring(at,to);
            if(!valid(part.field(),value))continue;
            fields.put(part.field(),value);spans.put(part.field(),new Span(at,to));
            walk(p,i+1,text,to,kind,fields,spans,out,budget);
            fields.remove(part.field());spans.remove(part.field());
        }
    }
    private static boolean valid(String field,String value) {
        if(value.isBlank())return false;
        if(field.equals("sender")||field.equals("target"))return value.matches("[A-Za-z0-9_.]{1,32}");
        if(field.endsWith("uuid")) {try{return UUID.fromString(value).toString().equalsIgnoreCase(value)&&!UUID.fromString(value).equals(new UUID(0,0));}catch(IllegalArgumentException e){return false;}}
        return true;
    }
    public static Preview match(List<MessageTemplate> templates,String text) {
        Match found=null;
        for(var t:templates)if(t.enabled()) {
            var p=preview(t,text);
            if(p.state()==State.AMBIGUOUS)return p;
            if(p.state()==State.MATCH) {if(found!=null&&!found.equals(p.match()))return new Preview(State.AMBIGUOUS,null,"");found=p.match();}
        }
        return found==null?new Preview(State.NO_MATCH,null,""):new Preview(State.MATCH,found,"");
    }
}
