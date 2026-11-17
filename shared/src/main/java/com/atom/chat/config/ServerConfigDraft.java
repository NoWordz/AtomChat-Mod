package com.atom.chat.config;

import java.util.*;

/** Server-owned baseline and local edits, independent of client settings persistence. */
public final class ServerConfigDraft {
    private int version;
    private ServerConfigValues baseline;
    private final Map<String, String> fields = new LinkedHashMap<>();
    private boolean hosting, pack, saving, acknowledged;
    private long sentAt;
    private String error = "";
    private List<com.atom.chat.chat.MessageTemplate> templates=List.of();
    private List<String> phraseValues=List.of();
    private List<AdminAsset> assets=List.of(),baselineAssets=List.of();
    private String fingerprint="";

    public ServerConfigDraft(int version, ServerConfigValues values) { confirm(version, values); }
    public int version() { return version; }
    public boolean saving() { return saving; }
    public boolean acknowledged() { return acknowledged; }
    public String error() { return error; }
    public String text(String id) { return fields.getOrDefault(id, ""); }
    public void text(String id, String text) { if (!saving && fields.containsKey(id)) {fields.put(id, text);if(id.equals("phrases"))phraseValues=text.isEmpty()?List.of():List.copyOf(Arrays.asList(text.split("\n",-1)));error="";} }
    public List<String> phrases(){return phraseValues;}
    public void phrases(List<String> phrases){if(!saving){phraseValues=List.copyOf(phrases);fields.put("phrases",String.join("\n",phrases));error="";}}
    public List<com.atom.chat.chat.MessageTemplate> templates(){return templates;}
    public void templates(List<com.atom.chat.chat.MessageTemplate> rules){if(!saving){templates=List.copyOf(rules);error="";}}
    public List<AdminAsset> assets(){return assets;}
    public void assets(List<AdminAsset> files){if(!saving){assets=List.copyOf(files);error="";}}
    public String fingerprint(){return fingerprint;}
    public void resourceBaseline(String fingerprint,List<AdminAsset> files){this.fingerprint=fingerprint;assets=List.copyOf(files);baselineAssets=assets;}
    public boolean enabled(String id) { return "hostingEnabled".equals(id) ? hosting : pack; }
    public void toggle(String id) { if (!saving) { if ("hostingEnabled".equals(id)) hosting = !hosting; else pack = !pack; } }
    public boolean dirty() { return !Objects.equals(baseline, values())||!Objects.equals(assets,baselineAssets); }
    public ServerConfigValues values() {
        try {
            return new ServerConfigValues(hosting, pack,
                    number("maxFileKb"), number("maxTotalMb"), number("maxAvatarTotalMb"),
                    number("retentionDays"), number("uploadCooldownMs"), number("packMaxFiles"), number("packMaxMb"),
                    text("packName"), phrases(),number("maxProfileTotalMb"),templates);
        } catch (NumberFormatException invalid) { return null; }
    }
    private int number(String id) { return Integer.parseInt(text(id)); }
    public boolean beginSave(long now) {
        if (saving) return false;
        error=validation();
        if (error != null) return false;
        error = ""; saving = true; acknowledged = false; sentAt = now; return true;
    }
    public String validation(){var values=values();String error=values==null?"bad_number":values.validate();if(error==null&&(assets.size()>values.packMaxFiles()||assets.stream().mapToLong(AdminAsset::size).sum()>values.packMaxMb()*1024L*1024L))error="too_large";return error;}
    public void result(boolean ok, String reason) {
        if (!saving) return;
        acknowledged = ok;
        if (!ok) { saving = false; error = reason == null ? "failed" : reason; }
    }
    public void timeout(long now) {
        if (saving && now - sentAt > 120_000) { saving = false; acknowledged = false; error = "timeout"; }
    }
    public void confirm(int version, ServerConfigValues values) {
        this.version = version; baseline = values;
        templates=List.copyOf(values.templates());
        phraseValues=List.copyOf(values.phrases());
        hosting = values.hostingEnabled(); pack = values.packEnabled();
        fields.put("maxFileKb", "" + values.maxFileKb()); fields.put("maxTotalMb", "" + values.maxTotalMb());
        fields.put("maxAvatarTotalMb", "" + values.maxAvatarTotalMb()); fields.put("retentionDays", "" + values.retentionDays());
        fields.put("uploadCooldownMs", "" + values.uploadCooldownMs()); fields.put("packMaxFiles", "" + values.packMaxFiles());
        fields.put("packMaxMb", "" + values.packMaxMb()); fields.put("packName", values.packName());
        fields.put("phrases", String.join("\n", values.phrases()));
        fields.put("maxProfileTotalMb",""+values.maxProfileTotalMb());
        saving = false; acknowledged = false; error = "";
    }
}
