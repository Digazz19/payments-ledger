package fraud.service;

public class InvalidRulesException extends RuntimeException {

    public InvalidRulesException(String message) {
        super(message);
    }
}