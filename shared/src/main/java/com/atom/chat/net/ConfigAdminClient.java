package com.atom.chat.net;

import com.atom.chat.config.*;
import com.atom.chat.chat.ServerTemplateStore;
import java.util.*;
import java.util.function.Consumer;

/** Client-thread session. Connection and operation identity keep late replies away from a newer draft. */
public final class ConfigAdminClient {
    private ConfigAdminClient(){}
    private static AdminProtocol.Receiver receiver=new AdminProtocol.Receiver();
    private static final Queue<AdminProtocol.Chunk> outgoing=new ArrayDeque<>();
    private record FileRequest(AdminAsset expected,Consumer<byte[]> receive,ServerConfigPage owner){}
    private static final Map<String,FileRequest> files=new HashMap<>();
    private static String detail="",pendingOpen,pendingSave,pendingStatus;
    private static ServerConfigPage saveOwner;
    private record AvatarRequest(String id, ServerConfigPage owner, long deadline) {}
    private static AvatarRequest avatarRequest;
    private static final PublicAvatarSubscription publicAvatar = new PublicAvatarSubscription();
    private static ConfigAdminServer.SyncStatus sync;
    private static long pollUntil,lastPoll;
    public static ConfigAdminServer.SyncStatus sync(){return sync;}
    public static String detail(){return detail;}
    public static void onJoin(){
        onDisconnect();
        if(AtomChatConfig.get().serverPacksEnabled&&ConfigAdminClientTransport.available())request("hello");
    }
    public static void onDisconnect(){
        publicAvatar.reset();
        com.atom.chat.render.PublicChatAvatarPaint.clear();
        avatarRequest=null;outgoing.clear();files.clear();receiver=new AdminProtocol.Receiver();ServerTemplateStore.clear();
        detail="";sync=null;pollUntil=lastPoll=0;pendingOpen=pendingSave=pendingStatus=null;saveOwner=null;
    }
    public static void tick(){
        long now=System.currentTimeMillis();receiver.expire(now);
        var subscription = publicAvatar.update(AtomChatConfig.get().serverPacksEnabled, ConfigAdminClientTransport.available());
        if (subscription != null) send(subscription);
        if (avatarRequest != null && now > avatarRequest.deadline()) {
            var request = avatarRequest;
            avatarRequest = null;
            if (ConfigScreenClient.page() == request.owner()) request.owner().publicAvatarResult(null, "failed");
        }
        if(now<pollUntil&&now-lastPoll>1000){lastPoll=now;request("status");}
        for(int i=0;i<8&&!outgoing.isEmpty();i++){
            if(!ConfigAdminClientTransport.available()){outgoing.clear();break;}
            ConfigAdminClientTransport.sendChunk(outgoing.remove());
        }
    }
    private static void send(AdminProtocol.Request request){
        outgoing.addAll(AdminProtocol.chunks(AdminProtocol.JSON.toJson(request)));
    }
    public static void request(String action){
        var request=new AdminProtocol.Request(action,0,null,null,null,null);
        if(action.equals("open"))pendingOpen=request.requestId();
        if(action.equals("status"))pendingStatus=request.requestId();
        if(action.equals("retry")){sync=null;pollUntil=System.currentTimeMillis()+120_000;lastPoll=0;}
        send(request);
    }
    public static void save(int version,ServerConfigValues values,List<AdminAsset> assets,String fingerprint){
        var request=new AdminProtocol.Request("save",version,fingerprint,values,assets,null);
        pendingSave=request.requestId();saveOwner=ConfigScreenClient.page();send(request);
    }
    public static void savePublicAvatar(ServerConfigPage owner, String revision, byte[] png) {
        if (ConfigScreenClient.page() != owner || !ConfigAdminClientTransport.available()) {
            owner.publicAvatarResult(null, "failed");
            return;
        }
        String action = png == null ? "public_avatar_reset" : "public_avatar_set";
        String data = png == null ? null : Base64.getEncoder().encodeToString(png);
        var request = new AdminProtocol.Request(action, 0, revision, null, null, data);
        avatarRequest = new AvatarRequest(request.requestId(), owner, System.currentTimeMillis() + 45_000);
        send(request);
    }

    private static void publicAvatarReply(AdminProtocol.Reply reply) {
        if (avatarRequest == null || !Objects.equals(reply.requestId(), avatarRequest.id())) return;
        var request = avatarRequest;
        avatarRequest = null;
        if (ConfigScreenClient.page() != request.owner()) return;
        request.owner().publicAvatarResult(reply.ok() ? reply.error() : null, reply.ok() ? "" : reply.error());
    }

