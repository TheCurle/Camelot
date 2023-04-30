package uk.gemwire.camelot.script;

public class RequestedHelpException extends RuntimeException {
    public static final String MESSAGE = "Help was requested!";
    public RequestedHelpException() {
        super(MESSAGE);
    }
}
