package ru.projectkingdom.npc.voice.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Generates Russian NPC speech with the Windows System.Speech engine. */
public final class WindowsSpeechSynthesizer {
    private static final long TIMEOUT_SECONDS=20L;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{
        Thread thread=new Thread(r,"kingdom-npc-speech-synthesis");
        thread.setDaemon(true);
        return thread;
    });
    private final Path cacheRoot;

    public WindowsSpeechSynthesizer(){
        this(Path.of(System.getProperty("java.io.tmpdir"),"kingdomnpc","speech"));
    }

    WindowsSpeechSynthesizer(Path cacheRoot){this.cacheRoot=cacheRoot;}

    public CompletableFuture<Path> synthesize(String text,String gender){
        if(!isWindows())return CompletableFuture.failedFuture(
                new IllegalStateException("NPC speech requires Windows"));
        if(text==null||text.isBlank())return CompletableFuture.failedFuture(
                new IllegalArgumentException("NPC speech text is blank"));
        return CompletableFuture.supplyAsync(()->synthesizeBlocking(text,gender),executor);
    }

    public static String encodedCommand(String text,String gender,Path output){
        Objects.requireNonNull(text,"text");
        Objects.requireNonNull(output,"output");
        String textData=data(text);
        String genderData=data(gender==null?"unspecified":gender.toLowerCase(Locale.ROOT));
        String pathData=data(output.toAbsolutePath().toString());
        String script="""
                $ErrorActionPreference='Stop'
                Add-Type -AssemblyName System.Speech
                $utf8=[System.Text.Encoding]::UTF8
                $text=$utf8.GetString([Convert]::FromBase64String('%s'))
                $gender=$utf8.GetString([Convert]::FromBase64String('%s'))
                $output=$utf8.GetString([Convert]::FromBase64String('%s'))
                $speaker=[System.Speech.Synthesis.SpeechSynthesizer]::new()
                try {
                  $voices=@($speaker.GetInstalledVoices() | Where-Object {
                    $_.Enabled -and $_.VoiceInfo.Culture.Name.StartsWith('ru')
                  } | ForEach-Object { $_.VoiceInfo })
                  if($voices.Count -eq 0){ throw 'Russian Windows speech voice is unavailable' }
                  $wanted=if($gender -eq 'female'){
                    [System.Speech.Synthesis.VoiceGender]::Female
                  }elseif($gender -eq 'male'){
                    [System.Speech.Synthesis.VoiceGender]::Male
                  }else{
                    [System.Speech.Synthesis.VoiceGender]::NotSet
                  }
                  $voice=$voices | Where-Object {
                    $wanted -eq [System.Speech.Synthesis.VoiceGender]::NotSet -or $_.Gender -eq $wanted
                  } | Select-Object -First 1
                  if($null -eq $voice){ $voice=$voices[0] }
                  $speaker.SelectVoice($voice.Name)
                  $speaker.SetOutputToWaveFile($output)
                  $speaker.Speak($text)
                } finally {
                  $speaker.Dispose()
                }
                """.formatted(textData,genderData,pathData);
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    private Path synthesizeBlocking(String text,String gender){
        Path output=null;
        try {
            Files.createDirectories(cacheRoot);
            output=Files.createTempFile(cacheRoot,"npc-speech-",".wav");
            Process process=new ProcessBuilder("powershell.exe","-NoLogo","-NoProfile",
                    "-NonInteractive","-EncodedCommand",encodedCommand(text,gender,output))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if(!process.waitFor(TIMEOUT_SECONDS,TimeUnit.SECONDS)){
                process.destroyForcibly();
                throw new IllegalStateException("Windows speech timed out");
            }
            if(process.exitValue()!=0||!Files.isRegularFile(output)||Files.size(output)<=44L)
                throw new IllegalStateException("Windows speech synthesis failed");
            return output;
        } catch(Exception error) {
            if(output!=null)try{Files.deleteIfExists(output);}catch(Exception ignored){}
            if(error instanceof InterruptedException)Thread.currentThread().interrupt();
            throw new IllegalStateException("Cannot synthesize NPC speech",error);
        }
    }

    private static boolean isWindows(){
        return System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static String data(String value){
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