    public static void packAck(String hash,boolean ok){if(ConfigAdminClientTransport.available()&&AtomChatConfig.get().serverPacksEnabled)send(new AdminProtocol.Request(ok?"pack_ack":"pack_failed",0,hash,null,null,null));}
    public static void file(AdminAsset asset,Consumer<byte[]> callback){
        if(asset.data()!=null){callback.accept(asset.bytes());return;}
        var request=new AdminProtocol.Request("file",0,null,null,null,asset.name());
        files.put(request.requestId(),new FileRequest(asset,callback,ConfigScreenClient.page()));send(request);
    }
    public static void receive(AdminProtocol.Chunk chunk,Object origin){
        if(origin==null||origin!=ConfigAdminClientTransport.connection())return;
        try{
            String text=receiver.offer(chunk,System.currentTimeMillis());if(text==null)return;
            var reply=AdminProtocol.JSON.fromJson(text,AdminProtocol.Reply.class);if(reply==null)return;
            if (publicAvatar.receive(reply, AtomChatConfig.get().serverPacksEnabled)) return;
            if(!reply.ok()){failure(reply);return;}
            switch(reply.action()){
                case "snapshot"->{
                    if(Objects.equals(reply.requestId(),pendingOpen)){pendingOpen=null;ConfigScreenClient.onAdministration(reply.snapshot());}
                }
                case "saved"->{
                    var page=ConfigScreenClient.page();
                    if(Objects.equals(reply.requestId(),pendingSave)&&page!=null&&page==saveOwner&&page.draft().saving()){
                        page.result(true,"");
                        if(page.acceptSnapshot(reply.snapshot().version(),reply.snapshot().values())) {
                            page.resources(reply.snapshot().fingerprint(),reply.snapshot().assets());
                            page.publicAvatarSnapshot(reply.snapshot().publicAvatarRevision());
                        }
                        pendingSave=null;saveOwner=null;
                        detail="syncing";sync=null;pollUntil=System.currentTimeMillis()+120_000;lastPoll=0;
                    }
                }
                case "public_avatar_saved" -> publicAvatarReply(reply);
                case "file"->{
                    var waiting=files.remove(reply.requestId());var asset=reply.asset();
                    if(waiting!=null&&asset!=null&&waiting.expected().hash().equals(asset.hash())&&waiting.expected().size()==asset.size()){
                        try{byte[] bytes=asset.bytes();
                            if(bytes.length==asset.size()&&com.atom.chat.pack.ServerPack.sha256Hex(bytes).equals(asset.hash()))waiting.receive().accept(bytes);
                            else if(waiting.owner()!=null)waiting.owner().assetFailure(waiting.expected().hash(),"bad_image");
                        }catch(RuntimeException invalid){if(waiting.owner()!=null)waiting.owner().assetFailure(waiting.expected().hash(),"bad_image");}
                    }else if(waiting!=null&&waiting.owner()!=null)waiting.owner().assetFailure(waiting.expected().hash(),"conflict");
                }
                case "templates"->{
                    if(!AtomChatConfig.get().serverPacksEnabled)break;
                    boolean ok=ServerTemplateStore.install(reply.templates());detail=ok?"synced":"bad_template";
                    int revision=Integer.parseInt(reply.error());
                    send(new AdminProtocol.Request(ok?"templates_ack":"templates_failed",revision,null,null,null,null));
                }
                case "sync"->{
                    if(Objects.equals(reply.requestId(),pendingStatus)){
                        sync=AdminProtocol.JSON.fromJson(reply.error(),ConfigAdminServer.SyncStatus.class);
                        if(sync.waiting()==0)pollUntil=0;
                    }
                }
                default->{}
            }
        }catch(RuntimeException invalid){detail="failed";}
    }
    private static void failure(AdminProtocol.Reply reply){
        detail=reply.error()==null?"failed":reply.error();
        if (reply.action().equals("public_avatar_set_error") || reply.action().equals("public_avatar_reset_error")) {
            publicAvatarReply(reply);
        } else if(reply.action().equals("save_error")&&Objects.equals(reply.requestId(),pendingSave)){
            var page=ConfigScreenClient.page();
            if(page!=null&&page==saveOwner&&page.draft().saving())page.result(false,detail);
            pendingSave=null;saveOwner=null;
        }else if(reply.action().equals("file_error")){
            var file=files.remove(reply.requestId());if(file!=null&&file.owner()!=null)file.owner().assetFailure(file.expected().hash(),detail);
        }else if(reply.action().equals("open_error")&&Objects.equals(reply.requestId(),pendingOpen)){
            pendingOpen=null;ConfigScreenClient.managementError(detail);
        }
    }
}
