package com.mitchej123.jarjar.fml.common;

import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.MetadataCollection;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import org.apache.logging.log4j.Level;

import java.io.InputStream;
import java.io.InputStreamReader;

@SuppressWarnings("unused")
public class MetadataCollectionV2 extends MetadataCollection {
    // Nested Jar files
    protected String[] jars;

    @SuppressWarnings("deprecation")
    public static MetadataCollection from(InputStream inputStream, String sourceName) {
        if (inputStream == null) {
            return new MetadataCollection();
        }

        InputStreamReader reader = new InputStreamReader(inputStream);
        try {
            final MetadataCollectionV2 collection;
            final Gson gson = new GsonBuilder().registerTypeAdapter(ArtifactVersion.class, new ArtifactVersionAdapter()).create();
            final JsonParser parser = new JsonParser();
            final JsonElement rootElement = parser.parse(reader);
            if (rootElement.isJsonArray()) {
                collection = new MetadataCollectionV2();
                final JsonArray jsonList = rootElement.getAsJsonArray();
                collection.modList = new ModMetadata[jsonList.size()];
                collection.jars = new String[0];
                int i = 0;
                for (JsonElement mod : jsonList) {
                    collection.modList[i++] = gson.fromJson(mod, ModMetadata.class);
                }
            } else {
                collection = gson.fromJson(rootElement, MetadataCollectionV2.class);
                if(collection.jars == null) {
                    collection.jars = new String[0];
                }
            }
            collection.parseModMetadataList();
            return collection;
        } catch (JsonParseException e) {
            FMLLog.log(Level.ERROR, e, "The mcmod.info file in %s cannot be parsed as valid JSON. It will be ignored", sourceName);
            return new MetadataCollectionV2();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public String[] getJars() {
        return jars;
    }
}
