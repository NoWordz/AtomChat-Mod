package com.atom.chat.config;

import com.atom.chat.pack.ServerPack;
import java.util.Base64;

/** Null data means an unchanged server file, base64 data means an unpublished import. */
public record AdminAsset(String name,String hash,long size,String data) {
    public static AdminAsset imported(String name,byte[] bytes) {
        return new AdminAsset(ServerPack.normalizeName(name),ServerPack.sha256Hex(bytes),bytes.length,Base64.getEncoder().encodeToString(bytes));
    }
    public AdminAsset metadata(){return new AdminAsset(name,hash,size,null);}
    public byte[] bytes(){return data==null?null:Base64.getDecoder().decode(data);}
}
