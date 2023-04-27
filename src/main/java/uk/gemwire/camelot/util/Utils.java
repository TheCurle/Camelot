package uk.gemwire.camelot.util;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * General utility methods.
 */
public class Utils {
    /**
     * Returns a new CompletableFuture that is completed when all of
     * the given CompletableFutures complete.  If any of the given
     * CompletableFutures complete exceptionally, then the returned
     * CompletableFuture also does so, with a CompletionException
     * holding this exception as its cause.  Otherwise, the results,
     * if any, of the given CompletableFutures are not reflected in
     * the returned CompletableFuture, but may be obtained by
     * inspecting them individually. If no CompletableFutures are
     * provided, returns a CompletableFuture completed with the value
     * {@code null}.
     *
     * <p>Unlike {@link CompletableFuture#allOf(CompletableFuture[])}, this future does not return {@link Void},
     * but the values of all completed futures.</p>
     *
     * @param futuresList the CompletableFutures
     * @return a new CompletableFuture that is completed when all of the
     * given CompletableFutures complete
     * @throws NullPointerException if the array or any of its elements are
     *                              {@code null}
     */
    public static <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> futuresList) {
        return CompletableFuture.allOf(futuresList.toArray(CompletableFuture[]::new)).thenApply(v ->
                futuresList.stream()
                        .map(future -> future.getNow(null)) // The future had already been completed, so `getNow` will result the future's value
                        .toList()
        );
    }

    public static CompletableFuture<?> whenComplete(@Nullable CompletableFuture<?> existing, Supplier<CompletableFuture<?>> cf) {
        if (existing == null) return cf.get();
        final CompletableFuture<?> newCf = new CompletableFuture<>();
        existing.whenComplete((o, throwable) -> {
            if (throwable == null) {
                cf.get().whenComplete((o1, throwable1) -> {
                    if (throwable1 == null) {
                        newCf.complete(null);
                    } else {
                        newCf.completeExceptionally(throwable1);
                    }
                });
            } else {
                newCf.completeExceptionally(throwable);
            }
        });
        return newCf;
    }
}
