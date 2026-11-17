package com.atom.chat.settings;

import java.util.*;

/** Text/selection/undo model. Offsets are UTF-16 for rendering, movement never splits surrogate pairs. */
public final class FormText {
    private record State(String text,int cursor,int anchor) {}
    private String text="";private int cursor,anchor;
    private final Deque<State> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    public String text(){return text;}public int cursor(){return cursor;}public int anchor(){return anchor;}
    public int start(){return Math.min(cursor,anchor);}public int end(){return Math.max(cursor,anchor);}
    public String selected(){return text.substring(start(),end());}
    public void set(String value){if(!Objects.equals(text,value)){text=value==null?"":value;cursor=anchor=text.length();undo.clear();redo.clear();}}
    public void select(int position,boolean extend){cursor=boundary(position);if(!extend)anchor=cursor;}
    private int boundary(int i){i=Math.max(0,Math.min(text.length(),i));return i>0&&i<text.length()&&Character.isLowSurrogate(text.charAt(i))&&Character.isHighSurrogate(text.charAt(i-1))?i-1:i;}
    public void all(){anchor=0;cursor=text.length();}
    private void checkpoint(){undo.push(new State(text,cursor,anchor));while(undo.size()>64)undo.removeLast();redo.clear();}
    public boolean replace(String insert,int max,boolean number,boolean multiline){
        insert=insert.replace("\r\n","\n").replace('\r','\n');
        if(!multiline)insert=insert.replace('\n',' ');
        String next=text.substring(0,start())+insert+text.substring(end());
        if(next.length()>max||!com.atom.chat.util.TextLimits.wellFormed(next)||number&&!next.matches("[0-9]*"))return false;
        if(next.equals(text))return false;
        int at=start()+insert.length();checkpoint();text=next;cursor=anchor=at;return true;
    }
    public void move(int direction,boolean extend,boolean word){
        int pos=cursor;
        if(!extend&&start()!=end()&&!word)pos=direction<0?start():end();
        else if(word){
            if(direction<0){while(pos>0&&Character.isWhitespace(text.codePointBefore(pos)))pos=text.offsetByCodePoints(pos,-1);while(pos>0&&!Character.isWhitespace(text.codePointBefore(pos)))pos=text.offsetByCodePoints(pos,-1);}
            else{while(pos<text.length()&&!Character.isWhitespace(text.codePointAt(pos)))pos=text.offsetByCodePoints(pos,1);while(pos<text.length()&&Character.isWhitespace(text.codePointAt(pos)))pos=text.offsetByCodePoints(pos,1);}
        } else if(direction<0&&pos>0||direction>0&&pos<text.length())pos=text.offsetByCodePoints(pos,direction);
        select(pos,extend);
    }
    public boolean delete(boolean forward,boolean word,int max,boolean number,boolean multiline){
        if(start()==end()){int old=cursor;move(forward?1:-1,true,word);anchor=old;}
        return replace("",max,number,multiline);
    }
    public void home(boolean end,boolean whole,boolean extend){int pos=whole?(end?text.length():0):end?text.indexOf('\n',cursor):text.lastIndexOf('\n',Math.max(-1,cursor-1))+1;if(pos<0)pos=text.length();select(pos,extend);}
    public void vertical(int direction,boolean extend){
        int start=text.lastIndexOf('\n',Math.max(-1,cursor-1))+1,col=text.codePointCount(start,cursor),target;
        if(direction<0){if(start==0){select(0,extend);return;}int prev=text.lastIndexOf('\n',start-2)+1;target=text.offsetByCodePoints(prev,Math.min(col,text.codePointCount(prev,start-1)));}
        else{int next=text.indexOf('\n',cursor);if(next<0){select(text.length(),extend);return;}int end=text.indexOf('\n',next+1);if(end<0)end=text.length();target=text.offsetByCodePoints(next+1,Math.min(col,text.codePointCount(next+1,end)));}
        select(target,extend);
    }
    public void undo(boolean forward){var from=forward?redo:undo;var to=forward?undo:redo;if(from.isEmpty())return;to.push(new State(text,cursor,anchor));var state=from.pop();text=state.text();cursor=state.cursor();anchor=state.anchor();}
}
