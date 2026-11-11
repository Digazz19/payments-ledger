package fraud.dsl

import spock.lang.Specification

class CheckerSpec extends Specification {
    def "aceita regras validas"() {
        expect:
        Checker.check(FraudDsl.parse('''
            RULE a WHEN amount > 1 AND target.country == "PT" THEN BLOCK
            RULE b WHEN source.ageDays < 30 OR velocity(1h) >= 10 THEN REVIEW
            RULE c WHEN NOT source.country != target.country THEN REVIEW
        ''')).isEmpty()
    }

    def "deteta erros semanticos"() {
        when:
        def codes = Checker.check(FraudDsl.parse(source))*.code()

        then:
        codes == [code]

        where:
        source                                                                  | code
        'RULE r WHEN amont > 1 THEN BLOCK'                                      | "UNKNOWN_FIELD"
        'RULE r WHEN source.name == "Ana" THEN BLOCK'                           | "UNKNOWN_FIELD"
        'RULE r WHEN amount == "PT" THEN BLOCK'                                 | "TYPE_MISMATCH"
        'RULE r WHEN source.country > "PT" THEN BLOCK'                          | "INVALID_OPERATOR"
        'RULE r WHEN velocity(2h) > 1 THEN BLOCK'                               | "WINDOW_TOO_LARGE"
        'RULE r WHEN velocity(0s) > 1 THEN BLOCK'                               | "INVALID_WINDOW"
        'RULE r WHEN amount > 1 THEN BLOCK RULE r WHEN amount > 2 THEN BLOCK'   | "DUPLICATE_RULE"
    }

    def "recolhe todos os erros"() {
        when:
        def codes = Checker.check(FraudDsl.parse('''
            RULE a WHEN amont > 1 THEN BLOCK
            RULE b WHEN amount == "PT" THEN REVIEW
        '''))*.code()

        then:
        codes == ["UNKNOWN_FIELD", "TYPE_MISMATCH"]
    }

    def "um campo desconhecido nao gera erros em cascata"() {
        expect:
        Checker.check(FraudDsl.parse('RULE r WHEN amont == "PT" THEN BLOCK'))*.code() == ["UNKNOWN_FIELD"]
    }

    def "indica a posicao e os campos validos"() {
        when:
        def diagnostic = Checker.check(FraudDsl.parse('RULE r\nWHEN amont > 1 THEN BLOCK'))[0]

        then:
        diagnostic.position() == new Position(2, 6)
        diagnostic.message().contains("amont")
        diagnostic.message().contains("amount")
    }
}