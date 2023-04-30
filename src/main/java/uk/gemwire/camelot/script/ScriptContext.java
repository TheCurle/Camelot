package uk.gemwire.camelot.script;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public record ScriptContext(
        JDA jda, Guild guild, Member member, MessageChannel channel,
        Consumer<MessageCreateData> reply
) {
    private static final Map<Class<?>, ScriptTransformer<?>> TRANSFORMERS = new HashMap<>();

    public static Object transform(ScriptContext context, Object toTransform) {
        return getTransformer(toTransform).transform(context, toTransform);
    }

    @SuppressWarnings("unchecked")
    public static <T> ScriptTransformer<T> getTransformer(T obj) {
        return (ScriptTransformer<T>) TRANSFORMERS.computeIfAbsent(obj.getClass(), k -> {
            if (obj instanceof User) {
                return cast(ScriptContext::createUser);
            } else if (obj instanceof Member) {
                return cast(ScriptContext::createMember);
            } else if (obj instanceof Channel) {
                return cast(ScriptContext::createChannel);
            } else if (obj instanceof Role) {
                return cast(ScriptContext::createRole);
            } else if (obj instanceof Guild) {
                return cast(ScriptContext::createGuild);
            } else if (obj instanceof List<?>) {
                return cast(ScriptContext::transformList);
            }

            return (context, object) -> object;
        });
    }

    public ScriptObject compile() {
        return ScriptObject.of("Script")
                // The context with which the script was executed
                .put("guild", createGuild(guild))
                .put("member", createMember(member))
                .put("channel", createChannel(channel))
                .put("user", createUser(member.getUser()))

                // Methods used for replying
                .putVoidMethod("reply", args -> reply.accept(MessageCreateData.fromContent(args.argString(0, true))))
                .put("console", ScriptObject.of("console")
                        .putVoidMethod("log", args -> reply.accept(MessageCreateData.fromContent(Arrays.stream(args.getArguments())
                                .map(ScriptUtils::toString).collect(Collectors.joining())))))
                .putVoidMethod("replyEmbed", args -> reply.accept(MessageCreateData.fromEmbeds(args.argMap(0, true).asEmbed())));
    }

    public List<Object> transformList(List<?> other) {
        return other.stream().map(o -> transform(this, o)).toList();
    }

    public ScriptObject createUser(User user) {
        return ScriptObject.mentionable("User", user)
                .put("name", user.getName())
                .put("discriminator", user.getDiscriminator())
                .put("avatarUrl", user.getAvatarUrl())
                .putMethod("asTag", args -> user.getAsTag())
                .putMethod("toString", args -> user.getAsTag());
    }

    public ScriptObject createMember(Member member) {
        return ScriptObject.mentionable("Member", member)
                .put("user", createUser(member.getUser()))
                .put("avatarUrl", member.getAvatarUrl())
                .putMethod("toString", args -> member.getUser().getAsTag() + " in " + member.getGuild().getName());
    }

    public ScriptObject createChannel(Channel channel) {
        return ScriptObject.mentionable("Channel", channel)
                .put("name", channel.getName())
                .put("type", channel.getType());
    }

    public ScriptObject createGuild(Guild guild) {
        return ScriptObject.snowflake("Guild", guild)
                .put("name", guild.getName())
                .putMethod("getRoles", args -> transformList(guild.getRoles()));
    }

    public ScriptObject createRole(Role role) {
        return ScriptObject.mentionable("Role", role)
                .put("name", role.getName())
                .putLazyGetter("getGuild", () -> createGuild(role.getGuild()));
    }

    public interface ScriptTransformer<T> {
        Object transform(ScriptContext context, T object);
    }

    @SuppressWarnings("unchecked")
    private static <F, T> ScriptTransformer<T> cast(ScriptTransformer<F> transformer) {
        return (ScriptTransformer<T>) transformer;
    }
}
