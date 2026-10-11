package com.atom.chat.fun;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
/** Deterministic, normalized particle positions; no graphics or client state. */
public final class ChatEffectParticles {
    public record Particle(float x,float y,float size,float alpha,float rotation) {}
    private ChatEffectParticles() {}
    public static List<Particle> frame(ChatEffectType type,float progress,long seed) {
        if(type==null || !Float.isFinite(progress) || progress<0 || progress>=1) return List.of();
        var random=new Random(seed);
        var result=new ArrayList<Particle>(28);
        for(int i=0;i<28;i++) {
            float x=random.nextFloat(), delay=random.nextFloat()*.38f;
            float speed=.9f+random.nextFloat()*.4f, phase=random.nextFloat()*6.283f;
            float t=(progress-delay)/(1-delay);
            float size=20+4*random.nextInt(3);
            if(t<0) continue;
            float fade=Math.min(1,t*8)*Math.min(1,(1-progress)*8)*.8f;
            if(type==ChatEffectType.FIREWORK) {
                float angle=i*2.399963f, radius=(float)(1-Math.pow(1-t,3))*.26f;
                x=.25f+(i%3)*.25f+(float)Math.cos(angle)*radius;
                result.add(new Particle(x,.35f+(i%2)*.22f+(float)Math.sin(angle)*radius,size,fade*(1-t),0));
            } else {
                result.add(new Particle(x+(float)Math.sin(t*6+phase)*.035f,-.08f+t*speed*1.2f,size,fade,(float)Math.sin(t*5+phase)*18));
            }
        }
        return List.copyOf(result);
    }
}
