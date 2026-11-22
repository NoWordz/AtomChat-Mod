package com.atom.chat.config;

import com.atom.chat.platform.Platform;
import com.atom.chat.ui.*;
import com.atom.chat.theme.ThemeService;
import com.google.gson.*;
import io.github.humbleui.skija.Surface;
import java.nio.file.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigPageTest {
    private Object oldConfig, oldProvider;
    private java.lang.reflect.Field configField, providerField;
    @BeforeAll static void bootstrapMinecraft() throws Exception {
        SharedSettingsFrontendTest.bootstrapMinecraft();
    }

    @BeforeEach void setup(@TempDir Path directory) throws Exception {
        configField=AtomChatConfig.class.getDeclaredField("instance");configField.setAccessible(true);oldConfig=configField.get(null);
        configField.set(null,new AtomChatConfig());AtomChatConfig.get().animationEnabled=false;
        providerField=Platform.class.getDeclaredField("provider");providerField.setAccessible(true);oldProvider=providerField.get(null);
        Platform.install(new Platform.Provider(){public Path configDir(){return directory;}public Path gameDir(){return directory;}public boolean isModLoaded(String id){return false;}});
    }
    @AfterEach void restore() throws Exception {configField.set(null,oldConfig);providerField.set(null,oldProvider);}
    private static ServerConfigValues values() {
        return new ServerConfigValues(true,true,2048,512,64,7,3000,32,8,"My server",List.of("Hello","Welcome"));
    }
    private static final class Host implements ServerConfigPage.Host {
        JsonObject language;
        int saves,backs,rescans,imports,clipboardReads,assetRequests;
        boolean clipboardFails;
        long clipboardDelayMs;
        byte[] serverImage;
        Host(String locale) throws Exception {
            try(var in=ServerConfigPageTest.class.getResourceAsStream("/assets/atomchat/lang/"+locale+".json")) {
                assertNotNull(in);language=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        public String label(String key){assertTrue(language.has(key),key);return language.get(key).getAsString();}
        public void save(int version,ServerConfigValues values){saves++;}
        public void refresh(){}
        public void rescan(){rescans++;}
        public void back(){backs++;}
        public String clipboard(){clipboardReads++;if(clipboardFails)throw new IllegalStateException("clipboard unavailable");if(clipboardDelayMs>0)try{Thread.sleep(clipboardDelayMs);}catch(InterruptedException e){Thread.currentThread().interrupt();}return "pasted";}
        public void clipboard(String text){}
        public boolean administration(){return true;}
        public void chooseImage(java.util.function.Consumer<Path> receive){imports++;receive.accept(null);}
        public void requestAsset(AdminAsset asset,java.util.function.Consumer<byte[]> receive){assetRequests++;if(asset.data()!=null)receive.accept(asset.bytes());else if(serverImage!=null)receive.accept(serverImage);}
    }
    @Test void ordinaryKeysNeverReadClipboardAndFailedPastePreservesTheDraft() throws Exception {
        var host=new Host("zh_cn");var page=new ServerConfigPage(host,1,values());page.key(258,0);
        page.key(263,0);page.key(259,0);page.key(65,2);
        assertEquals(0,host.clipboardReads,"ordinary editing and Select All must not call the system clipboard");
        var before=page.draft().text("maxFileKb");host.clipboardFails=true;
        assertDoesNotThrow(()->page.key(86,2));assertEquals(before,page.draft().text("maxFileKb"));assertEquals(1,host.clipboardReads);page.dispose();
    }
    @Test void mediaRowsOnlyRequestImagesInsideThePaintedViewport()throws Exception{
        var host=new Host("zh_cn");var page=new ServerConfigPage(host,1,values());var assets=new ArrayList<AdminAsset>();
        for(int i=0;i<200;i++)assets.add(new AdminAsset("image-"+i+".jpg",com.atom.chat.pack.ServerPack.sha256Hex(new byte[]{(byte)i}),20,null));
        page.resources("inventory",assets);var action=ServerConfigPage.class.getDeclaredMethod("act",String.class);action.setAccessible(true);action.invoke(page,"assets:list");
        try(var surface=Surface.makeRasterN32Premul(460,650)){
            page.render(surface.getCanvas(),UiLayout.ofDetail(10,10,420,620),-1,-1,16);
            assertTrue(host.assetRequests>0);assertTrue(host.assetRequests<8,"offscreen inventory must not request or decode all 200 images; actual="+host.assetRequests);
        }finally{page.dispose();}
    }
    @Test void inputAndLayoutCpuProbe() throws Exception {
        var host=new Host("zh_cn");host.clipboardDelayMs=2;var page=new ServerConfigPage(host,1,values());page.key(258,0);
        long started=System.nanoTime();for(int i=0;i<12;i++)page.key(263,0);long elapsed=System.nanoTime()-started;
        System.out.println("INPUT_PROBE syntheticClipboardDelayMs=2 keys=12 reads="+host.clipboardReads+" elapsedNs="+elapsed);page.dispose();
        for(int length:new int[]{32,512,4096}){
            String text="Alice 中文 😀 body ".repeat(300).substring(0,length);
            var field=new com.atom.chat.settings.SettingsFormField();field.configure(false,true,8192);
            var rect=new UiLayout.Rect(10,10,240,80);long[] samples=new long[25];
            try(var surface=Surface.makeRasterN32Premul(280,130)){
                for(int i=0;i<30;i++){
                    long at=System.nanoTime();field.paint(surface.getCanvas(),rect,text,true,false,-1,-1,16);
                    if(i>=5)samples[i-5]=System.nanoTime()-at;
                }
            }
            Arrays.sort(samples);System.out.println("LAYOUT_PROBE length="+length+" width=240 stableFrames=25 medianNs="+samples[12]+" p95Ns="+samples[23]);
        }
    }
    @Test void resourceRootContainsNamedDirectActionsRatherThanOnlyAnAnonymousSummary() throws Exception {
        var page=new ServerConfigPage(new Host("zh_cn"),1,values());
        page.draft().assets(List.of(AdminAsset.imported("mambo.jpg",new byte[]{1})));
        var chips=ServerConfigPage.class.getDeclaredField("chips");chips.setAccessible(true);((com.atom.chat.settings.SettingsChipBar)chips.get(page)).select(1);
        var content=ServerConfigPage.class.getDeclaredMethod("content");content.setAccessible(true);
        var groups=(List<?>)content.invoke(page);boolean named=false,importHeader=false,phraseCard=false;
        for(var group:groups){
            var rows=group.getClass().getDeclaredMethod("rows");rows.setAccessible(true);
            for(var row:(List<?>)rows.invoke(group)){
                var id=row.getClass().getDeclaredMethod("id");id.setAccessible(true);
                var kind=row.getClass().getDeclaredMethod("kind");kind.setAccessible(true);
                var title=row.getClass().getDeclaredMethod("title");title.setAccessible(true);
                named|=kind.invoke(row).toString().equals("ASSET")&&title.invoke(row).equals("mambo.jpg");
                importHeader|=id.invoke(row).equals("asset:import");
                var groupId=group.getClass().getDeclaredMethod("id");groupId.setAccessible(true);
                phraseCard|=id.invoke(row).equals("phrases:list")&&!groupId.invoke(group).equals("contents");
            }
        }
        boolean[] found={named,importHeader,phraseCard};
        assertAll(()->assertTrue(found[0],"root must expose named asset rows"),()->assertTrue(found[1],"import must be on root"),()->assertTrue(found[2],"phrases use their own entry card"));
        page.dispose();
    }
    @Test void saveStaysOnCurrentChipAndBackUsesTheExistingUnsavedConfirmation() throws Exception {
        var host=new Host("zh_cn");var page=new ServerConfigPage(host,1,values());
        var layout=UiLayout.ofDetail(10,10,420,780);
        try(var surface=Surface.makeRasterN32Premul(450,810)) {
            page.render(surface.getCanvas(),layout,-1,-1,16);
            var chip=layout.chipRect(2,3);page.click(chip.x()+1,chip.y()+1,0);page.render(surface.getCanvas(),layout,-1,-1,16);
            page.draft().text("packName","draft");
            var confirmation=new ConfirmationDialog();page.leave(confirmation);
            assertTrue(confirmation.isActive());assertEquals(0,host.backs);confirmation.reset();
            var save=ServerConfigLayout.of(layout).save();page.click(save.x()+1,save.y()+1,0);
            assertEquals(1,host.saves);assertTrue(page.draft().saving());
            page.result(true,"");assertTrue(page.acceptSnapshot(2,values()));
            assertEquals(2,page.chip());assertFalse(page.draft().dirty());
            page.leave(confirmation);assertEquals(1,host.backs);
        }
    }
    @Test void fixedSaveRemainsOutsideTheScrolledContentInBothLanguagesAndCompactLayouts() throws Exception {
        Path out=Path.of("build/test-artifacts/v090-server-config-list-editor");Files.createDirectories(out);
        for(String language:List.of("zh_cn","en_us"))for(int width:List.of(280,420))for(boolean light:List.of(false,true)) {
            var config=new AtomChatConfig();config.animationEnabled=false;if(light)ThemeService.MINIMAL.applyTo(config);configField.set(null,config);
            var page=new ServerConfigPage(new Host(language),1,values());var layout=UiLayout.ofDetail(10,10,width,600);
            var geometry=ServerConfigLayout.of(layout);
            assertTrue(geometry.viewport().bottom()<geometry.save().y());assertTrue(geometry.viewport().h()>0);
            try(var surface=Surface.makeRasterN32Premul(width+20,620)) {
                for(int i=0;i<3;i++) {
                    page.render(surface.getCanvas(),layout,-1,-1,16);
                    var cell=layout.chipRect(i,3);page.click(cell.x()+1,cell.y()+1,0);page.release();
                    surface.getCanvas().clear(config.panelBgColor);
                    page.render(surface.getCanvas(),layout,-1,-1,16);
                    ShellHeader.render(surface.getCanvas(),layout.header,new Host(language).label("atomchat.config.title"),true,
                            new UiLayout.Rect(layout.header.x()+com.atom.lib.design.atomchat.UiTokens.EDGE_CONTROL_INSET, layout.header.y()+(layout.header.h()-com.atom.lib.design.atomchat.UiTokens.ACTION_BUTTON_SIZE)/2, com.atom.lib.design.atomchat.UiTokens.ACTION_BUTTON_SIZE,com.atom.lib.design.atomchat.UiTokens.ACTION_BUTTON_SIZE),0,config.textPrimaryColor);
                    try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) {
                        assertNotNull(data);Files.write(out.resolve(language+"-"+width+"-"+(light?"light":"dark")+"-"+i+".png"),data.getBytes());
                    }
                }
                page.scroll(geometry.viewport().x()+1,geometry.viewport().y()+1,-100);
                page.render(surface.getCanvas(),layout,-1,-1,16);
                assertEquals(geometry.save(),ServerConfigLayout.of(layout).save());
            }
        }
    }
    @Test void messageTemplateGuideOpensFromTheRenderedTabAndPreservesTheDraft() throws Exception {
        for (String locale : List.of("zh_cn", "en_us")) {
            var host = new Host(locale);
            var page = new ServerConfigPage(host, 1, values());
            var layout = UiLayout.ofDetail(10, 10, 280, 820);
            try (var surface = Surface.makeRasterN32Premul(300, 840)) {
                page.render(surface.getCanvas(), layout, -1, -1, 16);
                click(page, layout.chipRect(2, 3));
                page.render(surface.getCanvas(), layout, -1, -1, 16);
                var guides = groupHits(page, "template-guide-entry");
                assertEquals(1, guides.size(), "the template tab has a visible tutorial entry");
                var prior = page.draft().values();
                click(page, guides.get(0));
                page.render(surface.getCanvas(), layout, -1, -1, 16);
                assertEquals(host.label("atomchat.admin.template_guide.title"), page.title());
                assertEquals(prior, page.draft().values());

                click(page, ServerConfigLayout.of(layout).save());
                page.render(surface.getCanvas(), layout, -1, -1, 16);
                assertEquals(2, page.chip());
                assertEquals(1, groupHits(page, "template-guide-entry").size());
                assertEquals(prior, page.draft().values());
                assertFalse(page.draft().dirty());
                assertEquals(0, host.saves);
            } finally {
                page.dispose();
            }
        }
    }
    @Test void templateEditorDoneAndBackRetainOneDraftUntilRootSave() throws Exception {
        var config=new AtomChatConfig();config.animationEnabled=false;configField.set(null,config);
        var host=new Host("zh_cn");var page=new ServerConfigPage(host,4,values());var layout=UiLayout.ofDetail(10,10,420,850);
        try(var surface=Surface.makeRasterN32Premul(450,880)){
            page.render(surface.getCanvas(),layout,-1,-1,16);
            var chip=layout.chipRect(2,3);page.click(chip.x()+1,chip.y()+1,0);page.release();page.render(surface.getCanvas(),layout,-1,-1,16);
            var viewport=ServerConfigLayout.of(layout).viewport();
            click(page,groupHits(page,"header:templates").get(0));
            page.render(surface.getCanvas(),layout,-1,-1,16);
            assertEquals("编辑消息模板",page.title());assertEquals(1,page.draft().templates().size());assertEquals(0,host.saves);
            Path errorPreview=Path.of("build/test-artifacts/v090-server-config-list-editor/details/zh_cn-420-dark-template-error.png");Files.createDirectories(errorPreview.getParent());writeFrame(page,surface,layout,errorPreview);
            page.key(258,0);page.render(surface.getCanvas(),layout,-1,-1,16);
            for(char c:"Global".toCharArray())page.character(c);
            assertEquals("Global",page.draft().templates().get(0).name());
            var done=ServerConfigLayout.of(layout).save();page.click(done.x()+1,done.y()+1,0);
            page.render(surface.getCanvas(),layout,-1,-1,16);
            assertEquals(0,host.saves);assertTrue(page.draft().dirty());assertEquals(2,page.chip());
            page.click(done.x()+1,done.y()+1,0);assertEquals(1,host.saves);assertTrue(page.draft().saving());
            page.dispose();
        }
    }
    @Test void nestedEditorsListsAndMediaRenderInCompactAndWideThemes() throws Exception {
        var action=ServerConfigPage.class.getDeclaredMethod("act",String.class);action.setAccessible(true);
        var edit=ServerConfigPage.class.getDeclaredMethod("templateAction",int.class,String.class);edit.setAccessible(true);
        var change=ServerConfigPage.class.getDeclaredMethod("change",String.class,String.class);change.setAccessible(true);
        Path out=Path.of("build/test-artifacts/v090-server-config-list-editor/details");Files.createDirectories(out);
        byte[] picture;try(var in=getClass().getResourceAsStream("/com/atom/chat/config/sample-emote.jpg")){assertNotNull(in);picture=in.readAllBytes();}
        var template=new com.atom.chat.chat.MessageTemplate("global","Global",com.atom.chat.chat.MessageTemplate.Kind.PUBLIC,"[{channel}] {sender}: {message}",true);
        for(String language:List.of("zh_cn","en_us"))for(int width:List.of(280,420))for(boolean light:List.of(false,true)){
            var config=new AtomChatConfig();config.animationEnabled=false;if(light)ThemeService.MINIMAL.applyTo(config);configField.set(null,config);
            var mediaHost=new Host(language);mediaHost.serverImage=picture;
            var page=new ServerConfigPage(mediaHost,1,values().withExtras(128,List.of(template,
                    new com.atom.chat.chat.MessageTemplate("incoming","Incoming",com.atom.chat.chat.MessageTemplate.Kind.INCOMING,"From {sender}: {message}",false),
                    new com.atom.chat.chat.MessageTemplate("outgoing","Outgoing",com.atom.chat.chat.MessageTemplate.Kind.OUTGOING,"To {target}: {message}",true))));
            var published=AdminAsset.imported("mambo.jpg",picture);
            page.resources("baseline",List.of(new AdminAsset(published.name(),published.hash(),published.size(),null)));
            page.draft().assets(List.of(AdminAsset.imported("mambo.jpg",picture)));
            var layout=UiLayout.ofDetail(10,10,width,820);String prefix=language+"-"+width+"-"+(light?"light":"dark");
            try(var surface=Surface.makeRasterN32Premul(width+20,840)){
                var rootChip=layout.chipRect(1,3);page.render(surface.getCanvas(),layout,-1,-1,16);page.click(rootChip.x()+1,rootChip.y()+1,0);page.release();
                writeFrame(page,surface,layout,out.resolve(prefix+"-resources-top.png"));
                var scrollField=ServerConfigPage.class.getDeclaredField("scroll");scrollField.setAccessible(true);
                ((ScrollController)scrollField.get(page)).scrollTo(10000,false);
                writeFrame(page,surface,layout,out.resolve(prefix+"-resources-contents.png"));
                page.draft().assets(List.of(published.metadata(),AdminAsset.imported("mambo-new.jpg",picture)));
                action.invoke(page,"phrases:list");writeFrame(page,surface,layout,out.resolve(prefix+"-phrases.png"));
                var phraseHits=groupHits(page,"phrases");assertFalse(phraseHits.isEmpty());var hovered=phraseHits.get(phraseHits.size()-1);
                surface.getCanvas().clear(config.panelBgColor);page.render(surface.getCanvas(),layout,hovered.x()+hovered.w()/2,hovered.y()+hovered.h()/2,1000);
                drawHeader(page,surface,layout);
                try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){Files.write(out.resolve(prefix+"-phrases-hover.png"),data.getBytes());}
                page.leave(new ConfirmationDialog());
                var templateChip=layout.chipRect(2,3);page.render(surface.getCanvas(),layout,-1,-1,16);page.click(templateChip.x()+1,templateChip.y()+1,0);page.release();
                writeFrame(page,surface,layout,out.resolve(prefix+"-templates.png"));
                edit.invoke(page,0,"edit");change.invoke(page,"template.sample","[Global] Alice: Hello 😀");
                writeFrame(page,surface,layout,out.resolve(prefix+"-template.png"));
                action.invoke(page,"assets:list");writeFrame(page,surface,layout,out.resolve(prefix+"-emotes.png"));
            }finally{page.dispose();}
        }
    }
    @Test void tinyAssetSizesAndDestructiveInlineActionsStayMeaningful() throws Exception {
        var size=ServerConfigPage.class.getDeclaredMethod("assetSize",long.class);size.setAccessible(true);
        assertEquals("<1 KiB",size.invoke(null,70L));assertEquals("1.5 KiB",size.invoke(null,1536L));
        assertEquals(com.atom.lib.design.atomchat.UiTokens.dangerColor(),com.atom.chat.settings.SettingsListAction.textColor(false,true,true));
    }
    @Test void addActionsAreOutsideListCardsAndSampleAndResultHaveSeparateCards() throws Exception {
        var page=new ServerConfigPage(new Host("zh_cn"),1,values().withExtras(64,List.of(new com.atom.chat.chat.MessageTemplate("a","Global",com.atom.chat.chat.MessageTemplate.Kind.PUBLIC,"{sender}: {message}",true))));
        var act=ServerConfigPage.class.getDeclaredMethod("act",String.class);act.setAccessible(true);act.invoke(page,"phrases:list");
        var content=ServerConfigPage.class.getDeclaredMethod("content");content.setAccessible(true);
        assertFalse(content.invoke(page).toString().contains("id=phrase:add"),"Add is a small list-header action, not a content row");
        var edit=ServerConfigPage.class.getDeclaredMethod("templateAction",int.class,String.class);edit.setAccessible(true);edit.invoke(page,0,"edit");
        var groups=(List<?>)content.invoke(page);String inputGroup="",outputGroup="";
        for(var group:groups){
            var id=group.getClass().getDeclaredMethod("id");id.setAccessible(true);
            if(group.toString().contains("id=template.sample"))inputGroup=(String)id.invoke(group);
            if(group.toString().contains("id=template.preview"))outputGroup=(String)id.invoke(group);
        }
        assertNotEquals(inputGroup,outputGroup,"sample input and parsed output must be independent cards");page.dispose();
    }
    @Test void editorsRenderByScrollingTheNormalPanelAndPreviewUsesTheActualParser() throws Exception {
        var editPhrase=ServerConfigPage.class.getDeclaredMethod("phraseAction",int.class,String.class);editPhrase.setAccessible(true);
        var editTemplate=ServerConfigPage.class.getDeclaredMethod("templateAction",int.class,String.class);editTemplate.setAccessible(true);
        var change=ServerConfigPage.class.getDeclaredMethod("change",String.class,String.class);change.setAccessible(true);
        var boxes=ServerConfigPage.class.getDeclaredMethod("fieldBoxes",float.class);boxes.setAccessible(true);
        var parsed=ServerConfigPage.class.getDeclaredMethod("parsed");parsed.setAccessible(true);
        Path out=Path.of("build/test-artifacts/v090-server-config-list-editor/segments");Files.createDirectories(out);
        var publicRule=new com.atom.chat.chat.MessageTemplate("public","Global",com.atom.chat.chat.MessageTemplate.Kind.PUBLIC,"[{channel}] {sender}: {message}",true);
        for(String language:List.of("zh_cn","en_us"))for(int width:List.of(280,420))for(boolean light:List.of(false,true)){
            var config=new AtomChatConfig();config.animationEnabled=false;if(light)ThemeService.MINIMAL.applyTo(config);configField.set(null,config);
            var page=new ServerConfigPage(new Host(language),1,values().withExtras(64,List.of(publicRule)));var layout=UiLayout.ofDetail(10,10,width,820);
            String prefix=language+"-"+width+"-"+(light?"light":"dark");
            try(var surface=Surface.makeRasterN32Premul(width+20,840)){
                editPhrase.invoke(page,0,"edit");change.invoke(page,"phrase.value",language.equals("zh_cn")?"稍等，我马上回来。":"Be right back.");
                writeFrame(page,surface,layout,out.resolve(prefix+"-phrase-edit.png"));
                editTemplate.invoke(page,0,"edit");change.invoke(page,"template.sample","[Global] Alice: Hello 😀");
                writeFrame(page,surface,layout,out.resolve(prefix+"-template-top.png"));
                var flowed=(List<?>)boxes.invoke(page,ServerConfigLayout.of(layout).viewport().w()-com.atom.lib.design.atomchat.UiTokens.SETTINGS_ROW_PAD*2);
                float available=ServerConfigLayout.of(layout).viewport().w()-com.atom.lib.design.atomchat.UiTokens.SETTINGS_ROW_PAD*2;
                for(int i=0;i<flowed.size();i++){
                    var box=(UiLayout.Rect)flowed.get(i);assertTrue(box.x()>=0&&box.right()<=available+.01f);
                    if(i>0){var prior=(UiLayout.Rect)flowed.get(i-1);assertTrue(box.y()>prior.y()||box.x()>=prior.right());}
                }
                if(width==420&&language.equals("zh_cn"))assertTrue(flowed.stream().map(r->(UiLayout.Rect)r).filter(r->r.y()==0).count()>=3,"wide field flow must not have a fixed two-column ceiling");
                captureRow(page,surface,layout,"template.format",out.resolve(prefix+"-template-format-fields.png"));
                captureRow(page,surface,layout,"template.fields",out.resolve(prefix+"-template-fields.png"));
                var fieldHits=groupHits(page,"template");click(page,fieldHits.get(fieldHits.size()-1));
                assertTrue(page.draft().templates().get(0).format().contains("{target}"),"the rendered last field button must insert its actual placeholder");
                page.draft().templates(List.of(publicRule));change.invoke(page,"template.sample","[Global] Alice: Hello 😀");
                assertEquals(com.atom.chat.chat.TemplateParser.State.MATCH,((com.atom.chat.chat.TemplateParser.Preview)parsed.invoke(page)).state());
                captureRow(page,surface,layout,"template.sample",out.resolve(prefix+"-template-sample-match.png"));
                change.invoke(page,"template.sample","this does not match the format");
                assertEquals(com.atom.chat.chat.TemplateParser.State.NO_MATCH,((com.atom.chat.chat.TemplateParser.Preview)parsed.invoke(page)).state());
                captureRow(page,surface,layout,"template.sample",out.resolve(prefix+"-template-sample-no-match.png"));
                page.draft().templates(List.of(publicRule,new com.atom.chat.chat.MessageTemplate("private","Private",com.atom.chat.chat.MessageTemplate.Kind.INCOMING,publicRule.format(),true)));
                change.invoke(page,"template.sample","[Global] Alice: Hello 😀");
                assertEquals(com.atom.chat.chat.TemplateParser.State.AMBIGUOUS,((com.atom.chat.chat.TemplateParser.Preview)parsed.invoke(page)).state());
                captureRow(page,surface,layout,"template.sample",out.resolve(prefix+"-template-sample-ambiguous.png"));
            }finally{page.dispose();}
        }
    }
    private static void captureRow(ServerConfigPage page,Surface surface,UiLayout layout,String target,Path path)throws Exception{
        page.render(surface.getCanvas(),layout,-1,-1,16);
        var content=ServerConfigPage.class.getDeclaredMethod("content");content.setAccessible(true);
        var header=ServerConfigPage.class.getDeclaredMethod("headerHeight",Class.forName("com.atom.chat.config.ServerConfigPage$Group"));header.setAccessible(true);
        var height=ServerConfigPage.class.getDeclaredMethod("height",Class.forName("com.atom.chat.config.ServerConfigPage$Row"),float.class);height.setAccessible(true);
        float offset=0,desired=-1;var viewport=ServerConfigLayout.of(layout).viewport();
        for(var group:(List<?>)content.invoke(page)){
            offset+=(float)header.invoke(page,group);var rows=group.getClass().getDeclaredMethod("rows");rows.setAccessible(true);
            for(var row:(List<?>)rows.invoke(group)){
                var id=row.getClass().getDeclaredMethod("id");id.setAccessible(true);
                if(target.equals(id.invoke(row)))desired=offset;
                offset+=(float)height.invoke(page,row,viewport.w());
            }
            offset+=com.atom.lib.design.atomchat.UiTokens.SETTINGS_ROW_GAP;
        }
        assertTrue(desired>=0,target);var field=ServerConfigPage.class.getDeclaredField("scroll");field.setAccessible(true);
        var controller=(ScrollController)field.get(page);controller.scrollTo(desired,false);
        assertTrue(controller.getScrollY()>0,"segment must use the actual panel scroll, not an enlarged surface");
        writeFrame(page,surface,layout,path);
        Files.writeString(path.resolveSibling(path.getFileName()+".scroll.txt"),"panel="+layout.panelW+"x"+layout.panelH+"; target="+target+"; scrollY="+controller.getScrollY()+"; viewport="+viewport+"\n");
    }
    @Test void rootImportPreviewAndRemoveUseActualHitTargetsAndOnlyRootSavePublishes() throws Exception {
        byte[] picture;try(var in=getClass().getResourceAsStream("/com/atom/chat/config/sample-emote.jpg")){picture=in.readAllBytes();}
        var host=new Host("zh_cn");host.serverImage=picture;var page=new ServerConfigPage(host,1,values());
        var baseline=List.of(AdminAsset.imported("mambo.jpg",picture).metadata());page.resources("original",baseline);
        var confirmation=new ConfirmationDialog();page.bindConfirmation(confirmation);
        var layout=UiLayout.ofDetail(10,10,420,1300);
        try(var surface=Surface.makeRasterN32Premul(450,1330)){
            page.render(surface.getCanvas(),layout,-1,-1,16);var chip=layout.chipRect(1,3);page.click(chip.x()+1,chip.y()+1,0);page.release();page.render(surface.getCanvas(),layout,-1,-1,16);
            var boxes=groupHits(page,"contents");assertEquals(5,boxes.size());
            click(page,boxes.get(0));assertEquals(1,host.imports);assertFalse(page.draft().dirty());
            click(page,boxes.get(2));assertEquals("预览",page.title());assertEquals(0,host.saves);
            page.leave(confirmation);assertEquals(1,page.chip());page.render(surface.getCanvas(),layout,-1,-1,16);
            click(page,groupHits(page,"contents").get(3));assertTrue(confirmation.hasPendingAction());
            confirmation.render(surface.getCanvas(),new UiLayout.Rect(10,10,420,1300),-1,-1,1000);confirmation.key(258);confirmation.key(257);
            assertTrue(page.draft().assets().isEmpty());assertEquals(1,baseline.size());assertTrue(page.draft().dirty());assertEquals(0,host.saves);
            page.render(surface.getCanvas(),layout,-1,-1,16);click(page,ServerConfigLayout.of(layout).save());assertEquals(1,host.saves);
        }finally{page.dispose();}
    }
    private static void click(ServerConfigPage page,UiLayout.Rect box){page.click(box.x()+box.w()/2,box.y()+box.h()/2,0);page.release();}
    @Test void compactPhraseArrowCellsDoNotOpenTheEditorAndReorderOnlyTheDraft() throws Exception {
        var host=new Host("zh_cn");var page=new ServerConfigPage(host,1,values());var layout=UiLayout.ofDetail(10,10,280,820);
        var action=ServerConfigPage.class.getDeclaredMethod("act",String.class);action.setAccessible(true);action.invoke(page,"phrases:list");
        try(var surface=Surface.makeRasterN32Premul(300,840)){
            page.render(surface.getCanvas(),layout,-1,-1,16);var boxes=groupHits(page,"phrases");
            click(page,boxes.get(1));assertEquals("常用语",page.title());assertEquals(List.of("Hello","Welcome"),page.draft().phrases());
            click(page,boxes.get(2));assertEquals("常用语",page.title());assertEquals(List.of("Welcome","Hello"),page.draft().phrases());assertEquals(0,host.saves);
            page.render(surface.getCanvas(),layout,-1,-1,16);click(page,groupHits(page,"phrases").get(0));assertEquals("编辑常用语",page.title());
        }finally{page.dispose();}
    }
    private static List<UiLayout.Rect> groupHits(ServerConfigPage page,String name)throws Exception{
        var field=ServerConfigPage.class.getDeclaredField("hits");field.setAccessible(true);var boxes=new ArrayList<UiLayout.Rect>();
        for(var hit:(List<?>)field.get(page)){
            var group=hit.getClass().getDeclaredMethod("group");group.setAccessible(true);
            if(!name.equals(group.invoke(hit)))continue;
            var box=hit.getClass().getDeclaredMethod("box");box.setAccessible(true);boxes.add((UiLayout.Rect)box.invoke(hit));
        }
        return boxes;
    }
    @Test void editorPreviewDetectsConflictsWithOtherEnabledTemplates() throws Exception {
        var publicRule=new com.atom.chat.chat.MessageTemplate("public","Public",com.atom.chat.chat.MessageTemplate.Kind.PUBLIC,"{sender}: {message}",true);
        var privateRule=new com.atom.chat.chat.MessageTemplate("private","Private",com.atom.chat.chat.MessageTemplate.Kind.INCOMING,"{sender}: {message}",true);
        var page=new ServerConfigPage(new Host("zh_cn"),1,values().withExtras(64,List.of(publicRule,privateRule)));
        var edit=ServerConfigPage.class.getDeclaredMethod("templateAction",int.class,String.class);edit.setAccessible(true);edit.invoke(page,0,"edit");
        var change=ServerConfigPage.class.getDeclaredMethod("change",String.class,String.class);change.setAccessible(true);change.invoke(page,"template.sample","Alice: hello");
        var parsed=ServerConfigPage.class.getDeclaredMethod("parsed");parsed.setAccessible(true);
        var preview=(com.atom.chat.chat.TemplateParser.Preview)parsed.invoke(page);
        assertEquals(com.atom.chat.chat.TemplateParser.State.AMBIGUOUS,preview.state());page.dispose();
    }
    @Test void editorPreviewIncludesThisClientsConfiguredRules() throws Exception {
        AtomChatConfig.get().whisperTemplates=List.of("{sender}: {message}");
        var rule=new com.atom.chat.chat.MessageTemplate("public","Public",com.atom.chat.chat.MessageTemplate.Kind.PUBLIC,"{sender}: {message}",true);
        var page=new ServerConfigPage(new Host("en_us"),1,values().withExtras(64,List.of(rule)));
        try {
            var edit=ServerConfigPage.class.getDeclaredMethod("templateAction",int.class,String.class);edit.setAccessible(true);edit.invoke(page,0,"edit");
            var change=ServerConfigPage.class.getDeclaredMethod("change",String.class,String.class);change.setAccessible(true);change.invoke(page,"template.sample","Alice: hello");
            var parsed=ServerConfigPage.class.getDeclaredMethod("parsed");parsed.setAccessible(true);
            var preview=(com.atom.chat.chat.TemplateParser.Preview)parsed.invoke(page);
            assertEquals(com.atom.chat.chat.TemplateParser.State.AMBIGUOUS,preview.state());
            assertEquals("rule_conflict",preview.error());
        } finally { page.dispose(); }
    }
    @Test void undoHistoryDoesNotLeakBetweenTwoPhraseEditorsWithTheSameText() throws Exception {
        var config=new AtomChatConfig();config.animationEnabled=false;configField.set(null,config);
        var page=new ServerConfigPage(new Host("zh_cn"),1,values());var layout=UiLayout.ofDetail(10,10,420,850);
        var edit=ServerConfigPage.class.getDeclaredMethod("phraseAction",int.class,String.class);edit.setAccessible(true);
        try(var surface=Surface.makeRasterN32Premul(450,880)){
            edit.invoke(page,0,"edit");page.render(surface.getCanvas(),layout,-1,-1,16);page.key(258,0);page.render(surface.getCanvas(),layout,-1,-1,16);
            page.key(65,2);for(char c:"Welcome".toCharArray())page.character(c);assertEquals("Welcome",page.draft().phrases().get(0));
            page.leave(new ConfirmationDialog());edit.invoke(page,1,"edit");page.render(surface.getCanvas(),layout,-1,-1,16);page.key(258,0);page.render(surface.getCanvas(),layout,-1,-1,16);
            page.key(90,2);assertEquals("Welcome",page.draft().phrases().get(1));
        }finally{page.dispose();}
    }
    private static void writeFrame(ServerConfigPage page,Surface surface,UiLayout layout,Path path)throws Exception{
        page.render(surface.getCanvas(),layout,-1,-1,16);
        surface.getCanvas().clear(AtomChatConfig.get().panelBgColor);page.render(surface.getCanvas(),layout,-1,-1,16);
        drawHeader(page,surface,layout);
        try(var image=surface.makeImageSnapshot();var data=image.encodeToData()){assertNotNull(data);Files.write(path,data.getBytes());}
    }
    private static void drawHeader(ServerConfigPage page,Surface surface,UiLayout layout){
        float edge=com.atom.lib.design.atomchat.UiTokens.EDGE_CONTROL_INSET,size=com.atom.lib.design.atomchat.UiTokens.ACTION_BUTTON_SIZE;
        ShellHeader.render(surface.getCanvas(),layout.header,page.title(),true,new UiLayout.Rect(layout.header.x()+edge,layout.header.y()+(layout.header.h()-size)/2,size,size),0,AtomChatConfig.get().textPrimaryColor);
    }
}
