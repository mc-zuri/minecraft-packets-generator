package dev.minecraftdatagenerator.generators;

import com.google.gson.JsonElement;

public interface IDataGenerator {
    String getDataName();

    JsonElement generateDataJson();

    default boolean isEnabled() {
        return true;
    }
}
