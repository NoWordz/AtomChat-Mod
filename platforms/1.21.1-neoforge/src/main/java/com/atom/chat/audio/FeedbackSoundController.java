package com.atom.chat.audio;
import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.EnumMap;
/** Client cue registration/playback and synchronous action arbitration. */
public final class FeedbackSoundController {
    private static final EnumMap<FeedbackCue,SoundEvent> SOUNDS=new EnumMap<>(FeedbackCue.class);
    private static boolean batching;
    private static FeedbackCue pending;
    private static java.util.function.Consumer<FeedbackCue> testObserver;
    static { for(var cue:FeedbackCue.values()) {
        String id="feedback_"+cue.name().toLowerCase(java.util.Locale.ROOT);
        SOUNDS.put(cue,SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("atomchat", id)));
    } }
    private FeedbackSoundController() {}
    /** Also used by the pitched poke cue, which has its own volume and cooldown. */
    public static SoundEvent toastSound() {
        return SOUNDS.get(FeedbackCue.TOAST);
    }
    public static void registerSound(RegisterEvent event) {
        for(var sound:SOUNDS.values()) event.register(Registries.SOUND_EVENT,sound.getLocation(),()->sound);
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
            case TOAST -> true;
        };
        float volume=FeedbackSoundPolicy.volume(cfg.feedbackSoundEnabled,enabled,cfg.feedbackVolume);
        if(volume<=0) return;
        var observer=testObserver;
        if(observer!=null) { observer.accept(cue); return; }
        var client=Minecraft.getInstance();
        if(client==null) return;
        var sound=SOUNDS.get(cue);
        client.getSoundManager().play(SimpleSoundInstance.forUI(sound,1.0F,volume));
        if (cfg.debug) com.atom.chat.diagnostics.AtomChatLog.diagnostic("feedback",
                "play cue=" + cue + " volume=" + volume);
    }
}
