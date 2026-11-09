package ledger.repository;

import static ledger.jooq.Tables.LEDGER_ENTRIES;

import java.util.List;
import java.util.UUID;
import ledger.domain.Page;
import ledger.domain.StatementEntry;
import org.jooq.Condition;
import org.jooq.DSLContext;

public class LedgerEntryRepository {

    private final DSLContext dsl;

    public LedgerEntryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Page<StatementEntry> findByAccount(UUID accountId, Long cursor, int limit) {
        Condition condition = LEDGER_ENTRIES.ACCOUNT_ID.eq(accountId);
        if (cursor != null) {
            condition = condition.and(LEDGER_ENTRIES.ID.lt(cursor));
        }

        List<StatementEntry> rows = dsl.selectFrom(LEDGER_ENTRIES)
            .where(condition)
            .orderBy(LEDGER_ENTRIES.ID.desc())
            .limit(limit + 1)
            .fetch(row -> new StatementEntry(
                row.getId(),
                row.getTransferId(),
                row.getDirection(),
                row.getAmountMinor(),
                row.getCreatedAt()));

        boolean hasMore = rows.size() > limit;
        List<StatementEntry> items = hasMore ? rows.subList(0, limit) : rows;
        Long nextCursor = hasMore ? items.get(items.size() - 1).id() : null;

        return new Page<>(List.copyOf(items), nextCursor);
    }
}