package ledger.idempotency;

import static ledger.jooq.Tables.IDEMPOTENCY_KEYS;

import org.jooq.DSLContext;

public class IdempotencyRepository {

    private final DSLContext dsl;

    public IdempotencyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean tryReserve(String key, String requestHash) {
        return dsl.insertInto(IDEMPOTENCY_KEYS)
            .set(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY, key)
            .set(IDEMPOTENCY_KEYS.REQUEST_HASH, requestHash)
            .set(IDEMPOTENCY_KEYS.STATE, "IN_PROGRESS")
            .onConflictDoNothing()
            .execute() == 1;
    }

    public IdempotencyRecord find(String key) {
        return dsl.selectFrom(IDEMPOTENCY_KEYS)
            .where(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(key))
            .fetchOne(row -> new IdempotencyRecord(
                row.getRequestHash(),
                row.getState(),
                row.getResponseStatus(),
                row.getResponseBody()));
    }

    public void complete(DSLContext tx, String key, StoredResponse response) {
        tx.update(IDEMPOTENCY_KEYS)
            .set(IDEMPOTENCY_KEYS.STATE, "DONE")
            .set(IDEMPOTENCY_KEYS.RESPONSE_STATUS, response.status())
            .set(IDEMPOTENCY_KEYS.RESPONSE_BODY, response.body())
            .where(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(key))
            .execute();
    }

    public void complete(String key, StoredResponse response) {
        complete(dsl, key, response);
    }

    public void release(String key) {
        dsl.deleteFrom(IDEMPOTENCY_KEYS)
            .where(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(key))
            .and(IDEMPOTENCY_KEYS.STATE.eq("IN_PROGRESS"))
            .execute();
    }
}