package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final PageParserFactory pageParserFactory;
  private final List<Pattern> ignoredUrls;
  private final int maxDepth;

  @Inject
  ParallelWebCrawler(
      Clock clock,
      @Timeout Duration timeout,
      @PopularWordCount int popularWordCount,
      @TargetParallelism int threadCount,
      PageParserFactory pageParserFactory,
      @IgnoredUrls List<Pattern> ignoredUrls,
      @MaxDepth int maxDepth) {
    this.clock             = clock;
    this.timeout           = timeout;
    this.popularWordCount  = popularWordCount;
    this.pool              = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    this.pageParserFactory = pageParserFactory;
    this.ignoredUrls       = ignoredUrls;
    this.maxDepth          = maxDepth;
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    // Set the timeout
    Instant deadline = clock.instant().plus(timeout);

    // Use the Concurrent collections to be thread safety
    ConcurrentMap<String, Integer> counts     = new ConcurrentSkipListMap<>();
    ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();

    // Invoke the Crawl tasks
    for (String url : startingUrls) {
      pool.invoke(new CrawlTask(maxDepth, deadline, url, counts, visitedUrls));
    }

    // Same in the SequentialWebCrawler, the result shall be sorted out
    if (counts.isEmpty()) {
      return new CrawlResult.Builder()
              .setWordCounts(counts)
              .setUrlsVisited(visitedUrls.size())
              .build();
    }

    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(counts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }

  /**
   * CrawlTasks class
   * There is an upgrade of SequentialWebCrawler to run the tasks in parallel
   */
  private class CrawlTask extends RecursiveAction {
    private final int maxDepth;
    private final Instant deadline;
    private final String url;
    private final ConcurrentMap<String, Integer> counts;
    private final ConcurrentSkipListSet<String> visitedUrls;

    private CrawlTask(int maxDepth, Instant deadline, String url, ConcurrentMap<String, Integer> counts, ConcurrentSkipListSet<String> visitedUrls) {
      this.maxDepth    = maxDepth;
      this.deadline    = deadline;
      this.url         = url;
      this.counts      = counts;
      this.visitedUrls = visitedUrls;
    }

    /**
     * compute() method
     * crawlInternal method variant from SequentialWebCrawler in order to be parallelized
     * Adapted to use the Concurrent collection
     */
    @Override
    protected void compute() {
      if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
        return;
      }
      for (Pattern pattern : ignoredUrls) {
        if (pattern.matcher(url).matches()) {
          return;
        }
      }

      // Combined to be thread-safe as suggested in the review
      if (!visitedUrls.add(url)) {
        return;
      }

      PageParser.Result result = pageParserFactory.get(url).parse();

      // Changed to lambda in order to be thread-safe
      for (ConcurrentMap.Entry<String, Integer> s : result.getWordCounts().entrySet()) {
        counts.compute(s.getKey(), (key, value) -> (value != null) ? s.getValue() + value : s.getValue());
      }

        // Not thread-safe
//      for (ConcurrentMap.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
//        if (counts.containsKey(e.getKey())) {
//          counts.put(e.getKey(), e.getValue() + counts.get(e.getKey()));
//        } else {
//          counts.put(e.getKey(), e.getValue());
//        }
//      }

      // Create a list of crawl tasks and then invoke all to be processed.
      List<CrawlTask> crawlTasks = new ArrayList<>();
      for (String link : result.getLinks()) {
        crawlTasks.add(new CrawlTask(maxDepth - 1, deadline, link, counts, visitedUrls));
      }
      invokeAll(crawlTasks);
    }
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }
}
