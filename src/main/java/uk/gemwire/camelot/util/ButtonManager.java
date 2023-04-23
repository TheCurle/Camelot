package uk.gemwire.camelot.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ButtonManager implements EventListener {
    private final Cache<UUID, Consumer<ButtonInteractionEvent>> buttons = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1_000)
            .build();

    @Override
    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof ButtonInteractionEvent event)) return;
        final Button button = event.getButton();
        if (button.getId() == null) return;
        final String[] split = button.getId().split("/");
        try {
            final UUID id = UUID.fromString(split[0]);
            final Consumer<ButtonInteractionEvent> cons = buttons.getIfPresent(id);
            if (cons != null) {
                cons.accept(event);
            }
        } catch (IllegalArgumentException ignored) {

        }
    }

    public UUID newButton(Consumer<ButtonInteractionEvent> consumer) {
        final UUID id = UUID.randomUUID();
        buttons.put(id, consumer);
        return id;
    }

}
