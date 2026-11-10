package ledger.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import ledger.http.ApiException;
import org.jooq.DSLContext;
import spark.Request;
import spark.Response;

public class IdempotencyHandler {

    @FunctionalInterface
    public interface Completion {
        void complete(DSLContext tx, StoredResponse response);
    }

    @FunctionalInterface
    public interface Action {
        StoredResponse run(Completion completion) throws Exception;
    }

    private final IdempotencyRepository repository;
    private final ObjectMapper mapper;
    private final Duration staleAfter;

    public IdempotencyHandler(IdempotencyRepository repository, ObjectMapper mapper, Duration staleAfter) {
        this.repository = repository;
        this.mapper = mapper;
        this.staleAfter = staleAfter;
    }

    public String handle(Request request, Response response, Action action) throws Exception {
        String key = request.headers("Idempotency-Key");
        if (key == null || key.isBlank() || key.length() > 255) {
            throw new ApiException(400, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
        }

        String hash = sha256(request.requestMethod() + " " + request.pathInfo() + "\n" + request.body());
        UUID token = UUID.randomUUID();

        boolean owned = repository.tryReserve(key, hash, token)
            || repository.tryTakeOver(key, hash, token, staleAfter);

        if (!owned) {
            return replay(key, hash, response);
        }

        try {
            Completion completion = (tx, stored) -> repository.complete(tx, key, token, stored);
            return send(response, action.run(completion));
        } catch (ApiException error) {
            repository.complete(repository.dsl(), key, token, errorResponse(error));
            throw error;
        } catch (Exception error) {
            repository.release(key, token);
            throw error;
        }
    }

    private String replay(String key, String hash, Response response) {
        IdempotencyRecord existing = repository.find(key);

        if (existing != null && !existing.requestHash().equals(hash)) {
            throw new ApiException(422, "IDEMPOTENCY_KEY_REUSED", "Key was used with a different request");
        }
        if (existing == null || "IN_PROGRESS".equals(existing.state())) {
            throw new ApiException(409, "IDEMPOTENCY_IN_PROGRESS", "A request with this key is in progress");
        }
        return send(response, new StoredResponse(existing.responseStatus(), existing.responseBody()));
    }

    private StoredResponse errorResponse(ApiException error) throws Exception {
        String body = mapper.writeValueAsString(Map.of("code", error.code(), "message", error.getMessage()));
        return new StoredResponse(error.status(), body);
    }

    private String send(Response response, StoredResponse stored) {
        response.status(stored.status());
        return stored.body();
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}