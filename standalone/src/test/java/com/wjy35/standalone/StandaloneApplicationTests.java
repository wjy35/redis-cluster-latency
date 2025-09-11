package com.wjy35.standalone;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class StandaloneApplicationTests {
  private static final Logger log = LoggerFactory.getLogger(StandaloneApplicationTests.class);

  /**
   * Warm up Properties
   */
  private static final int WARM_UP_ITERATION = 10;

  /**
   * Measure Properties
   */
  private static final int MEASURE_ITERATION = 100;
  private static final int THREAD_COUNT = 3;

  @Autowired
  RedisTemplate<String,String> template;

  @BeforeEach
  void warmUp(){
    log.info("Warm up iteration {}", WARM_UP_ITERATION);

    for(int i=0; i<WARM_UP_ITERATION; i++){
      template.opsForValue().get("key");
    }

    log.info("Warm up end");
  }

  @Test
  void measure(){
    log.info("Measure iteration {}", MEASURE_ITERATION);
    log.info("Measure thread count {}", THREAD_COUNT);

    List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
    LongAdder latencyAdder = new LongAdder();
    Runnable task = ()->{
      long start = System.currentTimeMillis();
      template.opsForValue().get("key");
      long duration = System.currentTimeMillis() - start;

      latencies.add(duration);
      latencyAdder.add(duration);
    };

    executeOnMultiThread(task);

    Collections.sort(latencies);

    log.info("Average Latency : {} ms", latencyAdder.sum() / MEASURE_ITERATION);
    log.info("p50 : {} ms", latencies.get(MEASURE_ITERATION * 50 / 100));
    log.info("p90 : {} ms", latencies.get(MEASURE_ITERATION * 90 / 100));
    log.info("p95 : {} ms", latencies.get(MEASURE_ITERATION * 95 / 100));
    log.info("p99 : {} ms", latencies.get(MEASURE_ITERATION * 99 / 100));
  }

  private void executeOnMultiThread(Runnable task){
    try {
      tryToExecuteOnMultiThread(task);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void tryToExecuteOnMultiThread(Runnable task) throws InterruptedException {
    ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);

    for (int i = 0; i<MEASURE_ITERATION; i++) {
      pool.execute(task);
    }
    pool.shutdown();
    pool.awaitTermination(10, TimeUnit.MINUTES);
  }

  @Test
  void contextLoads() {
    assertNotNull(template);
  }

  @Test
  void opsForValue(){
    // given
    String key = "key";
    String value = "value";

    // when
    template.opsForValue().set(key,value);
    String actual = template.opsForValue().get(key);

    // then
    assertEquals(value,actual);
  }
}
