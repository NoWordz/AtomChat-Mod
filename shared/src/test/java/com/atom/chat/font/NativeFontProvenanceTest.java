package com.atom.chat.font;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeFontProvenanceTest {
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    private static NativeFontResources.Resources resources(Map<String,String> files){return id->{var data=files.get(id);return data==null?null:new ByteArrayInputStream(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));};}
    private static final String BASE="{\"type\":\"bitmap\",\"file\":\"test:base.png\",\"height\":8,\"ascent\":7,\"chars\":[\"ABC\"]}";
    private static final String ALT="{\"type\":\"bitmap\",\"file\":\"test:alt.png\",\"height\":10,\"ascent\":8,\"chars\":[\"ABC\"]}";
    private static Map<String,String> builtin(){return new HashMap<>(Map.of(
        "minecraft:font/default.json","{\"providers\":[{\"type\":\"reference\",\"id\":\"test:base\"},"+ALT+"]}",
        "test:font/base.json","{\"providers\":["+BASE+"]}",
        "test:font/alt.json","{\"providers\":["+ALT+"]}",
        "test:textures/base.png","original bitmap bytes","test:textures/alt.png","original alternate bytes"));}
    private static Object loaded(Object reload,String font,String definition,Map<String,String> actual,Map<String,String> builtin){
        Object key=new Object(),provider=new Object();NativeFontProvenance.definition(key,font,json(definition));
        NativeFontProvenance.loaded(NativeFontProvenance.take(key),provider,reload,resources(actual),resources(builtin),value->value);
        return provider;
    }
    @Test void copiedDefinitionsAndAssetsDoNotSwitchOrdinaryTextButIndependentIconDoes(){
        var builtin=builtin();Object reload=new Object();Object normal=loaded(reload,"test:base",BASE,builtin,builtin);
        assertFalse(NativeFontProvenance.changed(normal));
        assertFalse(NativeFontProvenance.changedChoice(normal,'A',Map.of(),"minecraft:default",(p,cp)->true));
        Object icon=loaded(reload,"minecraft:default","{\"type\":\"space\",\"advances\":{\"X\":-3.0}}",builtin,builtin);
        assertTrue(NativeFontProvenance.changed(icon));
        assertFalse(NativeFontProvenance.changedChoice(normal,'B',Map.of(),"minecraft:default",(p,cp)->true));
    }
    @Test void changingOnlyAnAssetSwitchesEveryCoveredGlyphAndReloadDropsThatDecision(){
        var builtin=builtin();var actual=new HashMap<>(builtin);actual.put("test:textures/base.png","one atlas cell changed");
        Object changed=loaded(new Object(),"test:base",BASE,actual,builtin);
        assertTrue(NativeFontProvenance.changedChoice(changed,'A',Map.of(),"minecraft:default",(p,cp)->true));
        assertTrue(NativeFontProvenance.changedChoice(changed,'C',Map.of(),"minecraft:default",(p,cp)->true));
        Object restored=loaded(new Object(),"test:base",BASE,builtin,builtin);
        assertFalse(NativeFontProvenance.changedChoice(restored,'A',Map.of(),"minecraft:default",(p,cp)->true));
    }
    @Test void anUnchangedAlternateSelectedByReferenceOrOrderStillFollowsTheNativeChoice(){
        var builtin=builtin();Object alternate=loaded(new Object(),"test:alt",ALT,builtin,builtin);
        assertFalse(NativeFontProvenance.changed(alternate));
        assertTrue(NativeFontProvenance.changedChoice(alternate,'B',Map.of(),"minecraft:default",(p,cp)->true));
    }
    @Test void inheritedReferenceFilterAndOptionsChooseTheCorrectBaseline(){
        var builtin=builtin();builtin.put("minecraft:font/default.json","{\"providers\":[{\"type\":\"reference\",\"id\":\"test:base\",\"filter\":{\"jp\":false}},"+ALT+"]}");
        Object reload=new Object();Object normal=loaded(reload,"test:base",BASE,builtin,builtin);
        Object alternate=loaded(reload,"test:alt",ALT,builtin,builtin);
        assertFalse(NativeFontProvenance.changedChoice(normal,'A',Map.of("jp",false),"minecraft:default",(p,cp)->true));
        assertFalse(NativeFontProvenance.changedChoice(alternate,'A',Map.of("jp",true),"minecraft:default",(p,cp)->true));
        assertTrue(NativeFontProvenance.changedChoice(normal,'A',Map.of("jp",true),"minecraft:default",(p,cp)->true));
    }
    @Test void equalBuilderValuesCannotCrossAssociateAndClaimedDefinitionsSurviveNextReload(){
        record Key(int index){}
        Object first=new Key(1),second=new Key(1);
        NativeFontProvenance.definition(first,"test:base",json(BASE));
        NativeFontProvenance.definition(second,"test:alt",json(ALT));
        var claimed=NativeFontProvenance.take(first);assertEquals(json(BASE),claimed.json());
        NativeFontProvenance.beginReload();assertEquals(json(ALT),NativeFontProvenance.take(second).json(),"a concurrent reload must not erase an earlier live builder");
        Object provider=new Object();var builtin=builtin();
        NativeFontProvenance.loaded(claimed,provider,new Object(),resources(builtin),resources(builtin),value->value);
        assertFalse(NativeFontProvenance.changed(provider));
    }
    @Test void removingAnEligibleStockProviderPreservesNativeMissingGlyphSemanticsButAddedIconsDoNot(){
        var builtin=builtin();Object reload=new Object();Object normal=loaded(reload,"test:base",BASE,builtin,builtin);
        Object alternate=loaded(reload,"test:alt",ALT,builtin,builtin);Object storage=new Object();
        NativeFontProvenance.fontStorage(storage,"minecraft:default",List.of(normal,alternate),Map.of(),resources(builtin),value->value);
        assertFalse(NativeFontProvenance.removedDefault(storage));
        Object icon=loaded(reload,"minecraft:default","{\"type\":\"space\",\"advances\":{\"X\":-3.0}}",builtin,builtin);
        NativeFontProvenance.fontStorage(storage,"minecraft:default",List.of(icon,normal,alternate),Map.of(),resources(builtin),value->value);
        assertFalse(NativeFontProvenance.removedDefault(storage));
        NativeFontProvenance.fontStorage(storage,"minecraft:default",List.of(icon),Map.of(),resources(builtin),value->value);
        assertTrue(NativeFontProvenance.removedDefault(storage));
        NativeFontProvenance.fontStorage(storage,"test:alias",List.of(normal),Map.of(),resources(builtin),value->value);
        assertTrue(NativeFontProvenance.removedDefault(storage),"a resolved custom rename keeps native missing-glyph behavior");
    }
}
