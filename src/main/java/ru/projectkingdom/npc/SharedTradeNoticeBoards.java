package ru.projectkingdom.npc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** One live notice board per world data file. Event-driven: no tick or background scanning.
 * Use this for BOTH shop menus and board screens to avoid stale per-menu snapshots.
 */
public final class SharedTradeNoticeBoards {
    private static final Map<Path, DurableSimpleTradeNoticeBoard> LIVE = new HashMap<>();
    private SharedTradeNoticeBoards() {}

    public static synchronized DurableSimpleTradeNoticeBoard get(Path file) throws IOException {
        Path key = file.toAbsolutePath().normalize();
        DurableSimpleTradeNoticeBoard board = LIVE.get(key);
        if (board == null) {
            board = new DurableSimpleTradeNoticeBoard(key);
            LIVE.put(key, board);
        }
        return board;
    }

    /** Call when a world unloads, after all menus using the world are closed. */
    public static synchronized void unload(Path file) {
        LIVE.remove(file.toAbsolutePath().normalize());
    }
}
