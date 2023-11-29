/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.query.groupby.epinephelinae;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.druid.collections.BlockingPool;
import org.apache.druid.collections.ReferenceCountingResourceHolder;
import org.apache.druid.collections.Releaser;
import org.apache.druid.common.guava.GuavaUtils;
import org.apache.druid.java.util.common.ISE;
import org.apache.druid.java.util.common.Pair;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.java.util.common.guava.Accumulator;
import org.apache.druid.java.util.common.guava.BaseSequence;
import org.apache.druid.java.util.common.guava.Sequence;
import org.apache.druid.java.util.common.io.Closer;
import org.apache.druid.java.util.common.logger.Logger;
import org.apache.druid.query.AbstractPrioritizedQueryRunnerCallable;
import org.apache.druid.query.ChainedExecutionQueryRunner;
import org.apache.druid.query.QueryContexts;
import org.apache.druid.query.QueryInterruptedException;
import org.apache.druid.query.QueryPlus;
import org.apache.druid.query.QueryProcessingPool;
import org.apache.druid.query.QueryRunner;
import org.apache.druid.query.QueryTimeoutException;
import org.apache.druid.query.QueryWatcher;
import org.apache.druid.query.ResourceLimitExceededException;
import org.apache.druid.query.context.ResponseContext;
import org.apache.druid.query.groupby.GroupByQuery;
import org.apache.druid.query.groupby.GroupByQueryConfig;
import org.apache.druid.query.groupby.ResultRow;
import org.apache.druid.query.groupby.epinephelinae.RowBasedGrouperHelper.RowBasedKey;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Class that knows how to merge a collection of groupBy {@link QueryRunner} objects, called {@code queryables},
 * using a buffer provided by {@code mergeBufferPool} and a parallel executor provided by {@code exec}. Outputs a
 * fully aggregated stream of {@link ResultRow} objects. Does not apply post-aggregators.
 *
 * The input {@code queryables} are expected to come from a {@link GroupByQueryEngineV2}. This code runs on data
 * servers, like Historicals.
 *
 * This class has some resemblance to {@link GroupByRowProcessor}. See the javadoc of that class for a discussion of
 * similarities and differences.
 *
 * Used by
 * {@link org.apache.druid.query.groupby.strategy.GroupByStrategyV2#mergeRunners(QueryProcessingPool, Iterable)}
 */
public class GroupByMergingQueryRunnerV2 implements QueryRunner<ResultRow>
{
  private static final Logger log = new Logger(GroupByMergingQueryRunnerV2.class);
  private static final String CTX_KEY_MERGE_RUNNERS_USING_CHAINED_EXECUTION = "mergeRunnersUsingChainedExecution";

  private final GroupByQueryConfig config;
  private final Iterable<QueryRunner<ResultRow>> queryables;
  private final QueryProcessingPool queryProcessingPool;
  private final QueryWatcher queryWatcher;
  private final int concurrencyHint;
  private final BlockingPool<ByteBuffer> mergeBufferPool;
  private final ObjectMapper spillMapper;
  private final String processingTmpDir;
  private final int mergeBufferSize;

  public GroupByMergingQueryRunnerV2(
      GroupByQueryConfig config,
      QueryProcessingPool queryProcessingPool,
      QueryWatcher queryWatcher,
      Iterable<QueryRunner<ResultRow>> queryables,
      int concurrencyHint,
      BlockingPool<ByteBuffer> mergeBufferPool,
      int mergeBufferSize,
      ObjectMapper spillMapper,
      String processingTmpDir
  )
  {
    this.config = config;
    this.queryProcessingPool = queryProcessingPool;
    this.queryWatcher = queryWatcher;
    this.queryables = Iterables.unmodifiableIterable(Iterables.filter(queryables, Predicates.notNull()));
    // info: processingConfig.getNumThreads()，受制于 druid.processing.numThreads 参数，默认是 (cpuCores - 1)，显然大于1
    this.concurrencyHint = concurrencyHint;
    // info: 默认是  Math.max(2, druid.processing.numThreads / 4)，受 druid.processing.numMergeBuffers 参数限制
    this.mergeBufferPool = mergeBufferPool;
    this.spillMapper = spillMapper;
    this.processingTmpDir = processingTmpDir;
    // info: 堆外可用内存大小，默认 1GB, 受制于 druid.processing.buffer.sizeBytes 参数
    this.mergeBufferSize = mergeBufferSize;
  }

  @Override
  public Sequence<ResultRow> run(final QueryPlus<ResultRow> queryPlus, final ResponseContext responseContext)
  {
    final GroupByQuery query = (GroupByQuery) queryPlus.getQuery();
    final GroupByQueryConfig querySpecificConfig = config.withOverrides(query);

    // CTX_KEY_MERGE_RUNNERS_USING_CHAINED_EXECUTION is here because realtime servers use nested mergeRunners calls
    // (one for the entire query and one for each sink). We only want the outer call to actually do merging with a
    // merge buffer, otherwise the query will allocate too many merge buffers. This is potentially sub-optimal as it
    // will involve materializing the results for each sink before starting to feed them into the outer merge buffer.
    // I'm not sure of a better way to do this without tweaking how realtime servers do queries.
    final boolean forceChainedExecution = query.getContextBoolean(
        CTX_KEY_MERGE_RUNNERS_USING_CHAINED_EXECUTION,
        false
    );
    final QueryPlus<ResultRow> queryPlusForRunners = queryPlus
        .withQuery(
            query.withOverriddenContext(ImmutableMap.of(CTX_KEY_MERGE_RUNNERS_USING_CHAINED_EXECUTION, true))
        )
        .withoutThreadUnsafeState();


    if (QueryContexts.isBySegment(query) || forceChainedExecution) {
      ChainedExecutionQueryRunner<ResultRow> runner = new ChainedExecutionQueryRunner<>(queryProcessingPool, queryWatcher, queryables);
      return runner.run(queryPlusForRunners, responseContext);
    }

    final boolean isSingleThreaded = querySpecificConfig.isSingleThreaded();

    final File temporaryStorageDirectory = new File(
        processingTmpDir,
        StringUtils.format("druid-groupBy-%s_%s", UUID.randomUUID(), query.getId())
    );

    // info: 获取当前查询的优先级
    final int priority = QueryContexts.getPriority(query);

    // Figure out timeoutAt time now, so we can apply the timeout to both the mergeBufferPool.take and the actual
    // query processing together.
    final long queryTimeout = QueryContexts.getTimeout(query);
    final boolean hasTimeout = QueryContexts.hasTimeout(query);
    final long timeoutAt = System.currentTimeMillis() + queryTimeout;

    return new BaseSequence<>(
        new BaseSequence.IteratorMaker<ResultRow, CloseableGrouperIterator<RowBasedKey, ResultRow>>()
        {
          // info: 这个 make 在 QueryResouce 中调用，Yielders.each()
          @Override
          public CloseableGrouperIterator<RowBasedKey, ResultRow> make()
          {
            final Closer resources = Closer.create();

            try {
              // info: 堆外内存不足时，使用磁盘文件保存聚合的中间结果
              final LimitedTemporaryStorage temporaryStorage = new LimitedTemporaryStorage(
                  temporaryStorageDirectory,
                  querySpecificConfig.getMaxOnDiskStorage()
              );
              final ReferenceCountingResourceHolder<LimitedTemporaryStorage> temporaryStorageHolder =
                  ReferenceCountingResourceHolder.fromCloseable(temporaryStorage);
              resources.register(temporaryStorageHolder);

              // If parallelCombine is enabled, we need two merge buffers for parallel aggregating and parallel combining
              // info: 这个值默认是 1
              final int numMergeBuffers = querySpecificConfig.getNumParallelCombineThreads() > 1 ? 2 : 1;

              final List<ReferenceCountingResourceHolder<ByteBuffer>> mergeBufferHolders = getMergeBuffersHolder(
                  numMergeBuffers,
                  hasTimeout,
                  timeoutAt
              );
              resources.registerAll(mergeBufferHolders);

              final ReferenceCountingResourceHolder<ByteBuffer> mergeBufferHolder = mergeBufferHolders.get(0);
              final ReferenceCountingResourceHolder<ByteBuffer> combineBufferHolder = numMergeBuffers == 2 ?
                                                                                      mergeBufferHolders.get(1) :
                                                                                      null;

              // info: 执行排序、聚合、创建迭代器的工具类，grouper 是具体的执行类
              Pair<Grouper<RowBasedKey>, Accumulator<AggregateResult, ResultRow>> pair =
                  RowBasedGrouperHelper.createGrouperAccumulatorPair(
                      query,
                      null,
                      config,
                      Suppliers.ofInstance(mergeBufferHolder.get()),
                      combineBufferHolder,
                      concurrencyHint,
                      temporaryStorage,
                      spillMapper,
                      queryProcessingPool, // Passed as executor service
                      priority,
                      hasTimeout,
                      timeoutAt,
                      mergeBufferSize
                  );
              //info: 由于 concurrencyHint > 1, 这个 grouper 是 ConcurrentGrouper
              final Grouper<RowBasedKey> grouper = pair.lhs;
              final Accumulator<AggregateResult, ResultRow> accumulator = pair.rhs;
              grouper.init();

              final ReferenceCountingResourceHolder<Grouper<RowBasedKey>> grouperHolder =
                  ReferenceCountingResourceHolder.fromCloseable(grouper);
              resources.register(grouperHolder);

              List<ListenableFuture<AggregateResult>> futures = Lists.newArrayList(
                      Iterables.transform(
                          queryables,
                          new Function<QueryRunner<ResultRow>, ListenableFuture<AggregateResult>>()
                          {
                            @Override
                            public ListenableFuture<AggregateResult> apply(final QueryRunner<ResultRow> input)
                            {
                              if (input == null) {
                                throw new ISE("Null queryRunner! Looks to be some segment unmapping action happening");
                              }

                              // info: 每个 segment 都要区分开来查询，同一个 query 的 segment 具有相同的 priority
                              ListenableFuture<AggregateResult> future = queryProcessingPool.submitRunnerTask(
                                  new AbstractPrioritizedQueryRunnerCallable<AggregateResult, ResultRow>(priority, input)
                                  {
                                    @Override
                                    public AggregateResult call()
                                    {
                                      try (
                                          // These variables are used to close releasers automatically.
                                          @SuppressWarnings("unused")
                                          Releaser bufferReleaser = mergeBufferHolder.increment();
                                          @SuppressWarnings("unused")
                                          Releaser grouperReleaser = grouperHolder.increment()
                                      ) {
                                        // Return true if OK, false if resources were exhausted.
                                        return input.run(queryPlusForRunners, responseContext)
                                                // info: accumulator 根据 dim 聚合数据
                                            .accumulate(AggregateResult.ok(), accumulator);
                                      }
                                      catch (QueryInterruptedException | QueryTimeoutException e) {
                                        throw e;
                                      }
                                      catch (Exception e) {
                                        log.error(e, "Exception with one of the sequences!");
                                        throw new RuntimeException(e);
                                      }
                                    }
                                  }
                              );

                              if (isSingleThreaded) {
                                waitForFutureCompletion(
                                    query,
                                    ImmutableList.of(future),
                                    hasTimeout,
                                    timeoutAt - System.currentTimeMillis()
                                );
                              }

                              return future;
                            }
                          }
                      )
                  );

              //info: 等待所有查询完成
              if (!isSingleThreaded) {
                waitForFutureCompletion(query, futures, hasTimeout, timeoutAt - System.currentTimeMillis());
              }

              // info: 排序，太复杂了，暂时难以理解
              return RowBasedGrouperHelper.makeGrouperIterator(
                  grouper,
                  query,
                  resources
              );
            }
            catch (Throwable t) {
              // Exception caught while setting up the iterator; release resources.
              try {
                resources.close();
              }
              catch (Exception ex) {
                t.addSuppressed(ex);
              }
              throw t;
            }
          }

          @Override
          public void cleanup(CloseableGrouperIterator<RowBasedKey, ResultRow> iterFromMake)
          {
            iterFromMake.close();
          }
        }
    );
  }

  private List<ReferenceCountingResourceHolder<ByteBuffer>> getMergeBuffersHolder(
      int numBuffers,
      boolean hasTimeout,
      long timeoutAt
  )
  {
    try {
      if (numBuffers > mergeBufferPool.maxSize()) {
        throw new ResourceLimitExceededException(
            "Query needs " + numBuffers + " merge buffers, but only "
            + mergeBufferPool.maxSize() + " merge buffers were configured. "
            + "Try raising druid.processing.numMergeBuffers."
        );
      }
      final List<ReferenceCountingResourceHolder<ByteBuffer>> mergeBufferHolder;
      // This will potentially block if there are no merge buffers left in the pool.
      if (hasTimeout) {
        final long timeout = timeoutAt - System.currentTimeMillis();
        if (timeout <= 0) {
          throw new QueryTimeoutException();
        }
        if ((mergeBufferHolder = mergeBufferPool.takeBatch(numBuffers, timeout)).isEmpty()) {
          throw new QueryTimeoutException("Cannot acquire enough merge buffers");
        }
      } else {
        mergeBufferHolder = mergeBufferPool.takeBatch(numBuffers);
      }
      return mergeBufferHolder;
    }
    catch (QueryTimeoutException | ResourceLimitExceededException e) {
      throw e;
    }
    catch (Exception e) {
      throw new QueryInterruptedException(e);
    }
  }

  private void waitForFutureCompletion(
      GroupByQuery query,
      List<ListenableFuture<AggregateResult>> futures,
      boolean hasTimeout,
      long timeout
  )
  {
    ListenableFuture<List<AggregateResult>> future = Futures.allAsList(futures);
    try {
      if (queryWatcher != null) {
        queryWatcher.registerQueryFuture(query, future);
      }

      if (hasTimeout && timeout <= 0) {
        throw new QueryTimeoutException();
      }

      final List<AggregateResult> results = hasTimeout ? future.get(timeout, TimeUnit.MILLISECONDS) : future.get();

      for (AggregateResult result : results) {
        if (!result.isOk()) {
          GuavaUtils.cancelAll(true, future, futures);
          throw new ResourceLimitExceededException(result.getReason());
        }
      }
    }
    catch (InterruptedException e) {
      log.warn(e, "Query interrupted, cancelling pending results, query id [%s]", query.getId());
      GuavaUtils.cancelAll(true, future, futures);
      throw new QueryInterruptedException(e);
    }
    catch (CancellationException e) {
      GuavaUtils.cancelAll(true, future, futures);
      throw new QueryInterruptedException(e);
    }
    catch (TimeoutException e) {
      log.info("Query timeout, cancelling pending results for query id [%s]", query.getId());
      GuavaUtils.cancelAll(true, future, futures);
      throw new QueryTimeoutException();
    }
    catch (ExecutionException e) {
      GuavaUtils.cancelAll(true, future, futures);
      throw new RuntimeException(e);
    }
  }

}
