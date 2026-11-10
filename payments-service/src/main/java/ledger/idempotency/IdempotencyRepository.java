package ledger.idempotency;

import static ledger.jooq.Tables.IDEMPOTENCY_KEYS;

import java.time.Duration;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class IdempotencyRepository {

    private final DSLContext dsl;

    public IdempotencyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public DSLContext dsl() {
        return dsl;
    }

    public boolean tryReserve(String key, String requestHash, UUID token) {
        return dsl.insertInto(IDEMPOTENCY_KEYS)
            .set(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY, key)
            .set(IDEMPOTENCY_KEYS.REQUEST_HASH, requestHash)
            .set(IDEMPOTENCY_KEYS.STATE, "IN_PROGRESS")
            .set(IDEMPOTENCY_KEYS.LOCK_TOKEN, token)
            .onConflictDoNothing()
            .execute() == 1;
    }

    public boolean tryTakeOver(String key, String requestHash, UUID token, Duration staleAfter) {
        return dsl.update(IDEMPOTENCY_KEYS)
            .set(IDEMPOTENCY_KEYS.LOCK_TOKEN, token)
            .set(IDEMPOTENCY_KEYS.LOCKED_AT, DSL.currentOffsetDateTime())
            .where(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(key))
            .and(IDEMPOTENCY_KEYS.STATE.eq("IN_PROGRESS"))
            .and(IDEMPOTENCY_KEYS.REQUEST_HASH.eq(requestHash))
            .and(DSL.condition("locked_at < now() - make_interval(secs => ?)", (double) staleAfter.toSeconds()))
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

    public void complete(DSLContext tx, String key, UUID token, StoredResponse response) {
        int updated = tx.update(IDEMPOTENCY_KEYS)
            .set(IDEMPOTENCY_KEYS.STATE, "DONE")
            .set(IDEMPOTENCY_KEYS.RESPONSE_STATUS, response.status())
            .set(IDEMPOTENCY_KEYS.RESPONSE_BODY, response.body())
            .where(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(key))
            .and(IDEMPOTENCY_KEYS.LOCK_TOKEN.eq(token))
            .and(IDEMPOTENCY_KEYS.STATE.eq("IN_PROGRESS"))
            .execute();

        if (updated == 0) {
            throw new IllegalStateException("Lost ownership of idempotency key " + key);
        }
    }

    public void release(String key, UUID token) {
        dsl.deleteFrom(IDEMPOTENCY_KEYS)
            .where(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(key))
            .and(IDEMPOTENCY_KEYS.LOCK_TOKEN.eq(token))
            .and(IDEMPOTENCY_KEYS.STATE.eq("IN_PROGRESS"))
            .execute();
    }
}