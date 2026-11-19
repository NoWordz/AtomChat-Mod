package com.atom.chat.settings.contract;

import com.atom.chat.settings.SettingsGroupFrontend;
import com.atom.lib.design.atomchat.UiTokens;
import com.atom.lib.geometry.Viewport;
import com.atom.lib.layout.*;
import com.atom.lib.layout.internal.YogaLayoutEngine;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Per-view settings layout adapter. Existing material, input and motion remain in the production components. */
public final class SettingsYogaLayout implements AutoCloseable {
    public record Placed(SettingsGroupFrontend.Slots slots,PresentationFrame frame) {}
    private YogaLayoutEngine engine;
    public Placed layout(float width,float[] full,String[] groups,ToDoubleFunction<String> expansion){
        if(!Float.isFinite(width)||width<0||full.length!=groups.length)throw new IllegalArgumentException("Invalid settings measurement catalog");
        Map<String,Float> folds=new HashMap<>();
        for(int row=0;row<full.length;row++){
            if(!Float.isFinite(full[row])||full[row]<0)throw new IllegalArgumentException("Invalid settings row measurement");
            if(groups[row]!=null)folds.computeIfAbsent(groups[row],id->{double value=expansion.applyAsDouble(id);if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("Invalid settings fold state "+id);return (float)value;});
        }
        var children=new ArrayList<LayoutNode>();
        List<LayoutNode> cardRows=null;
        int firstCardRow=-1;
        float cursor=0;
        float cardTop=0;
        double total=0;
        for(int row=0;row<full.length;row++){
            String group=groups[row];
            float height=full[row]*(group==null?1:folds.get(group));
            float top=cursor;
            cursor+=height;
            total+=height;
            var node=LayoutNode.leaf("settings-contract/row/"+row,leaf(width,height),null,0);
            if(group==null){
                children.add(node);
            }else{
                if(cardRows==null){
                    firstCardRow=row;
                    cardTop=top;
                    cardRows=new ArrayList<>();
                }
                cardRows.add(node);
                if(row+1==full.length||!group.equals(groups[row+1])){
                    if (!Float.isFinite(cursor)) throw new IllegalArgumentException("Settings measurements overflow logical coordinates");
                    // Folded rows historically advance the float cursor one row at a time.
                    // Give Yoga the resulting group extent so the next header does not inherit
                    // the different rounding from adding all child heights before their offset.
                    float heightAtOriginalOrigin=cursor-cardTop;
                    var box=LayoutStyle.column().size(Length.points(width),Length.points(heightAtOriginalOrigin)).flex(0,0,Length.auto());
                    children.add(new LayoutNode(new NodeId("settings-contract/group/"+firstCardRow),box,cardRows,null,0));
                    cardRows=null;
                }
            }
            if(row+1<full.length){
                float next=groups[row+1]==null?1:folds.get(groups[row+1]);
                if(cardRows==null){
                    float gap=UiTokens.SETTINGS_ROW_GAP*next;
                    cursor+=gap;
                    total+=gap;
                    // Keep the original separate inter-card gap as the next section unfolds.
                    children.add(LayoutNode.leaf("settings-contract/gap/"+row,leaf(width,gap),null,0));
                }
            }
        }
        if(total>Float.MAX_VALUE)throw new IllegalArgumentException("Settings measurements overflow logical coordinates");
        var root=new NodeId("settings-contract/root");var tree=new LayoutTree(new LayoutNode(root,LayoutStyle.column().size(Length.points(width),Length.auto()).flex(0,0,Length.auto()),children,null,0));
        var viewport=new Viewport(width,Float.MAX_VALUE,1);
        if(engine==null)engine=new YogaLayoutEngine(YogaLayoutEngine.Precision.FRACTIONAL);
        var snapshot=engine.layoutIntrinsicHeight(tree,viewport);var frame=PresentationFrame.create(tree,snapshot,viewport,Map.of(),new PresentationFrame.Offset(0,0));
        float[] tops=new float[full.length],heights=new float[full.length];for(int row=0;row<full.length;row++){var box=frame.box(new NodeId("settings-contract/row/"+row)).border();tops[row]=box.y();heights[row]=box.height();}
        return new Placed(new SettingsGroupFrontend.Slots(tops,heights,frame.box(root).border().height()),frame);
    }
    private static LayoutStyle leaf(float width,float height){return LayoutStyle.column().size(Length.points(width),Length.points(height)).flex(0,0,Length.auto());}
    /** Releases the per-view cache; reopening the same settings controller creates a fresh native tree. */
    public void close(){if(engine!=null){engine.close();engine=null;}}
}
