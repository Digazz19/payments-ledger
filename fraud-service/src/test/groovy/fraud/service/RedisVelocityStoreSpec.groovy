package fraud.service

import java.time.Duration
import java.time.Instant
import org.testcontainers.containers.GenericContainer
import redis.clients.jedis.Jedis
import spock.lang.Shared
import spock.lang.Specification

class RedisVelocityStoreSpec extends Specification {
    static final GenericContainer redis = new GenericContainer("redis:7").withExposedPorts(6379)

    static {
        redis.start()
    }

    @Shared
    RedisVelocityStore store = new RedisVelocityStore(redis.host, redis.getMappedPort(6379))

    @Shared
    Jedis jedis = new Jedis(redis.host, redis.getMappedPort(6379))

    def cleanupSpec() {
        store?.close()
        jedis?.close()
    }

    Instant now = Instant.parse("2026-11-12T12:00:00Z")

    String account() {
        UUID.randomUUID().toString()
    }

    def "conta apenas dentro da janela"() {
        given:
        def a = account()
        store.record(a, "t1", now.minusSeconds(90))
        store.record(a, "t2", now.minusSeconds(30))
        store.record(a, "t3", now)

        expect:
        store.count(a, Duration.ofMinutes(1), now) == 2
        store.count(a, Duration.ofMinutes(2), now) == 3
    }

    def "o limite inicial da janela fica de fora"() {
        given:
        def a = account()
        store.record(a, "t1", now.minusSeconds(60))
        store.record(a, "t2", now)

        expect:
        store.count(a, Duration.ofMinutes(1), now) == 1
    }

    def "uma repeticao conta uma vez e mantem o instante original"() {
        given:
        def a = account()
        store.record(a, "t1", now.minusSeconds(90))
        store.record(a, "t1", now)

        expect:
        store.count(a, Duration.ofMinutes(2), now) == 1
        store.count(a, Duration.ofMinutes(1), now) == 0
    }

    def "contas diferentes sao independentes"() {
        given:
        def a = account()
        store.record(a, "t1", now)

        expect:
        store.count(account(), Duration.ofMinutes(1), now) == 0
    }

    def "apaga entradas com mais de uma hora"() {
        given:
        def a = account()
        store.record(a, "old", now.minus(Duration.ofHours(2)))
        store.record(a, "new", now)

        expect:
        jedis.zcard("velocity:$a") == 1
    }

    def "define expiracao para a chave nao ficar para sempre"() {
        given:
        def a = account()

        when:
        store.record(a, "t1", now)

        then:
        jedis.ttl("velocity:$a") > 0
    }

    def "lanca um erro proprio quando o Redis nao responde"() {
        given:
        def broken = new RedisVelocityStore("localhost", 1)

        when:
        broken.count(account(), Duration.ofMinutes(1), now)

        then:
        thrown(VelocityUnavailableException)

        cleanup:
        broken.close()
    }
}