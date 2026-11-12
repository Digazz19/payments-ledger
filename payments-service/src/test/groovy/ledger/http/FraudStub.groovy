package ledger.http

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spark.Route
import spark.Service

class FraudStub {
    private final Service service = Service.ignite().port(0)

    volatile String decision = "APPROVE"
    volatile List<String> rules = []
    volatile long delayMs = 0
    volatile Integer failStatus = null
    volatile Closure onRequest = null

    final List<Map> requests = Collections.synchronizedList([])

    FraudStub() {
        service.post("/evaluations", { request, response ->
            requests << new JsonSlurper().parseText(request.body())
            onRequest?.call()
            if (delayMs > 0) {
                Thread.sleep(delayMs)
            }
            response.type("application/json")
            if (failStatus) {
                response.status(failStatus)
                return '{"message":"down"}'
            }
            JsonOutput.toJson([decision: decision, matchedRules: rules])
        } as Route)
        service.init()
        service.awaitInitialization()
    }

    String url() {
        "http://localhost:${service.port()}"
    }

    void respond(String decision, List<String> rules) {
        this.decision = decision
        this.rules = rules
    }

    void delay(long ms) {
        this.delayMs = ms
    }

    void failWith(int status) {
        this.failStatus = status
    }

    void reset() {
        decision = "APPROVE"
        rules = []
        delayMs = 0
        failStatus = null
        onRequest = null
        requests.clear()
    }

    void stop() {
        service.stop()
        service.awaitStop()
    }
}