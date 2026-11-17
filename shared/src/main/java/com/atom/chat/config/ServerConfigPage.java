package com.atom.chat.config;

import com.atom.chat.chat.*;
import com.atom.chat.font.FontManager;
import com.atom.chat.nav.PageNavAxis;
import com.atom.chat.render.*;
import com.atom.chat.settings.*;
import com.atom.chat.ui.*;
import com.atom.lib.design.atomchat.*;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import java.util.*;
import java.util.function.Consumer;
import java.nio.file.*;
import static com.atom.chat.config.ServerConfigTokens.*;

/** One server draft with nested editors, rendered through the existing settings primitives. */
public final class ServerConfigPage {
    public interface Host {
        String label(String key);
        void save(int version,ServerConfigValues values);
        void refresh(); void rescan(); void back();
        String clipboard(); void clipboard(String text);
        default void saveDraft(int v,ServerConfigValues values,List<AdminAsset> assets,String fingerprint){save(v,values);}
        default boolean administration(){return false;}
        default void requestAsset(AdminAsset asset,Consumer<byte[]> receive){}
        default void chooseImage(Consumer<Path> receive){}
        default void retrySync(){}
    }
    private enum View { ROOT, PHRASES, PHRASE, TEMPLATE, EMOTES, IMAGE }
    private enum Kind { TOGGLE, NUMBER, TEXT, MULTILINE, ACTION, PHRASE, TEMPLATE, ASSET, TYPES, FIELDS, PREVIEW, IMAGE, MEDIA }
    private record Row(String id,String title,String description,Kind kind,String unit,int index){}
    private record Group(String id,String title,io.github.humbleui.skija.Path icon,List<Row> rows){}
    private record Motion(float cx,float cy,float scale,float dx,float dy){
        UiLayout.Rect map(UiLayout.Rect r){return new UiLayout.Rect(cx+(r.x()-cx)*scale+dx,cy+(r.y()-cy)*scale+dy,r.w()*scale,r.h()*scale);}
        float x(float x){return cx+(x-cx-dx)/scale;}
        float y(float y){return cy+(y-cy-dy)/scale;}
    }
    private record Hit(UiLayout.Rect box,Runnable action,String field,String group,Motion motion){}
    private final Host host;
    private Consumer<Boolean> navigationDirection=backwards->{};
    private final ServerConfigDraft draft;
    private final SettingsChipBar chips=new SettingsChipBar();
    private final Map<String,ScrollController> scrolls=new HashMap<>();
    private final Map<String,SettingsFormField> fields=new LinkedHashMap<>();
    private final Map<String,ToggleSwitch> switches=new HashMap<>();
    private final Map<String,ConfirmationSurface.Button> buttons=new HashMap<>();
    private final Map<String,SettingsListAction> listActions=new HashMap<>();
    private final Map<String,PressScale> scales=new HashMap<>();
    private final Map<String,Float> hovers=new HashMap<>(),folds=new HashMap<>();
    private final Set<String> collapsed=new HashSet<>(),loading=new HashSet<>();
    private final Set<String> failedImages=new HashSet<>();
    private final LinkedHashMap<String,Image> images=new LinkedHashMap<>();
    private final List<Hit> hits=new ArrayList<>();
    private final UiStagger.Entrance entrance=new UiStagger.Entrance();
    private ConfirmationDialog confirmation=new ConfirmationDialog();
    private UiLayout layout;
    private ServerConfigLayout geometry;
    private ScrollController scroll;
    private View view=View.ROOT,imageReturn=View.EMOTES;
    private int editIndex=-1;
    private String focused,pressed,feedback="",sample="",drawingGroup="",previewSample;
    private List<MessageTemplate> previewRules;
    private MessageTemplate previewRule;
    private String legacyPackSummary="";
    private TemplateParser.Preview preview;
    private AdminAsset shownImage;
    private Hit dragging;
    private String requestedFocus;
    private boolean closed,requestedSnapshot;
    private volatile boolean importing;
    private long acknowledgedAt;
    private float frameAlpha=1;

