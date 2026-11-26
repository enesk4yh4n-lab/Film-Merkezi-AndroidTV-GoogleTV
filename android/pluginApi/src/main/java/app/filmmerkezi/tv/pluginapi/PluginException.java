package app.filmmerkezi.tv.pluginapi;

/** A stable wire error; never pass an app implementation exception across the SDK boundary. */
public final class PluginException extends Exception {
    public final String kind;
    public final String stage;
    public final int httpCode;
    public PluginException(String kind, String message, String stage, int httpCode) {
        super(message);
        this.kind = kind;
        this.stage = stage;
        this.httpCode = httpCode;
    }
}
