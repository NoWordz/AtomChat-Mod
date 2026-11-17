package com.atom.chat.chat;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TemplateParserTest {
    private MessageTemplate rule(String format){return new MessageTemplate("test","Test",MessageTemplate.Kind.PUBLIC,format,true);}
    @Test void extractsColdUnknownAccountAndOffsetsWithoutInventingUuid(){
        var p=TemplateParser.preview(rule("[{channel}] {sender}: {message}"),"[全服] Stranger: 晚上好😀");
        assertEquals(TemplateParser.State.MATCH,p.state());assertEquals("Stranger",p.match().fields().get("sender"));
        assertNull(p.match().fields().get("uuid"));assertEquals("晚上好😀",p.match().fields().get("message"));
        var span=p.match().spans().get("message");assertEquals("晚上好😀","[全服] Stranger: 晚上好😀".substring(span.start(),span.end()));
    }
    @Test void ambiguousSplitsAndMultipleRulesNeverPickTheFirst(){
        assertEquals(TemplateParser.State.AMBIGUOUS,TemplateParser.preview(rule("{sender}{message}"),"Alicehello").state());
        var incoming=new MessageTemplate("in","in",MessageTemplate.Kind.INCOMING,"{sender}: {message}",true);
        assertEquals(TemplateParser.State.AMBIGUOUS,TemplateParser.match(List.of(rule("{sender}: {message}"),incoming),"Alice: hello").state());
    }
    @Test void validatesDirectionsUnknownFieldsAndDisabledRules(){
        assertNotNull(rule("{typo}: {message}").validate());assertNotNull(rule("{sender}: {sender} {message}").validate());
        var out=new MessageTemplate("out","out",MessageTemplate.Kind.OUTGOING,"我 -> {target}: {message}",true);
        assertNull(out.validate());assertEquals("Bob",TemplateParser.preview(out,"我 -> Bob: hi").match().fields().get("target"));
        assertEquals(TemplateParser.State.NO_MATCH,TemplateParser.match(List.of(new MessageTemplate("x","x",MessageTemplate.Kind.PUBLIC,"{sender}: {message}",false)),"Alice: hi").state());
    }
    @Test void suffixBodiesPreserveTheirExactRegion(){var p=TemplateParser.preview(rule("{message} < {sender}"),"hi < Alice");assertEquals(TemplateParser.State.MATCH,p.state());assertEquals(new TemplateParser.Span(0,2),p.match().spans().get("message"));}
}
