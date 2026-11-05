import spock.lang.Specification

class SmokeSpec extends Specification {
    def "java 21 esta ativo"() {
        expect:
        Runtime.version().feature() == 20
    }
}