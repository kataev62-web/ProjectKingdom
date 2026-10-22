import java.util.List;
import ru.projectkingdom.economy.*;

public final class PurseTransactionContractTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        var debit = PurseTransaction.debit(List.of(
                new MoneyPurse(0, 1, 0, false),
                new MoneyPurse(0, 2, 0, false)), 150);
        check(debit.committed(), "debit committed");
        check(debit.purses().get(0).valueCopper() == 0, "first purse used first");
        check(debit.purses().get(1).valueCopper() == 150, "remaining value conserved");
        var full = List.of(new MoneyPurse(1000, 0, 0, false));
        var transfer = PurseTransaction.transfer(
                List.of(new MoneyPurse(0, 1, 0, false)), full, 100);
        check(!transfer.committed(), "receiver capacity rejection");
        check(transfer.sender().get(0).valueCopper() == 100, "sender rollback");
        System.out.println("PASS atomic multi-purse transaction contract");
    }
}