    public ServerConfigPage(Host host,int version,ServerConfigValues values){
        this.host=host;draft=new ServerConfigDraft(version,values);entrance.markEntered();
    }
    public ServerConfigDraft draft(){return draft;}
    public int chip(){return chips.selected();}
    public int bodyVariant(){return view.ordinal()*3+chip();}
    public void markEntered(long at){entrance.markEntered(at);}
    public float chromeBottom(UiLayout layout){return layout.chipBar.bottom();}
    public boolean stationaryPointer(UiLayout layout,float y){return y<=chromeBottom(layout)||y>=ServerConfigLayout.of(layout).status().y();}
    public void resources(String fingerprint,List<AdminAsset> files){draft.resourceBaseline(fingerprint,files);}
    public void bindConfirmation(ConfirmationDialog dialog){confirmation=dialog;}
    public void bindNavigationDirection(Consumer<Boolean> direction){navigationDirection=Objects.requireNonNull(direction);}
    public String title(){return switch(view){
        case PHRASES->t("phrases"); case PHRASE->t("edit_phrase");
        case TEMPLATE->t("edit_template"); case EMOTES->t("emotes");
        case IMAGE->t("preview_image"); default->host.label("atomchat.config.title");};}
    private String t(String key){return host.label("atomchat.admin."+key);}
    public void status(String summary){legacyPackSummary=summary;}
    public void result(boolean ok,String error){
        draft.result(ok,error);feedback=ok?"saved":error==null||error.isBlank()?"failed":error;acknowledgedAt=System.currentTimeMillis();
    }
    public void assetFailure(String hash,String error){if(!closed){loading.remove(hash);failedImages.add(hash);feedback=error==null?"failed":error;}}
    public boolean acceptSnapshot(int v,ServerConfigValues values){
        if(!draft.saving()||!draft.acknowledged())return false;
        draft.confirm(v,values);feedback="saved";requestedSnapshot=false;blur();return true;
    }
    public void dispose(){closed=true;for(var image:images.values())image.close();images.clear();loading.clear();}
    public void leave(ConfirmationDialog dialog){
        confirmation=dialog;
        if(draft.saving()||importing)return;
        if(view!=View.ROOT){navigateBack();return;}
        if(!draft.dirty()){dispose();host.back();return;}
        dialog.openDangerous(host.label("atomchat.config.unsaved_title"),host.label("atomchat.config.unsaved"),
                host.label("atomchat.config.cancel"),host.label("atomchat.config.discard"),()->{dispose();host.back();});
    }
    private void navigateBack(){cue();show(view==View.PHRASE?View.PHRASES:view==View.IMAGE?imageReturn:View.ROOT,true);}
    private static void cue(){com.atom.chat.audio.FeedbackSoundController.play(com.atom.chat.audio.FeedbackCue.BUTTON);}
    private void show(View next){show(next,false);}
    private void show(View next,boolean backwards){
        if(next!=view)navigationDirection.accept(backwards);
        blur();
        if(next==View.TEMPLATE||next==View.PHRASE){
            fields.keySet().removeIf(id->id.startsWith("template.")||id.equals("phrase.value"));
            var editorScroll=scrolls.get(next+":"+chip());if(editorScroll!=null)editorScroll.reset();
        }
        view=next;pressed=null;entrance.markEntered();hits.clear();
    }
    private void blur(){if(focused!=null&&fields.containsKey(focused))fields.get(focused).focus(false);focused=null;dragging=null;}
    public void tick(){
        draft.timeout(System.currentTimeMillis());
        if(draft.saving()&&draft.acknowledged()&&!requestedSnapshot&&System.currentTimeMillis()-acknowledgedAt>750){
            requestedSnapshot=true;host.refresh();
        }
    }
    private Row row(String id,Kind kind,String unit){return new Row(id,t("field."+id),t("field."+id+".desc"),kind,unit,-1);}
    private Row action(String id,String title,String desc){return new Row(id,title,desc,Kind.ACTION,"",-1);}
    private Group group(String id,String title,io.github.humbleui.skija.Path icon,Row... rows){return new Group(id,title,icon,List.of(rows));}
    private MessageTemplate rule(){return draft.templates().get(editIndex);}
    private List<Group> content(){
        if(view==View.PHRASES){
            var rows=new ArrayList<Row>();
            for(int i=0;i<draft.phrases().size();i++)rows.add(new Row("phrase:"+i,
                    draft.phrases().get(i).isBlank()?t("empty_phrase"):draft.phrases().get(i),t("click_edit"),Kind.PHRASE,"",i));
            return List.of(new Group("phrases",t("phrases_list"),DrawPrimitives.ICON_PHRASE_PATH,rows));
        }
        if(view==View.PHRASE)return List.of(group("phrase",t("phrase_group"),DrawPrimitives.ICON_PHRASE_PATH,
                new Row("phrase.value",t("phrase_content"),t("phrase_insert"),Kind.MULTILINE,"",editIndex)));
        if(view==View.TEMPLATE)return List.of(
                group("template",t("template_settings"),SettingsGroupGlyphs.MESSAGE_LIST,
                        row("template.name",Kind.TEXT,""),row("template.kind",Kind.TYPES,""),
                        row("template.format",Kind.MULTILINE,""),row("template.fields",Kind.FIELDS,"")),
                group("sample",t("sample_preview"),DrawPrimitives.ICON_IMAGE_PATH,row("template.sample",Kind.MULTILINE,"")),
                group("result","",DrawPrimitives.ICON_IMAGE_PATH,row("template.preview",Kind.PREVIEW,"")));
        if(view==View.EMOTES){
            var rows=assetRows(false);
            return List.of(new Group("emotes",t("emotes"),DrawPrimitives.ICON_EMOJI_PATH,rows));
        }
        if(view==View.IMAGE)return List.of(group("image",shownImage.name(),DrawPrimitives.ICON_IMAGE_PATH,
                new Row("image",shownImage.name(),"",Kind.IMAGE,"",-1)));
        if(chip()==0)return List.of(
                group("hosting",t("group.hosting"),DrawPrimitives.ICON_IMAGE_PATH,row("hostingEnabled",Kind.TOGGLE,"")),
                group("storage",t("group.storage"),SettingsGroupGlyphs.LAYOUT,row("maxFileKb",Kind.NUMBER,"KiB"),
                        row("maxTotalMb",Kind.NUMBER,"MiB"),row("maxAvatarTotalMb",Kind.NUMBER,"MiB"),row("maxProfileTotalMb",Kind.NUMBER,"MiB")),
                group("retention",t("group.retention"),AppIcons.ICON_CLOCK_PATH,row("retentionDays",Kind.NUMBER,t("days")),row("uploadCooldownMs",Kind.NUMBER,"ms")));
        if(chip()==1)return List.of(
                group("pack",t("group.pack"),SettingsGroupGlyphs.LAYOUT,row("packEnabled",Kind.TOGGLE,""),row("packName",Kind.TEXT,""),
                        row("packMaxFiles",Kind.NUMBER,t("files")),row("packMaxMb",Kind.NUMBER,"MiB")),
                new Group("contents",t("group.contents"),DrawPrimitives.ICON_EMOJI_PATH,assetRows(true)),
                group("phrases-entry","",DrawPrimitives.ICON_PHRASE_PATH,
                        action("phrases:list",t("phrases"),draft.phrases().size()+" "+t("phrases_count")+" · "+t("click_edit"))));
        var rows=new ArrayList<Row>();
        for(int i=0;i<draft.templates().size();i++){
            var r=draft.templates().get(i);
            rows.add(new Row("template:"+i,r.name().isBlank()?t("empty_template"):r.name(),
                    templateSummary(r),Kind.TEMPLATE,"",i));
        }
        return List.of(new Group("templates",t("templates"),SettingsGroupGlyphs.MESSAGE_LIST,rows));
    }
    private ArrayList<Row> assetRows(boolean root){
        var rows=new ArrayList<Row>();
        rows.add(new Row("asset:import",t("emotes"),host.administration()?draft.assets().size()+" "+t(draft.assets().size()==1?"file_single":"files")+" · "+t("assets_save_hint"):legacyPackSummary,Kind.MEDIA,"",-1));
        int count=root?Math.min(3,draft.assets().size()):draft.assets().size();
        for(int i=0;i<count;i++){
            var a=draft.assets().get(i);
            rows.add(new Row("asset:"+i,a.name(),assetSize(a.size())+" · "+t(a.data()==null?"published":"staged"),Kind.ASSET,"",i));
        }
        if(root&&host.administration())rows.add(action("assets:list",t("manage_emotes"),t("manage_emotes_hint")));
        return rows;
    }
    private static String assetSize(long bytes){return bytes<1024?"<1 KiB":String.format(java.util.Locale.ROOT,"%.1f KiB",bytes/1024d);}
    private String headerAction(Group group){return group.id().equals("phrases")?"phrase:add":group.id().equals("templates")?"template:add":null;}
    private String foldOwner(String group){return group.equals("result")?"sample":group;}
    private String headerSummary(Group group){return group.id().equals("phrases")?draft.phrases().size()+" / 20":!host.administration()?t("upgrade_server"):t("template_count").replace("{count}",""+draft.templates().size()).replace("{enabled}",""+draft.templates().stream().filter(MessageTemplate::enabled).count());}
    private float headerHeight(Group group){return (group.title().isEmpty()?0:UiTokens.SETTINGS_LABEL_H+UiTokens.SETTINGS_ROW_GAP)+(headerAction(group)==null?0:SettingsListAction.HEIGHT+UiTokens.SETTINGS_ROW_GAP*2);}
    private String templateSummary(MessageTemplate rule){
        if(rule.validate()!=null)return t("error.bad_template");
        var fields=new ArrayList<String>();for(var field:List.of("sender","target","message","channel","display","uuid"))if(rule.format().contains("{"+field+"}"))fields.add(t("placeholder."+field));
        return t("kind."+rule.kind().name())+(fields.isEmpty()?"":" · "+String.join(" / ",fields));
    }
    private float assetTextWidth(float width){return width-UiTokens.SETTINGS_ROW_PAD*2-THUMB-UiTokens.SETTINGS_ROW_GAP;}
    private List<String> assetTitles(Row row,float width){
        var lines=wrapTitle(row.title(),width);if(lines.size()<=2)return lines;
        return List.of(lines.get(0),SkiaFontRenderer.truncate(FontManager.font(UiTokens.SETTINGS_TILE_TITLE),String.join("",lines.subList(1,lines.size())),width));
    }
    private float assetActionsHeight(float width){return SettingsListAction.width(t("preview_image"))+SettingsListAction.width(t("remove"))+UiTokens.SETTINGS_ROW_GAP<=width?SettingsListAction.HEIGHT:SettingsListAction.HEIGHT*2+UiTokens.SETTINGS_ROW_GAP;}
    private List<String> assetDescription(Row row,float width){
        if(SkiaFontRenderer.getStringWidth(FontManager.font(UiTokens.SETTINGS_TILE_SUB),row.description())<=width)return List.of(row.description());
        var lines=new ArrayList<String>();for(var part:row.description().split(" · "))lines.addAll(wrap(part,width));return lines;
    }
    private boolean inlineList(Row row,float width){
        float actions=row.kind()==Kind.PHRASE?SettingsListAction.HEIGHT*3+UiTokens.SETTINGS_ROW_GAP*2:UiTokens.SWITCH_W+SettingsListAction.HEIGHT+SettingsListAction.width(t("edit"))+UiTokens.SETTINGS_ROW_GAP*2;
        return width-UiTokens.SETTINGS_ROW_PAD*2-actions-UiTokens.SETTINGS_ROW_GAP>=NUMBER_TEXT_MIN;
    }
    private String value(String id){return switch(id){
        case "phrase.value"->draft.phrases().get(editIndex);case "template.name"->rule().name();
        case "template.format"->rule().format();case "template.sample"->sample;default->draft.text(id);};}
    private void change(String id,String value){
        if(draft.saving())return;feedback="";
        if(id.equals("phrase.value")){var list=new ArrayList<>(draft.phrases());list.set(editIndex,value);draft.phrases(list);}
        else if(id.equals("template.sample"))sample=value;
        else if(id.startsWith("template.")){var r=rule();setRule(new MessageTemplate(r.id(),id.endsWith("name")?value:r.name(),r.kind(),id.endsWith("format")?value:r.format(),r.enabled()));}
        else draft.text(id,value);
    }
    private void setRule(MessageTemplate next){var list=new ArrayList<>(draft.templates());list.set(editIndex,next);draft.templates(list);}
    private List<String> wrap(String text,float width){
        var lines=new ArrayList<String>();
        for(String line:text.split("\n",-1)){
            var wrapped=SkiaFontRenderer.wrapWords(FontManager.font(UiTokens.SETTINGS_TILE_SUB),line,Math.max(1,width));
            lines.addAll(wrapped.isEmpty()?List.of(""):wrapped);
        }
        return lines;
    }
    private List<String> wrapTitle(String text,float width){return SkiaFontRenderer.wrapWords(FontManager.font(UiTokens.SETTINGS_TILE_TITLE),text,Math.max(1,width));}
    private boolean compactNumber(float width){return width-UiTokens.SETTINGS_ROW_PAD*2-NUMBER_W-UNIT_W<NUMBER_TEXT_MIN;}
    private float reserved(Row row){
        if(row.kind()==Kind.MEDIA)return SettingsListAction.width(t("import_short"))+UiTokens.SETTINGS_ROW_GAP;
        if(row.kind()==Kind.ACTION)return UiTokens.COMPOSER_ICON_SIZE+UiTokens.SETTINGS_ROW_GAP;
        if((row.kind()==Kind.PHRASE||row.kind()==Kind.TEMPLATE)&&inlineList(row,geometry.viewport().w()))return row.kind()==Kind.PHRASE?
                SettingsListAction.HEIGHT*3+UiTokens.SETTINGS_ROW_GAP*3:UiTokens.SWITCH_W+SettingsListAction.HEIGHT+SettingsListAction.width(t("edit"))+UiTokens.SETTINGS_ROW_GAP*3;
        return row.kind()==Kind.NUMBER?SettingsRowText.NUMBER_W+SettingsRowText.UNIT_W+UiTokens.SETTINGS_COMPACT_GAP:row.kind()==Kind.TOGGLE?UiTokens.SWITCH_W+UiTokens.SETTINGS_ROW_PAD:0;
    }
    private int columns(float width,List<String> labels,int preferred){
        float need=0;var font=FontManager.font(UiTokens.FONT_BUTTON);for(var label:labels)need=Math.max(need,SkiaFontRenderer.getStringWidth(font,label)+UiTokens.BUTTON_GAP);
        while(preferred>1&&(width-UiTokens.SETTINGS_ROW_GAP*(preferred-1))/preferred<need)preferred=preferred==4?2:1;
        return preferred;
    }
    private List<String> kindLabels(){return Arrays.stream(MessageTemplate.Kind.values()).map(k->t("kind.short."+k.name())).toList();}
    private List<String> fieldLabels(){return placeholderNames().stream().map(k->t("placeholder."+k)).toList();}
    private List<UiLayout.Rect> fieldBoxes(float width){
        var boxes=new ArrayList<UiLayout.Rect>();float x=0,y=0,gap=UiTokens.SETTINGS_ROW_GAP;
        for(var label:fieldLabels()){
            float w=Math.min(width,SettingsListAction.width(label));if(x>0&&x+w>width){x=0;y+=ACTION_H+gap;}
            boxes.add(new UiLayout.Rect(x,y,w,ACTION_H));x+=w+gap;
        }
        return boxes;
    }
    private SettingsFormField form(Row row){
        var field=fields.computeIfAbsent(row.id(),k->new SettingsFormField());
        field.configure(row.kind()==Kind.NUMBER,row.kind()==Kind.MULTILINE,row.id().equals("template.sample")?8192:row.id().equals("phrase.value")?200:row.id().equals("template.format")?512:row.id().equals("template.name")||row.id().equals("packName")?48:10);
        field.wrap(row.kind()!=Kind.NUMBER);
        return field;
    }
    private float editorHeight(Row row){return form(row).preferredHeight(value(row.id()),geometry.viewport().w()-UiTokens.SETTINGS_ROW_PAD*2);}
    private SettingsRowText.Text rowText(Row row,float width){
        return SettingsRowText.measure(row.title(),description(row),width,reserved(row),row.kind()==Kind.NUMBER?SettingsFormField.BASE_H:row.kind()==Kind.TOGGLE?UiTokens.SWITCH_H:0,true);
    }
    private float textHeight(Row row,float width){return rowText(row,width).height();}
    private String description(Row row){return (fieldError(row)?t("field_error")+" · ":"")+row.description();}
    private float height(Row row,float width){
        float base=Math.max(UiTokens.SETTINGS_ROW_H,textHeight(row,width));
        return switch(row.kind()){
            case NUMBER,TOGGLE->rowText(row,width).height();
            case TEXT->base+editorHeight(row)+UiTokens.SETTINGS_ROW_PAD;
            case MULTILINE->base+editorHeight(row)+UiTokens.SETTINGS_ROW_PAD+(row.id().equals("phrase.value")?TITLE_LINE_H+UiTokens.SETTINGS_ROW_GAP:0);
            case PHRASE,TEMPLATE->inlineList(row,width)?base:base+SettingsListAction.HEIGHT+UiTokens.SETTINGS_ROW_GAP;
            case ASSET->UiTokens.SETTINGS_ROW_PAD*2+Math.max(THUMB,
                    assetTitles(row,assetTextWidth(width)).size()*TITLE_LINE_H+assetDescription(row,assetTextWidth(width)).size()*TITLE_LINE_H+UiTokens.SETTINGS_ROW_GAP+assetActionsHeight(assetTextWidth(width)));
            case MEDIA->base;case IMAGE->IMAGE_H;
            case PREVIEW->UiTokens.SETTINGS_ROW_PAD*2+previewLayout(null,0,0,width-UiTokens.SETTINGS_ROW_PAD*2);
            case TYPES->base+(3/columns(width-UiTokens.SETTINGS_ROW_PAD*2,kindLabels(),3))*(ACTION_H+UiTokens.SETTINGS_ROW_GAP);
            case FIELDS->base+fieldBoxes(width-UiTokens.SETTINGS_ROW_PAD*2).get(placeholderNames().size()-1).bottom()+UiTokens.SETTINGS_ROW_GAP;
            default->base;};
    }
    private boolean fieldError(String id){
        if(id.equals("template.name"))return rule().name().isBlank()||rule().name().length()>48;
        if(id.equals("template.format"))return TemplateParser.validate(rule())!=null;
        if(id.equals("phrase.value"))return value(id).isBlank()||value(id).length()>200;
        int max=switch(id){case "maxFileKb"->16384;case "maxTotalMb","maxAvatarTotalMb","maxProfileTotalMb"->65536;
            case "retentionDays"->3650;case "uploadCooldownMs"->600000;case "packMaxFiles"->200;case "packMaxMb"->16;default->-1;};
        if(max<0)return false;
        try{int n=Integer.parseInt(value(id));return n<((id.equals("retentionDays")||id.equals("uploadCooldownMs"))?0:1)||n>max;}
        catch(NumberFormatException e){return true;}
    }
    private boolean fieldError(Row row){return (row.kind()==Kind.NUMBER||row.kind()==Kind.TEXT||row.kind()==Kind.MULTILINE)&&fieldError(row.id());}
    private List<String> placeholderNames(){return rule().kind()==MessageTemplate.Kind.OUTGOING?
            List.of("target","message","sender","uuid","display","channel"):List.of("sender","message","channel","display","uuid","target");}
    private TemplateParser.Preview parsed(){
        var edited=rule();var rules=draft.templates();
        if(previewRules!=rules||previewRule!=edited||!Objects.equals(previewSample,sample)){
            previewRules=rules;previewRule=edited;previewSample=sample;
            preview=TemplateParser.preview(edited,sample);
            if(preview.state()==TemplateParser.State.MATCH){
                var candidates=new ArrayList<MessageTemplate>();
                for(var candidate:rules)if(candidate==edited)candidates.add(new MessageTemplate(edited.id(),edited.name(),edited.kind(),edited.format(),true));else if(candidate.enabled())candidates.add(candidate);
                var combined=TemplateParser.match(candidates,sample);
                if(combined.state()==TemplateParser.State.AMBIGUOUS)preview=combined;
            }
        }
        return preview;
    }
    /** The same measured lines drive the separate result card's height and paint. */
    private float previewLayout(Canvas canvas,float left,float top,float width){
        float start=top,gap=UiTokens.SETTINGS_ROW_GAP;var c=AtomChatConfig.get();var result=parsed();
        var titleFont=FontManager.boldFont(UiTokens.SETTINGS_TILE_TITLE);
        int statusColor=result.state()==TemplateParser.State.MATCH?c.accentColor:result.state()==TemplateParser.State.NO_MATCH?c.textPrimaryColor:UiTokens.dangerColor();
        for(var line:SkiaFontRenderer.wrapWords(titleFont,t("preview.title."+result.state().name()),width)){
            if(canvas!=null)SkiaFontRenderer.drawText(canvas,titleFont,line,left,SkiaFontRenderer.centerBaselineY(titleFont,top+TITLE_HALF_LINE),statusColor);top+=TITLE_LINE_H;
        }
        top+=gap;
        if(result.state()!=TemplateParser.State.MATCH){
            var lines=wrap(t("preview."+result.state().name()),width);if(canvas!=null)drawLines(canvas,lines,left,top,c.textSecondaryColor);return top-start+lines.size()*TITLE_LINE_H;
        }
        var font=FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        for(var name:List.of("channel","sender","target","display","uuid","message")){
            String value=result.match().fields().get(name);if(value==null)continue;
            String label=t("placeholder."+name);float labelW=SkiaFontRenderer.getStringWidth(font,label)+gap;
            var lines=wrap(value,Math.max(1,width-labelW));
            if(canvas!=null){SkiaFontRenderer.drawText(canvas,font,label,left,SkiaFontRenderer.centerBaselineY(font,top+TITLE_HALF_LINE),c.textSecondaryColor);drawLines(canvas,lines,left+labelW,top,c.textPrimaryColor);}
            top+=lines.size()*TITLE_LINE_H;
        }
        top+=gap;
        var identity=wrap(t(result.match().fields().containsKey("uuid")?"uuid_in_sample":"uuid_absent"),width);
        if(canvas!=null)drawLines(canvas,identity,left,top,c.textSecondaryColor);
        return top-start+identity.size()*TITLE_LINE_H;
    }

