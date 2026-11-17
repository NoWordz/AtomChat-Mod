package com.atom.chat.config;

import com.atom.chat.pack.ServerPack;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;

/** Directory transaction for server-owned emotes. No client/personal directory is ever used. */
public final class ServerResources {
    private ServerResources() {}
    public static final long MAX_BYTES=16L*1024*1024;
    public static List<AdminAsset> list(Path dir) throws IOException {
        if(!Files.exists(dir))return List.of();
        if(Files.isSymbolicLink(dir))throw new IOException("unsafe_directory");
        var result=new ArrayList<AdminAsset>(); long total=0;
        try(var stream=Files.list(dir)) {
            for(var path:stream.sorted().toList()) {
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))continue;
                String name=ServerPack.normalizeName(path.getFileName().toString());
                if(!ServerPack.isSafeName(name))continue;
                long size=Files.size(path);
                // Older servers allowed 64 MiB. Expose their inventory so an admin can reduce it to 16 MiB.
                if(size>64L*1024*1024||result.size()>=1000||total+size>64L*1024*1024)throw new IOException("too_large");
                byte[] bytes=Files.readAllBytes(path);total+=bytes.length;
                result.add(new AdminAsset(name,ServerPack.sha256Hex(bytes),bytes.length,null));
            }
        }
        if(result.stream().map(AdminAsset::name).distinct().count()!=result.size())throw new IOException("duplicate_name");
        return List.copyOf(result);
    }
    public static String fingerprint(List<AdminAsset> list) {
        StringBuilder text=new StringBuilder();list.stream().sorted(Comparator.comparing(AdminAsset::name))
                .forEach(a->text.append(a.name()).append(':').append(a.hash()).append(':').append(a.size()).append('\n'));
        return ServerPack.sha256Hex(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static void validateImage(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw new IOException("bad_image");
        try(var in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(in);
            if(!readers.hasNext())throw new IOException("bad_image");
            var reader=readers.next();
            try {
                reader.setInput(in,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<=0||h<=0||(long)w*h>16_777_216)throw new IOException("bad_image");
                if(reader.read(0)==null)throw new IOException("bad_image");
            }finally{reader.dispose();}
        } catch(RuntimeException invalid){throw new IOException("bad_image",invalid);}
    }
    public static byte[] read(Path dir,AdminAsset asset) throws IOException {
        Path file=dir.resolve(asset.name()).normalize();
        if(!Files.exists(file)&&Files.isDirectory(dir))try(var paths=Files.list(dir)){
            file=paths.filter(p->ServerPack.normalizeName(p.getFileName().toString()).equals(asset.name())).findFirst().orElse(file);
        }
        if(!file.getParent().equals(dir.normalize())||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("conflict");
        if(Files.size(file)!=asset.size()||asset.size()>MAX_BYTES)throw new IOException("conflict");
        byte[] bytes=Files.readAllBytes(file);
        if(!ServerPack.sha256Hex(bytes).equals(asset.hash()))throw new IOException("conflict");return bytes;
    }
    public static synchronized void commit(Path dir,String expected,List<AdminAsset> desired,int maxFiles,long maxBytes,BooleanSupplier persist) throws IOException {
        var before=list(dir);
        if(!fingerprint(before).equals(expected))throw new IOException("conflict");
        if(desired==null||desired.size()>Math.min(200,maxFiles))throw new IOException("too_large");
        var existing=new HashMap<String,AdminAsset>();for(var asset:before)existing.put(asset.name(),asset);
        var names=new HashSet<String>();long size=0;
        Path absolute=dir.toAbsolutePath().normalize(),parent=absolute.getParent();Files.createDirectories(parent);
        Path stage=Files.createTempDirectory(parent,"emotes-draft-"),backup=null;boolean swapped=false;
        try {
            for(var asset:desired) {
                if(asset==null||asset.name()==null||!asset.name().equals(ServerPack.normalizeName(asset.name()))||!ServerPack.isSafeName(asset.name())||!names.add(asset.name()))throw new IOException("duplicate_name");
                if(asset.size()<=0||asset.size()>MAX_BYTES||(size+=asset.size())>Math.min(MAX_BYTES,maxBytes))throw new IOException("too_large");
                byte[] bytes;
                if(asset.data()==null) {
                    if(!asset.equals(existing.get(asset.name())))throw new IOException("conflict");
                    bytes=read(dir,asset);
                } else {
                    try{bytes=asset.bytes();}catch(IllegalArgumentException invalid){throw new IOException("bad_image",invalid);}
                    if(bytes.length!=asset.size()||!ServerPack.sha256Hex(bytes).equals(asset.hash()))throw new IOException("bad_image");
                    validateImage(bytes);
                }
                Files.write(stage.resolve(asset.name()),bytes,StandardOpenOption.CREATE_NEW);
            }
            if(!fingerprint(list(dir)).equals(expected))throw new IOException("conflict");
            // Preserve non-pack files (including invalid names) rather than deleting unrelated admin files.
            if(Files.exists(absolute))try(var entries=Files.list(absolute)) {
                for(var path:entries.toList())if(!existing.containsKey(ServerPack.normalizeName(path.getFileName().toString()))) {
                    if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("unsafe_directory");
                    Files.copy(path,stage.resolve(path.getFileName()),StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            if(Files.exists(absolute)) {backup=parent.resolve("emotes-backup-"+UUID.randomUUID());Files.move(absolute,backup);}
            Files.move(stage,absolute);swapped=true;
            if(!persist.getAsBoolean())throw new IOException("failed");
        } catch(IOException|RuntimeException error) {
            if(swapped)removeTree(absolute);
            if(backup!=null&&Files.exists(backup))Files.move(backup,absolute);
            throw error;
        } finally {
            if(Files.exists(stage))removeTree(stage);
        }
        // Failed backup cleanup is not a failed commit: retain it for the administrator.
        if(backup!=null)try{removeTree(backup);}catch(IOException ignored){}
    }
    private static void removeTree(Path root) throws IOException {
        try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
    }
}
