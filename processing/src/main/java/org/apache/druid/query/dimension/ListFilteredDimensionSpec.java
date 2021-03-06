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

package org.apache.druid.query.dimension;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.apache.druid.common.config.NullHandling;
import org.apache.druid.java.util.common.StringUtils;
import org.apache.druid.query.filter.DimFilterUtils;
import org.apache.druid.segment.DimensionSelector;
import org.apache.druid.segment.IdLookup;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Set;

/**
 *
 */
public class ListFilteredDimensionSpec extends BaseFilteredDimensionSpec
{

  private static final byte CACHE_TYPE_ID = 0x3;

  private final Set<String> values;
  private final boolean isWhitelist;

  public ListFilteredDimensionSpec(
      @JsonProperty("delegate") DimensionSpec delegate,
      @JsonProperty("values") Set<String> values,
      @JsonProperty("isWhitelist") Boolean isWhitelist
  )
  {
    super(delegate);

    Preconditions.checkArgument(values != null && values.size() > 0, "values list must be non-empty");
    this.values = values;

    this.isWhitelist = isWhitelist == null ? true : isWhitelist.booleanValue();
  }

  @JsonProperty
  public Set<String> getValues()
  {
    return values;
  }

  @JsonProperty("isWhitelist")
  public boolean isWhitelist()
  {
    return isWhitelist;
  }

  @Override
  public DimensionSelector decorate(final DimensionSelector selector)
  {
    if (selector == null) {
      return null;
    }

    if (isWhitelist) {
      return filterWhiteList(selector);
    } else {
      return filterBlackList(selector);
    }
  }

  private DimensionSelector filterWhiteList(DimensionSelector selector)
  {
    final int selectorCardinality = selector.getValueCardinality();
    if (selectorCardinality < 0 || !selector.nameLookupPossibleInAdvance()) {
      return new PredicateFilteredDimensionSelector(selector, Predicates.in(values));
    }
    final int maxPossibleFilteredCardinality = values.size();
    int count = 0;
    final Int2IntOpenHashMap forwardMapping = new Int2IntOpenHashMap(maxPossibleFilteredCardinality);
    forwardMapping.defaultReturnValue(-1);
    final int[] reverseMapping = new int[maxPossibleFilteredCardinality];
    IdLookup idLookup = selector.idLookup();
    if (idLookup != null) {
      for (String value : values) {
        int i = idLookup.lookupId(value);
        if (i >= 0) {
          forwardMapping.put(i, count);
          reverseMapping[count++] = i;
        }
      }
    } else {
      for (int i = 0; i < selectorCardinality; i++) {
        if (values.contains(NullHandling.nullToEmptyIfNeeded(selector.lookupName(i)))) {
          forwardMapping.put(i, count);
          reverseMapping[count++] = i;
        }
      }
    }
    return new ForwardingFilteredDimensionSelector(selector, forwardMapping, reverseMapping);
  }

  private DimensionSelector filterBlackList(DimensionSelector selector)
  {
    final int selectorCardinality = selector.getValueCardinality();
    if (selectorCardinality < 0 || !selector.nameLookupPossibleInAdvance()) {
      return new PredicateFilteredDimensionSelector(
          selector,
          input -> !values.contains(input)
      );
    }
    final int maxPossibleFilteredCardinality = selectorCardinality;
    int count = 0;
    final Int2IntOpenHashMap forwardMapping = new Int2IntOpenHashMap(maxPossibleFilteredCardinality);
    forwardMapping.defaultReturnValue(-1);
    final int[] reverseMapping = new int[maxPossibleFilteredCardinality];
    for (int i = 0; i < selectorCardinality; i++) {
      if (!values.contains(NullHandling.nullToEmptyIfNeeded(selector.lookupName(i)))) {
        forwardMapping.put(i, count);
        reverseMapping[count++] = i;
      }
    }
    return new ForwardingFilteredDimensionSelector(selector, forwardMapping, reverseMapping);
  }

  @Override
  public byte[] getCacheKey()
  {
    byte[] delegateCacheKey = delegate.getCacheKey();

    byte[][] valuesBytes = new byte[values.size()][];
    int valuesBytesSize = 0;
    int index = 0;
    for (String value : values) {
      valuesBytes[index] = StringUtils.toUtf8(value);
      valuesBytesSize += valuesBytes[index].length + 1;
      ++index;
    }

    ByteBuffer filterCacheKey = ByteBuffer.allocate(3 + delegateCacheKey.length + valuesBytesSize)
                                          .put(CACHE_TYPE_ID)
                                          .put(delegateCacheKey)
                                          .put((byte) (isWhitelist ? 1 : 0))
                                          .put(DimFilterUtils.STRING_SEPARATOR);
    for (byte[] bytes : valuesBytes) {
      filterCacheKey.put(bytes)
                    .put(DimFilterUtils.STRING_SEPARATOR);
    }
    return filterCacheKey.array();
  }

  @Override
  public DimensionSpec withDimension(String newDimension)
  {
    return new ListFilteredDimensionSpec(delegate.withDimension(newDimension), values, isWhitelist);
  }

  @Override
  public boolean equals(Object o)
  {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ListFilteredDimensionSpec that = (ListFilteredDimensionSpec) o;
    return Objects.equals(getDelegate(), that.getDelegate())
           && isWhitelist == that.isWhitelist
           && Objects.equals(values, that.values);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(getDelegate(), values, isWhitelist);
  }

  @Override
  public String toString()
  {
    return "ListFilteredDimensionSpec{" +
           "values=" + values +
           ", isWhitelist=" + isWhitelist +
           '}';
  }
}
