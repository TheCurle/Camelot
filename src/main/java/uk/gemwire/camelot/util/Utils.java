package uk.gemwire.camelot.util;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Utils {
    public static <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> futuresList) {
        return CompletableFuture.allOf(futuresList.toArray(CompletableFuture[]::new)).thenApply(v ->
                futuresList.stream()
                        .map(future -> future.getNow(null)) // The future had already been completed, so `getNow` will result the future's value
                        .toList()
        );
    }
}
