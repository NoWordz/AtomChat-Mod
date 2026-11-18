package com.atom.chat.chat;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TemplateParserTest {
    private MessageTemplate rule(String format){return new MessageTemplate("test","Test",MessageTemplate.Kind.PUBLIC,format,true);}
    @Test void nicknameDoesNotRequireAnAccountPlaceholder(){
        var preview=TemplateParser.preview(rule("[{channel}] {display}: {message}"),"[Hub] 云狐:  hi 😀 ");
        assertEquals(TemplateParser.State.MATCH,preview.state());
        assertEquals("云狐",preview.match().fields().get("display"));
        assertEquals(" hi 😀 ",preview.match().fields().get("message"));
        assertFalse(preview.match().fields().containsKey("sender"));
    }
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

    @Test void clientAndServerReportTheSameSplitAndConflictsForCanonicalRules(){
        String format="{message} < {sender}";
        String sample="Alice < Alice";
        assertEquals(TemplateParser.preview(rule(format),sample),TemplateParser.previewClient(format,MessageTemplate.Kind.PUBLIC,sample,List.of()));
        assertEquals("split_conflict",TemplateParser.previewClient("{sender}{message}",MessageTemplate.Kind.PUBLIC,sample,List.of()).error());
        assertEquals("duplicate_field",TemplateParser.preview(rule("{sender}: {message} {message}"),sample).error());
        assertEquals("duplicate_field",TemplateParser.previewClient("{name}: {content} {content}",MessageTemplate.Kind.PUBLIC,sample,List.of()).error());
    }

    @Test void thirdPartyWhispersAndTrChatSpyAreDistinctFromPublicBodiesContainingArrows(){
        assertTrue(WhisperTextParser.isThirdPartyEnvelope("§8[§3Spy§8] Alice -> Bob: hello","Alice"));
        assertTrue(WhisperTextParser.isThirdPartyEnvelope("[监听] Alice -> Bob: hello","Self"));
        assertTrue(WhisperTextParser.isThirdPartyEnvelope("[Alice -> Bob] hello","Self"));
        assertFalse(WhisperTextParser.isThirdPartyEnvelope("[Alice -> Bob] hello","Bob"));
        assertFalse(WhisperTextParser.isThirdPartyEnvelope("[Hub] Alice: [Alice -> Bob] hello","Self"));
    }
}
