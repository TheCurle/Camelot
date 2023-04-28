package uk.gemwire.camelot.commands.information;

import club.minnced.discord.webhook.external.JDAWebhookClient;
import club.minnced.discord.webhook.send.AllowedMentions;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.common.hash.Hashing;
import com.jagrosh.jdautilities.command.MessageContextMenu;
import com.jagrosh.jdautilities.command.MessageContextMenuEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.EmbedType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GHContent;
import uk.gemwire.camelot.BotMain;
import uk.gemwire.camelot.configuration.Config;
import uk.gemwire.camelot.db.schemas.GithubLocation;
import uk.gemwire.camelot.db.schemas.InfoChannel;
import uk.gemwire.camelot.db.transactionals.InfoChannelsDAO;
import uk.gemwire.camelot.util.Utils;
import uk.gemwire.camelot.util.jda.WebhookCache;
import uk.gemwire.camelot.util.jda.WebhookManager;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class InfoChannelCommand extends SlashCommand {
    public InfoChannelCommand() {
        this.name = "info-channel";
        this.userPermissions = new Permission[] {
                Permission.MANAGE_CHANNEL
        };
        this.children = new SlashCommand[] {
                new Add(),
                new Delete(),
                new GetWebhook()
        };
    }

    @Override
    protected void execute(SlashCommandEvent event) {

    }

    public static final class Delete extends SlashCommand {
        public Delete() {
            this.name = "delete";
            this.help = "Removes this channel's status as an info channel";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final InfoChannel ch = BotMain.jdbi().withExtension(InfoChannelsDAO.class, db -> db.getChannel(event.getChannel().getIdLong()));
            if (ch == null) {
                event.reply("This channel is not an info channel!").setEphemeral(true).queue();
                return;
            }

            BotMain.jdbi().useExtension(InfoChannelsDAO.class, db -> db.delete(event.getChannel().getIdLong()));
            event.reply("The channel is no longer an info channel!").queue();
        }
    }

    public static final class Add extends SlashCommand {
        public Add() {
            this.name = "add";
            this.help = "Makes this channel an info channel";
            this.options = List.of(
                    new OptionData(OptionType.STRING, "repo", "GitHub repository location of the contents", true),
                    new OptionData(OptionType.BOOLEAN, "recreate", "If updates should forcibly resend the channel contents")
            );
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final String repo = event.getOption("repo", "", OptionMapping::getAsString);
            final GithubLocation location;
            try {
                location = GithubLocation.parse(repo);
            } catch (Exception ex) {
                event.reply("Invalid repository location format! The format is: `repository@branch:folder`").setEphemeral(true).queue();
                return;
            }

            final InfoChannel ic = new InfoChannel(event.getChannel().getIdLong(), location, event.getOption("recreate", false, OptionMapping::getAsBoolean), null);
            BotMain.jdbi().useExtension(InfoChannelsDAO.class, db -> db.insert(ic));
            event.reply("Successfully set channel as info channel!")
                    .setEphemeral(true)
                    .delay(5, TimeUnit.SECONDS)
                    .flatMap(InteractionHook::deleteOriginal)
                    .queue();
        }
    }

    public static final class GetWebhook extends SlashCommand {
        public GetWebhook() {
            this.name = "get-webhook";
            this.help = "Gets the webhook URL for this channel";
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            final InfoChannel ch = BotMain.jdbi().withExtension(InfoChannelsDAO.class, db -> db.getChannel(event.getChannel().getIdLong()));
            if (ch == null || !(event.getChannel() instanceof IWebhookContainer web)) {
                event.reply("This channel is not an info channel with webhook support!").setEphemeral(true).queue();
                return;
            }

            event.reply("`" + WEBHOOKS.getWebhook(web).getUrl() + "`").setEphemeral(true).queue();
        }
    }

    public static final class UploadToDiscohookContextMenu extends MessageContextMenu {
        private static final URI SHARE_URL = URI.create("https://share.discohook.app/create");

        public UploadToDiscohookContextMenu() {
            this.name = "Upload to Discohook";
            this.guildOnly = true;
            this.userPermissions = new Permission[] {
                    Permission.MESSAGE_MANAGE
            };
        }

        @Override
        protected void execute(MessageContextMenuEvent event) {
            if (!event.getTarget().isWebhookMessage()) {
                event.reply("Message is not sent by webhook!")
                        .setEphemeral(true).queue();
                return;
            }

            event.deferReply(true).queue();

            final Message message = event.getTarget();
            event.getJDA().retrieveWebhookById(message.getAuthor().getId())
                    .submit()
                    .thenApply(webhook -> {
                        final List<DataObject> embeds = message.getEmbeds()
                                .stream().filter(it -> it.getType() == EmbedType.RICH)
                                .map(MessageEmbed::toData).toList();

                        for (final DataObject obj : embeds) {
                            obj.remove("type");
                            obj.remove("video");
                            obj.remove("provider");
                            Stream.concat(obj.optObject("image").stream(), obj.optObject("thumbnail").stream())
                                    .peek(oo -> oo.remove("width").remove("height").remove("proxy_url")).close();

                            Stream.concat(obj.optObject("footer").stream(), obj.optObject("author").stream())
                                    .peek(oo -> oo.remove("proxy_icon_url")).close();
                        }

                        final DataObject json = DataObject.empty();
                        final DataObject data = DataObject.empty()
                                .put("content", event.getTarget().getContentRaw())
                                .put("embeds", embeds.isEmpty() ? null : DataArray.fromCollection(embeds));
                        json.put("data", data);

                        json.put("reference", message.getJumpUrl());

                        return DataObject.empty()
                                .put("messages", DataArray.fromCollection(List.of(json)))
                                .put("targets", DataArray.empty()
                                        .add(DataObject.empty()
                                                .put("url", webhook.getUrl().replace("/v" + JDAInfo.DISCORD_REST_VERSION, "")))); // Seems like Discohook doesn't like the API version
                    })
                    .thenApply(db -> URLEncoder.encode(Base64.getEncoder().encodeToString(db.toString().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))
                    .thenCompose(base64 -> BotMain.HTTP_CLIENT.sendAsync(HttpRequest.newBuilder()
                            .POST(HttpRequest.BodyPublishers.ofString(DataObject.empty()
                                    .put("url", "https://discohook.app/?data=" + base64)
                                    .put("ttl", -1)
                                    .toString()))
                            .header("Content-Type", "application/json")
                            .uri(SHARE_URL)
                            .build(), HttpResponse.BodyHandlers.ofString()))
                    .thenApply(res -> DataObject.fromJson(res.body()))
                    .thenCompose(response -> event.getHook().sendMessage(new MessageCreateBuilder()
                            .addEmbeds(new EmbedBuilder()
                                    .setTitle("Restored message")
                                    .setDescription("The restored message can be found at %s. This link will expire %s.".formatted(
                                            response.get("url"), TimeFormat.RELATIVE.format(Instant.parse(response.getString("expires")))
                                    ))
                                    .build())
                            .build()).submit());
        }
    }

    public static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
    ).registerModule(new MessagesModule());

    private static final WebhookManager WEBHOOKS = new WebhookManager(
            e -> e.startsWith("Info"),
            "Info",
            AllowedMentions.none(),
            web -> {}
    );

    /**
     * Update the info channels to make sure they're not outdated.
     */
    public static void run() {
        final List<InfoChannel> channels = BotMain.jdbi().withExtension(InfoChannelsDAO.class, InfoChannelsDAO::getChannels);
        for (final InfoChannel ch : channels) {
            if (Config.GITHUB == null) {
                BotMain.LOGGER.error("Info channels are set up, but a GitHub connection is not configured!");
                return;
            }
            if (UPDATING_CHANNELS.contains(ch.channel())) return;

            final MessageChannel messageChannel = BotMain.get().getChannelById(MessageChannel.class, ch.channel());
            if (messageChannel == null) {
                BotMain.jdbi().useExtension(InfoChannelsDAO.class, db -> db.delete(ch.channel()));
                return;
            }

            try {
                final GHContent content = ch.location().resolveAsDirectory(Config.GITHUB, ch.channel() + ".yml");
                try (final var is = content.read()) {
                    final byte[] ct = is.readAllBytes();
                    final String hash = Hashing.sha256()
                            .hashBytes(ct)
                            .toString();

                    if (Objects.equals(hash, ch.hash())) {
                        return;
                    }

                    final List<MessageData> data = MAPPER.readValue(ct, new TypeReference<>() {});
                    if (data.isEmpty()) return;

                    if (ch.forceRecreate()) {
                        messageChannel.getIterableHistory()
                                .takeWhileAsync(e -> true)
                                .whenComplete((msg, t) -> {
                                    if (t != null) {
                                        BotMain.LOGGER.error("Could not update info channel {}:", ch, t);
                                    } else {
                                        CompletableFuture.allOf(messageChannel.purgeMessages(msg).toArray(CompletableFuture[]::new))
                                                .whenComplete((v, $t) -> {
                                                    if ($t != null) {
                                                        BotMain.LOGGER.error("Could not update info channel {}:", ch, $t);
                                                    } else {
                                                        UPDATING_CHANNELS.add(ch.channel());
                                                        final Function<MessageData, CompletableFuture<?>> cfSend = getMessageSender(messageChannel);
                                                        CompletableFuture<?> cf = null;
                                                        for (final MessageData theMessage : data) {
                                                            cf = Utils.whenComplete(cf, () -> cfSend.apply(theMessage));
                                                        }
                                                        cf.whenComplete((o, throwable) -> BotMain.EXECUTOR.schedule(() -> UPDATING_CHANNELS.remove(ch.channel()), 5, TimeUnit.SECONDS)); // Give some wiggle room in case of high latency
                                                        BotMain.jdbi().useExtension(InfoChannelsDAO.class, db -> db.updateHash(ch.channel(), hash));
                                                    }
                                                });
                                    }
                                });
                    } else {
                        messageChannel.getIterableHistory()
                                .takeWhileAsync(e -> true)
                                .whenComplete((msg, t) -> {
                                    if (t != null) {
                                        BotMain.LOGGER.error("Could not update info channel {}:", ch, t);
                                    } else {
                                        final var msgEdit = getMessageEdit(messageChannel);
                                        final var msgSend = getMessageSender(messageChannel);
                                        CompletableFuture<?> cf = null;

                                        msg.removeIf(ms -> {
                                            if (ms.getAuthor().getIdLong() != ms.getJDA().getSelfUser().getIdLong()) {
                                                ms.delete().queue();
                                                return true;
                                            }
                                            return false;
                                        });
                                        UPDATING_CHANNELS.add(ch.channel());

                                        for (final MessageData theMessage : data) {
                                            if (msg.isEmpty()) {
                                                cf = Utils.whenComplete(cf, () -> msgSend.apply(theMessage));
                                            } else {
                                                final Message message = msg.get(msg.size() - 1);
                                                cf = Utils.whenComplete(cf, () -> msgEdit.apply(message.getIdLong(), theMessage));
                                                msg.remove(message);
                                            }
                                        }

                                        if (!msg.isEmpty()) {
                                            RestAction.allOf(msg.stream().map(Message::delete).toList()).queue();
                                        }

                                        cf.whenComplete((o, throwable) -> BotMain.EXECUTOR.schedule(() -> UPDATING_CHANNELS.remove(ch.channel()), 5, TimeUnit.SECONDS)); // Give some wiggle room in case of high latency
                                        BotMain.jdbi().useExtension(InfoChannelsDAO.class, db -> db.updateHash(ch.channel(), hash));
                                    }
                                });
                    }
                }
            } catch (Exception ex) {
                BotMain.LOGGER.error("Could not update info channel {}:", ch, ex);
            }
        }
    }

    private static Function<MessageData, CompletableFuture<?>> getMessageSender(MessageChannel channel) {
        if (channel instanceof IWebhookContainer container) {
            final JDAWebhookClient client = WEBHOOKS.getWebhook(container);
            return messageData -> client.send(new WebhookMessageBuilder()
                    .setContent(messageData.data.getContent())
                    .setAvatarUrl(messageData.avatarUrl)
                    .setUsername(messageData.authorName)
                    .addEmbeds(messageData.data.getEmbeds().stream().map(m -> WebhookEmbedBuilder.fromJDA(m).build()).toList())
                    .build());
        }
        return messageData -> channel.sendMessage(messageData.data).submit();
    }

    private static BiFunction<Long, MessageData, CompletableFuture<?>> getMessageEdit(MessageChannel channel) {
        if (channel instanceof IWebhookContainer container) {
            final JDAWebhookClient client = WEBHOOKS.getWebhook(container);
            return (id, messageData) -> client.edit(id, new WebhookMessageBuilder()
                    .setContent(messageData.data.getContent())
                    .setAvatarUrl(messageData.avatarUrl)
                    .setUsername(messageData.authorName)
                    .addEmbeds(messageData.data.getEmbeds().stream().map(m -> WebhookEmbedBuilder.fromJDA(m).build()).toList())
                    .build());
        }
        return (id, messageData) -> channel.editMessageById(id, MessageEditBuilder.fromCreateData(messageData.data).build()).submit();
    }

    private static final List<Long> UPDATING_CHANNELS = new CopyOnWriteArrayList<>();

    public static final EventListener EVENT_LISTENER = gevent -> {
        final MessageChannel channel;
        if (gevent instanceof MessageReceivedEvent event && event.isFromGuild()) {
            channel = event.getMessage().getChannel();
        } else if (gevent instanceof MessageUpdateEvent event && event.isFromGuild()) {
            channel = event.getMessage().getChannel();
        } else if (gevent instanceof MessageDeleteEvent event && event.isFromGuild()) {
            channel = event.getChannel();
        } else {
            return;
        }

        final InfoChannel infoChannel = BotMain.jdbi().withExtension(InfoChannelsDAO.class, db -> db.getChannel(channel.getIdLong()));
        if (infoChannel == null || Config.GITHUB == null || UPDATING_CHANNELS.contains(infoChannel.channel())) return;
        channel.getIterableHistory()
                .takeWhileAsync(e -> true)
                .whenComplete((msg, t) -> {
                    if (t != null) {
                        BotMain.LOGGER.error("Could not retrieve messages in info channel {}:", infoChannel, t);
                    } else {
                        try {
                            Collections.reverse(msg); // We receive newest to oldest
                            final String dump = MAPPER.writer().writeValueAsString(msg);
                            infoChannel.location().updateInDirectory(
                                    Config.GITHUB, infoChannel.channel() + ".yml",
                                    "Updated info channel content: " + infoChannel.channel(),
                                    dump
                            );
                        } catch (Exception e) {
                            BotMain.LOGGER.error("Could not update messages on GitHub {}:", infoChannel, e);
                        }
                    }
                });
    };

    public static final class MessagesModule extends SimpleModule {

        public MessagesModule() {
            super("MessagesModule");

            final SimpleSerializers serializers = new SimpleSerializers();

            serializers.addSerializer(MessageEmbed.class, new JsonSerializer<>() {
                @Override
                public void serialize(MessageEmbed value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    gen.writeStartObject();

                    if (value.getAuthor() != null) {
                        gen.writeFieldName("author");
                        gen.writeStartObject();
                        ifNotNull("name", value.getAuthor().getName(), gen);
                        ifNotNull("url", value.getAuthor().getUrl(), gen);
                        ifNotNull("icon", value.getAuthor().getIconUrl(), gen);
                        gen.writeEndObject();
                    }

                    ifNotNull("title", value.getTitle(), gen);
                    ifNotNull("description", value.getDescription(), gen);
                    gen.writeFieldName("color");
                    gen.writeString(Utils.rgbToString(value.getColorRaw()));

                    if (!value.getFields().isEmpty()) {
                        gen.writeFieldName("fields");
                        gen.writeStartArray();

                        for (final MessageEmbed.Field field : value.getFields()) {
                            gen.writeStartObject();
                            ifNotNull("name", field.getName(), gen);
                            ifNotNull("value", field.getValue(), gen);
                            gen.writeFieldName("inline");
                            gen.writeBoolean(true);
                            gen.writeEndObject();
                        }

                        gen.writeEndArray();
                    }

                    ifNotNull("thumbnail", value.getThumbnail() == null ? null : value.getThumbnail().getUrl(), gen);
                    ifNotNull("image", value.getImage() == null ? null : value.getImage().getUrl(), gen);

                    if (value.getFooter() != null) {
                        gen.writeFieldName("footer");
                        gen.writeStartObject();
                        ifNotNull("text", value.getFooter().getText(), gen);
                        ifNotNull("icon", value.getFooter().getIconUrl(), gen);
                        gen.writeEndObject();
                    }

                    gen.writeEndObject();
                }
            });
            serializers.addSerializer(Message.class, new JsonSerializer<>() {
                @Override
                public void serialize(Message value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    gen.writeStartObject();

                    ifNotNull("content", value.getContentRaw(), gen);
                    if (value.isWebhookMessage()) {
                        final Webhook webhook = WebhookCache.of(value.getJDA())
                                .retrieveWebhookById(value.getAuthor().getIdLong()).submit(true).join();
                        final Map<String, String> data = new HashMap<>();
                        if (!value.getAuthor().getName().equals(webhook.getDefaultUser().getName()))
                            data.put("name", value.getAuthor().getName());
                        if (!Objects.equals(value.getAuthor().getAvatarUrl(), webhook.getDefaultUser().getAvatarUrl()) && value.getAuthor().getAvatarUrl() != null)
                            data.put("avatar", value.getAuthor().getAvatarUrl());

                        if (!data.isEmpty()) {
                            gen.writeFieldName("author");
                            gen.writeStartObject();

                            for (final var entry : data.entrySet()) {
                                gen.writeFieldName(entry.getKey());
                                gen.writeString(entry.getValue());
                            }

                            gen.writeEndObject();
                        }
                    }

                    final List<MessageEmbed> actualEmbeds = value.getEmbeds().stream().filter(it -> it.getType() == EmbedType.RICH).toList();
                    if (!actualEmbeds.isEmpty()) {
                        gen.writeFieldName("embeds");
                        gen.writeStartArray();

                        for (final MessageEmbed embed : actualEmbeds) {
                            serializers.findValueSerializer(MessageEmbed.class).serialize(embed, gen, serializers);
                        }

                        gen.writeEndArray();
                    }

                    gen.writeEndObject();
                }

            });

            setSerializers(serializers);

            final SimpleDeserializers deserializers = new SimpleDeserializers();

            deserializers.addDeserializer(MessageEmbed.class, new JsonDeserializer<>() {
                @Override
                public MessageEmbed deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
                    return readEmbed(p.readValueAsTree());
                }
            });
            deserializers.addDeserializer(MessageData.class, new JsonDeserializer<>() {
                @Override
                public MessageData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    final JsonNode node = p.readValueAsTree();

                    final MessageCreateBuilder builder = new MessageCreateBuilder();
                    builder.setContent(getString(node, "content"));

                    final JsonNode embeds = node.get("embeds");
                    if (embeds != null) {
                        for (int i = 0; i < embeds.size(); i++) {
                            final JsonNode embed = embeds.get(i);
                            builder.addEmbeds(readEmbed(embed));
                        }
                    }

                    final JsonNode author = Objects.requireNonNullElseGet(node.get("author"), MAPPER::createObjectNode);
                    return new MessageData(builder.build(), getString(author, "name"), getString(author, "avatar"));
                }
            });

            setDeserializers(deserializers);
        }

        private static void ifNotNull(String key, String value, JsonGenerator generator) throws IOException {
            if (value != null && !value.isBlank()) {
                generator.writeFieldName(key);
                generator.writeString(value);
            }
        }

        @Nullable
        private static <T> T get(JsonNode node, String key, Function<JsonNode, T> deserializer) {
            final JsonNode n = node.get(key);
            return n == null ? null : deserializer.apply(n);
        }

        @Nullable
        private static String getString(JsonNode node, String key) {
            final JsonNode n = node.get(key);
            return n == null ? null : n.asText();
        }

        private static MessageEmbed readEmbed(JsonNode node) {
            final EmbedBuilder builder = new EmbedBuilder()
                    .setTitle(getString(node, "title"))
                    .setDescription(getString(node, "description"))
                    .setColor(Objects.requireNonNullElse(get(node, "color", cl ->
                            cl.getNodeType() == JsonNodeType.NUMBER ? cl.asInt() : Integer.parseUnsignedInt(
                                    cl.asText().startsWith("#") ? cl.asText().substring(1) : cl.asText(), 16
                            )), Role.DEFAULT_COLOR_RAW))
                    .setThumbnail(getString(node, "thumbnail"))
                    .setImage(getString(node, "image"));

            final JsonNode author = node.get("author");
            if (author != null) {
                builder.setAuthor(
                        getString(author, "name"),
                        getString(author, "url"),
                        getString(author, "icon")
                );
            }

            final JsonNode footer = node.get("footer");
            if (footer != null) {
                if (footer.getNodeType() == JsonNodeType.STRING) {
                    builder.setFooter(footer.asText());
                } else {
                    builder.setFooter(
                            getString(footer, "text"),
                            getString(node, "icon")
                    );
                }
            }

            final JsonNode fields = node.get("fields");
            if (fields != null) {
                for (int i = 0; i < fields.size(); i++) {
                    final JsonNode field = fields.get(i);
                    builder.addField(
                            field.get("name").asText(),
                            field.get("value").asText(),
                            field.has("inline") && field.get("inline").asBoolean()
                    );
                }
            }

            return builder.build();
        }
    }

    public record MessageData(MessageCreateData data, @Nullable String authorName, @Nullable String avatarUrl) {}
}
