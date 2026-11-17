package com.atom.chat.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import java.util.UUID;

/** Server templates interpret a region; native identity wins and missing UUIDs remain missing. */
public final class ServerTemplateRecognition {
    private ServerTemplateRecognition() {}
    public static SenderMeta parse(Component message,SenderMeta corroboration){
        var client=Minecraft.getInstance();
        return parse(message,corroboration,client==null||client.player==null?null:client.player.getName().getString(),client==null||client.player==null?null:client.player.getUUID());
    }
    static SenderMeta parse(Component message,SenderMeta corroboration,String own,UUID ownUuid){
        if(!com.atom.chat.config.AtomChatConfig.get().serverPacksEnabled)return null;
        var preview=TemplateParser.match(ServerTemplateStore.rules(),message.getString());
        if(preview.state()==TemplateParser.State.AMBIGUOUS)return conflict();
        if(preview.state()!=TemplateParser.State.MATCH)return null;
        var result=preview.match();var fields=result.fields();
        boolean outgoing=result.kind()==MessageTemplate.Kind.OUTGOING;
        if(outgoing&&(own==null||fields.containsKey("sender")&&!own.equalsIgnoreCase(fields.get("sender"))))return conflict();
        if(result.kind()==MessageTemplate.Kind.INCOMING&&fields.containsKey("target")&&(own==null||!own.equalsIgnoreCase(fields.get("target"))))return conflict();
        String account=outgoing?own:fields.get("sender");
        String partner=result.kind()==MessageTemplate.Kind.INCOMING?account:outgoing?fields.get("target"):null;
        UUID uuid=fields.containsKey("uuid")?UUID.fromString(fields.get("uuid")):null;
        if(outgoing&&uuid!=null&&!uuid.equals(ownUuid))return conflict();
        boolean recipientProof=outgoing&&corroboration!=null&&fields.get("target").equalsIgnoreCase(corroboration.profileName());
        if(recipientProof&&corroboration.evidence().confirmsIdentity()&&corroboration.senderUuid()!=null)
            SeenPlayers.remember(corroboration.senderUuid(),fields.get("target"),corroboration.senderName());
        if(corroboration!=null&&!recipientProof){
            if(corroboration.profileName()!=null&&!corroboration.profileName().equalsIgnoreCase(account))return conflict();
            if(uuid!=null&&corroboration.senderUuid()!=null&&!uuid.equals(corroboration.senderUuid()))return conflict();
            if(uuid==null)uuid=corroboration.senderUuid();
        }
        if(uuid==null)uuid=outgoing?ownUuid:ChatClassifier.resolveAccountUuid(account);
        String known=uuid==null?null:ChatClassifier.profileName(uuid);
        if(known!=null&&!known.equalsIgnoreCase(account))return conflict();
        var body=result.spans().get("message");var name=result.spans().get(fields.containsKey("display")?"display":"sender");
        Component display=body.start()>0?ComponentIdentity.slice(message,0,body.start()):name!=null?ComponentIdentity.slice(message,name.start(),name.end()):Component.literal(account);
        return new SenderMeta(uuid,fields.getOrDefault("display",account),account,fields.get("message"),false,
                result.kind()!=MessageTemplate.Kind.PUBLIC,partner,display,ComponentIdentity.slice(message,body.start(),body.end()))
                .withEvidence(corroboration!=null&&!recipientProof?corroboration.evidence():IdentityEvidence.SERVER_TEMPLATE);
    }
    private static SenderMeta conflict(){return SenderMeta.systemMeta().withEvidence(IdentityEvidence.CONFLICT);}
}
