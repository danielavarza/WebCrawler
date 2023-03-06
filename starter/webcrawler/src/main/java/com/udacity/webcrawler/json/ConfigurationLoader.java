package com.udacity.webcrawler.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A static utility class that loads a JSON configuration file.
 */
public final class ConfigurationLoader {

  private final Path path;

  /**
   * Create a {@link ConfigurationLoader} that loads configuration from the given {@link Path}.
   */
  public ConfigurationLoader(Path path) {
    this.path = Objects.requireNonNull(path);
  }

  /**
   * Loads configuration from this {@link ConfigurationLoader}'s path
   *
   * @return the loaded {@link CrawlerConfiguration}.
   */
  public CrawlerConfiguration load() {
    // Try to load the configuration from the file path
    try (Reader reader = Files.newBufferedReader(path)) {
      return read(reader);
    } catch (IOException exception) {
      exception.printStackTrace();
    }

    // Return null in case of IOException
    return null;
  }

  /**
   * Loads crawler configuration from the given reader.
   *
   * @param reader a Reader pointing to a JSON string that contains crawler configuration.
   * @return a crawler configuration
   */
  public static CrawlerConfiguration read(Reader reader) throws IOException {
    Objects.requireNonNull(reader);

    // Create an instance of ObjectMapper to read the JSON string
    ObjectMapper objectMapper = new ObjectMapper();

    // Because the reader is closed in the load method
    // This prevents for closing before using it.
    objectMapper.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

    return objectMapper.readValue(reader, CrawlerConfiguration.Builder.class).build();
  }
}
