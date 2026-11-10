package ledger.http

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class TestClient {
    static class Response {
        int status
        def json
    }

    private final String baseUrl
    private final HttpClient http = HttpClient.newHttpClient()

    TestClient(int port) {
        baseUrl = "http://localhost:$port"
    }

    Response get(String path) {
        send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build())
    }

    Response post(String path, Map body, Map<String, String> headers = [:]) {
        def allHeaders = ["Idempotency-Key": UUID.randomUUID().toString()] + headers
        sendPost(path, body, allHeaders)
    }

    Response postWithoutKey(String path, Map body) {
        sendPost(path, body, [:])
    }

    private Response sendPost(String path, Map body, Map<String, String> headers) {
        def builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(body)))
        headers.each { name, value -> builder.header(name, value) }
        send(builder.build())
    }

    private Response send(HttpRequest request) {
        def response = http.send(request, HttpResponse.BodyHandlers.ofString())
        def json = response.body() ? new JsonSlurper().parseText(response.body()) : null
        new Response(status: response.statusCode(), json: json)
    }
}