    public void render(Canvas canvas,UiLayout layout,float mx,float my,float dt){
        renderBody(canvas,layout,mx,my,dt);renderFixedChrome(canvas,layout,mx,my,dt);
    }
    public void renderBody(Canvas canvas,UiLayout layout,float mx,float my,float elapsed){
        float dt=Math.min(50,Math.max(1,elapsed));
        tick();this.layout=layout;geometry=ServerConfigLayout.of(layout);hits.clear();
        scroll=scrolls.computeIfAbsent(view+":"+chip(),k->new ScrollController(false));scroll.setDecorativeMotion(Animations.enabled());
        var groups=content();
        for(var group:groups){String owner=foldOwner(group.id());folds.putIfAbsent(owner,collapsed.contains(owner)?0f:1f);}
        SettingsGroupFrontend.stepFolds(folds,collapsed,Math.min(50,Math.max(1,dt)));
        var full=new ArrayList<Float>();var owners=new ArrayList<String>();
        for(var group:groups){if(headerHeight(group)>0){full.add(headerHeight(group)-UiTokens.SETTINGS_ROW_GAP);owners.add(null);}for(var row:group.rows()){full.add(height(row,geometry.viewport().w()));owners.add(group.id());}}
        float[] fullHeights=new float[full.size()];for(int i=0;i<full.size();i++)fullHeights[i]=full.get(i);
        var slots=SettingsGroupFrontend.layout(fullHeights,owners.toArray(String[]::new),id->folds.getOrDefault(foldOwner(id),1f));
        var viewport=geometry.viewport();float top=SettingsGroupFrontend.contentTop(layout,SettingsGroupFrontend.chipBand());
        scroll.setContent(top-viewport.y()+slots.contentHeight()+UiTokens.ROW_CLIP_INSET,viewport.h());
        if(requestedFocus!=null){int at=0;for(var group:groups){if(headerHeight(group)>0)at++;for(var row:group.rows()){if(row.id().equals(requestedFocus))scroll.scrollTo(Math.max(0,slots.tops()[at]),false);at++;}}requestedFocus=null;}
        scroll.updateAnimation(UiStagger.frameTimeMillis());long now=UiStagger.frameTimeMillis();var axis=PageNavAxis.current();
        var config=AtomChatConfig.get();int slot=0,index=0;String[] hoveredRow={null};float drift=axis==UiStagger.Axis.HORIZONTAL&&!UiStagger.itemsSettled(entrance,now)?UiStagger.ITEM_STYLE_SLIDE_PX:0;
        canvas.save();
        try{
            SkiaDraw.clip(canvas,layout.list.x()-CardShadowReach.PAD,viewport.y(),layout.list.w()+drift+CardShadowReach.PAD*2,viewport.h(),0);
            for(var group:groups){
                float expansion=folds.get(foldOwner(group.id()));
                if(headerHeight(group)>0){
                    var header=new UiLayout.Rect(viewport.x(),top+slots.tops()[index]-scroll.getScrollY(),viewport.w(),slots.heights()[index]);index++;
                    if(header.bottom()>=viewport.y()&&header.y()<=viewport.bottom()){
                        drawingGroup="header:"+group.id();var f=SettingsGroupFrontend.motion(header,entrance,slot++,now,axis);
                        var m=new Motion(f.cx(),f.cy(),f.scale(),f.dx(),f.dy());frameAlpha=f.alpha();
                        var press=scales.computeIfAbsent(drawingGroup,k->PressScale.bounce());press.update(header.contains(m.x(mx),m.y(my)),Objects.equals(pressed,drawingGroup),dt,Animations.enabled(),header.w());
                        SettingsGroupFrontend.stagger(canvas,header,f,()->{
                            press.begin(canvas,header.x()+header.w()/2,header.y()+header.h()/2);
                            try{SettingsGroupPaint.draw(canvas,new UiLayout.Rect(header.x(),header.y(),header.w(),UiTokens.SETTINGS_LABEL_H),group.title(),group.icon(),true,expansion);drawHeaderAction(canvas,group,header,m,viewport,m.x(mx),m.y(my),dt);}finally{canvas.restore();}return 0;
                        });
                        hit(new UiLayout.Rect(header.x(),header.y(),header.w(),UiTokens.SETTINGS_LABEL_H),()->{if(!collapsed.add(group.id()))collapsed.remove(group.id());},null,m,viewport);
                    }
                }
                int start=index;for(var row:group.rows())index++;
                if(index==start||expansion<SettingsGroupFrontend.MIN_DRAWN)continue;
                var card=new UiLayout.Rect(viewport.x(),top+slots.tops()[start]-scroll.getScrollY(),viewport.w(),slots.tops()[index-1]+slots.heights()[index-1]-slots.tops()[start]);
                if(card.bottom()<viewport.y()||card.y()>viewport.bottom())continue;
                drawingGroup=group.id();var f=SettingsGroupFrontend.motion(card,entrance,slot++,now,axis);var m=new Motion(f.cx(),f.cy(),f.scale(),f.dx(),f.dy());frameAlpha=f.alpha()*expansion;
                var press=scales.computeIfAbsent(group.id(),k->PressScale.bounce());press.update(card.contains(m.x(mx),m.y(my)),Objects.equals(pressed,group.id()),dt,Animations.enabled(),Math.max(card.w(),card.h()));
                SettingsGroupFrontend.stagger(canvas,card,f,()->SettingsGroupFrontend.merged(canvas,card,expansion,press,()->{
                    int at=start;
                    for(var row:group.rows()){
                        var box=new UiLayout.Rect(card.x(),top+slots.tops()[at]-scroll.getScrollY(),card.w(),slots.heights()[at]);at++;
                        var painted=m.map(box);if(painted.bottom()<viewport.y()||painted.y()>viewport.bottom())continue;
                        String key=group.id()+":"+row.id();float hover=hovers.getOrDefault(key,0f);
                        if(hover>.01f)SkiaDraw.drawRoundedRect(canvas,box.x(),box.y(),box.w(),box.h(),0,UiTokens.cardHover(hover));
                        if(at>start+1)SettingsGroupFrontend.divider(canvas,card,box.y());
                        drawRow(canvas,row,box,m,card,m.x(mx),m.y(my),dt);
                        if(!draft.saving()&&box.contains(m.x(mx),m.y(my)))hoveredRow[0]=key;
                    }return 0;
                }));
            }
        }finally{canvas.restore();}
        SettingsGroupFrontend.stepHover(hovers,hoveredRow[0],dt);
        SettingsScrollbar.draw(canvas,geometry.scrollViewport(),scroll,mx,my,config.accentColor,dt);
    }
    private void drawHeaderAction(Canvas canvas,Group group,UiLayout.Rect header,Motion m,UiLayout.Rect clip,float mx,float my,float dt){
        String add=headerAction(group);if(add==null)return;var config=AtomChatConfig.get();
        float y=header.y()+UiTokens.SETTINGS_LABEL_H+UiTokens.SETTINGS_ROW_GAP,pad=UiTokens.SETTINGS_ROW_PAD,w=SettingsListAction.width(t("add_short"));var font=FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        SkiaFontRenderer.drawText(canvas,font,SkiaFontRenderer.truncate(font,headerSummary(group),header.w()-pad*2-w-UiTokens.SETTINGS_ROW_GAP),header.x()+pad,SkiaFontRenderer.centerBaselineY(font,y+SettingsListAction.HEIGHT/2),config.textSecondaryColor);
        button(canvas,add,t("add_short"),new UiLayout.Rect(header.right()-pad-w,y,w,SettingsListAction.HEIGHT),true,false,!draft.saving()&&(group.id().equals("phrases")?draft.phrases().size()<20:host.administration()&&draft.templates().size()<64),()->act(add),m,clip,mx,my,dt);
    }
    public void renderFixedChrome(Canvas canvas,UiLayout layout,float mx,float my,float elapsed){
        float dt=Math.min(50,Math.max(1,elapsed));
        this.layout=layout;geometry=ServerConfigLayout.of(layout);var config=AtomChatConfig.get();
        if(view==View.ROOT)chips.render(canvas,layout,List.of(host.label("atomchat.config.tab.hosting"),host.label("atomchat.config.tab.pack"),t("templates")),mx,my,dt);
        else{var breadcrumb=FontManager.font(UiTokens.SETTINGS_TILE_SUB);SkiaFontRenderer.drawText(canvas,breadcrumb,t(view==View.TEMPLATE?"breadcrumb.templates":"breadcrumb.pack"),layout.chipBar.x()+UiTokens.SETTINGS_ROW_PAD,SkiaFontRenderer.centerBaselineY(breadcrumb,layout.chipBar.y()+layout.chipBar.h()/2),config.textSecondaryColor);}
        var bar=geometry.bar();
        SkiaDraw.drawChromeShadow(canvas,bar.x(),bar.y(),bar.w(),bar.h(),UiTokens.shellChromeRadius());
        SkiaDraw.drawRoundedRect(canvas,bar.x(),bar.y(),bar.w(),bar.h(),UiTokens.shellChromeRadius(),UiTokens.cardFill());
        boolean root=view==View.ROOT;
        boolean enabled=!draft.saving()&&!importing&&(!root||draft.dirty()&&draft.validation()==null);
        String action=view==View.PHRASE||view==View.TEMPLATE?t("done"):view==View.PHRASES||view==View.EMOTES?t("back_pack"):!root?t("back"):host.label(draft.saving()?"atomchat.config.saving":"atomchat.config.save");
        buttons.computeIfAbsent("save",k->new ConfirmationSurface.Button()).draw(canvas,geometry.save(),action,true,false,false,enabled&&geometry.save().contains(mx,my),dt,enabled?1:.45f);
        String status=importing?t("importing"):draft.saving()?host.label("atomchat.config.saving"):!draft.error().isEmpty()?error(draft.error()):draft.dirty()&&draft.validation()!=null?error(draft.validation()):
                !feedback.isEmpty()?(feedback.equals("saved")?t("saved_sync"):error(feedback)):t(view==View.TEMPLATE||view==View.PHRASE?"editor_commit_hint":draft.dirty()?(root?"dirty":"editor_draft"):"unchanged");
        if(host.administration()&&root&&feedback.equals("saved"))status+=" · "+syncText();
        var font=FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        SkiaFontRenderer.drawTextCentered(canvas,font,SkiaFontRenderer.truncate(font,status,geometry.status().w()),
                geometry.status().x()+geometry.status().w()/2,geometry.status().y()+geometry.status().h()/2,
                !draft.error().isEmpty()?UiTokens.dangerColor():config.textSecondaryColor);
    }
    private String syncText(){
        var status=com.atom.chat.net.ConfigAdminClient.sync();
        if(status==null)return t("syncing");
        if(status.failed()>0)return t("sync_failed").replace("{count}",""+status.failed());
        if(status.waiting()>0)return t("sync_waiting").replace("{count}",""+status.waiting());
        return t(status.complete()==0?"sync_none":"synced");
    }
    private String error(String reason){return t("error."+reason);}
    private void hit(UiLayout.Rect box,Runnable action,String field,Motion motion,UiLayout.Rect clip){
        if(frameAlpha<.01f)return;
        var a=motion.map(box);var b=motion.map(clip);
        float x=Math.max(a.x(),b.x()),y=Math.max(a.y(),b.y()),right=Math.min(a.right(),b.right()),bottom=Math.min(a.bottom(),b.bottom());
        if(right>x&&bottom>y)hits.add(new Hit(new UiLayout.Rect(x,y,right-x,bottom-y),action,field,drawingGroup,motion));
    }
    private void button(Canvas canvas,String id,String label,UiLayout.Rect box,boolean primary,boolean danger,boolean enabled,
                        Runnable action,Motion motion,UiLayout.Rect clip,float mx,float my,float dt){
        listButton(canvas,id,label,null,0,box,primary,danger,enabled,action,motion,clip,mx,my,dt);
    }
    private void listButton(Canvas canvas,String id,String label,io.github.humbleui.skija.Path icon,float rotation,UiLayout.Rect box,boolean primary,boolean danger,boolean enabled,
                            Runnable action,Motion motion,UiLayout.Rect clip,float mx,float my,float dt){
        var control=listActions.computeIfAbsent(id,k->new SettingsListAction());
        control.draw(canvas,box,label,icon,rotation,primary,danger,enabled,box.contains(mx,my),dt);
        if(enabled){
            hit(box,()->{control.press();action.run();},null,motion,clip);
            var mapped=motion.map(box);var boundary=motion.map(clip);var viewport=geometry.viewport();
            float x=Math.max(mapped.x(),Math.max(boundary.x(),viewport.x())),y=Math.max(mapped.y(),Math.max(boundary.y(),viewport.y()));
            float right=Math.min(mapped.right(),Math.min(boundary.right(),viewport.right())),bottom=Math.min(mapped.bottom(),Math.min(boundary.bottom(),viewport.bottom()));
            ClickRipples.region(x,y,right-x,bottom-y,SettingsListAction.RADIUS*motion.scale(),primary?AtomChatConfig.get().accentColor:UiTokens.cardCutout());
        }
    }
    private void drawRow(Canvas canvas,Row row,UiLayout.Rect rect,Motion motion,UiLayout.Rect clip,float mx,float my,float dt){
        var c=AtomChatConfig.get();
        float pad=UiTokens.SETTINGS_ROW_PAD,left=rect.x()+pad,width=rect.w()-pad*2;
        boolean enabled=!draft.saving()&&(host.administration()||!row.id().equals("maxProfileTotalMb")&&!row.id().startsWith("template")&&!row.id().startsWith("asset"));
        if(row.kind()==Kind.PREVIEW){previewLayout(canvas,left,rect.y()+pad,width);return;}
        if(row.kind()==Kind.IMAGE){drawImage(canvas,shownImage,new UiLayout.Rect(left,rect.y()+pad,width,rect.h()-pad*2));return;}
        if(row.kind()==Kind.ASSET){drawAsset(canvas,row,rect,motion,clip,mx,my,dt,enabled);return;}
        float top=SettingsRowText.draw(canvas,rect,rowText(row,rect.w()),enabled?c.textPrimaryColor:c.textSecondaryColor,fieldError(row)?UiTokens.dangerColor():c.textSecondaryColor);
        if(row.kind()==Kind.TOGGLE){
            var toggle=switches.computeIfAbsent(row.id(),k->{var t=new ToggleSwitch();t.snapTo(draft.enabled(row.id()));return t;});
            var box=new UiLayout.Rect(rect.right()-pad-UiTokens.SWITCH_W,rowText(row,rect.w()).stacked()?rect.bottom()-pad-UiTokens.SWITCH_H:rect.y()+(rect.h()-UiTokens.SWITCH_H)/2,UiTokens.SWITCH_W,UiTokens.SWITCH_H);
            toggle.setInteraction(enabled&&box.contains(mx,my),Objects.equals(pressed,drawingGroup)&&box.contains(mx,my));
            toggle.update(dt,draft.enabled(row.id()));toggle.render(canvas,box.x(),box.y(),c.accentColor,enabled);
            if(enabled)hit(rect,()->toggleRow(row.id(),toggle),null,motion,clip);
            return;
        }
        if(row.kind()==Kind.NUMBER||row.kind()==Kind.TEXT||row.kind()==Kind.MULTILINE){
            boolean number=row.kind()==Kind.NUMBER;
            var box=number?SettingsRowText.number(rect,rowText(row,rect.w()).stacked()):
                    new UiLayout.Rect(left,top+UiTokens.SETTINGS_ROW_GAP,width,editorHeight(row));
            var field=form(row);
            field.focus(row.id().equals(focused));field.paint(canvas,box,value(row.id()),enabled,fieldError(row),mx,my,dt);
            if(number)SkiaFontRenderer.drawText(canvas,FontManager.font(UiTokens.SETTINGS_TILE_SUB),row.unit(),box.right()+UiTokens.SETTINGS_ROW_GAP,
                    SkiaFontRenderer.centerBaselineY(FontManager.font(UiTokens.SETTINGS_TILE_SUB),box.y()+box.h()/2),c.textSecondaryColor);
            if(row.id().equals("phrase.value")){
                var font=FontManager.font(UiTokens.SETTINGS_TILE_SUB);String count=value(row.id()).length()+" / 200";
                SkiaFontRenderer.drawText(canvas,font,count,rect.right()-pad-SkiaFontRenderer.getStringWidth(font,count),SkiaFontRenderer.centerBaselineY(font,box.bottom()+UiTokens.SETTINGS_ROW_GAP+TITLE_HALF_LINE),c.textSecondaryColor);
            }
            if(enabled)hit(box,()->{},row.id(),motion,clip);return;
        }
        if(row.kind()==Kind.ACTION||row.kind()==Kind.MEDIA){
            if(row.kind()==Kind.MEDIA){
                float w=SettingsListAction.width(t("import_short"));
                button(canvas,"asset:import",t("import_short"),new UiLayout.Rect(rect.right()-pad-w,rect.y()+pad,w,SettingsListAction.HEIGHT),true,false,enabled&&!importing,()->act("asset:import"),motion,clip,mx,my,dt);
            }else{
                if(enabled)hit(rect,()->act(row.id()),null,motion,clip);
                SettingsGroupPaint.chevron(canvas,rect.right()-pad-UiTokens.COMPOSER_ICON_SIZE/2,rect.y()+UiTokens.SETTINGS_ROW_H/2,UiTokens.SETTINGS_FOLD_CHEVRON_SIZE,0,c.textSecondaryColor);
            }
            return;
        }
        if(row.kind()==Kind.PHRASE){
            if(enabled)hit(rect,()->phraseAction(row.index(),"edit"),null,motion,clip);
            float size=SettingsListAction.HEIGHT,x=rect.right()-pad-size*3-UiTokens.SETTINGS_ROW_GAP*2;
            float y=inlineList(row,rect.w())?rect.y()+(rect.h()-size)/2:rect.bottom()-pad-size;
            for(var action:List.of("up","down","delete")){
                boolean allowed=enabled&&(!action.equals("up")||row.index()>0)&&(!action.equals("down")||row.index()<draft.phrases().size()-1);
                var box=new UiLayout.Rect(x,y,size,size);
                // Disabled boundary arrows still consume their own cell, never opening the editor behind them.
                hit(box,()->{},null,motion,clip);
                listButton(canvas,row.id()+action,t(action),action.equals("delete")?SettingsGroupGlyphs.TRASH:null,action.equals("up")?-90:action.equals("down")?90:0,
                        box,false,action.equals("delete"),allowed,()->phraseAction(row.index(),action),motion,clip,mx,my,dt);
                x+=size+UiTokens.SETTINGS_ROW_GAP;
            }
            return;
        }
        if(row.kind()==Kind.TEMPLATE){
            var r=draft.templates().get(row.index());
            if(enabled)hit(rect,()->templateAction(row.index(),"edit"),null,motion,clip);
            float size=SettingsListAction.HEIGHT,y=inlineList(row,rect.w())?rect.y()+(rect.h()-size)/2:rect.bottom()-pad-size;
            var toggle=switches.computeIfAbsent(row.id(),k->{var control=new ToggleSwitch();control.snapTo(r.enabled());return control;});
            var toggleBox=new UiLayout.Rect(rect.right()-pad-size-UiTokens.SETTINGS_ROW_GAP-UiTokens.SWITCH_W,y+(size-UiTokens.SWITCH_H)/2,UiTokens.SWITCH_W,UiTokens.SWITCH_H);
            toggle.setInteraction(enabled&&toggleBox.contains(mx,my),Objects.equals(pressed,drawingGroup)&&toggleBox.contains(mx,my));toggle.update(dt,r.enabled());toggle.render(canvas,toggleBox.x(),toggleBox.y(),c.accentColor,enabled);
            if(enabled)hit(toggleBox,()->{toggle.pulse();templateAction(row.index(),r.enabled()?"disable":"enable");cue();},null,motion,clip);
            for(var action:List.of("edit","delete")){
                float w=action.equals("edit")?SettingsListAction.width(t("edit")):size;
                var box=new UiLayout.Rect(action.equals("edit")?toggleBox.x()-UiTokens.SETTINGS_ROW_GAP-w:rect.right()-pad-size,y,w,size);
                listButton(canvas,row.id()+action,t(action),action.equals("edit")?null:SettingsGroupGlyphs.TRASH,0,box,false,action.equals("delete"),enabled,()->templateAction(row.index(),action),motion,clip,mx,my,dt);
            }
            return;
        }
        if(row.kind()==Kind.TYPES){
            int cols=columns(width,kindLabels(),3);float w=(width-UiTokens.SETTINGS_ROW_GAP*(cols-1))/cols;int i=0;
            for(var kind:MessageTemplate.Kind.values()){
                var r=rule();var box=new UiLayout.Rect(left+(i%cols)*(w+UiTokens.SETTINGS_ROW_GAP),top+UiTokens.SETTINGS_ROW_GAP+(i/cols)*(ACTION_H+UiTokens.SETTINGS_ROW_GAP),w,ACTION_H);
                button(canvas,"kind:"+kind,t("kind.short."+kind.name()),box,r.kind()==kind,false,enabled,
                        ()->setRule(new MessageTemplate(r.id(),r.name(),kind,r.format(),r.enabled())),motion,clip,mx,my,dt);i++;
            }
            return;
        }
        if(row.kind()==Kind.FIELDS){
            var boxes=fieldBoxes(width);int i=0;
            for(var name:placeholderNames()){
                var local=boxes.get(i);var box=new UiLayout.Rect(left+local.x(),top+UiTokens.SETTINGS_ROW_GAP+local.y(),local.w(),local.h());
                button(canvas,"placeholder:"+name,t("placeholder."+name),box,false,false,enabled,()->insertPlaceholder(name),motion,clip,mx,my,dt);i++;
            }
        }
    }
    private void drawAsset(Canvas canvas,Row row,UiLayout.Rect rect,Motion motion,UiLayout.Rect clip,float mx,float my,float dt,boolean enabled){
        var c=AtomChatConfig.get();var asset=draft.assets().get(row.index());
        float pad=UiTokens.SETTINGS_ROW_PAD,gap=UiTokens.SETTINGS_ROW_GAP,available=assetTextWidth(rect.w());
        var thumb=new UiLayout.Rect(rect.x()+pad,rect.y()+pad,THUMB,THUMB);
        drawImage(canvas,asset,thumb);
        float left=thumb.right()+gap,top=rect.y()+pad;
        for(var line:assetTitles(row,available)){
            var font=FontManager.font(UiTokens.SETTINGS_TILE_TITLE);
            SkiaFontRenderer.drawText(canvas,font,line,left,SkiaFontRenderer.centerBaselineY(font,top+TITLE_HALF_LINE),c.textPrimaryColor);top+=TITLE_LINE_H;
        }
        var desc=assetDescription(row,available);drawLines(canvas,desc,left,top,c.textSecondaryColor);top+=desc.size()*TITLE_LINE_H+gap;
        View origin=view;
        if(enabled)hit(thumb,()->previewImage(asset,origin),null,motion,clip);
        float previewW=SettingsListAction.width(t("preview_image")),removeW=SettingsListAction.width(t("remove"));
        boolean stacked=previewW+removeW+gap>available;
        button(canvas,row.id()+"preview",t("preview_image"),new UiLayout.Rect(left,top,Math.min(previewW,available),SettingsListAction.HEIGHT),
                false,false,enabled,()->previewImage(asset,origin),motion,clip,mx,my,dt);
        button(canvas,row.id()+"delete",t("remove"),new UiLayout.Rect(stacked?left:left+previewW+gap,stacked?top+SettingsListAction.HEIGHT+gap:top,Math.min(removeW,available),SettingsListAction.HEIGHT),
                false,true,enabled,()->danger(()->{var list=new ArrayList<>(draft.assets());list.remove(row.index());draft.assets(list);}),motion,clip,mx,my,dt);
    }
    private void drawLines(Canvas canvas,List<String> lines,float x,float top,int color){
        var font=FontManager.font(UiTokens.SETTINGS_TILE_SUB);
        for(String line:lines){SkiaFontRenderer.drawText(canvas,font,line,x,SkiaFontRenderer.centerBaselineY(font,top+TITLE_HALF_LINE),color);top+=TITLE_LINE_H;}
    }

