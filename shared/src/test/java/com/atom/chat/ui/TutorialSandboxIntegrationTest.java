package com.atom.chat.ui;

import com.atom.chat.chat.*;
import com.atom.chat.config.*;
import com.atom.chat.onboarding.*;
import com.atom.chat.platform.Platform;
import com.atom.chat.text.RichText;
import io.github.humbleui.skija.Image;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TutorialSandboxIntegrationTest {
    @TempDir Path tmp;
    @BeforeEach void install(){Platform.install(new Platform.Provider(){public Path configDir(){return tmp.resolve("config");}public Path gameDir(){return tmp;}public boolean isModLoaded(String id){return false;}});}
    private TutorialSandbox sandbox(){return new TutorialSandbox((s,own)->LocalEcho.build(s,s,null,null,null,"Guide",RichText.literal("Guide")),"Hello","Reply");}
    @Test void realFeedFilterAlsoAppliesToSandboxMessages() {
        var cfg=AtomChatConfig.get();var previous=cfg.worldFeedFilter;
        var feed=new WorldFeedFilter();
        @SuppressWarnings("unchecked")
        var host=(MessageListWiring.Host<Object>)java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),new Class[]{MessageListWiring.Host.class},
                (proxy,method,args)->method.getName().equals("worldFeed")?feed:null);
        try(var sandbox=sandbox()) {
            cfg.worldFeedFilter=MessageFilter.SYSTEM;
            var result=new MessageListWiring<>(host).messagesForNav(
                    com.atom.chat.nav.NavPage.of(com.atom.chat.nav.AppPage.WORLD_CHAT));
            assertTrue(result.isEmpty(),"system-only filter must hide player samples instead of only changing the icon");
        } finally {cfg.worldFeedFilter=previous;}
    }
    @Test void actualSendPipelineKeepsCommandsInSandboxWithoutOpeningNetworkOrTouchingRealHistory() {
        var before=ChatStore.get().snapshot();var calls=new ArrayList<String>();
        var host=(SendPipeline.Host)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SendPipeline.Host.class},(p,m,a)->{
            calls.add(m.getName());if(m.getName().equals("client"))throw new AssertionError("sandbox reached network client");
            if(m.getName().equals("replySlots"))return new ReplySlotStore();return null;
        });
        try(var sandbox=sandbox()) {
            new SendPipeline(host).sendMessage("/kill @a");
            assertEquals(3,TutorialSandbox.messages().size());assertEquals("/kill @a",TutorialSandbox.messages().get(2).getRawText());
            assertEquals(before,ChatStore.get().snapshot());assertFalse(calls.contains("client"));assertTrue(calls.contains("clearComposer"));
        }
        assertFalse(TutorialSandbox.active());
    }
    @Test void blockActionChangesSandboxOnlyAndNeverPersistsPersonalBlockList() {
        var c=AtomChatConfig.get();var old=c.blockedPlayers;c.blockedPlayers=List.of("Existing");
        try(var sandbox=sandbox()) {
            var player=PlayerRef.of(UUID.randomUUID(),"Guide");BlockList.setBlocked(player,true);
            assertTrue(BlockList.isBlocked(player));assertEquals(List.of("Existing"),c.blockedPlayers);
        } finally {c.blockedPlayers=old;}
    }
    @Test void imageStepAddsARealCICodeMessageWithAnInMemoryPreview() {
        var preview=new com.atom.chat.image.ImagePreviewOverlay();
        try(var sandbox=sandbox()) {
            sandbox.showImage();sandbox.showImage();
            assertEquals(3,TutorialSandbox.messages().size(),"image demo is added once");
            assertFalse(TutorialSandbox.imageActionPerformed());
            var imageMessage=TutorialSandbox.messages().get(2);
            assertEquals(TutorialSandbox.IMAGE_URL,Cicodes.extractImageUrl(imageMessage.getRawText()));
            Image image=com.atom.chat.image.ImageLoader.get().get(TutorialSandbox.IMAGE_URL);
            assertNotNull(image,"the picture is already in memory before the real message draws");
            preview.open(null);
            assertFalse(TutorialSandbox.imageActionPerformed(),"a failed preview cannot complete the step");
            preview.open(image);
            assertTrue(preview.isActive(),"the production preview accepts the sandbox bitmap");
            assertTrue(TutorialSandbox.imageActionPerformed(),"the real preview entry must complete the image step");
        } finally {preview.close();}
        assertFalse(TutorialSandbox.active());
        assertNull(com.atom.chat.image.ImageLoader.get().get(TutorialSandbox.IMAGE_URL),
                "leaving the tutorial releases the memory-only picture");
    }
    @Test void productionPopupSandboxesOwnVisibleStateWithoutLiveFilesOrPhrases() throws Exception {
        var c=AtomChatConfig.get();var before=List.copyOf(c.quickPhrases);
        var emoji=EmojiPanel.sandbox(new EmojiPanel.Host(){
            public void insert(String text){}
            public void sendSticker(Path file){}
            public void pickEmoteFile(){}
        });
        emoji.toggle();emoji.update(200);assertTrue(emoji.isOpen());
        assertFalse(java.nio.file.Files.exists(tmp.resolve("atomchat/client/emotes")));
        var phrases=QuickPhrasePanel.sandbox(text->{},()->0xFF4A90E2);
        assertFalse(phrases.phraseAt(0).isEmpty());phrases.open();phrases.deleteAt(0);phrases.commitText("demo only");
        assertEquals(before,c.quickPhrases);
    }
    @Test void tutorialCannotStartAnInvisibleLivePanelEditor() {
        var opened = new java.util.concurrent.atomic.AtomicBoolean();
        var feedback = new ArrayList<String>();
        var host = (com.atom.chat.settings.SettingsActions.Host) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{com.atom.chat.settings.SettingsActions.Host.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sectionPage")) return new com.atom.chat.settings.SettingsSectionPage();
                    if (method.getName().equals("openPanelEditor")) opened.set(true);
                    if (method.getName().equals("feedback")) feedback.add((String) args[0]);
                    return null;
                });
        try (var sandbox = sandbox()) {
            new com.atom.chat.settings.SettingsActions(host).handleSettingsAction("panel_edit");
            assertFalse(opened.get(), "tutorial cannot leave an editor hidden behind its input gate");
            assertTrue(feedback.contains("atomchat.tutorial.preview_only"));
        }
    }
}
