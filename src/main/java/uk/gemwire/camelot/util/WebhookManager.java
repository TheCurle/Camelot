package uk.gemwire.camelot.util;

import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.send.AllowedMentions;
import club.minnced.discord.webhook.send.WebhookMessage;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class WebhookManager {
    private static final List<WebhookManager> MANAGERS = new CopyOnWriteArrayList<>();
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static ScheduledExecutorService executor;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                MANAGERS.forEach(WebhookManager::close), "WebhookClosing"));
    }

    private static ScheduledExecutorService getExecutor() {
        if (executor == null) {
            executor = Executors.newScheduledThreadPool(Math.max(MANAGERS.size() / 3, 1), r -> {
                final Thread thread = new Thread(r, "Webhooks");
                thread.setDaemon(true);
                return thread;
            });
            // Clear webhooks after 6 hours to refresh them
            getExecutor().scheduleAtFixedRate(() -> MANAGERS.forEach(it -> it.webhooks.clear()), 1, 6, TimeUnit.HOURS);
        }
        return executor;
    }

    private final Predicate<String> predicate;
    private final String webhookName;
    private final AllowedMentions allowedMentions;
    private final Map<Long, JDAWebhookClient> webhooks = new HashMap<>();
    @Nullable
    private final Consumer<Webhook> creationListener;

    public WebhookManager(final Predicate<String> predicate, final String webhookName, final AllowedMentions allowedMentions, @javax.annotation.Nullable final Consumer<Webhook> creationListener) {
        this.predicate = predicate;
        this.webhookName = webhookName;
        this.allowedMentions = allowedMentions;
        this.creationListener = creationListener;
        MANAGERS.add(this);
    }

    public JDAWebhookClient getWebhook(final IWebhookContainer channel) {
        return webhooks.computeIfAbsent(channel.getIdLong(), k ->
                WebhookClientBuilder.fromJDA(getOrCreateWebhook(channel))
                        .setExecutorService(getExecutor())
                        .setHttpClient(HTTP_CLIENT)
                        .setAllowedMentions(allowedMentions)
                        .buildJDA());
    }

    private Webhook getOrCreateWebhook(IWebhookContainer channel) {
        final var alreadyExisted = unwrap(Objects.requireNonNull(channel).retrieveWebhooks()
                .submit(false))
                .stream()
                .filter(w -> predicate.test(w.getName()))
                .findAny();
        return alreadyExisted.orElseGet(() -> {
            final var webhook = unwrap(channel.createWebhook(webhookName).submit(false));
            if (creationListener != null) {
                creationListener.accept(webhook);
            }
            return webhook;
        });
    }

    private static <T> T unwrap(CompletableFuture<T> completableFuture) {
        try {
            return completableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        webhooks.forEach((id, client) -> client.close());
    }
}
