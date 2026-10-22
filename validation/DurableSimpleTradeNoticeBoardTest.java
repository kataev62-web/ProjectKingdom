import java.nio.file.*;
import java.util.*;
import ru.projectkingdom.npc.*;

public class DurableSimpleTradeNoticeBoardTest {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("kingdom-notices-");
        Path path = dir.resolve("board.bin");
        UUID buy = UUID.randomUUID(), sell = UUID.randomUUID(), owner = UUID.randomUUID();
        var b = new DurableSimpleTradeNoticeBoard(path);
        b.post(new SimpleTradeNoticeBoard.Notice(buy, owner, SimpleTradeNoticeBoard.Side.BUY, "tfc:ore/normal_hematite", 100, 5, "Кузница"));
        b.post(new SimpleTradeNoticeBoard.Notice(sell, owner, SimpleTradeNoticeBoard.Side.SELL, "minecraft:oak_log", 20, 8, "Лавка"));
        try {
            b.post(new SimpleTradeNoticeBoard.Notice(buy, UUID.randomUUID(), SimpleTradeNoticeBoard.Side.BUY,
                    "tfc:ore/normal_hematite", 999, 1, "Подмена"));
            throw new AssertionError("owner change accepted");
        } catch (IllegalArgumentException expected) { }
        check(b.notice(buy).owner().equals(owner));
        check(b.purchases(owner, "tfc:ore/normal_hematite").size() == 1);
        check(b.purchases(owner, "minecraft:oak_log").isEmpty());
        check(b.purchases(UUID.randomUUID(), "tfc:ore/normal_hematite").isEmpty());
        check(!b.confirmedTrade(buy, "minecraft:oak_log", 20));
        check(!b.confirmedTrade(buy, "tfc:ore/normal_hematite", 101));
        check(b.confirmedTrade(buy, "tfc:ore/normal_hematite", 20));
        b = new DurableSimpleTradeNoticeBoard(path);
        check(b.page(SimpleTradeNoticeBoard.Side.BUY, 0, 50).get(0).quantity() == 80);
        check(b.page(SimpleTradeNoticeBoard.Side.SELL, 0, 50).size() == 1);
        check(b.confirmedTrade(buy, "tfc:ore/normal_hematite", 80));
        check(b.page(SimpleTradeNoticeBoard.Side.BUY, 0, 50).isEmpty());
        check(b.remove(sell));
        b = new DurableSimpleTradeNoticeBoard(path);
        check(b.page(SimpleTradeNoticeBoard.Side.SELL, 0, 50).isEmpty());
        Files.write(path, new byte[]{0, 1, 2});
        try { new DurableSimpleTradeNoticeBoard(path); throw new AssertionError("corruption not detected"); }
        catch (java.io.IOException expected) { }
        System.out.println("PASS persisted informational board, ID matching, restart, corruption");
    }
}
