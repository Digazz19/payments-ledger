package ledger.reconciliation;

import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;

public class Reconciliation {

    private static final String MISMATCHES = """
        SELECT b.account_id, b.balance_minor, COALESCE(e.total, 0) AS ledger_total
        FROM account_balances b
        LEFT JOIN (
          SELECT account_id,
                 SUM(CASE direction WHEN 'CREDIT' THEN amount_minor ELSE -amount_minor END) AS total
          FROM ledger_entries
          GROUP BY account_id
        ) e USING (account_id)
        WHERE b.balance_minor <> COALESCE(e.total, 0)
        """;

    private static final String LEDGER_TOTAL = """
        SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount_minor ELSE -amount_minor END), 0)
        FROM ledger_entries
        """;

    private final DSLContext dsl;

    public Reconciliation(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Mismatch> check() {
        return dsl.fetch(MISMATCHES).map(row -> new Mismatch(
            row.get("account_id", UUID.class),
            row.get("balance_minor", Long.class),
            row.get("ledger_total", Long.class)));
    }

    public long ledgerTotal() {
        return dsl.fetchOne(LEDGER_TOTAL).get(0, Long.class);
    }
}