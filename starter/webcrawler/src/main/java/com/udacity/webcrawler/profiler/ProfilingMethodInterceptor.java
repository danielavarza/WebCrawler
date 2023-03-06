package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final Object target;
  private final ProfilingState state;

  // TODO: You will need to add more instance fields and constructor arguments to this class.
  ProfilingMethodInterceptor(Clock clock, Object target, ProfilingState state) {
    this.clock = Objects.requireNonNull(clock);
    this.target = Objects.requireNonNull(target);
    this.state = Objects.requireNonNull(state);

  }

  //Support from: https://knowledge.udacity.com/questions/551483
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    Object result;
    Instant start = null;

    if (isMethodProfiled(method)) {
      start = clock.instant();
    }

    try {
      result = method.invoke(target, args);
    } catch (IllegalAccessException ex) {
      throw new RuntimeException(ex);
    } catch (Throwable t) {
      throw t.getCause();
    } finally {
      if (isMethodProfiled(method)) {
        state.record(target.getClass(), method, Duration.between(start, clock.instant()));
      }
    }
    return result;
  }

  private boolean isMethodProfiled(Method method) {
    return method.getAnnotation(Profiled.class) != null;
  }
}
