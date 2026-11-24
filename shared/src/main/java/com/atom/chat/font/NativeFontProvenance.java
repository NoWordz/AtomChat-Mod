package com.atom.chat.font;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.function.*;

/** Resource provenance is transient. MC owns provider loading, ordering, filters and glyph selection. */
public final class NativeFontProvenance {
    public record Definition(String font, JsonObject json) {}
    private record Provider(JsonObject json, boolean changed, Baseline baseline) {}
    private static final java.lang.ref.ReferenceQueue<Object> EXPIRED=new java.lang.ref.ReferenceQueue<>();
    private static final class BuilderKey extends java.lang.ref.WeakReference<Object> {
        private final int hash;
        BuilderKey(Object builder){super(builder,EXPIRED);hash=System.identityHashCode(builder);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){Object builder=get();return this==other || builder!=null && other instanceof BuilderKey key && builder==key.get();}
    }
    private static final Map<BuilderKey, Definition> DEFINITIONS = new HashMap<>();
    private static void discardExpired(){for(java.lang.ref.Reference<?> key;(key=EXPIRED.poll())!=null;)DEFINITIONS.remove(key);}
    private static final Map<Object, Provider> PROVIDERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object,Boolean> REMOVED_DEFAULT=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Object> ACTIVE=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Object, Baseline> BASELINES = new WeakHashMap<>();
    private static final class Baseline {
        final NativeFontResources.Resources builtin;
        final UnaryOperator<JsonObject> normalize;
        final Map<String, List<JsonObject>> fonts = new HashMap<>();
        Baseline(NativeFontResources.Resources builtin, UnaryOperator<JsonObject> normalize) {
            this.builtin=builtin;this.normalize=normalize;
        }
        synchronized List<JsonObject> font(String id) throws IOException {
            var result=fonts.get(id);
            if(result==null){result=new ArrayList<>();collect(id,new JsonObject(),result,new HashSet<>());fonts.put(id,List.copyOf(result));}
            return result;
        }
        void collect(String font,JsonObject inherited,List<JsonObject> result,Set<String> visiting) throws IOException {
            if(!visiting.add(font))return;
            int colon=font.indexOf(':');
            String path=font.substring(0,colon+1)+"font/"+font.substring(colon+1)+".json";
            try(InputStream stream=builtin.open(path)) {
                if(stream==null)return;
                var entries=JsonParser.parseReader(new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("providers");
                for(var entry:entries){
                    var json=entry.getAsJsonObject().deepCopy();
                    JsonObject filter=json.has("filter")?json.getAsJsonObject("filter").deepCopy():new JsonObject();
                    for(var value:inherited.entrySet())filter.add(value.getKey(),value.getValue());
                    String type=json.get("type").getAsString();
                    if(type.equals("reference")||type.equals("minecraft:reference")) {
                        String reference=json.get("id").getAsString();
                        collect(reference.contains(":")?reference:"minecraft:"+reference,filter,result,visiting);
                    } else {
                        if(!filter.entrySet().isEmpty())json.add("filter",filter);
                        result.add(normalize.apply(json));
                    }
                }
            } finally {visiting.remove(font);}
        }
    }
    private NativeFontProvenance() {}
    public static void definition(Object key,String font,JsonObject json) {
        String type=json.get("type").getAsString();
        if(!type.endsWith("reference"))synchronized(DEFINITIONS){discardExpired();DEFINITIONS.put(new BuilderKey(key),new Definition(font,json));}
    }
    public static Definition take(Object key){
        Definition definition;synchronized(DEFINITIONS){discardExpired();definition=DEFINITIONS.remove(new BuilderKey(key));}
        java.lang.ref.Reference.reachabilityFence(key);return definition;
    }
    public static void beginReload(){synchronized(DEFINITIONS){discardExpired();}}
    public static void loaded(Definition definition,Object provider,Object reload,NativeFontResources.Resources selected,
                              NativeFontResources.Resources builtin,UnaryOperator<JsonObject> normalize) {
        if(definition==null)throw new IllegalStateException("Font loaded without its definition");
        Baseline baseline;
        synchronized(BASELINES){baseline=BASELINES.computeIfAbsent(reload,ignored->new Baseline(builtin,normalize));}
        try {
            boolean changed=NativeFontResources.changed(bare(definition.json()),
                    new HashSet<>(baseline.font(definition.font()).stream().map(NativeFontProvenance::bare).toList()),selected,builtin);
            PROVIDERS.put(provider,new Provider(bare(definition.json()),changed,baseline));
        } catch(IOException e){throw new UncheckedIOException("Cannot compare native font resources",e);}
    }
    /** Missing glyphs follow MC only when a formerly eligible stock provider was actually removed. */
    public static void fontStorage(Object storage,String font,List<?> eligible,Map<String,Boolean> options,
                                   NativeFontResources.Resources builtin,UnaryOperator<JsonObject> normalize) {
        var definitions=new HashSet<JsonObject>();Baseline baseline=null;
        for(Object provider:eligible){var record=PROVIDERS.get(provider);if(record!=null){definitions.add(record.json());baseline=record.baseline();}}
        if(baseline==null && !font.equals("minecraft:default")){REMOVED_DEFAULT.put(storage,true);return;}
        if(baseline==null)baseline=new Baseline(builtin,normalize);
        try {
            var stock=baseline.font(font);
            boolean removed=stock.isEmpty() && !font.equals("minecraft:default");
            for(var conditional:stock) {
                boolean allowed=true;
                if(conditional.has("filter"))for(var filter:conditional.getAsJsonObject("filter").entrySet())
                    if(options.getOrDefault(filter.getKey(),false)!=filter.getValue().getAsBoolean()){allowed=false;break;}
                if(allowed && !definitions.contains(bare(conditional))){removed=true;break;}
            }
            REMOVED_DEFAULT.put(storage,removed);
        }catch(IOException error){throw new UncheckedIOException(error);}
    }
    public static boolean removedDefault(Object storage){return Boolean.TRUE.equals(REMOVED_DEFAULT.get(storage));}

