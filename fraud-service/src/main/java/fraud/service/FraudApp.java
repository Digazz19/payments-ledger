package fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fraud.dsl.Checker;
import fraud.dsl.Diagnostic;
import fraud.dsl.DslSyntaxException;
import fraud.dsl.EvaluationContext;
import fraud.dsl.Evaluator;
import fraud.dsl.FraudDsl;
import fraud.dsl.RuleSet;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import spark.Response;
import spark.Service;

public final class FraudApp {

    private final Service service;

    private FraudApp(Service service) {
        this.service = service;
    }

    public static FraudApp start(String rulesSource, VelocityStore velocity, Clock clock, int port) {
        RuleSet rules = compile(rulesSource);

        ObjectMapper mapper = new ObjectMapper();
        Service service = Service.ignite().port(port);

        service.before((request, response) -> response.type("application/json"));

        service.get("/health", (request, response) -> "{\"status\":\"ok\"}");

        service.post("/evaluations", (request, response) -> {
            EvaluationRequest body = mapper.readValue(request.body(), EvaluationRequest.class);
            validate(body);

            Instant now = clock.instant();
            velocity.record(body.sourceAccountId(), body.transferId(), now);

            EvaluationContext context = new EvaluationContext(
                body.amountMinor(),
                body.sourceCountry(),
                body.targetCountry(),
                body.sourceAgeDays(),
                window -> velocity.count(body.sourceAccountId(), window, now));

            return mapper.writeValueAsString(Evaluator.evaluate(rules, context));
        });

        service.exception(BadRequestException.class, (error, request, response) ->
            writeError(mapper, response, 400, error.getMessage()));

        service.exception(JsonProcessingException.class, (error, request, response) ->
            writeError(mapper, response, 400, "Invalid JSON body"));

        service.exception(VelocityUnavailableException.class, (error, request, response) ->
            writeError(mapper, response, 503, "Velocity data is unavailable"));

        service.exception(Exception.class, (error, request, response) ->
            writeError(mapper, response, 500, "Unexpected error"));

        service.init();
        service.awaitInitialization();
        return new FraudApp(service);
    }

    static RuleSet compile(String source) {
        RuleSet rules;
        try {
            rules = FraudDsl.parse(source);
        } catch (DslSyntaxException error) {
            throw new InvalidRulesException("Syntax error: " + error.getMessage());
        }

        List<Diagnostic> diagnostics = Checker.check(rules);
        if (!diagnostics.isEmpty()) {
            String report = diagnostics.stream()
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n"));
            throw new InvalidRulesException("Invalid rules:\n" + report);
        }
        return rules;
    }

    private static void validate(EvaluationRequest body) {
        if (body.transferId() == null || body.sourceAccountId() == null
                || body.sourceCountry() == null || body.targetCountry() == null
                || body.sourceAgeDays() == null || body.amountMinor() == null) {
            throw new BadRequestException("All fields are required");
        }
        if (body.amountMinor() <= 0) {
            throw new BadRequestException("amountMinor must be positive");
        }
    }

    private static void writeError(ObjectMapper mapper, Response response, int status, String message) {
        try {
            response.status(status);
            response.type("application/json");
            response.body(mapper.writeValueAsString(Map.of("message", message)));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }

    public int port() {
        return service.port();
    }

    public void stop() {
        service.stop();
        service.awaitStop();
    }
}