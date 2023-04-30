package uk.gemwire.camelot.script;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;

public record ScriptMap(Value value) {
    @Nullable
    public String getString(String key) {
        final Value val = value.getMember(key);
        if (val == null) return null;
        return ScriptUtils.toString(val);
    }

    @Nullable
    public Integer getInt(String key) {
        final Value val = value.getMember(key);
        if (val == null) return null;
        return val.asInt();
    }

    public MessageEmbed asEmbed() {
        final EmbedBuilder builder = new EmbedBuilder();
        builder.setDescription(getString("description"));

        final Value title = value.getMember("title");
        if (title != null) {
            if (title.isString()) {
                builder.setTitle(ScriptUtils.toString(title));
            } else {
                final ScriptMap scr = new ScriptMap(title);
                builder.setTitle(scr.getString("value"), scr.getString("url"));
            }
        }

        final Integer colour = getInt("color");
        if (colour != null) {
            builder.setColor(colour);
        }

        return builder.build();
    }
}
