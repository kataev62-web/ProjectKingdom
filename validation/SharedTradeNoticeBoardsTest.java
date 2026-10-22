import ru.projectkingdom.npc.*;
import java.nio.file.*;
import java.util.*;

public class SharedTradeNoticeBoardsTest {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Path p = Files.createTempDirectory("kingdom-shared-board").resolve("board.bin");
        var a = SharedTradeNoticeBoards.get(p);
        var b = SharedTradeNoticeBoards.get(p.getParent().resolve(".").resolve("board.bin"));
        check(a == b);
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
        a.post(new SimpleTradeNoticeBoard.Notice(id,owner,SimpleTradeNoticeBoard.Side.BUY,"minecraft:iron_ingot",40,3,"Кузница"));
        check(b.notice(id).quantity() == 40);
        check(b.confirmedTrade(id,"minecraft:iron_ingot",12));
        check(a.notice(id).quantity() == 28);
        check(new DurableSimpleTradeNoticeBoard(p).notice(id).quantity() == 28);
        SharedTradeNoticeBoards.unload(p);
        var c = SharedTradeNoticeBoards.get(p);
        check(c != a && c.notice(id).quantity() == 28);
        System.out.println("PASS shared world notice board: live updates and reload");
    }
}
