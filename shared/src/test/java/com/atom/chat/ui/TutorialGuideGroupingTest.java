package com.atom.chat.ui;
import com.atom.chat.onboarding.*;
import com.atom.chat.settings.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TutorialGuideGroupingTest {
    @BeforeAll static void setup(@TempDir Path root)throws Exception{UiFeatureRenderTest.setup(root);}
    @Test void groupEntriesCoverEveryCurrentSettingsItemAndNeverReturnToAnEarlierChip() {
        var page=new SettingsSectionPage();var guide=TutorialGuide.build(page,UiFeatureRenderTest::tr);
        int rows=0,grouped=0;String previous="";var finished=new HashSet<String>();
        for(var e:guide)if(e.section()!=null) {
            String key=e.section()+":"+e.chip();if(!previous.equals(key)){if(!previous.isEmpty())finished.add(previous);assertFalse(finished.contains(key));previous=key;}
            assertFalse(e.body().contains("atomchat."),"guide must use localized production copy: "+e.body());
            assertTrue(e.lastRow()>=e.row());if(e.lastRow()>e.row())grouped++;
        }
        assertTrue(guide.stream().filter(e->e.section()!=null).count()<=9,"one purpose per settings chip, never a narration of each group");
        assertTrue(grouped>=4,"ordinary settings must advance by group, not by item");
        for(var section:SettingsSection.values())for(int chip=0;chip<Math.max(1,page.chips(section).size());chip++) {
            page.selectChip(section,chip);var copy=page.tutorialRows(section);rows+=copy.size();final int index=chip;
            var entries=guide.stream().filter(e->e.section()==section&&e.chip()==index).toList();
            for(int i=0;i<copy.size();i++){final int row=i;
                assertTrue(entries.stream().anyMatch(e->e.row()<=row&&row<=e.lastRow()&&!e.body().isBlank()),section+":"+chip+":"+i);}
        }
        assertTrue(guide.stream().filter(e->e.section()!=null).count()<rows);
    }
}
