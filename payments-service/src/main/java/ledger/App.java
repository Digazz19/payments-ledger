package ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import ledger.db.Database;
import ledger.http.ApiException;
import ledger.repository.AccountRepository;
import ledger.repository.TransferRepository;
import ledger.service.AccountService;
import ledger.service.CreateAccountRequest;
import ledger.service.DepositRequest;
import ledger.service.DepositService;
import spark.Response;
import spark.Service;

public final class App {

    private final Service service;

    private App(Service service) {
        this.service = service;
    }

    public static App start(Database database, int port) {
        ObjectMapper mapper = new ObjectMapper();
        AccountService accounts = new AccountService(new AccountRepository(database.dsl()));
        DepositService deposits = new DepositService(accounts, new TransferRepository(database.dsl()));
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

        service.post("/deposits", (request, response) -> {
            DepositRequest body = mapper.readValue(request.body(), DepositRequest.class);
            response.status(201);
            return mapper.writeValueAsString(deposits.deposit(body));
        });

        service.exception(ApiException.class, (error, request, response) ->
            writeError(mapper, response, error.status(), error.code(), error.getMessage()));

        service.exception(JsonProcessingException.class, (error, request, response) ->
            writeError(mapper, response, 400, "VALIDATION_ERROR", "Invalid JSON body"));

        service.exception(IllegalArgumentException.class, (error, request, response) ->
            writeError(mapper, response, 400, "VALIDATION_ERROR", error.getMessage()));

        service.init();
        service.awaitInitialization();
        return new App(service);
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