package app.filmmerkezi.tv.pluginapi;
public interface PluginHost {
    String request(String payload) throws Exception;
    String resolver(String dependencyId, String operation, String payload) throws Exception;
    String storage(String operation, String key, String value) throws Exception;
    boolean cancelled();
}
