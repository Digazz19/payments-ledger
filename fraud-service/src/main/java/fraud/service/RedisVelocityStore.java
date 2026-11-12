package fraud.service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.ZAddParams;

public final class RedisVelocityStore implements VelocityStore, AutoCloseable {

    private static final Duration RETENTION = Duration.ofHours(1);
    private static final long TTL_SECONDS = Duration.ofHours(2).toSeconds();

    private final JedisPool pool;

    public RedisVelocityStore(String host, int port) {
        this.pool = new JedisPool(host, port);
    }

    @Override
    public void record(String accountId, String transferId, Instant at) {
        String key = key(accountId);
        double oldest = at.minus(RETENTION).toEpochMilli();

        run(jedis -> {
            Transaction tx = jedis.multi();
            tx.zadd(key, at.toEpochMilli(), transferId, ZAddParams.zAddParams().nx());
            tx.zremrangeByScore(key, Double.NEGATIVE_INFINITY, oldest);
            tx.expire(key, TTL_SECONDS);
            tx.exec();
            return null;
        });
    }

    @Override
    public long count(String accountId, Duration window, Instant now) {
        String from = "(" + now.minus(window).toEpochMilli();
        String to = String.valueOf(now.toEpochMilli());
        return run(jedis -> jedis.zcount(key(accountId), from, to));
    }

    @Override
    public void close() {
        pool.close();
    }

    private String key(String accountId) {
        return "velocity:" + accountId;
    }

    private <T> T run(Function<Jedis, T> action) {
        try (Jedis jedis = pool.getResource()) {
            return action.apply(jedis);
        } catch (JedisException error) {
            throw new VelocityUnavailableException("Redis is unavailable", error);
        }
    }
}