package ru.projectkingdom.economy;

import java.util.ArrayList;
import java.util.List;

/** Pure atomic planner for ordered lists of physical purses. */
public final class PurseTransaction {
    private PurseTransaction() {}

    public enum Reason { COMMITTED, INVALID_AMOUNT, INSUFFICIENT_VALUE, INSUFFICIENT_CAPACITY, CHANGE_TOO_LARGE }

    public record Result(boolean committed, List<MoneyPurse> purses, Reason reason) {
        public Result {
            purses = List.copyOf(purses);
        }

        public long totalValueCopper() {
            long total = 0;
            for (MoneyPurse purse : purses) total = Math.addExact(total, purse.valueCopper());
            return total;
        }
    }

    public record TransferResult(boolean committed, List<MoneyPurse> sender,
                                 List<MoneyPurse> receiver, Reason reason) {
        public TransferResult {
            sender = List.copyOf(sender);
            receiver = List.copyOf(receiver);
        }
    }

    public static Result debit(List<MoneyPurse> original, long amountCopper) {
        List<MoneyPurse> before = List.copyOf(original);
        if (amountCopper <= 0) return rejected(before, Reason.INVALID_AMOUNT);
        long total;
        try {
            total = totalValue(before);
        } catch (ArithmeticException overflow) {
            return rejected(before, Reason.INSUFFICIENT_VALUE);
        }
        if (total < amountCopper) return rejected(before, Reason.INSUFFICIENT_VALUE);

        ArrayList<MoneyPurse> changed = new ArrayList<>(before);
        long remaining = amountCopper;
        for (int slot = 0; slot < changed.size() && remaining > 0; slot++) {
            MoneyPurse purse = changed.get(slot);
            long taken = Math.min(remaining, purse.valueCopper());
            if (taken == 0) continue;
            MoneyPurse replacement = LegacyPurseMigration.fromCopperValue(purse.valueCopper() - taken);
            if (replacement.legacyOverflow() && !purse.legacyOverflow()) {
                return rejected(before, Reason.CHANGE_TOO_LARGE);
            }
            changed.set(slot, replacement);
            remaining -= taken;
        }
        return new Result(true, changed, Reason.COMMITTED);
    }

    public static Result credit(List<MoneyPurse> original, long amountCopper) {
        List<MoneyPurse> before = List.copyOf(original);
        if (amountCopper <= 0) return rejected(before, Reason.INVALID_AMOUNT);
        ArrayList<MoneyPurse> changed = new ArrayList<>(before);
        long remaining = amountCopper;
        for (MoneyDenomination denomination : List.of(
                MoneyDenomination.GOLD, MoneyDenomination.SILVER, MoneyDenomination.COPPER)) {
            long coins = remaining / denomination.copperValue();
            remaining %= denomination.copperValue();
            for (int slot = 0; slot < changed.size() && coins > 0; slot++) {
                MoneyPurse purse = changed.get(slot);
                long room = purse.legacyOverflow() ? 0 : MoneyPurse.MAX_COINS - purse.coinCount();
                long accepted = Math.min(room, coins);
                if (accepted > 0) {
                    changed.set(slot, purse.add(denomination, accepted));
                    coins -= accepted;
                }
            }
            if (coins > 0) return rejected(before, Reason.INSUFFICIENT_CAPACITY);
        }
        if (remaining != 0) return rejected(before, Reason.INSUFFICIENT_CAPACITY);
        return new Result(true, changed, Reason.COMMITTED);
    }

    public static TransferResult transfer(List<MoneyPurse> sender, List<MoneyPurse> receiver,
                                          long amountCopper) {
        List<MoneyPurse> senderBefore = List.copyOf(sender);
        List<MoneyPurse> receiverBefore = List.copyOf(receiver);
        Result debit = debit(senderBefore, amountCopper);
        if (!debit.committed()) {
            return new TransferResult(false, senderBefore, receiverBefore, debit.reason());
        }
        Result credit = credit(receiverBefore, amountCopper);
        if (!credit.committed()) {
            return new TransferResult(false, senderBefore, receiverBefore, credit.reason());
        }
        return new TransferResult(true, debit.purses(), credit.purses(), Reason.COMMITTED);
    }

    private static Result rejected(List<MoneyPurse> original, Reason reason) {
        return new Result(false, original, reason);
    }

    private static long totalValue(List<MoneyPurse> purses) {
        long result = 0;
        for (MoneyPurse purse : purses) result = Math.addExact(result, purse.valueCopper());
        return result;
    }
}
