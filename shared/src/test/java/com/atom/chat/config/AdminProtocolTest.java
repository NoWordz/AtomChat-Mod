package com.atom.chat.config;
import org.junit.jupiter.api.Test;
import com.atom.chat.chat.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AdminProtocolTest {
    @Test void chunkedUnicodeRoundTripRejectsMissingAndReorderedChunks(){
        String text="{"+"正文😀".repeat(6000)+"}";var chunks=AdminProtocol.chunks(text);var receiver=new AdminProtocol.Receiver();String result=null;
        for(var c:chunks){String wire=new String(c.text().getBytes(java.nio.charset.StandardCharsets.UTF_8),java.nio.charset.StandardCharsets.UTF_8);result=receiver.offer(new AdminProtocol.Chunk(c.id(),c.index(),c.count(),wire),1);}assertEquals(text,result);
        var broken=new AdminProtocol.Receiver();broken.offer(chunks.get(0),1);assertThrows(IllegalArgumentException.class,()->broken.offer(chunks.get(2),2));
    }
    @Test void extendedConfigurationAndMultilinePhrasesShareOneDraft(){
        var rule=new MessageTemplate("one","Global",MessageTemplate.Kind.PUBLIC,"{sender}: {message}",true);
        var base=new ServerConfigValues(true,true,2048,512,64,7,3000,200,16,"",List.of("one"),128,List.of(rule));
        var draft=new ServerConfigDraft(1,base);draft.phrases(List.of("a\nb","second"));
        assertEquals(List.of("a\nb","second"),draft.values().phrases());assertEquals(128,draft.values().maxProfileTotalMb());
        var json=AdminProtocol.JSON.toJson(draft.values());assertEquals(draft.values(),AdminProtocol.JSON.fromJson(json,ServerConfigValues.class));
        assertEquals(List.of(rule),draft.values().templates());assertTrue(draft.dirty());
    }
    @Test void repliesRetainTheOperationIdentityAcrossSerialization(){
        var first=new AdminProtocol.Request("open",0,null,null,null,null);
        var second=new AdminProtocol.Request("save",1,"pack",null,List.of(),null);
        assertNotEquals(first.requestId(),second.requestId());
        var reply=new AdminProtocol.Reply("saved",true,"",null,null,null).correlate(second.requestId());
        var wire=AdminProtocol.JSON.fromJson(AdminProtocol.JSON.toJson(reply),AdminProtocol.Reply.class);
        assertEquals(second.requestId(),wire.requestId());assertNotEquals(first.requestId(),wire.requestId());
    }
}
