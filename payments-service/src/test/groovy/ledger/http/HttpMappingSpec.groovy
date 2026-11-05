package ledger.http

import ledger.domain.TransferOutcome.Completed
import ledger.domain.TransferOutcome.InReview
import ledger.domain.TransferOutcome.Rejected
import spock.lang.Specification

class HttpMappingSpec extends Specification {
    def "mapeia cada resultado"() {
        expect:
        HttpMapping.statusFor(outcome) == status

        where:
        outcome                            | status
        new Completed()                    | 201
        new Rejected("INSUFFICIENT_FUNDS") | 422
        new InReview("RULE_HIGH_VALUE")    | 202
    }
}