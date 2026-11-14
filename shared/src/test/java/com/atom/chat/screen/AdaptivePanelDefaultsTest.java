package com.atom.chat.screen;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.platform.Platform;
import com.atom.chat.settings.SettingsCatalog;
import com.atom.chat.settings.SettingsSection;
import com.atom.chat.settings.SettingsTabDefaults;
import com.atom.lib.design.atomchat.UiTokens;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AdaptivePanelDefaultsTest {
    @TempDir Path root;
    private Object old;
    @BeforeEach void setup() throws Exception {
        Platform.install(new Platform.Provider(){
            public Path configDir(){return root.resolve("config");}
            public Path gameDir(){return root;}
            public boolean isModLoaded(String id){return false;}
        });
        var field=AtomChatConfig.class.getDeclaredField("instance");field.setAccessible(true);
        old=field.get(null);field.set(null,null);
    }
    @AfterEach void restore() throws Exception {
        var field=AtomChatConfig.class.getDeclaredField("instance");field.setAccessible(true);field.set(null,old);
    }
    @Test void firstInstallKeepsTheReferenceProportionsBelowAndAbove1080p() {
        AtomChatConfig.get();
        for(int[] viewport:new int[][]{{1280,720},{1920,1080},{2560,1440},{3840,2160},{3440,1440}}) {
            float width=ScreenMetrics.panelWidth(viewport[0],viewport[1]);
            float height=ScreenMetrics.panelHeight(viewport[1]);
            assertEquals(600F/1048F,width/height,.001F,"default proportions at "+viewport[0]+"x"+viewport[1]);
        }
    }
    @Test void legacySavedLayoutStaysManualUntilTheAdjustTabIsReset() throws Exception {
        Path path=root.resolve("config/atomchat/atomchat-client.json");Files.createDirectories(path.getParent());
        Files.writeString(path,"{\"panelWidth\":420,\"panelHeight\":700,\"pixelRadiusMigrated\":true}");
        var cfg=AtomChatConfig.get();
        assertEquals(525F,ScreenMetrics.panelWidth(1920,1080),.001F);
        assertEquals(875F,ScreenMetrics.panelHeight(1080),.001F);
        assertTrue(SettingsTabDefaults.reset(cfg,SettingsSection.APPEARANCE,"adjust",()->true));
        assertEquals(600F/1048F,ScreenMetrics.panelWidth(1280,720)/ScreenMetrics.panelHeight(720),.001F);
    }
    @Test void heightSliderStopsAtTheActuallyVisibleHeight() {
        AtomChatConfig.get();
        var slider=SettingsCatalog.sliders(SettingsSection.APPEARANCE).stream().filter(s->s.id().equals("height")).findFirst().orElseThrow();
        assertEquals(1048F/UiTokens.SCALE,slider.max(),.01F,"headless fallback uses the 1080p viewport");
        assertEquals(slider.max(),slider.denormalize(1),.01F,"the last slider position reaches the real upper edge");
    }
    @Test void heightMaximumFollowsWindowAndScaleRatherThanAFixedPixelCeiling() {
        var cfg=AtomChatConfig.get();cfg.panelHeightAutomatic=false;
        float small=PanelSizing.maximumHeight(720,cfg),full=PanelSizing.maximumHeight(1080,cfg);
        assertTrue(small<full,"a shorter window has a smaller design-pixel range");
        assertEquals(full,PanelSizing.maximumHeight(2160,cfg),.01F,
                "1080p and 4K can show the same design-pixel maximum because density scales with height");
        for(int height:new int[]{720,1080,1440,2160})
            for(float scale:new float[]{.75F,1F,1.5F})
                for(float content:new float[]{.8F,1F,1.5F}) {
                    cfg.uiScale=scale;cfg.contentScale=content;
                    cfg.panelHeight=PanelSizing.maximumHeight(height,cfg);
                    float drawnHeight=ScreenMetrics.panelHeight(height)*ScreenMetrics.uiDensity(height);
                    float frameDensity=Math.max(1F,height/1080F)*scale;
                    assertEquals(height-32F*content*frameDensity,drawnHeight,.01F,
                            "the last design-pixel value reaches the actual window height minus existing margins");
                }
    }
    @Test void aManualPresetAppliedInHalfScreenKeepsThePhoneWidthButAppliesItsHeight() {
        var cfg=AtomChatConfig.get();cfg.uiScale=.75F;
        float phoneWidth=ScreenMetrics.panelWidth(1280,720);
        cfg.halfScreenMode=true;
        var preset=new AtomChatConfig();preset.uiScale=.75F;preset.panelWidth=410;preset.panelHeight=700;
        com.atom.chat.theme.AppearancePresetStore.Snapshot.capture(preset).applyTo(cfg);
        cfg.halfScreenMode=false;
        assertEquals(875F,ScreenMetrics.panelHeight(720),.01F,"the manual height survives leaving half screen");
        assertEquals(phoneWidth,ScreenMetrics.panelWidth(1280,720),.01F,"the half-screen preset must preserve the current automatic phone width");
    }
    @Test void narrowAndScaledDefaultsStayInsideTheViewportWithoutLosingTheirAspect() {
        var cfg=AtomChatConfig.get();
        for(int[] viewport:new int[][]{{320,240},{720,1280},{2560,1080},{3840,2160}})
            for(float scale:new float[]{.75F,1F,1.5F}) {
                cfg.uiScale=scale;
                float width=ScreenMetrics.panelWidth(viewport[0],viewport[1]);
                float height=ScreenMetrics.panelHeight(viewport[0],viewport[1]);
                assertEquals(600F/1048F,width/height,.001F);
                assertTrue(ScreenMetrics.panelX(viewport[0],viewport[1])+width<=ScreenMetrics.vw(viewport[0],viewport[1])+.01F);
                assertTrue(ScreenMetrics.panelY(viewport[0],viewport[1])+height<=ScreenMetrics.vh(viewport[1])+.01F);
            }
    }
}