    public static void publish(Collection<?> providers) {
        synchronized(PROVIDERS){
            ACTIVE.clear();ACTIVE.addAll(providers);
            PROVIDERS.keySet().removeIf(provider->!ACTIVE.contains(provider));
        }
    }
    private static JsonObject bare(JsonObject json){var result=json.deepCopy();result.remove("filter");return result;}
    public static boolean changed(Object provider){var record=PROVIDERS.get(provider);return record!=null && record.changed();}
    /** Compares the effective choice, including changed reference order/filters, without comparing glyph pixels. */
    public static boolean changedChoice(Object selected,int cp,Map<String,Boolean> options,String defaultFont,
                                        BiPredicate<Object,Integer> hasGlyph) {
        var chosen=PROVIDERS.get(selected);
        if(chosen==null)return false;
        if(chosen.changed())return true;
        try {
            for(var conditional:chosen.baseline().font(defaultFont)) {
                if(conditional.has("filter")) {
                    boolean allowed=true;
                    for(var filter:conditional.getAsJsonObject("filter").entrySet())
                        if(options.getOrDefault(filter.getKey(),false)!=filter.getValue().getAsBoolean()){allowed=false;break;}
                    if(!allowed)continue;
                }
                var definition=bare(conditional);
                if(definition.equals(chosen.json()))return false;
                String type=definition.get("type").getAsString();
                if(type.endsWith("bitmap")) {
                    for(var row:definition.getAsJsonArray("chars"))if(row.getAsString().codePoints().anyMatch(value->value==cp))return true;
                } else if(type.endsWith("space")) {
                    if(definition.getAsJsonObject("advances").has(new String(Character.toChars(cp))))return true;
                } else {
                    boolean known=false;
                    synchronized(PROVIDERS) {
                        for(var candidate:PROVIDERS.entrySet()) {
                            var record=candidate.getValue();
                            if(ACTIVE.contains(candidate.getKey()) && record.baseline()==chosen.baseline() && record.json().equals(definition)) {
                                known=true;
                                if(record.changed() || hasGlyph.test(candidate.getKey(),cp))return true;
                            }
                        }
                    }
                    if(!known)return true;
                }
            }
            return true;
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
}
