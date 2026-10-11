package com.atom.chat.audio;
import com.atom.chat.AtomChat;
import com.atom.chat.config.AtomChatConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegisterEvent;
import java.util.EnumMap;
/** Client cue registration/playback and synchronous action arbitration. */
public final class FeedbackSoundController {
    private static final EnumMap<FeedbackCue,SoundEvent> SOUNDS=new EnumMap<>(FeedbackCue.class);
    private static boolean batching;
    private static FeedbackCue pending;
    static { for(var cue:FeedbackCue.values()) {
        String id="feedback_"+cue.name().toLowerCase(java.util.Locale.ROOT);
        SOUNDS.put(cue,SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("atomchat", id)));
    } }
    private FeedbackSoundController() {}
    public static void registerSound(RegisterEvent event) {
        for(var sound:SOUNDS.values()) event.register(Registries.SOUND_EVENT,sound.getLocation(),()->sound);
    }
    public static void beginAction() { batching=true; pending=null; }
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
        var client=Minecraft.getInstance();
        if(volume<=0 || client==null) return;
        var sound=SOUNDS.get(cue);
        client.getSoundManager().play(SimpleSoundInstance.forUI(sound,1.0F,volume));
        if(cfg.debug) AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("feedback", "[AtomChat][feedback] play cue={} volume={}"),cue,volume);
    }
}