    private void act(String id){
        switch(id){
            case "phrases:list"->show(View.PHRASES);
            case "assets:list"->show(View.EMOTES);
            case "asset:import"->{if(!importing){importing=true;host.chooseImage(this::importImage);}}
            case "phrase:add"->{
                if(draft.phrases().size()>=20){feedback="bad_phrases";return;}
                var list=new ArrayList<>(draft.phrases());list.add("");draft.phrases(list);
                editIndex=list.size()-1;show(View.PHRASE);
            }
            case "template:add"->{
                if(draft.templates().size()>=64){feedback="bad_template";return;}
                var list=new ArrayList<>(draft.templates());
                list.add(new MessageTemplate(UUID.randomUUID().toString(),"",MessageTemplate.Kind.PUBLIC,"{sender}: {message}",true));
                draft.templates(list);editIndex=list.size()-1;sample="";show(View.TEMPLATE);
            }
            default->{}
        }
    }
    private void phraseAction(int index,String action){
        if(action.equals("edit")){editIndex=index;show(View.PHRASE);return;}
        if(action.equals("delete")){danger(()->{var list=new ArrayList<>(draft.phrases());list.remove(index);draft.phrases(list);});return;}
        var list=new ArrayList<>(draft.phrases());Collections.swap(list,index,index+(action.equals("up")?-1:1));draft.phrases(list);
    }
    private void templateAction(int index,String action){
        if(action.equals("edit")){editIndex=index;sample="";show(View.TEMPLATE);return;}
        if(action.equals("delete")){danger(()->{var list=new ArrayList<>(draft.templates());list.remove(index);draft.templates(list);});return;}
        var list=new ArrayList<>(draft.templates());var r=list.get(index);
        list.set(index,new MessageTemplate(r.id(),r.name(),r.kind(),r.format(),!r.enabled()));draft.templates(list);
    }
    private void toggleRow(String id,ToggleSwitch toggle){
        draft.toggle(id);toggle.pulse();feedback="";cue();
    }
    private void danger(Runnable action){
        confirmation.openDangerous(t("delete_title"),t("delete_hint"),host.label("atomchat.config.cancel"),t("delete"),action);
    }
    private void insertPlaceholder(String name){
        String id="template.format";var field=fields.get(id);if(field==null)return;
        blur();focused=id;field.focus(true);field.insert("{"+name+"}");change(id,field.edit.text());
    }
    private void previewImage(AdminAsset asset,View back){shownImage=asset;imageReturn=back;failedImages.remove(asset.hash());loading.remove(asset.hash());show(View.IMAGE);}
    private void drawImage(Canvas canvas,AdminAsset asset,UiLayout.Rect box){
        if(failedImages.contains(asset.hash())){if(box.w()<NUMBER_TEXT_MIN)DrawPrimitives.drawIconCentered(canvas,DrawPrimitives.ICON_IMAGE_PATH,box.x()+box.w()/2,box.y()+box.h()/2,UiTokens.COMPOSER_ICON_SIZE,AtomChatConfig.get().textSecondaryColor);else{var font=FontManager.font(UiTokens.SETTINGS_TILE_SUB);SkiaFontRenderer.drawTextCentered(canvas,font,t("preview_unavailable"),box.x()+box.w()/2,box.y()+box.h()/2,AtomChatConfig.get().textSecondaryColor);}return;}
        var image=images.get(asset.hash());
        if(image==null){
            if(loading.add(asset.hash()))host.requestAsset(asset,bytes->{
                if(closed)return;
                try{
                    var decoded=Image.makeFromEncoded(bytes);
                    if(decoded!=null){
                        images.put(asset.hash(),decoded);
                        while(images.size()>8){var key=images.keySet().iterator().next();images.remove(key).close();loading.remove(key);}
                    }
                }catch(RuntimeException ignored){assetFailure(asset.hash(),"bad_image");}
            });
            return;
        }
        float scale=Math.min(box.w()/image.getWidth(),box.h()/image.getHeight()),w=image.getWidth()*scale,h=image.getHeight()*scale;
        SkiaImages.drawRoundedImage(canvas,image,box.x()+(box.w()-w)/2,box.y()+(box.h()-h)/2,w,h,UiTokens.buttonRadius());
    }
    private void importImage(Path path){
        if(path==null){importing=false;return;}
        Thread worker=new Thread(()->{
            try{
                if(Files.size(path)>ServerResources.MAX_BYTES)throw new java.io.IOException("too_large");
                byte[] bytes=Files.readAllBytes(path);ServerResources.validateImage(bytes);
                String original=path.getFileName().toString().toLowerCase(Locale.ROOT);
                String extension=original.contains(".")?original.substring(original.lastIndexOf('.')):".png";
                String hash=com.atom.chat.pack.ServerPack.sha256Hex(bytes);
                String name=com.atom.chat.pack.ServerPack.isSafeName(original)?original:"image-"+hash.substring(0,12)+extension;
                com.atom.chat.net.Host.execute(()->{
                    importing=false;if(closed||draft.saving())return;
                    var list=new ArrayList<>(draft.assets());String chosen=name;
                    if(list.stream().anyMatch(a->a.name().equals(name)))chosen="image-"+hash.substring(0,12)+extension;
                    String finalName=chosen;
                    if(list.stream().anyMatch(a->a.name().equals(finalName))){feedback="duplicate_name";return;}
                    list.add(AdminAsset.imported(chosen,bytes));draft.assets(list);feedback="";
                });
            }catch(Exception error){
                com.atom.chat.net.Host.execute(()->{importing=false;if(!closed)feedback="too_large".equals(error.getMessage())?"too_large":"bad_image";});
            }
        },"AtomChat-AdminImport");
        worker.setDaemon(true);worker.start();
    }
    public boolean click(float x,float y,int button){
        if(layout==null||geometry==null||button!=0)return true;
        if(view==View.ROOT){
            int chip=chips.hit(layout,x,y);
            if(chip>=0){if(chip!=chip()){navigationDirection.accept(chip<chip());blur();chips.select(chip);entrance.markEntered();cue();}return true;}
        }
        if(draft.saving())return true;
        if(geometry.save().contains(x,y)){
            if(importing)return true;
            if(view!=View.ROOT){navigateBack();return true;}
            if(draft.dirty()&&draft.beginSave(System.currentTimeMillis())){
                buttons.get("save").press();host.saveDraft(draft.version(),draft.values(),draft.assets(),draft.fingerprint());
            }
            return true;
        }
        if(geometry.status().contains(x,y)&&feedback.equals("saved")){host.retrySync();return true;}
        if(!geometry.scrollViewport().contains(x,y)){blur();return true;}
        if(SettingsScrollbar.over(geometry.scrollViewport(),scroll,x,y)){scroll.beginDrag(y);return true;}
        for(int i=hits.size()-1;i>=0;i--){
            var h=hits.get(i);if(!h.box().contains(x,y))continue;blur();
            if(h.field()!=null){
                focused=h.field();fields.get(focused).focus(true);
                fields.get(focused).pointer(h.motion().x(x),h.motion().y(y),false);dragging=h;
            }else h.action().run();
            pressed=h.group();return true;
        }
        blur();return true;
    }
    public boolean drag(float x,float y){
        if(dragging!=null&&focused!=null)fields.get(focused).pointer(dragging.motion().x(x),dragging.motion().y(y),true);
        if(scroll!=null&&scroll.isDragging())scroll.dragTo(y,geometry.viewport().h());return true;
    }
    public void release(){chips.release();pressed=null;dragging=null;if(scroll!=null)scroll.endDrag();}
    public boolean scroll(float x,float y,double amount){
        for(int i=hits.size()-1;i>=0;i--){var hit=hits.get(i);if(hit.field()!=null&&hit.box().contains(x,y)&&fields.get(hit.field()).scroll(amount))return true;}
        if(geometry!=null&&geometry.scrollViewport().contains(x,y)&&scroll!=null)scroll.wheel((float)amount);return true;
    }
    public boolean key(int key,int modifiers){
        if(draft.saving())return true;
        if(key==258){
            var ids=new ArrayList<String>();
            for(var g:content())for(var row:g.rows())if((row.kind()==Kind.NUMBER||row.kind()==Kind.TEXT||row.kind()==Kind.MULTILINE)&&(host.administration()||!row.id().equals("maxProfileTotalMb")))ids.add(row.id());
            if(ids.isEmpty())return true;
            int at=focused==null?((modifiers&1)!=0?0:-1):ids.indexOf(focused);
            blur();focused=ids.get(Math.floorMod(at+((modifiers&1)!=0?-1:1),ids.size()));
            SettingsFormField next=null;for(var group:content())for(var row:group.rows())if(row.id().equals(focused))next=form(row);
            if(next==null)return true;next.edit.set(value(focused));next.focus(true);requestedFocus=focused;
            for(var g:content())if(g.rows().stream().anyMatch(r->r.id().equals(focused))){collapsed.remove(g.id());folds.put(g.id(),1f);}
            return true;
        }
        if(focused==null)return true;
        var field=fields.get(focused);String before=field.edit.text();field.key(key,modifiers,host::clipboard,host::clipboard);
        if(!before.equals(field.edit.text()))change(focused,field.edit.text());if(!field.focused())blur();return true;
    }
    public boolean character(char chr){
        if(focused!=null&&!draft.saving()&&chr>=32&&chr!=127){
            var field=fields.get(focused);String before=field.edit.text();field.typed(chr);if(!before.equals(field.edit.text()))change(focused,field.edit.text());
        }
        return true;
    }
}
