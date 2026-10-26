package ru.projectkingdom.network;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Common-side bridge installed by client setup; keeps client classes off dedicated servers. */
public final class NpcSpeechClientBridge {
    private static final AtomicReference<Consumer<NpcSpeechPacket>> SINK =
            new AtomicReference<>(packet->{});

    private NpcSpeechClientBridge() {}

    public static void install(Consumer<NpcSpeechPacket> sink){SINK.set(Objects.requireNonNull(sink));}
    public static void accept(NpcSpeechPacket packet){SINK.get().accept(packet);}
}
