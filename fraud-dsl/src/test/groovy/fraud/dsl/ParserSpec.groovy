package fraud.dsl

import java.time.Duration
import spock.lang.Specification

class ParserSpec extends Specification {
    def "le uma regra completa"() {
        when:
        def rule = FraudDsl.parse('RULE r WHEN amount > 10 THEN BLOCK').rules()[0]

        then:
        rule.name() == "r"
        rule.action() == Action.BLOCK
        rule.condition() instanceof Expr.Comparison
    }

    def "AND tem precedencia sobre OR"() {
        when:
        def expr = FraudDsl.parse('RULE r WHEN amount > 1 OR amount > 2 AND amount > 3 THEN BLOCK')
            .rules()[0].condition()

        then:
        expr instanceof Expr.Or
        expr.right() instanceof Expr.And
    }

    def "os parenteses mudam a precedencia"() {
        expect:
        FraudDsl.parse('RULE r WHEN (amount > 1 OR amount > 2) AND amount > 3 THEN BLOCK')
            .rules()[0].condition() instanceof Expr.And
    }

    def "NOT aplica-se a expressao seguinte"() {
        when:
        def expr = FraudDsl.parse('RULE r WHEN NOT amount > 1 THEN REVIEW').rules()[0].condition()

        then:
        expr instanceof Expr.Not
        expr.operand() instanceof Expr.Comparison
    }

    def "le campos com varios niveis"() {
        when:
        def comparison = FraudDsl.parse('RULE r WHEN source.country != target.country THEN REVIEW')
            .rules()[0].condition()

        then:
        comparison.left().path() == ["source", "country"]
        comparison.op() == CompareOp.NEQ
        comparison.right().path() == ["target", "country"]
    }

    def "converte a duracao de velocity"() {
        when:
        def comparison = FraudDsl.parse('RULE r WHEN velocity(2m) >= 5 THEN BLOCK').rules()[0].condition()

        then:
        comparison.left().window() == Duration.ofMinutes(2)
        comparison.right().value() == 5
    }

    def "le varias regras"() {
        expect:
        FraudDsl.parse('''
            RULE a WHEN amount > 1 THEN REVIEW
            RULE b WHEN amount > 2 THEN BLOCK
        ''').rules()*.name() == ["a", "b"]
    }

    def "indica o que esperava e onde"() {
        when:
        FraudDsl.parse('RULE r WHEN (amount > 1 THEN BLOCK')

        then:
        def e = thrown(DslSyntaxException)
        e.message.contains("Expected ')'")
        e.message.contains("found 'THEN'")
        e.line == 1
        e.column == 25
    }

    def "recusa programas invalidos"() {
        when:
        FraudDsl.parse(source)

        then:
        thrown(DslSyntaxException)

        where:
        source << [
            'RULE WHEN amount > 1 THEN BLOCK',
            'RULE r WHEN amount > THEN BLOCK',
            'RULE r WHEN amount > 1 THEN APPROVE',
            'RULE r WHEN (amount > 1 THEN BLOCK',
            'RULE r WHEN amount > 1',
            'RULE r WHEN amount THEN BLOCK',
            'RULE r WHEN velocity(5) > 1 THEN BLOCK'
        ]
    }
}