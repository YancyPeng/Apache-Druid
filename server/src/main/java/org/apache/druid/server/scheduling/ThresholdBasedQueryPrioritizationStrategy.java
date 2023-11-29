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

package org.apache.druid.server.scheduling;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import org.apache.druid.client.SegmentServerSelector;
import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.query.Query;
import org.apache.druid.query.QueryContexts;
import org.apache.druid.query.QueryPlus;
import org.apache.druid.server.QueryPrioritizationStrategy;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

/**
 * Lowers query priority when any of the configured thresholds is exceeded
 */
public class ThresholdBasedQueryPrioritizationStrategy implements QueryPrioritizationStrategy
{
  private static final int DEFAULT_SEGMENT_THRESHOLD = Integer.MAX_VALUE;
  private static final int DEFAULT_ADJUSTMENT = 5;

  private final int segmentCountThreshold;
  private final int adjustment;

  /**
   * druid.query.scheduler.prioritization.periodThreshold
   */
  private final Optional<Duration> periodThreshold;
  /**
   * druid.query.scheduler.prioritization.durationThreshold
   */
  private final Optional<Duration> durationThreshold;

  @JsonCreator
  public ThresholdBasedQueryPrioritizationStrategy(
      @JsonProperty("periodThreshold") @Nullable String periodThresholdString,
      @JsonProperty("durationThreshold") @Nullable String durationThresholdString,
      @JsonProperty("segmentCountThreshold") @Nullable Integer segmentCountThreshold,
      @JsonProperty("adjustment") @Nullable Integer adjustment
  )
  {
    this.segmentCountThreshold = segmentCountThreshold == null ? DEFAULT_SEGMENT_THRESHOLD : segmentCountThreshold;
    this.adjustment = adjustment == null ? DEFAULT_ADJUSTMENT : adjustment;
    this.periodThreshold = periodThresholdString == null
                           ? Optional.empty()
                           : Optional.of(new Period(periodThresholdString).toDurationFrom(DateTimes.nowUtc()));
    this.durationThreshold = durationThresholdString == null
                             ? Optional.empty()
                             : Optional.of(new Period(durationThresholdString).toStandardDuration());
    Preconditions.checkArgument(
        segmentCountThreshold != null || periodThreshold.isPresent() || durationThreshold.isPresent(),
        "periodThreshold, durationThreshold, or segmentCountThreshold must be set"
    );
  }

  @Override
  // info: 计算当前查询的 prority
  public <T> Optional<Integer> computePriority(QueryPlus<T> query, Set<SegmentServerSelector> segments)
  {
    Query<T> theQuery = query.getQuery();
    // info: 检查当前查询的 startTime < currentTime - periodThreashold，受 druid.query.scheduler.prioritization.periodThreshold 参数影响
    final boolean violatesPeriodThreshold = periodThreshold.map(duration -> {
      final DateTime periodThresholdStartDate = DateTimes.nowUtc().minus(duration);
      return theQuery.getIntervals()
                     .stream()
                     .anyMatch(interval -> interval.getStart().isBefore(periodThresholdStartDate));
    }).orElse(false);
    // info: 检查当前查询的 duration（endTime - startTime）> durationThreshold，受 druid.query.scheduler.prioritization.durationThreshold 参数影响
    final boolean violatesDurationThreshold =
        durationThreshold.map(duration -> theQuery.getDuration().isLongerThan(duration)).orElse(false);
    // info: 检查当前查询的 segmentCount 值是否大于 segmentCountThreshold，受 druid.query.scheduler.prioritization.segmentCountThreshold 参数影响
    boolean violatesSegmentThreshold = segments.size() > segmentCountThreshold;
    // info：计算 priority 值
    if (violatesPeriodThreshold || violatesDurationThreshold || violatesSegmentThreshold) {
      // info: 我们的查询中不会手动设置 priority，默认是0，只要满足以上任一条件就是负数，就会被分配到 low pane
      final int adjustedPriority = QueryContexts.getPriority(theQuery) - adjustment;
      return Optional.of(adjustedPriority);
    }
    return Optional.empty();
  }
}
