import ru.projectkingdom.npc.TradeRecoveryPolicy;
import static ru.projectkingdom.npc.TradeRecoveryPolicy.*;
public class TradeRecoveryPolicyTest {
    static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
    static Evidence evidence(Observed observed, boolean journal, boolean receipt, boolean barrier,
                             boolean world, boolean pendingClear) {
        return new Evidence(observed,journal,receipt,barrier,world,pendingClear);
    }
    public static void main(String[] args) {
        check(evaluate(evidence(Observed.BEFORE,true,false,false,true,false))==Decision.NO_MUTATION_NEEDED,"before no mutation");
        check(evaluate(evidence(Observed.AFTER,true,true,false,true,true))==Decision.KEEP_LOCKED,"receipt not barrier");
        check(evaluate(evidence(Observed.AFTER,true,true,true,true,true))==Decision.ELIGIBLE_FOR_OPERATOR_REVIEW,"trusted barrier");
        check(evaluate(evidence(Observed.AFTER,true,true,true,false,true))==Decision.KEEP_LOCKED,"foreign world");
        check(evaluate(evidence(Observed.AFTER,false,true,true,true,true))==Decision.KEEP_LOCKED,"damaged journal");
        check(evaluate(evidence(Observed.AFTER,true,true,true,true,false))==Decision.KEEP_LOCKED,"another pending trade");
        check(evaluate(evidence(Observed.MIXED,true,true,true,true,true))==Decision.KEEP_LOCKED,"partial save");
        check(evaluate(evidence(Observed.UNKNOWN,true,true,true,true,true))==Decision.KEEP_LOCKED,"unreadable inventory");
        check(evaluate(evidence(Observed.BEFORE,true,true,true,true,true))==Decision.KEEP_LOCKED,"receipt contradicts before");
        System.out.println("PASS recovery policy: 9 cases");
    }
}
