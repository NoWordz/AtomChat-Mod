package com.atom.chat.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Publish a completed sibling directory, restoring the previous one if the final rename fails. */
public final class DirectorySwap {
    private DirectorySwap(){}
    public static void replace(Path stage,Path target)throws IOException{
        Path backup=null;
        if(Files.exists(target)){
            backup=target.resolveSibling(target.getFileName()+"-previous-"+UUID.randomUUID());
            Files.move(target,backup);
        }
        try{Files.move(stage,target);}
        catch(IOException error){
            if(backup!=null)try{Files.move(backup,target);}catch(IOException rollback){error.addSuppressed(rollback);}
            throw error;
        }
        if(backup!=null)try{delete(backup);}catch(IOException ignored){}
    }
    private static void delete(Path root)throws IOException{
        try(var paths=Files.walk(root)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
    }
}
