package com.sojourners.chess.model;

import java.io.Serializable;
import java.util.LinkedHashMap;

public class EngineConfig implements Serializable {

    private static final long serialVersionUID = 1323134234;

    private String name;

    private String path;

    private String protocol;

    private LinkedHashMap<String, String> options;

    public EngineConfig(String name, String path, String protocol, LinkedHashMap<String, String> options) {
        this.name = name;
        this.path = path;
        this.protocol = protocol;
        this.options = options;
    }

    // 新增：设置MultiPV选项
    public void setMultiPV(int value) {
        if (options == null) {
            options = new LinkedHashMap<>();
        }
        options.put("MultiPV", String.valueOf(value));
    }

    // 新增：获取MultiPV值
    public int getMultiPV() {
        if (options != null && options.containsKey("MultiPV")) {
            try {
                return Integer.parseInt(options.get("MultiPV"));
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
    }

    // 新增：检查是否支持MultiPV
    public boolean isMultiPVSupported() {
        return options != null && options.containsKey("MultiPV");
    }

    // 新增：移除MultiPV设置
    public void removeMultiPV() {
        if (options != null) {
            options.remove("MultiPV");
        }
    }

    public LinkedHashMap<String, String> getOptions() {
        return options;
    }

    public void setOptions(LinkedHashMap<String, String> options) {
        this.options = options;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }
}
