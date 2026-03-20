package com.pakgopay.demo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;

    public ConfigStore() {
        Path home = Path.of(System.getProperty("user.home"));
        this.configPath = home.resolve(".merchant-demo").resolve("config.json");
    }

    public AppConfig load() {
        try {
            if (!Files.exists(configPath)) {
                return AppConfig.defaults();
            }
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            AppConfig cfg = GSON.fromJson(json, AppConfig.class);
            return cfg == null ? AppConfig.defaults() : cfg;
        } catch (Exception e) {
            return AppConfig.defaults();
        }
    }

    public void save(AppConfig cfg) throws IOException {
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, GSON.toJson(cfg), StandardCharsets.UTF_8);
    }
}
