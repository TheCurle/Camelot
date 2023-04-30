package uk.gemwire.camelot.script;

import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public interface InvocationArguments {
    Value[] getArguments();

    @Nullable
    @Contract("_, true -> !null")
    default String argString(int index, boolean required) {
        if (index >= getArguments().length) {
            if (required) {
                throw createException("Missing argument at position " + index);
            }
            return null;
        }
        return ScriptUtils.toString(getArguments()[index]);
    }

    @Nullable
    @Contract("_, true -> !null")
    default ScriptMap argMap(int index, boolean required) {
        if (index >= getArguments().length) {
            if (required) {
                throw createException("Missing argument at position " + index);
            }
            return null;
        }
        return new ScriptMap(getArguments()[index]);
    }

    RuntimeException createException(String message);
}
