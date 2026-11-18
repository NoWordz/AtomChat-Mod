package com.atom.chat.settings.contract;

import com.atom.chat.settings.SettingsGroupFrontend;
import com.atom.lib.layout.NodeId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsYogaLayoutTest {
    @Test void productionFoldGeometryIsPreservedAndPaintAndHitReadTheYogaFrame(){
        try(var layout=new SettingsYogaLayout()){
            for(float fraction:new float[]{0,.01f,.125f,.33333334f,.5f,.875f,.9999f,1}){
                float[] full={42.5f,73.125f,110.75f,42.5f,96.875f};String[] groups={null,"storage","storage",null,"cleanup"};
                java.util.function.ToDoubleFunction<String> expansion=id->id.equals("storage")?fraction:1-fraction;
                var expected=SettingsGroupFrontend.layout(full,groups,expansion);var actual=layout.layout(420,full,groups,expansion);
                assertNotNull(actual.frame(),"actual Yoga frame must own geometry, not a second coordinate walk");
                assertArrayEquals(expected.tops(),actual.slots().tops(),.0001f);assertArrayEquals(expected.heights(),actual.slots().heights(),.0001f);assertEquals(expected.contentHeight(),actual.slots().contentHeight(),.0001f);
                for(int row=0;row<full.length;row++){var box=actual.frame().box(new NodeId("settings-contract/row/"+row));assertEquals(actual.slots().tops()[row],box.border().y());assertEquals(actual.slots().heights()[row],box.border().height());assertEquals(box.border(),box.hit());}
            }
            layout.close();assertNotNull(layout.layout(420,new float[]{12.25f},new String[]{null},id->1).frame(),"reopened page owns a fresh native layout");
        }
    }
    @Test void malformedMeasurementsAndFoldStateAreRejectedBeforeNativeLayout(){
        try(var layout=new SettingsYogaLayout()){
            assertThrows(IllegalArgumentException.class,()->layout.layout(420,new float[]{1},new String[]{},id->1));
            assertThrows(IllegalArgumentException.class,()->layout.layout(420,new float[]{Float.NaN},new String[]{null},id->1));
            assertThrows(IllegalArgumentException.class,()->layout.layout(420,new float[]{12},new String[]{"group"},id->2));
        }
    }
}
