package com.atom.chat.onboarding;

import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsSectionPage;
import java.util.*;
import java.util.function.Function;

/** Linear itinerary derived from the rows and chips the production settings page actually exposes. */
public final class TutorialGuide {
    private TutorialGuide() { }
    public record Entry(TutorialSession.Step step,SettingsSection section,int chip,int row,int lastRow,String title,String body) {
        public static Entry page(TutorialSession.Step step){return new Entry(step,null,-1,-1,-1,null,null);}
    }
    public static List<Entry> build(SettingsSectionPage page,Function<String,String> translator) {
        var result=new ArrayList<Entry>();
        for(var step:List.of(TutorialSession.Step.NAVIGATION,TutorialSession.Step.CONVERSATIONS,
                TutorialSession.Step.COMPOSE,TutorialSession.Step.MESSAGE_ACTIONS,TutorialSession.Step.EXPRESSIONS,
                TutorialSession.Step.IMAGES,TutorialSession.Step.QUICK_PHRASES,TutorialSession.Step.SEARCH_FILTER,
                TutorialSession.Step.FILTER,TutorialSession.Step.PLAYERS_BLOCK,TutorialSession.Step.AVATAR_PROFILE,TutorialSession.Step.PROFILE,TutorialSession.Step.SETTINGS_HOME))result.add(Entry.page(step));
        for(var section:SettingsSection.values()) {
            var step=switch(section){case APPEARANCE->TutorialSession.Step.APPEARANCE;case CHAT->TutorialSession.Step.CHAT_SETTINGS;case PRIVACY->TutorialSession.Step.PRIVACY;case ABOUT->TutorialSession.Step.ABOUT;};
            for(int chip=0;chip<Math.max(1,page.chips(section).size());chip++) {
                page.selectChip(section,chip);
                var rows=page.tutorialRows(section);
                if (rows.isEmpty()) continue;
                String id = page.chips(section).isEmpty() ? section.id() : page.chips(section).get(chip).id();
                String key = "atomchat.tutorial.settings." + section.id() + "." + id;
                String title = page.chips(section).isEmpty()
                        ? translator.apply("atomchat.tutorial." + step.key() + ".title")
                        : translator.apply(page.chips(section).get(chip).labelKey());
                result.add(new Entry(step, section, chip, 0, rows.size() - 1,
                        title, translator.apply(key)));

            }
        }
        return List.copyOf(result);
    }
}
