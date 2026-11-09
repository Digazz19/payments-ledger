package ledger.domain;

import java.util.List;

public record Page<T>(List<T> items, Long nextCursor) {
}