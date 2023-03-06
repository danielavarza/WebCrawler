package com.udacity.webcrawler.profiler;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {

  private final Clock clock;
  private final ProfilingState state = new ProfilingState();
  private final ZonedDateTime startTime;

  @Inject
  ProfilerImpl(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    this.startTime = ZonedDateTime.now(clock);
  }

  // Support from: https://knowledge.udacity.com/questions/551483
  private boolean isClassProfiled(Class<?> c) {
    List<Method> methods = Arrays.stream(c.getDeclaredMethods()).toList();
    if (methods.isEmpty()) {
      return false;
    }

    for (Method method : methods) {
      if (method.getAnnotation(Profiled.class) != null) {
        return true;
      }
    }
    return false;
  }

  // Reference: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html
  @Override
  public <T> T wrap(Class<T> klass, T delegate) {
    Objects.requireNonNull(klass);

    // Throw an exception if there is no profiled method
    if (!isClassProfiled(klass)) {
      throw new IllegalArgumentException("There is no profiled method");
    }

    // Create the handler to return the proxy object
    InvocationHandler handler = new ProfilingMethodInterceptor(clock, delegate, state);
    return (T) Proxy.newProxyInstance(klass.getClassLoader(), new Class[]{klass}, handler);
  }

  @Override
  public void writeData(Path path) throws IOException {
    // Using standard options to avoid checking the existence of the file
      Writer writer = Files.newBufferedWriter(path,
                      StandardOpenOption.CREATE,
                      StandardOpenOption.WRITE,
                      StandardOpenOption.APPEND);
      writeData(writer);
      writer.close();
  }

  @Override
  public void writeData(Writer writer) throws IOException {
    writer.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
    writer.write(System.lineSeparator());
    state.write(writer);
    writer.write(System.lineSeparator());
  }
}
