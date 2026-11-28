package com.atom.chat.net;

import com.atom.chat.pack.PublicChatAvatar;

import com.atom.chat.config.*;
import com.atom.chat.util.CacheDirs;
import java.util.*;
import java.io.IOException;

/** Server-thread administration. Every chunk and every final mutation rechecks permission in its platform bridge. */
public final class ConfigAdminServer {
    private ConfigAdminServer() {}
    private record Session(Object player,AdminProtocol.Receiver receiver) {}
    private record AvatarWatcher(Object player, String requestId) {}
    private static final Map<UUID, AvatarWatcher> AVATAR_WATCHERS = new HashMap<>();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Queue<AdminProtocol.Chunk>> OUTGOING=new HashMap<>();
    private static final Map<UUID,Object> TEMPLATE_PEERS=new HashMap<>();
    private static final Map<UUID,Long> TEMPLATE_PENDING=new HashMap<>();
    private static final Set<UUID> TEMPLATE_FAILED=new HashSet<>();
    public static void receive(Object player,AdminProtocol.Chunk chunk,boolean mayEdit) {
        UUID id=Host.playerId(player);if(id==null||Host.playerById(Host.serverOf(player),id)!=player)return;
        if(!mayEdit&&chunk.count()!=1)return;
        var session=SESSIONS.get(id);
        if (session == null || session.player() != player) {
            removeSession(id);
            session = new Session(player, new AdminProtocol.Receiver());
            SESSIONS.put(id, session);
        }
        AdminProtocol.Request current=null;
        try {
            String text=session.receiver().offer(chunk,System.currentTimeMillis());if(text==null)return;
            var request=AdminProtocol.JSON.fromJson(text,AdminProtocol.Request.class);
            if(request==null||request.action()==null||request.requestId()==null||request.requestId().length()>64)return;
            current=request;
            if(request.action().equals("hello")){TEMPLATE_PEERS.put(id,player);templates(player);return;}
            if(request.action().equals("templates_ack")||request.action().equals("templates_failed")){
                if(TEMPLATE_PEERS.get(id)==player&&request.version()==ConfigRevision.get()){
                    TEMPLATE_PENDING.remove(id);if(request.action().equals("templates_failed"))TEMPLATE_FAILED.add(id);else TEMPLATE_FAILED.remove(id);
                }return;
            }
            if(request.action().equals("pack_ack")||request.action().equals("pack_failed")){
                PackSyncServer.verifiedAck(player, request.fingerprint(), request.action().equals("pack_ack"));
                return;
            }
            if (request.action().equals("public_avatar_watch")) {
                var watcher = new AvatarWatcher(player, request.requestId());
                AVATAR_WATCHERS.put(id, watcher);
                sendPublicAvatar(watcher, publicAvatarAsset());
                return;
            }
            if (request.action().equals("public_avatar_unwatch")) {
                var watcher = AVATAR_WATCHERS.get(id);
                if (watcher != null && watcher.player() == player
                        && watcher.requestId().equals(request.fingerprint())) AVATAR_WATCHERS.remove(id);
                return;
            }
            if(!mayEdit)throw new IllegalArgumentException("permission");
            switch(request.action()) {
                case "open" -> respond(player,request,new AdminProtocol.Reply("snapshot",true,"",snapshot(),null,null));
                case "file" -> {
                    var asset=ServerResources.list(CacheDirs.serverEmotesDir()).stream().filter(a->a.name().equals(request.name())).findFirst().orElseThrow(()->new IllegalArgumentException("conflict"));
                    respond(player,request,new AdminProtocol.Reply("file",true,"",null,AdminAsset.imported(asset.name(),ServerResources.read(CacheDirs.serverEmotesDir(),asset)),null));
                }
                case "save" -> {
                    if(request.version()!=ConfigRevision.get())throw new IllegalArgumentException("conflict");
                    var values=request.values();if(values==null)throw new IllegalArgumentException("failed");
                    String error=values.validate();if(error!=null)throw new IllegalArgumentException(error);
                    ServerResources.commit(CacheDirs.serverEmotesDir(),request.fingerprint(),request.assets(),values.packMaxFiles(),values.packMaxMb()*1024L*1024L,
                            ()->AtomChatServerConfig.saveValues(values.sanitize()));
                    ConfigRevision.incrementAndGet();PackSyncServer.invalidate();
                    respond(player,request,new AdminProtocol.Reply("saved",true,"",snapshot(),null,null));
                    broadcast(Host.serverOf(player));PackSyncServer.pushUpdate(Host.serverOf(player));
                }
                case "public_avatar_set", "public_avatar_reset" -> {
                    byte[] png = request.action().equals("public_avatar_set")
                            ? PublicChatAvatar.decodeUpload(request.name()) : null;
                    var avatar = CacheDirs.publicChatAvatar();
                    String revision = PublicChatAvatar.save(avatar, request.fingerprint(), png);
                    respond(player, request, new AdminProtocol.Reply("public_avatar_saved", true,
                            revision, null, null, null));
                    broadcastPublicAvatar(Host.serverOf(player));
                }
                case "retry" -> {broadcast(Host.serverOf(player));PackSyncServer.pushUpdate(Host.serverOf(player));}
                case "status" -> respond(player,request,new AdminProtocol.Reply("sync",true,AdminProtocol.JSON.toJson(syncStatus(Host.serverOf(player))),null,null,null));
                default -> throw new IllegalArgumentException("failed");
            }
        } catch(Exception error) {
            String reason=error.getMessage();if(!Set.of("conflict","bad_template","bad_range","bad_phrases","bad_name","duplicate_name","bad_image","too_large","permission").contains(reason==null?"":reason))reason="failed";
            reply(player,new AdminProtocol.Reply(current==null?"request_error":current.action()+"_error",false,reason,null,null,null).correlate(current==null?null:current.requestId()));
            if(reason.equals("failed")&&!(error instanceof IllegalArgumentException)&&!(error instanceof com.google.gson.JsonParseException))
                com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("config","Administration failed"),error);
        }
    }
    static boolean watchesPublicAvatar(Object player) {
        var watcher = AVATAR_WATCHERS.get(Host.playerId(player));
        return watcher != null && watcher.player() == player;
    }

    private static AdminAsset publicAvatarAsset() {
        if (!AtomChatServerConfig.get().packEnabled) return null;
        try {
            byte[] bytes = PublicChatAvatar.read(CacheDirs.publicChatAvatar());
            return bytes == null ? null : AdminAsset.imported(PublicChatAvatar.FILE_NAME, bytes);
        } catch (IOException error) {
            com.atom.chat.AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("config", "Could not read the public chat avatar"), error);
            return null;
        }
    }

    private static void sendPublicAvatar(AvatarWatcher watcher, AdminAsset image) {
        reply(watcher.player(), new AdminProtocol.Reply("public_avatar", true, "", null, image, null)
                .correlate(watcher.requestId()));
    }

    private static void broadcastPublicAvatar(Object server) {
        if (AVATAR_WATCHERS.isEmpty()) return;
        AdminAsset image = publicAvatarAsset();
        var iterator = AVATAR_WATCHERS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var watcher = entry.getValue();
            if (Host.serverOf(watcher.player()) != server) continue;
            if (Host.playerById(server, entry.getKey()) == watcher.player()) sendPublicAvatar(watcher, image);
            else iterator.remove();
        }
    }

    private static void removeSession(UUID id) {
        SESSIONS.remove(id);
        OUTGOING.remove(id);
        TEMPLATE_PEERS.remove(id);
        TEMPLATE_PENDING.remove(id);
        TEMPLATE_FAILED.remove(id);
        AVATAR_WATCHERS.remove(id);
    }

    private static AdminProtocol.Snapshot snapshot() throws java.io.IOException {
        var assets=ServerResources.list(CacheDirs.serverEmotesDir());
        return new AdminProtocol.Snapshot(ConfigRevision.get(),ServerConfigValues.of(AtomChatServerConfig.get()),ServerResources.fingerprint(assets),assets,
                PublicChatAvatar.revision(CacheDirs.publicChatAvatar()));
    }
    private static void respond(Object player,AdminProtocol.Request request,AdminProtocol.Reply response){reply(player,response.correlate(request.requestId()));}
    private static void reply(Object player,AdminProtocol.Reply reply){
        var queue=OUTGOING.computeIfAbsent(Host.playerId(player),id->new ArrayDeque<>());
        var chunks=AdminProtocol.chunks(AdminProtocol.JSON.toJson(reply));
        int limit=reply.action().equals("file")?4096:4224;
        if(queue.size()+chunks.size()<=limit)queue.addAll(chunks);
        else if(reply.action().equals("file"))reply(player,new AdminProtocol.Reply("file_error",false,"failed",null,null,null).correlate(reply.requestId()));
    }
    public static void tick(Object server) {
        if(server==null)return;
        for(var entry:new ArrayList<>(SESSIONS.entrySet())) {
            var player=Host.playerById(server,entry.getKey());
            if (player != entry.getValue().player()) { removeSession(entry.getKey()); continue; }
            entry.getValue().receiver().expire(System.currentTimeMillis());
            var queue=OUTGOING.get(entry.getKey());
            if(queue!=null)for(int i=0;i<8&&!queue.isEmpty();i++)ConfigAdminPayloads.sendPlayerChunk(player,queue.remove());
            if(queue!=null&&queue.isEmpty())OUTGOING.remove(entry.getKey());
        }
    }
    private static void templates(Object player){TEMPLATE_PENDING.put(Host.playerId(player),System.currentTimeMillis()+45_000);TEMPLATE_FAILED.remove(Host.playerId(player));reply(player,new AdminProtocol.Reply("templates",true,""+ConfigRevision.get(),null,null,AtomChatServerConfig.get().templates));}
    public record SyncStatus(int waiting,int failed,int complete){}
    public static boolean isTemplatePeer(Object player){return TEMPLATE_PEERS.get(Host.playerId(player))==player;}
    private static SyncStatus syncStatus(Object server){
        var states=PackSyncServer.syncStates(server);long now=System.currentTimeMillis();
        for(var entry:TEMPLATE_PEERS.entrySet())if(Host.playerById(server,entry.getKey())==entry.getValue()){
            UUID id=entry.getKey();Long deadline=TEMPLATE_PENDING.get(id);
            if(TEMPLATE_FAILED.contains(id)||deadline!=null&&deadline<now)states.put(id,"failed");
            else if(deadline!=null&&!states.getOrDefault(id,"ok").equals("failed"))states.put(id,"pending");
            else states.putIfAbsent(id,"ok");
        }
        int pending=0,failed=0,ok=0;for(var state:states.values()){if(state.equals("ok"))ok++;else if(state.equals("pending"))pending++;else failed++;}return new SyncStatus(pending,failed,ok);
    }
    public static void broadcast(Object server) {
        broadcastPublicAvatar(server);
        for(var entry:new ArrayList<>(TEMPLATE_PEERS.entrySet())) {
            Object player=Host.playerById(server,entry.getKey());
            if(player!=entry.getValue()){TEMPLATE_PEERS.remove(entry.getKey());TEMPLATE_PENDING.remove(entry.getKey());TEMPLATE_FAILED.remove(entry.getKey());continue;}
            templates(player);
        }
    }
}
