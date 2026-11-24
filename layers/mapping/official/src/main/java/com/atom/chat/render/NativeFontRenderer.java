package com.atom.chat.render;
import com.atom.chat.font.NativeFontProvenance;
import com.atom.chat.mixin.NativeFontAccess;
import com.atom.chat.mixin.NativeFontStorageAccess;
import com.atom.chat.text.RichText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.font.FontSet;
import com.mojang.blaze3d.font.GlyphInfo;
import io.github.humbleui.skija.*;
import java.util.*;

/** The same resolved glyph supplies advance, preUi raster preparation and final Skia paint. */
public final class NativeFontRenderer {
    private record Key(Object storage, int codepoint, boolean explicit) {}
    private static final class GlyphPlan {
        final GlyphInfo glyph;
        NativeGlyphRaster raster;
        boolean captured;
        Key key;
        GlyphPlan(GlyphInfo glyph) { this.glyph= glyph; }
        GlyphInfo glyph() { return glyph; }
        NativeGlyphRaster raster() { return raster; }
        void close() { if(raster!=null) raster.close(); raster=null;captured=false; }
    }
    private record RunKey(String text, Style style) {}
    private record RunGlyph(int offset, int codepoint, GlyphPlan plan) {}
    private static final Map<Object,FontSet> STORAGES=new LinkedHashMap<>(128,.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Object,FontSet> eldest) {return size()>128;}
    };
    private static final Map<Object,it.unimi.dsi.fastutil.ints.Int2ObjectMap<GlyphPlan>> DEFAULT_GLYPHS=new IdentityHashMap<>();
    private static final Map<Object,it.unimi.dsi.fastutil.ints.Int2ObjectMap<GlyphPlan>> EXPLICIT_GLYPHS=new IdentityHashMap<>();
    private static int glyphCount;
    private static final Map<RunKey,List<RunGlyph>> RUNS=new LinkedHashMap<>(128,.75F,true);
    private static int runCharacters;
    private static Object fontOwner;
    private static final GlyphPlan AC=new GlyphPlan(null);
    // Raster ownership is separate from layout metadata: measuring hidden rows never uploads them.
    private static final Map<GlyphPlan,Boolean> RASTERS=new LinkedHashMap<>(128,.75F,true);
    private static final Map<Key,GlyphPlan> PINNED=new HashMap<>();
    private static final Set<GlyphPlan> FRAME=Collections.newSetFromMap(new IdentityHashMap<>());
    private static long framePhase,epoch;
    public static void beginFrame() { FRAME.clear();PINNED.clear();framePhase=ObfuscatedText.clock.getAsLong(); }
    public static void endFrame() {
        var entries=RASTERS.keySet().iterator();
        while(RASTERS.size()>4096 && entries.hasNext()) {entries.next().close();entries.remove();}
        FRAME.clear();PINNED.clear();
    }
    private NativeFontRenderer() {}
    public static long epoch() {return epoch;}
    public static void invalidate() {
        RASTERS.keySet().forEach(GlyphPlan::close);RASTERS.clear();FRAME.clear();PINNED.clear();
        STORAGES.clear();DEFAULT_GLYPHS.clear();EXPLICIT_GLYPHS.clear();glyphCount=0;RUNS.clear();runCharacters=0;epoch++;
    }
    private static List<RunGlyph> route(String text,Style style) {
        var client=Minecraft.getInstance();
        Object owner=client==null?null:client.font;
        if(owner!=fontOwner){invalidate();fontOwner=owner;}
        if(owner==null || text.isEmpty())return List.of();
        var key=new RunKey(text,style);
        var result=RUNS.get(key);if(result!=null)return result;
        var nativeRuns=new ArrayList<RunGlyph>();
        for(int i=0;i<text.length();) {
            int cp=text.codePointAt(i);var plan=glyph(style,cp);
            if(plan!=null)nativeRuns.add(new RunGlyph(i,cp,plan));
            i+=Character.charCount(cp);
        }
        result=List.copyOf(nativeRuns);
        while(!RUNS.isEmpty() && (RUNS.size()>=512 || runCharacters+text.length()>65536)) {
            var it=RUNS.keySet().iterator();var old=it.next();runCharacters-=old.text().length();it.remove();
        }
        if(text.length()<=65536){RUNS.put(key,result);runCharacters+=text.length();}
        return result;
    }
    private static GlyphPlan glyph(Style style, int cp) { return glyph(style,cp,false); }
    private static GlyphPlan glyph(Style style, int cp, boolean force) {
        var client = Minecraft.getInstance();
        if (client == null || client.font == null) return null;
        var storage=STORAGES.computeIfAbsent(style.getFont(),ignored->((NativeFontAccess)(Object)client.font).atomchat$storage(style.getFont()));
        boolean explicit=force || !style.getFont().equals(net.minecraft.client.Minecraft.DEFAULT_FONT);
        var tables=explicit?EXPLICIT_GLYPHS:DEFAULT_GLYPHS;
        var table=tables.computeIfAbsent(storage,ignored->new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>());
        var cached=table.get(cp);if(cached!=null)return cached==AC?null:cached;
        var key=new Key(storage,cp,explicit);
        var pinned=PINNED.get(key);if(pinned!=null)return pinned;
        GlyphInfo selected = null;
        boolean changed = false;
        for (var provider : ((NativeFontStorageAccess)(Object)storage).atomchat$providers()) {
            var candidate = provider.getGlyph(cp);
            if (candidate != null) { selected = candidate; changed = NativeFontProvenance.changedChoice(provider,cp,NativeFontOptions.values(client), ((NativeFontStorageAccess)(Object)storage).atomchat$fontId().toString(), (source,code)->((com.mojang.blaze3d.font.GlyphProvider)source).getGlyph(code)!=null); break; }
        }
        if(selected==null || selected==net.minecraft.client.gui.font.glyphs.SpecialGlyphs.MISSING) {
            // External font systems can supply glyphs without populating vanilla's provider lists.
            // Their public glyph may not have a vanilla upload recipe; keep AC's existing font.
            var resolved=storage.getGlyphInfo(cp,false);
            if(resolved!=null && resolved!=net.minecraft.client.gui.font.glyphs.SpecialGlyphs.MISSING) {cache(key,AC);return null;}
            changed|=NativeFontProvenance.removedDefault(storage);
        }
        if (!key.explicit() && !changed) { cache(key, AC); return null; }
        if (selected == null) selected = net.minecraft.client.gui.font.glyphs.SpecialGlyphs.MISSING;
        var plan = new GlyphPlan(selected);
        plan.key=key;cache(key, plan); return plan;
    }
    private static void cache(Key key,GlyphPlan plan) {
        if(glyphCount>=4096){DEFAULT_GLYPHS.clear();EXPLICIT_GLYPHS.clear();glyphCount=0;}
        var tables=key.explicit()?EXPLICIT_GLYPHS:DEFAULT_GLYPHS;
        tables.computeIfAbsent(key.storage(),ignored->new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()).put(key.codepoint(),plan);
        glyphCount++;
    }
    public static boolean hasNative(Style style, int cp) { return !route(cp==32?" ":new String(Character.toChars(cp)),style).isEmpty(); }
    public static boolean hasNative(java.util.List<com.atom.chat.text.RichTextLayout.RichLine> lines) {
        for(var line:lines)for(var part:line.parts())
            if(!part.isItem() && !route(part.text(),part.source().style()).isEmpty())return true;
        return false;
    }
    private static GlyphPlan obfuscated(Style style,int cp,GlyphPlan original) {
        if(!style.isObfuscated() || cp==32)return original;
        var client=Minecraft.getInstance();
        var storage=((NativeFontAccess)(Object)client.font).atomchat$storage(style.getFont());
        var choices=((NativeFontStorageAccess)(Object)storage).atomchat$byWidth().get((int)Math.ceil(original.glyph().getAdvance(false)));
        if(choices==null || choices.isEmpty())return original;
        int index=Math.floorMod(Long.hashCode(framePhase)+cp,choices.size());
        var replacement=glyph(style,choices.getInt(index),true);
        return replacement==null?original:replacement;
    }
    private static void requestGlyph(GlyphPlan plan) {
        FRAME.add(plan);PINNED.put(plan.key,plan);RASTERS.put(plan,Boolean.TRUE);
        if(!plan.captured){plan.raster=NativeGlyphSource.capture(plan.glyph());plan.captured=true;}
    }
    private static void request(String text,Style style) {
        for(var run:route(text,style)) {
            requestGlyph(run.plan());
            requestGlyph(obfuscated(style,run.codepoint(),run.plan()));
        }
    }
    public static void request(RichText text) {
        request("…",Style.EMPTY);
        for(var run:text.runs())request(run.text(),run.style());
    }
    public static void requestLines(java.util.List<com.atom.chat.text.RichTextLayout.RichLine> lines) {
        for(var line:lines)for(var part:line.parts())if(!part.isItem())request(part.text(),part.source().style());
    }
    /** Only requested visible glyphs get rasters, and retired rasters can be requested again. */
    public static void prepare() {
        for(var plan:FRAME)if(plan.raster()!=null)plan.raster().prepare();
    }
    /** Bounds relative to the run origin/baseline. Advance and visible ink remain distinct. */
    public static io.github.humbleui.types.Rect bounds(Font font,String text,Style style) {
        float left=0,right=0,top=0,bottom=0,used=0;int start=0;boolean nativeInk=false;
        float scale=font.getSize()/9F;
        Font ac=style.isBold()?com.atom.chat.font.FontManager.boldFont(font.getSize()):font;
        for(var run:route(text,style)) {
            int i=run.offset(),n=Character.charCount(run.codepoint());var plan=run.plan();
            {
                nativeInk=true;used+=acWidth(ac,text.substring(start,i),style);
                if(plan.raster()!=null) {
                    var source=plan.raster().source();
                    float t=(source.top()-7F)*scale,b=(source.bottom()-7F)*scale;
                    float slant=style.isItalic()?scale-.25F*source.top()*scale:0;
                    float slantBottom=style.isItalic()?scale-.25F*source.bottom()*scale:0;
                    left=Math.min(left,used+source.left()*scale+Math.min(slant,slantBottom));
                    right=Math.max(right,used+source.right()*scale+Math.max(slant,slantBottom)+(style.isBold()?plan.glyph().getBoldOffset()*scale:0));
                    top=Math.min(top,t);bottom=Math.max(bottom,b);
                }
                used+=plan.glyph().getAdvance(style.isBold())*scale;
                left=Math.min(left,used);right=Math.max(right,used);start=i+n;
            }
        }
        used+=acWidth(ac,text.substring(start),style);left=Math.min(left,used);right=Math.max(right,used);
        return nativeInk?io.github.humbleui.types.Rect.makeLTRB(left,top,right,bottom):null;
    }
    public static float width(Font font, String text, Style style) {
        float used=0; int start=0;
        Font ac=style.isBold() ? com.atom.chat.font.FontManager.boldFont(font.getSize()) : font;
        for(var run:route(text,style)) {
            int i=run.offset(),n=Character.charCount(run.codepoint());var nativeGlyph=run.plan();
            {
                used+=acWidth(ac,text.substring(start,i),style);
                used+=nativeGlyph.glyph().getAdvance(style.isBold())*font.getSize()/9F; start=i+n;
            }
        }
        return used+acWidth(ac,text.substring(start),style);
    }
    private static float acWidth(Font font,String text,Style style) {
        return style.isObfuscated() ? ObfuscatedText.width(font,text) : PlainText.width(font,text);
    }
    public static void draw(Canvas canvas, Font font, String text, float x, float baseline, int color, Style style, long phase) {
        float used=0; int start=0; float scale=font.getSize()/9F;
        Font ac=style.isBold() ? com.atom.chat.font.FontManager.boldFont(font.getSize()) : font;
        for(var run:route(text,style)) {
            int i=run.offset(),cp=run.codepoint(),n=Character.charCount(cp);var plan=run.plan();
            {
                String preceding=text.substring(start,i); drawAc(canvas,ac,preceding,x+used,baseline,color,style,phase);
                used+=acWidth(ac,preceding,style);
                var ink=obfuscated(style,cp,plan);
                if (ink.raster()!=null) {
                    ink.raster().draw(canvas,x+used,baseline,scale,color,style.isItalic(),false);
                    if(style.isBold()) ink.raster().draw(canvas,x+used+plan.glyph().getBoldOffset()*scale,baseline,scale,color,style.isItalic(),false);
                }
                used+=plan.glyph().getAdvance(style.isBold())*scale;start=i+n;
            }
        }
        drawAc(canvas,ac,text.substring(start),x+used,baseline,color,style,phase);
    }
    private static void drawAc(Canvas canvas,Font font,String text,float x,float baseline,int color,Style style,long phase) {
        if(text.isEmpty()) return;
        int save=canvas.save();
        try {
            canvas.translate(x,baseline); if(style.isItalic())canvas.skew(-.25F,0);
            if(style.isObfuscated())ObfuscatedText.draw(canvas,font,text,0,0,color,phase);
            else PlainText.draw(canvas,font,text,0,0,color);
        } finally {canvas.restoreToCount(save);}
    }
}
