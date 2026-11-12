package ledger.fraud;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class HttpFraudClient implements FraudClient {

    private record FraudResponse(String decision, List<String> matchedRules) {
    }

    private final HttpClient http;
    private final URI uri;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public HttpFraudClient(String baseUrl, Duration timeout) {
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.uri = URI.create(baseUrl + "/evaluations");
        this.timeout = timeout;
    }

    @Override
    public FraudVerdict evaluate(FraudRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
                .build();

            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return FraudVerdict.unavailable();
            }

            FraudResponse body = mapper.readValue(response.body(), FraudResponse.class);
            FraudDecision decision = FraudDecision.valueOf(body.decision());
            List<String> rules = body.matchedRules() == null ? List.of() : body.matchedRules();
            return new FraudVerdict(decision, rules);
        } catch (IOException | IllegalArgumentException error) {
            return FraudVerdict.unavailable();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return FraudVerdict.unavailable();
        }
    }
}