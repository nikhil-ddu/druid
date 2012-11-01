/*
 * Druid - a distributed column store.
 * Copyright (C) 2012  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.metamx.druid.query.extraction;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.metamx.druid.query.search.FragmentSearchQuerySpec;
import com.metamx.druid.query.search.LexicographicSearchSortSpec;
import com.metamx.druid.query.search.SearchQuerySpec;

/**
 */
public class SearchQuerySpecDimExtractionFnTest
{
  private static final String[] testStrings = {
      "Kyoto",
      "Calgary",
      "Tokyo",
      "Stockholm",
      "Toyokawa",
      "Pretoria",
      "Yorktown",
      "Ontario"
  };

  @Test
  public void testExtraction()
  {
    SearchQuerySpec spec = new FragmentSearchQuerySpec(
        Arrays.asList("to", "yo"), new LexicographicSearchSortSpec()
    );
    DimExtractionFn dimExtractionFn = new SearchQuerySpecDimExtractionFn(spec);
    List<String> expected = Arrays.asList("Kyoto", "Tokyo", "Toyokawa", "Yorktown");
    Set<String> extracted = Sets.newHashSet();

    for (String str : testStrings) {
      String res = dimExtractionFn.apply(str);
      if (res != null) {
        extracted.add(res);
      }
    }

    Assert.assertEquals(4, extracted.size());

    for (String str : extracted) {
      Assert.assertTrue(expected.contains(str));
    }
  }
}
