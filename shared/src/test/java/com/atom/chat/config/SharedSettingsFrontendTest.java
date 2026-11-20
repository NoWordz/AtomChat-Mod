package com.atom.chat.config;

import com.atom.chat.avatar.*;
import com.atom.chat.audio.*;
import com.atom.chat.chat.*;
import com.atom.chat.nav.*;
import com.atom.chat.platform.Platform;
import com.atom.chat.render.*;
import com.atom.chat.settings.*;
import com.atom.chat.settings.contract.SettingsContractResources;
import com.atom.chat.theme.ThemeService;
import com.atom.chat.ui.*;
import com.atom.lib.design.atomchat.*;
import com.atom.lib.frontend.contract.UiSoundIntent;
import com.atom.lib.frontend.contract.UiSoundSink;
import com.google.gson.*;
import io.github.humbleui.skija.*;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Software Skija production component/composite integration. Never a Minecraft or GPU acceptance test. */
class SharedSettingsFrontendTest {
    @TempDir Path temp;
    private Object oldConfig,oldProvider;private java.lang.reflect.Field configField,providerField;
    @BeforeAll static void bootstrapMinecraft()throws Exception{
        // The real Save button reaches FeedbackSoundController in every host. Bootstrap
        // once so a filtered run cannot poison Minecraft's registry class initialization.
        var constants=Class.forName("net.minecraft.SharedConstants");
        try{constants.getMethod("tryDetectVersion").invoke(null);}
        catch(NoSuchMethodException ignored){constants.getMethod("createGameVersion").invoke(null);}
        try{Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);}
        catch(ClassNotFoundException ignored){Class.forName("net.minecraft.Bootstrap").getMethod("initialize").invoke(null);}
    }
    @BeforeEach void setup()throws Exception{
        configField=AtomChatConfig.class.getDeclaredField("instance");configField.setAccessible(true);oldConfig=configField.get(null);configField.set(null,new AtomChatConfig());
        providerField=Platform.class.getDeclaredField("provider");providerField.setAccessible(true);oldProvider=providerField.get(null);
        Platform.install(new Platform.Provider(){public Path configDir(){return temp.resolve("config");}public Path gameDir(){return temp;}public boolean isModLoaded(String id){return false;}});
    }
    @AfterEach void restore()throws Exception{configField.set(null,oldConfig);providerField.set(null,oldProvider);com.atom.chat.audio.FeedbackSoundController.observeCuesForTest(null);}
    private record RowBox(UiLayout.Rect rect,Object row){}
    private static RowBox rowBox(ServerConfigPage page,UiLayout layout,String rowId)throws Exception{
        var content=ServerConfigPage.class.getDeclaredMethod("content");content.setAccessible(true);
        var groupType=Class.forName("com.atom.chat.config.ServerConfigPage$Group");var rowType=Class.forName("com.atom.chat.config.ServerConfigPage$Row");
        var headerHeight=ServerConfigPage.class.getDeclaredMethod("headerHeight",groupType);headerHeight.setAccessible(true);
        var height=ServerConfigPage.class.getDeclaredMethod("height",rowType,float.class);height.setAccessible(true);
        var gid=groupType.getDeclaredMethod("id");gid.setAccessible(true);var rows=groupType.getDeclaredMethod("rows");rows.setAccessible(true);
        var rid=rowType.getDeclaredMethod("id");rid.setAccessible(true);var viewport=ServerConfigLayout.of(layout).viewport();
        var full=new ArrayList<Float>();var owners=new ArrayList<String>();int target=-1,index=0;Object targetRow=null;
        for(var group:(List<?>)content.invoke(page)){
            float header=(Float)headerHeight.invoke(page,group);if(header>0){full.add(header-UiTokens.SETTINGS_ROW_GAP);owners.add(null);index++;}
            for(var row:(List<?>)rows.invoke(group)){full.add((Float)height.invoke(page,row,viewport.w()));owners.add((String)gid.invoke(group));if(rowId.equals(rid.invoke(row))){target=index;targetRow=row;}index++;}
        }
        assertTrue(target>=0,"row not found: "+rowId);float[] heights=new float[full.size()];for(int i=0;i<heights.length;i++)heights[i]=full.get(i);
        var slots=SettingsGroupFrontend.layout(heights,owners.toArray(String[]::new),key->1.0);
        float top=SettingsGroupFrontend.contentTop(layout,SettingsGroupFrontend.chipBand());
        return new RowBox(new UiLayout.Rect(viewport.x(),top+slots.tops()[target],viewport.w(),slots.heights()[target]),targetRow);
    }
    private static final class Pane implements AutoCloseable {
        final boolean admin;final UiLayout layout;final SettingsSectionPage settings=new SettingsSectionPage();
        final ServerConfigPage server;final ScrollSyncService scrolling;final SettingsDetailPage detail;final HeaderChrome chrome;
        final ClickRipples ripples=new ClickRipples();final PageTransitionComposite composite;
        final JsonObject language;long now;float mx=-100,my=-100;long dt=16;
        int saves;final List<Boolean> directions=new ArrayList<>();
        Pane(boolean admin,int width,String locale)throws Exception{
            this.admin=admin;SettingsSectionPage.clearTranslationCache();layout=UiLayout.ofDetail(20,30,width,820);
            try(var in=new java.io.InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/assets/atomchat/lang/"+locale+".json")),java.nio.charset.StandardCharsets.UTF_8)){language=JsonParser.parseReader(in).getAsJsonObject();}
            server=new ServerConfigPage(new ServerConfigPage.Host(){
                public String label(String key){return tr(key);}public void save(int v,ServerConfigValues values){saves++;}public void refresh(){}public void rescan(){}public void back(){}public String clipboard(){return "";}public void clipboard(String value){}public boolean administration(){return true;}
                public void requestAsset(AdminAsset asset,java.util.function.Consumer<byte[]> receive){if(asset.data()!=null)receive.accept(asset.bytes());}
            },1,new ServerConfigValues(true,true,2048,512,64,7,3000,32,16,"Shared frontend",List.of("欢迎来到服务器！","稍等，我马上回来。")));
            var rule=new MessageTemplate("global","跨服公共聊天",MessageTemplate.Kind.PUBLIC,"[{channel}] {sender}: {message}",true);server.draft().templates(List.of(rule));
            try(var in=getClass().getResourceAsStream("/com/atom/chat/config/sample-emote.jpg")){server.draft().assets(List.of(AdminAsset.imported("mambo.jpg",Objects.requireNonNull(in).readAllBytes())));}
            var primitives=new DrawPrimitives(new DrawPrimitives.Host(){public float frameDt(){return dt;}public boolean emojiPanelOpen(){return false;}public boolean phrasePanelOpen(){return false;}});
            scrolling=new ScrollSyncService(new ScrollSyncService.Host(){public long frameDt(){return dt;}public AppPage topPage(){return nav().page();}public PlayerRef activePrivateTarget(){return null;}public DrawPrimitives primitives(){return primitives;}});
            var picker=new ColorPickerOverlay(s->{});var cropper=new ImageCropper(null);
            detail=new SettingsDetailPage(new SettingsDetailPage.Host(){
                public SettingsSectionPage sectionPage(){return settings;}public ColorPickerOverlay colorPicker(){return picker;}public ImageCropper imageCropper(){return cropper;}public ScrollSyncService scrollSync(){return scrolling;}public UiLayout detailLayout(){return layout;}public int accent(){return AtomChatConfig.get().accentColor;}public float toVirtualX(double x){return (float)x;}public float toVirtualY(double y){return (float)y;}
            });
            chrome=new HeaderChrome(new HeaderChrome.Host(){
                public String label(String key){return admin&&key.equals("atomchat.config.title")?server.title():tr(key);}public String activePrivateName(){return null;}public UiLayout.Rect headerRect(){return layout.header;}public UiLayout.Rect pushedHeaderRect(){return layout.header;}public float frameDt(){return dt;}public int textPrimary(){return AtomChatConfig.get().textPrimaryColor;}
            });
            composite=new PageTransitionComposite(new PageTransitionComposite.Host(){
                public NavPage topNav(){return nav();}public int bodyVariant(){return admin?server.bodyVariant():settings.activeChipIndex(SettingsSection.CHAT);}public boolean arrivalStaggersSurfaces(){return false;}
                public float bodyClipTop(UiLayout l){return admin?server.chromeBottom(l):l.chipBar.bottom();}
                public void drawArrivingBody(Canvas canvas,UiLayout l,int x,int y){if(admin)server.renderBody(canvas,l,mx,my,dt);else detail.drawSettingsSection(canvas,(int)mx,(int)my,SettingsSection.CHAT);}
                public void drawArrivingHeader(Canvas canvas,UiLayout l,int x,int y,float vx,float vy){
                    var reset=admin?null:new ShellHeader.HeaderAction(chrome.adjacentButton(),0,EditorActionIcons.RESTORE,AtomChatConfig.get().textPrimaryColor);
                    chrome.drawPushedHeader(canvas,mx,my,nav(),reset);
                    if(admin)server.renderFixedChrome(canvas,l,mx,my,dt);else settings.renderFixedChipBar(canvas,l,SettingsSection.CHAT,mx,my,AtomChatConfig.get().accentColor);
                }
            });
            server.bindNavigationDirection(backwards->{directions.add(backwards);composite.navigationDirection(backwards);});
            server.markEntered(0);settings.markEntered(0);composite.beginFrame(0);
        }
        String tr(String key){return language.has(key)?language.get(key).getAsString():key;}
        NavPage nav(){return admin?NavPage.of(AppPage.SERVER_CONFIG):NavPage.settingsSection(SettingsSection.CHAT);}
        void chip(int value){if(admin){var cell=layout.chipRect(value,3);server.click(cell.x()+cell.w()/2,cell.y()+cell.h()/2,0);server.release();}else settings.selectChip(SettingsSection.CHAT,value);}
        void fold(){
            float x=layout.list.x()+UiTokens.SETTINGS_ROW_PAD,y=SettingsGroupFrontend.contentTop(layout,SettingsGroupFrontend.chipBand())+UiTokens.SETTINGS_LABEL_H/2;
            if(admin){server.click(x,y,0);server.release();}else{
                var section=SettingsSection.CHAT;
                var rows=settings.rows(section);
                String label="atomchat.settings.group.chat.input";
                int index=java.util.stream.IntStream.range(0,rows.size())
                        .filter(i -> label.equals(rows.get(i).labelKey())).findFirst().orElseThrow();
                float scrollY=scrolling.detailScroll().getScrollY();
                var heading=settings.tutorialGroupAnchor(section,index,index,layout,scrollY);
                y=heading.y()+heading.h()/2;
                var hit=settings.hit(x,y,layout,section,scrollY);
                assertNotNull(hit);assertEquals(label,hit.row().labelKey());assertTrue(settings.perform(hit));
            }
            ripples.click(x,y);
        }
        byte[] frame(Surface surface,long time)throws Exception{
            dt=now>0?Math.max(1,Math.min(50,time-now)):16;now=time;Canvas canvas=surface.getCanvas();int saved=canvas.getSaveCount();
            // A shared nonuniform fixture exposes the actual translucent surfaces; it is not a game background.
            try(var shader=Shader.makeLinearGradient(0,0,600,900,new int[]{0xff687f94,0xff64767b,0xff253947});var paint=new Paint().setShader(shader)){canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(0,0,layout.panelW+40,900),paint);}
            float panelRadius=UiTokens.panelRadius(AtomChatAppearance.theme());
            SoftShadow.drawPanel(canvas,layout.panelX,layout.panelY,layout.panelW,layout.panelH,panelRadius);
            var cfg=AtomChatConfig.get();int tint=PanelBackground.tintFor(cfg.blurEnabled,false,cfg.panelBgColor,cfg.panelOpacity);
            SkiaDraw.drawRoundedRect(canvas,layout.panelX,layout.panelY,layout.panelW,layout.panelH,panelRadius,tint);ShellHeader.drawBackdrop(canvas,layout.header);
            composite.beginFrame(time);if(composite.consumeArrivalEvent()){if(admin)server.markEntered(composite.entrance().enteredAtMs());else settings.markEntered(composite.entrance().enteredAtMs());}
            ripples.begin(this);composite.drawArrivingFrame(canvas,layout,(int)mx,(int)my,mx,my,time);ripples.end(canvas,dt);PanelBackground.drawBezel(canvas,layout.rect());
            assertEquals(saved,canvas.getSaveCount());try(var bitmap=new Bitmap()){bitmap.allocN32Pixels((int)layout.panelW+40,900);assertTrue(surface.readPixels(bitmap,0,0));return bitmap.readPixels();}
        }
        public void close(){server.dispose();composite.close();ripples.clear();}
    }
    private static void png(Surface surface,Path path)throws Exception{Files.createDirectories(path.getParent());try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){Files.write(path,data.getBytes());}}
    private static Path out(){return Path.of("build/test-artifacts/shared-frontend");}
    @Test void serverCueRoutingCoversTabsInternalBackAndToggles()throws Exception{
        var cues=new ArrayList<FeedbackCue>();FeedbackSoundController.observeCuesForTest(cues::add);
        var surface=Surface.makeRasterN32Premul(460,900);
        try(var language=PreviewLanguage.install("zh_cn");var pane=new Pane(true,420,"zh_cn")){
            var registry = SettingsContractResources.approved();
            var contractCues = new ArrayList<UiSoundIntent>();
            if (registry != null) {
                pane.server.bindSettingsContract(registry, new UiSoundSink() {
                    @Override public boolean supports(String channel) { return "MASTER".equals(channel); }
                    @Override public boolean available(String resource) { return "atomchat:feedback_button".equals(resource); }
                    @Override public void play(UiSoundIntent intent) {
                        contractCues.add(intent);
                        cues.add(FeedbackCue.BUTTON);
                    }
                });
            }
            for(int i=0;i<50;i++)pane.frame(surface,1000+i*16);
            assertEquals(registry != null, pane.server.hostingBodyMounted());
            pane.chip(1);assertEquals(List.of(FeedbackCue.BUTTON),cues,"a tab switch is one button cue");
            pane.chip(1);assertEquals(1,cues.size(),"re-clicking the selected tab stays silent");
            pane.chip(0);assertEquals(2,cues.size());
            var edit=ServerConfigPage.class.getDeclaredMethod("templateAction",int.class,String.class);edit.setAccessible(true);edit.invoke(pane.server,0,"edit");
            for(int i=0;i<4;i++)pane.frame(surface,1900+i*16);
            var save=ServerConfigLayout.of(pane.layout).save();pane.server.click(save.x()+save.w()/2,save.y()+save.h()/2,0);pane.server.release();
            assertEquals(3,cues.size(),"Done on an internal page is one button cue");
            for(int i=0;i<4;i++)pane.frame(surface,2000+i*16);
            boolean before=pane.server.draft().enabled("hostingEnabled");var toggle=rowBox(pane.server,pane.layout,"hostingEnabled").rect();
            pane.server.click(toggle.x()+toggle.w()/2,toggle.y()+toggle.h()/2,0);pane.server.release();
            assertEquals(4,cues.size(),"a committed toggle is one button cue");assertNotEquals(before,pane.server.draft().enabled("hostingEnabled"));
            pane.chip(2);assertEquals(5,cues.size());for(int i=0;i<4;i++)pane.frame(surface,2100+i*16);
            var template=rowBox(pane.server,pane.layout,"template:0");var rowType=Class.forName("com.atom.chat.config.ServerConfigPage$Row");
            var inline=ServerConfigPage.class.getDeclaredMethod("inlineList",rowType,float.class);inline.setAccessible(true);
            float size=SettingsListAction.HEIGHT;boolean inLine=(Boolean)inline.invoke(pane.server,template.row(),template.rect().w());
            float actionY=inLine?template.rect().y()+(template.rect().h()-size)/2:template.rect().bottom()-UiTokens.SETTINGS_ROW_PAD-size;
            var templateToggle=new UiLayout.Rect(template.rect().right()-UiTokens.SETTINGS_ROW_PAD-size-UiTokens.SETTINGS_ROW_GAP-UiTokens.SWITCH_W,
                    actionY+(size-UiTokens.SWITCH_H)/2,UiTokens.SWITCH_W,UiTokens.SWITCH_H);
            boolean templateBefore=pane.server.draft().templates().get(0).enabled();
            pane.server.click(templateToggle.x()+templateToggle.w()/2,templateToggle.y()+templateToggle.h()/2,0);pane.server.release();
            assertEquals(6,cues.size(),"a template enable switch is one button cue");assertNotEquals(templateBefore,pane.server.draft().templates().get(0).enabled());
            assertEquals(registry == null ? 0 : 1, contractCues.size(),
                    "Only the selected hosting toggle emits through the contract");
        }finally{surface.close();}
    }
    @Test void serverMeasuredRowsKeepMiBAndEnglishWordsWhole()throws Exception{
        for(String locale:List.of("zh_cn","en_us"))for(int width:new int[]{280,420}){
            try(var language=PreviewLanguage.install(locale);var pane=new Pane(true,width,locale)){
                var content=ServerConfigPage.class.getDeclaredMethod("content");content.setAccessible(true);
                var rowType=Class.forName("com.atom.chat.config.ServerConfigPage$Row");
                var measure=ServerConfigPage.class.getDeclaredMethod("rowText",rowType,float.class);measure.setAccessible(true);
                var description=rowType.getDeclaredMethod("description");description.setAccessible(true);
                var title=rowType.getDeclaredMethod("title");title.setAccessible(true);
                for(int view=0;view<2;view++){
                    if(view==1){var edit=ServerConfigPage.class.getDeclaredMethod("templateAction",int.class,String.class);edit.setAccessible(true);edit.invoke(pane.server,0,"edit");}
                    for(var group:(List<?>)content.invoke(pane.server)){
                        var rows=group.getClass().getDeclaredMethod("rows");rows.setAccessible(true);
                        for(var row:(List<?>)rows.invoke(group)){
                            var flow=(SettingsRowText.Text)measure.invoke(pane.server,row,ServerConfigLayout.of(pane.layout).viewport().w());
                            assertWholeWords((String)description.invoke(row),flow.descriptions());
                            assertWholeWords((String)title.invoke(row),flow.titles());
                        }
                    }
                }
            }
        }
    }
    private static void assertWholeWords(String source,List<String> lines){
        var words=java.util.regex.Pattern.compile("[A-Za-z]+").matcher(source);
        while(words.find()){String word=words.group();assertTrue(lines.stream().anyMatch(line->line.contains(word)),"word split: "+word+" in "+lines);}
    }
    @Test void commonFrontendProducesSameCandidateConditionPairsAndEditorSegments()throws Exception{
        for(String locale:List.of("zh_cn","en_us"))for(int width:new int[]{280,420})for(boolean light:List.of(false,true)){
            var config=new AtomChatConfig();config.animationEnabled=true;if(light)ThemeService.MINIMAL.applyTo(config);config.blurEnabled=false;configField.set(null,config);
            try(var language=PreviewLanguage.install(locale);var normal=new Pane(false,width,locale);var admin=new Pane(true,width,locale);var left=Surface.makeRasterN32Premul(width+40,900);var right=Surface.makeRasterN32Premul(width+40,900);var pair=Surface.makeRasterN32Premul((width+40)*2,900)){
                SettingsSectionPage.clearTranslationCache();normal.chip(1);
                for(int i=0;i<50;i++){normal.frame(left,1000+i*16);admin.frame(right,1000+i*16);}
                try(var a=left.makeImageSnapshot();var b=right.makeImageSnapshot()){pair.getCanvas().drawImage(a,0,0);pair.getCanvas().drawImage(b,width+40,0);}
                String prefix=locale+"-"+width+"-"+(light?"light":"dark");png(pair,out().resolve("pairs/"+prefix+"-history-hosting.png"));
                Files.writeString(out().resolve("pairs/"+prefix+".conditions.txt"),"Software Skija production component/composite test; not Minecraft. Both panels: width="+width+",height=820,locale="+locale+",animation=true,blur=false,panelOpacity="+config.panelOpacity+",style="+config.pageNavStyle+"; fixed gradient input, no raw-GL blur.\n");
                normal.settings.beginNumberEdit(normal.settings.rows(SettingsSection.CHAT).stream().filter(row->row.slider()!=null&&row.slider().id().equals("history_retention")).findFirst().orElseThrow().slider());admin.server.key(258,0);
                for(int i=0;i<4;i++){normal.frame(left,1850+i*16);admin.frame(right,1850+i*16);}
                try(var a=left.makeImageSnapshot();var b=right.makeImageSnapshot()){pair.getCanvas().drawImage(a,0,0);pair.getCanvas().drawImage(b,width+40,0);}png(pair,out().resolve("pairs/"+prefix+"-numeric-focus.png"));normal.settings.cancelNumberEdit();
                for(int chip=1;chip<=2;chip++){admin.chip(chip);for(int i=0;i<50;i++)admin.frame(right,2000+chip*1000+i*16);png(right,out().resolve("pages/"+prefix+"-root-"+chip+".png"));}
                var action=ServerConfigPage.class.getDeclaredMethod("act",String.class);action.setAccessible(true);
                action.invoke(admin.server,"assets:list");for(int i=0;i<50;i++)admin.frame(right,5000+i*16);png(right,out().resolve("pages/"+prefix+"-assets.png"));
                action.invoke(admin.server,"phrases:list");for(int i=0;i<50;i++)admin.frame(right,6000+i*16);png(right,out().resolve("pages/"+prefix+"-phrases.png"));
                var edit=ServerConfigPage.class.getDeclaredMethod("phraseAction",int.class,String.class);edit.setAccessible(true);edit.invoke(admin.server,1,"edit");for(int i=0;i<50;i++)admin.frame(right,7000+i*16);png(right,out().resolve("pages/"+prefix+"-phrase-edit.png"));
                admin.server.key(258,0);admin.server.key(65,2);for(char ch:"中文😀".toCharArray())admin.server.character(ch);
                for(int lines=1;lines<=5;lines++){
                    if(lines>1){admin.server.key(257,0);for(char ch:("第"+lines+"行").toCharArray())admin.server.character(ch);}
                    for(int i=0;i<4;i++)admin.frame(right,8000+lines*80+i*16);
                    png(right,out().resolve("input/"+prefix+"-"+lines+"-lines.png"));
                }
                admin.server.key(65,2);for(int i=0;i<4;i++)admin.frame(right,9000+i*16);png(right,out().resolve("input/"+prefix+"-selection.png"));
                var template=ServerConfigPage.class.getDeclaredMethod("templateAction",int.class,String.class);template.setAccessible(true);template.invoke(admin.server,0,"edit");for(int i=0;i<50;i++)admin.frame(right,10000+i*16);png(right,out().resolve("pages/"+prefix+"-template-top.png"));
                var focused=ServerConfigPage.class.getDeclaredField("requestedFocus");focused.setAccessible(true);focused.set(admin.server,"template.fields");for(int i=0;i<50;i++)admin.frame(right,11000+i*16);png(right,out().resolve("pages/"+prefix+"-template-fields.png"));
                var change=ServerConfigPage.class.getDeclaredMethod("change",String.class,String.class);change.setAccessible(true);change.invoke(admin.server,"template.sample","[Global] Alice: Hello 😀");focused.set(admin.server,"template.sample");
                for(int i=0;i<50;i++)admin.frame(right,12000+i*16);png(right,out().resolve("pages/"+prefix+"-template-match.png"));
            }
        }
    }
    @Test void actualCompositeKeepsDepartingPixelsOnServerTabAndFastReversal()throws Exception{
        for(var style:AtomChatConfig.PageNavStyle.values()){
            AtomChatConfig.get().animationEnabled=true;AtomChatConfig.get().pageNavStyle=style;
            try(var language=PreviewLanguage.install("zh_cn");var pane=new Pane(true,420,"zh_cn");var surface=Surface.makeRasterN32Premul(460,900)){
                byte[] before=null;for(int i=0;i<50;i++)before=pane.frame(surface,1000+i*16);
                pane.chip(2);byte[] departure=pane.frame(surface,2000);assertBodyEquals(before,departure,pane.layout,"server tab departure");assertFalse(pane.composite.overlaySettled(2000));
                byte[] middle=pane.frame(surface,2090);pane.chip(0);assertBodyEquals(middle,pane.frame(surface,2090),pane.layout,"actual 0→2→0 reversal");
                assertEquals(List.of(false,true),pane.directions,"both styles must receive the same forward/backward signals");
                var motion=PageTransitionComposite.class.getDeclaredField("motion");motion.setAccessible(true);
                var sampled=((InterruptiblePageMotion)motion.get(pane.composite)).sample(2100);
                if(style==AtomChatConfig.PageNavStyle.SLIDE)assertTrue(sampled.exitDx()>0,"reversal must exit rightward toward previous tab");
                for(int i=0;i<45;i++){pane.frame(surface,2100+i*16);png(surface,out().resolve("motion/"+style.name().toLowerCase()+"/"+String.format("%03d.png",i)));}
                assertTrue(pane.composite.overlaySettled(2900));pane.chip(0);pane.frame(surface,2910);assertTrue(pane.composite.overlaySettled(2910),"clicking the current chip must not replay entrance");
            }
        }
    }
    @Test void internalEditorsAndReturnsShareTheSameCompositeDirection()throws Exception{
        for(var style:AtomChatConfig.PageNavStyle.values()){
            AtomChatConfig.get().animationEnabled=true;AtomChatConfig.get().pageNavStyle=style;
            try(var language=PreviewLanguage.install("zh_cn");var pane=new Pane(true,420,"zh_cn");var surface=Surface.makeRasterN32Premul(460,900)){
                for(int i=0;i<50;i++)pane.frame(surface,1000+i*16);pane.chip(2);
                for(int i=0;i<50;i++){pane.frame(surface,2000+i*16);if(i<45)png(surface,out().resolve("motion/tab-forward-"+style.name().toLowerCase()+"/"+String.format("%03d.png",i)));}
                pane.chip(1);pane.frame(surface,3000);assertDirection(pane,true,3001);
                for(int i=1;i<50;i++){pane.frame(surface,3000+i*16);if(i<=45)png(surface,out().resolve("motion/tab-reverse-"+style.name().toLowerCase()+"/"+String.format("%03d.png",i-1)));}
                var act=ServerConfigPage.class.getDeclaredMethod("act",String.class);act.setAccessible(true);
                var phrase=ServerConfigPage.class.getDeclaredMethod("phraseAction",int.class,String.class);phrase.setAccessible(true);
                act.invoke(pane.server,"phrases:list");pane.frame(surface,4000);assertDirection(pane,false,4001);
                for(int i=1;i<50;i++)pane.frame(surface,4000+i*16);
                phrase.invoke(pane.server,0,"edit");pane.frame(surface,5000);assertDirection(pane,false,5001);
                for(int i=1;i<50;i++)pane.frame(surface,5000+i*16);
                // The real footer 'Done' is navigation, not a server submission.
                var done=ServerConfigLayout.of(pane.layout).save();pane.server.click(done.x()+10,done.y()+done.h()/2,0);pane.server.release();
                pane.frame(surface,6000);assertDirection(pane,true,6001);assertEquals(0,pane.saves);
                for(int i=1;i<50;i++)pane.frame(surface,6000+i*16);
                pane.server.leave(new ConfirmationDialog());pane.frame(surface,7000);assertDirection(pane,true,7001);
                for(int i=1;i<50;i++)pane.frame(surface,7000+i*16);
                pane.chip(2);for(int i=0;i<50;i++)pane.frame(surface,8000+i*16);
                var template=ServerConfigPage.class.getDeclaredMethod("templateAction",int.class,String.class);template.setAccessible(true);template.invoke(pane.server,0,"edit");
                for(int i=0;i<30;i++){pane.frame(surface,9000+i*16);png(surface,out().resolve("motion/internal-"+style.name().toLowerCase()+"/forward/"+String.format("%03d.png",i)));}
                assertDirection(pane,false,9016);
                pane.server.click(done.x()+10,done.y()+done.h()/2,0);pane.server.release();
                for(int i=0;i<30;i++){pane.frame(surface,10000+i*16);png(surface,out().resolve("motion/internal-"+style.name().toLowerCase()+"/backward/"+String.format("%03d.png",i)));}
                assertDirection(pane,true,10016);assertEquals(0,pane.saves);
            }
        }
    }
    private static void assertDirection(Pane pane,boolean backwards,long time)throws Exception{
        assertEquals(backwards,pane.directions.get(pane.directions.size()-1));
        var field=PageTransitionComposite.class.getDeclaredField("motion");field.setAccessible(true);
        var motion=(InterruptiblePageMotion)field.get(pane.composite);
        var direction=InterruptiblePageMotion.class.getDeclaredField("direction");direction.setAccessible(true);
        assertEquals(backwards?-1:1,direction.getInt(motion));
        if(AtomChatConfig.get().pageNavStyle==AtomChatConfig.PageNavStyle.SLIDE)assertTrue(backwards?motion.sample(time).exitDx()>0:motion.sample(time).exitDx()<0);
    }
    @Test void foldUsesTheActualSettingsTimeConstantInBothPages()throws Exception{
        AtomChatConfig.get().animationEnabled=true;
        try(var language=PreviewLanguage.install("zh_cn");var normal=new Pane(false,420,"zh_cn");var admin=new Pane(true,420,"zh_cn");var left=Surface.makeRasterN32Premul(460,900);var right=Surface.makeRasterN32Premul(460,900);var pair=Surface.makeRasterN32Premul(920,900)){
            for(int i=0;i<50;i++){normal.frame(left,1000+i*16);admin.frame(right,1000+i*16);}normal.fold();admin.fold();
            var folds=ServerConfigPage.class.getDeclaredField("folds");folds.setAccessible(true);
            for(int i=0;i<30;i++){
                normal.frame(left,1800+i*16);admin.frame(right,1800+i*16);
                 float expected=normal.settings.tutorialFolds().expansion().get("label:atomchat.settings.group.chat.input");
                assertEquals(expected,((Map<?,?>)folds.get(admin.server)).get("hosting"));
                try(var a=left.makeImageSnapshot();var b=right.makeImageSnapshot()){pair.getCanvas().drawImage(a,0,0);pair.getCanvas().drawImage(b,460,0);}png(pair,out().resolve("motion/fold/"+String.format("%03d.png",i)));
            }
        }
    }
    @Test void fixedSaveKeepsItsOwnCoordinatesDuringTheRealTabTransition()throws Exception{
        for(var style:AtomChatConfig.PageNavStyle.values()){
            AtomChatConfig.get().animationEnabled=true;AtomChatConfig.get().pageNavStyle=style;
            try(var language=PreviewLanguage.install("zh_cn");var pane=new Pane(true,420,"zh_cn");var surface=Surface.makeRasterN32Premul(460,900)){
                for(int i=0;i<50;i++)pane.frame(surface,1000+i*16);pane.chip(1);pane.frame(surface,2000);pane.frame(surface,2090);
                assertFalse(pane.composite.overlaySettled(2090));var save=ServerConfigLayout.of(pane.layout).save();float x=save.x()+10,y=save.y()+save.h()/2;
                assertTrue(pane.server.stationaryPointer(pane.layout,y));assertFalse(pane.server.stationaryPointer(pane.layout,pane.layout.chipBar.bottom()+100));
                float routedX=pane.server.stationaryPointer(pane.layout,y)?x:pane.composite.bodyX(x),routedY=pane.server.stationaryPointer(pane.layout,y)?y:pane.composite.bodyY(y);
                pane.server.click(routedX,routedY,0);assertEquals(1,pane.saves,"fixed save must not inherit the moving body's inverse transform");
            }
        }
    }
    private static void assertBodyEquals(byte[] expected,byte[] actual,UiLayout layout,String message){
        int width=(int)layout.panelW+40,top=(int)Math.ceil(layout.chipBar.bottom()),bottom=(int)ServerConfigLayout.of(layout).status().y()-20;
        for(int y=top;y<bottom;y++)assertArrayEquals(Arrays.copyOfRange(expected,(y*width+20)*4,(y*width+(int)layout.panelW+20)*4),Arrays.copyOfRange(actual,(y*width+20)*4,(y*width+(int)layout.panelW+20)*4),message+" row="+y);
    }
}
