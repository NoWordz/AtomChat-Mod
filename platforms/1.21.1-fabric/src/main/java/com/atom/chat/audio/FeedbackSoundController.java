package com.atom.chat.audio;
import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import java.util.EnumMap;
/** Client cue registration/playback and synchronous action arbitration. */
public final class FeedbackSoundController {
    private static final EnumMap<FeedbackCue,SoundEvent> SOUNDS=new EnumMap<>(FeedbackCue.class);
    private static boolean batching;
    private static FeedbackCue pending;
    private static java.util.function.Consumer<FeedbackCue> testObserver;
    static { for(var cue:FeedbackCue.values()) {
        String id="feedback_"+cue.name().toLowerCase(java.util.Locale.ROOT);
        SOUNDS.put(cue,SoundEvent.of(Identifier.of("atomchat", id)));
    } }
    private FeedbackSoundController() {}
    public static void registerSound() {
        for(var sound:SOUNDS.values()) Registry.register(Registries.SOUND_EVENT,sound.getId(),sound);
    }
    public static void beginAction() { batching=true; pending=null; }
    /** Software tests record cue routing; production never installs an observer. */
    public static void observeCuesForTest(java.util.function.Consumer<FeedbackCue> observer) { testObserver=observer; }
    public static void endAction() {
        var cue=pending; batching=false; pending=null;
        if(cue!=null) play(cue);
    }
    public static void play(FeedbackCue cue) {
        if(batching) { pending=FeedbackSoundPolicy.choose(pending,cue); return; }
        var cfg=AtomChatConfig.get();
        boolean enabled=switch(cue) {
            case BUTTON -> cfg.feedbackButtonEnabled;
            case SEND -> cfg.feedbackSendEnabled;
            case PANEL_OPEN -> cfg.feedbackPanelEnabled;
        };
        float volume=FeedbackSoundPolicy.volume(cfg.feedbackSoundEnabled,enabled,cfg.feedbackVolume);
        if(volume<=0) return;
        var observer=testObserver;
        if(observer!=null) { observer.accept(cue); return; }
        var client=MinecraftClient.getInstance();
        if(client==null) return;
        var sound=SOUNDS.get(cue);
        client.getSoundManager().play(PositionedSoundInstance.master(sound,1.0F,volume));
        if(cfg.debug) AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("feedback", "[AtomChat][feedback] play cue={} volume={}"),cue,volume);
    }
}
