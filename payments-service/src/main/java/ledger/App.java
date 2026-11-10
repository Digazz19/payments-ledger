package ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import ledger.db.Database;
import ledger.domain.Transfer;
import ledger.http.ApiException;
import ledger.http.HttpMapping;
import ledger.repository.AccountRepository;
import ledger.repository.TransferRepository;
import ledger.repository.LedgerEntryRepository;
import ledger.service.AccountService;
import ledger.service.CreateAccountRequest;
import ledger.service.DepositRequest;
import ledger.service.DepositService;
import ledger.service.TransferRequest;
import ledger.service.TransferService;
import ledger.service.StatementService;
import ledger.idempotency.IdempotencyHandler;
import ledger.idempotency.IdempotencyRepository;
import ledger.idempotency.StoredResponse;
import spark.Response;
import spark.Service;

public final class App {

    private final Service service;

    private App(Service service) {
        this.service = service;
    }

    public static App start(Database database, int port) {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        TransferRepository transferRepository = new TransferRepository(database.dsl());
        AccountService accounts = new AccountService(new AccountRepository(database.dsl()));
        DepositService deposits = new DepositService(accounts, transferRepository);
        TransferService transfers = new TransferService(accounts, transferRepository);
        StatementService statements = new StatementService(accounts, new LedgerEntryRepository(database.dsl()));
        IdempotencyRepository idempotencyRepository = new IdempotencyRepository(database.dsl());
        IdempotencyHandler idempotency = new IdempotencyHandler(
            new IdempotencyRepository(database.dsl()), mapper, Duration.ofSeconds(60));
        Service service = Service.ignite().port(port);

        service.before((request, response) -> response.type("application/json"));

        service.get("/health", (request, response) -> "{\"status\":\"ok\"}");

        service.post("/accounts", (request, response) -> {
            CreateAccountRequest body = mapper.readValue(request.body(), CreateAccountRequest.class);
            response.status(201);
            return mapper.writeValueAsString(accounts.create(body));
        });

        service.get("/accounts/:id", (request, response) -> {
            UUID id = UUID.fromString(request.params("id"));
            return mapper.writeValueAsString(accounts.get(id));
        });

        service.get("/accounts/:id/entries", (request, response) -> {
            UUID id = UUID.fromString(request.params("id"));
            return mapper.writeValueAsString(
                statements.entries(id, request.queryParams("limit"), request.queryParams("cursor")));
        });

        service.post("/deposits", (request, response) -> idempotency.handle(request, response, completion -> {
            DepositRequest body = mapper.readValue(request.body(), DepositRequest.class);
            Transfer transfer = deposits.deposit(body, (tx, saved) ->
                completion.complete(tx, stored(mapper, 201, saved)));
            return stored(mapper, 201, transfer);
        }));

        service.post("/transfers", (request, response) -> idempotency.handle(request, response, completion -> {
            TransferRequest body = mapper.readValue(request.body(), TransferRequest.class);
            Transfer transfer = transfers.transfer(body, (tx, saved) ->
                completion.complete(tx, stored(mapper, HttpMapping.statusFor(saved.outcome()), saved)));
            return stored(mapper, HttpMapping.statusFor(transfer.outcome()), transfer);
        }));

        service.get("/transfers/:id", (request, response) -> {
            UUID id = UUID.fromString(request.params("id"));
            return mapper.writeValueAsString(transfers.get(id));
        });

        service.exception(ApiException.class, (error, request, response) ->
            writeError(mapper, response, error.status(), error.code(), error.getMessage()));

        service.exception(JsonProcessingException.class, (error, request, response) ->
            writeError(mapper, response, 400, "VALIDATION_ERROR", "Invalid JSON body"));

        service.exception(IllegalArgumentException.class, (error, request, response) ->
            writeError(mapper, response, 400, "VALIDATION_ERROR", error.getMessage()));

        service.exception(IllegalArgumentException.class, (error, request, response) ->
            writeError(mapper, response, 400, "VALIDATION_ERROR", error.getMessage()));

        service.exception(Exception.class, (error, request, response) ->
            writeError(mapper, response, 500, "INTERNAL_ERROR", "Unexpected error"));

        service.init();
        service.awaitInitialization();
        return new App(service);
    }

    private static StoredResponse stored(ObjectMapper mapper, int status, Object body) {
        try {
            return new StoredResponse(status, mapper.writeValueAsString(body));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void writeError(ObjectMapper mapper, Response response, int status, String code, String message) {
        try {
            response.status(status);
            response.type("application/json");
            response.body(mapper.writeValueAsString(Map.of("code", code, "message", message)));
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