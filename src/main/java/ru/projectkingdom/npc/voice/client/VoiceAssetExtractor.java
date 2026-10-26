package ru.projectkingdom.npc.voice.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipInputStream;

/** Extracts the bundled recognizer model into a versioned cache with ZIP-slip protection. */
public final class VoiceAssetExtractor {
    private static final long MAX_EXTRACTED_BYTES=256L*1024L*1024L;
    private static final int MAX_ENTRIES=4096;

    private VoiceAssetExtractor() {}

    public static Path extract(InputStream archive,Path cacheRoot,String version,
                               String expectedSha256,String modelFolder) throws IOException {
        if(archive==null)throw new IOException("Bundled Vosk model is missing");
        Path root=cacheRoot.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path destination=root.resolve(version).normalize();
        if(!destination.startsWith(root))throw new SecurityException("Unsafe voice cache version");
        Path marker=destination.resolve(".complete");
        Path model=destination.resolve(modelFolder).normalize();
        if(Files.isRegularFile(marker)&&Files.isDirectory(model)
                &&expectedSha256.equalsIgnoreCase(Files.readString(marker).strip()))return model;

        Path archiveFile=Files.createTempFile(root,"voice-model-",".zip");
        Path staging=Files.createTempDirectory(root,"voice-unpack-");
        try {
            MessageDigest digest=sha256();
            try(InputStream input=archive;OutputStream output=Files.newOutputStream(archiveFile)){
                byte[] buffer=new byte[8192];int read;
                while((read=input.read(buffer))>=0){
                    if(read==0)continue;
                    digest.update(buffer,0,read);output.write(buffer,0,read);
                }
            }
            String actual=HexFormat.of().formatHex(digest.digest());
            if(!actual.equalsIgnoreCase(expectedSha256))
                throw new SecurityException("Bundled Vosk model checksum mismatch");
            unzipSafely(archiveFile,staging);
            Path stagedModel=staging.resolve(modelFolder).normalize();
            if(!Files.isDirectory(stagedModel))throw new IOException("Vosk model directory is missing");
            Files.writeString(staging.resolve(".complete"),actual);
            if(Files.exists(destination))deleteTree(destination);
            try {Files.move(staging,destination,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(staging,destination);}
            return destination.resolve(modelFolder);
        } finally {
            Files.deleteIfExists(archiveFile);
            if(Files.exists(staging))deleteTree(staging);
        }
    }

    private static void unzipSafely(Path archive,Path staging) throws IOException {
        long written=0L;int entries=0;
        try(var input=new ZipInputStream(Files.newInputStream(archive))){
            for(var entry=input.getNextEntry();entry!=null;entry=input.getNextEntry()){
                if(++entries>MAX_ENTRIES)throw new SecurityException("Too many voice model files");
                String name=entry.getName().replace('\\','/');
                if(name.startsWith("/")||name.matches("^[A-Za-z]:.*"))
                    throw new SecurityException("Unsafe voice model path");
                Path target=staging.resolve(name).normalize();
                if(!target.startsWith(staging))throw new SecurityException("Unsafe voice model path");
                if(entry.isDirectory()){Files.createDirectories(target);continue;}
                Files.createDirectories(target.getParent());
                try(OutputStream output=Files.newOutputStream(target)){
                    byte[] buffer=new byte[8192];int read;
                    while((read=input.read(buffer))>=0){
                        if(read==0)continue;
                        written+=read;
                        if(written>MAX_EXTRACTED_BYTES)
                            throw new SecurityException("Voice model archive is too large");
                        output.write(buffer,0,read);
                    }
                }
            }
        }
    }

    private static MessageDigest sha256(){
        try{return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }

    private static void deleteTree(Path root) throws IOException {
        try(var paths=Files.walk(root)){
            for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
        }
    }
}
