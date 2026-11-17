package com.atom.chat.settings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FormTextTest {
    @Test void pairedCharEventsAtTheLimitNeverStoreHalfAnEmoji(){
        var field=new SettingsFormField();field.configure(false,false,4);field.focus(true);field.edit.set("abc");field.typed('\uD83D');field.typed('\uDE00');assertEquals("abc",field.edit.text());
        field.edit.set("ab");field.typed('\uD83D');field.typed('\uDE00');assertEquals("ab😀",field.edit.text());
        assertFalse(field.edit.replace("\uD83D",10,false,false));assertTrue(com.atom.chat.util.TextLimits.wellFormed(field.edit.text()));
    }
    @Test void movementAndDeletionKeepSupplementaryCharactersWhole(){
        var edit=new FormText();edit.set("A😀中");edit.select(3,false);edit.move(-1,false,false);assertEquals(1,edit.cursor());
        edit.move(1,false,false);assertEquals(3,edit.cursor());edit.delete(false,false,200,false,true);assertEquals("A中",edit.text());
        edit.undo(false);assertEquals("A😀中",edit.text());edit.undo(true);assertEquals("A中",edit.text());
    }
    @Test void selectionPasteUndoAndMultilineNavigationPreserveDraft(){
        var edit=new FormText();edit.set("first\n第二行\nlast");edit.select(8,false);edit.vertical(-1,false);assertEquals(2,edit.cursor());
        edit.all();assertTrue(edit.replace("hi\r\n😀",200,false,true));assertEquals("hi\n😀",edit.text());
        edit.undo(false);assertEquals("first\n第二行\nlast",edit.text());
        edit.set("12");edit.all();assertFalse(edit.replace("abc",10,true,false));assertEquals("12",edit.text());
    }
}
