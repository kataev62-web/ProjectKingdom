package ru.projectkingdom.npc.voice.client;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import org.vosk.Model;
import org.vosk.Recognizer;

/** Lazy offline Russian recognizer. The 45 MB model is bundled inside the mod JAR. */
public final class VoskRussianRecognizer {
    static final String MODEL_RESOURCE="/assets/kingdomnpc/voice/vosk-model-small-ru-0.22.zip";
    static final String MODEL_SHA256="961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4";
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{
        Thread thread=new Thread(r,"kingdom-npc-vosk");thread.setDaemon(true);return thread;
    });
    private volatile CompletableFuture<Model> model;

    public CompletableFuture<Void> warmUp(){return model().thenApply(ignored->null);}

    public CompletableFuture<String> recognize(byte[] pcm){
        if(pcm==null||pcm.length==0)return CompletableFuture.completedFuture("");
        return model().thenApplyAsync(loaded->{
            try(Recognizer recognizer=new Recognizer(loaded,16000F)){
                recognizer.acceptWaveForm(pcm,pcm.length);
                var json=JsonParser.parseString(recognizer.getFinalResult()).getAsJsonObject();
                return json.has("text")?json.get("text").getAsString().strip():"";
            } catch(Exception error){throw new IllegalStateException("Russian recognition failed",error);}
        },executor);
    }

    private CompletableFuture<Model> model(){
        var current=model;if(current!=null)return current;
        synchronized(this){
            if(model==null)model=CompletableFuture.supplyAsync(this::loadModel,executor);
            return model;
        }
    }

    private Model loadModel(){
        try(InputStream archive=VoskRussianRecognizer.class.getResourceAsStream(MODEL_RESOURCE)){
            Path cache=Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("kingdomnpc/voice-cache");
            Path path=VoiceAssetExtractor.extract(archive,cache,"vosk-small-ru-0.22",MODEL_SHA256,
                    "vosk-model-small-ru-0.22");
            return new Model(path.toString());
        } catch(Exception error){throw new IllegalStateException("Cannot load bundled Russian voice model",error);}
    }
}
