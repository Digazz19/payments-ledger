package fraud.dsl

import java.time.Duration
import spock.lang.Specification

class EvaluatorSpec extends Specification {
    static final RULES = FraudDsl.parse('''
        RULE high WHEN amount > 1000 THEN REVIEW
        RULE fast WHEN velocity(1m) >= 5 THEN BLOCK
    ''')

    EvaluationContext ctx(long amount, long velocity) {
        new EvaluationContext(amount, "PT", "PT", 100, { window -> velocity })
    }

    def "aplica a decisao mais severa"() {
        when:
        def result = Evaluator.evaluate(RULES, ctx(amount, velocity))

        then:
        result.decision() == decision
        result.matchedRules() == matched

        where:
        amount | velocity | decision          | matched
        100    | 0        | Decision.APPROVE  | []
        1000   | 4        | Decision.APPROVE  | []
        1001   | 0        | Decision.REVIEW   | ["high"]
        100    | 5        | Decision.BLOCK    | ["fast"]
        1001   | 5        | Decision.BLOCK    | ["high", "fast"]
    }

    def "compara texto e aplica NOT e parenteses"() {
        given:
        def rules = FraudDsl.parse('''
            RULE foreign WHEN source.country != target.country THEN REVIEW
            RULE young WHEN NOT (source.ageDays >= 30) THEN REVIEW
        ''')

        expect:
        Evaluator.evaluate(rules, new EvaluationContext(1, source, target, age, { w -> 0L })).matchedRules() == matched

        where:
        source | target | age | matched
        "PT"   | "PT"   | 100 | []
        "PT"   | "ES"   | 100 | ["foreign"]
        "PT"   | "PT"   | 10  | ["young"]
    }

    def "nao consulta velocity quando nao e preciso"() {
        given:
        def calls = []
        def rules = FraudDsl.parse('RULE r WHEN amount > 1000 AND velocity(1m) >= 5 THEN BLOCK')
        def context = new EvaluationContext(100, "PT", "PT", 100, { window -> calls << window; 0L })

        when:
        Evaluator.evaluate(rules, context)

        then:
        calls.isEmpty()
    }

    def "consulta cada janela uma unica vez"() {
        given:
        def calls = []
        def rules = FraudDsl.parse('''
            RULE a WHEN velocity(1m) >= 5 THEN BLOCK
            RULE b WHEN velocity(1m) >= 3 THEN REVIEW
            RULE c WHEN velocity(1h) >= 50 THEN REVIEW
        ''')
        def context = new EvaluationContext(100, "PT", "PT", 100, { window -> calls << window; 0L })

        when:
        Evaluator.evaluate(rules, context)

        then:
        calls == [Duration.ofMinutes(1), Duration.ofHours(1)]
    }
}