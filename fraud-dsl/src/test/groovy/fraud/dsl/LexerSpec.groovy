package fraud.dsl

import spock.lang.Specification

class LexerSpec extends Specification {
    def "reconhece os tokens"() {
        when:
        def types = Lexer.tokenize('amount >= 100 AND source.country != "PT"')*.type()*.name()

        then:
        types == ["IDENT", "GTE", "NUMBER", "AND", "IDENT", "DOT", "IDENT", "NEQ", "STRING", "EOF"]
    }

    def "reconhece duracoes e ignora comentarios"() {
        expect:
        Lexer.tokenize("# nota\nvelocity(1m)")*.type()*.name() ==
            ["VELOCITY", "LPAREN", "DURATION", "RPAREN", "EOF"]
    }

    def "reconhece todos os operadores"() {
        expect:
        Lexer.tokenize("> >= < <= == !=")*.type()*.name() ==
            ["GT", "GTE", "LT", "LTE", "EQ", "NEQ", "EOF"]
    }

    def "distingue palavras reservadas de identificadores"() {
        expect:
        Lexer.tokenize("RULE RULEX rule")*.type()*.name() == ["RULE", "IDENT", "IDENT", "EOF"]
    }

    def "guarda o texto de cada token"() {
        when:
        def tokens = Lexer.tokenize('amount 100 "PT" 30s')

        then:
        tokens*.lexeme() == ["amount", "100", "PT", "30s", ""]
    }

    def "indica a linha e a coluna de cada token"() {
        when:
        def token = Lexer.tokenize("RULE a\n  WHEN")[2]

        then:
        token.type() == TokenType.WHEN
        token.line() == 2
        token.column() == 3
    }

    def "indica a posicao do erro"() {
        when:
        Lexer.tokenize("RULE a\nWHEN @")

        then:
        def e = thrown(DslSyntaxException)
        e.line == 2
        e.column == 6
    }

    def "recusa erros lexicos"() {
        when:
        Lexer.tokenize(source)

        then:
        thrown(DslSyntaxException)

        where:
        source << ['"PT', "a = b", "a ! b", "10x"]
    }
}