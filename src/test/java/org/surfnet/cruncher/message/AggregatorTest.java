/*
 * Copyright 2013 SURFnet bv, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.surfnet.cruncher.message;

import static org.junit.Assert.assertEquals;
import static org.surfnet.cruncher.message.Aggregator.aggregationRecordHash;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.surfnet.cruncher.model.LoginEntry;
import org.surfnet.cruncher.test.config.SpringConfigurationForTest;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringConfigurationForTest.class)
@TransactionConfiguration(defaultRollback=true)
@Transactional
public class AggregatorTest {
  private static final Logger LOG = LoggerFactory.getLogger(AggregatorTest.class);

  @Inject
  private Aggregator aggregator;

  @Inject
  private JdbcTemplate jdbcTemplate;

  private String sqlRowCountAggregated = "select count(*) from aggregated_log_logins";

  @Test(expected=IllegalArgumentException.class)
  public void aggregateLoginNull() throws Exception {
    aggregator.aggregateLogin(null);
  }

  @Test
  public void testRun() {
    aggregator.run();
    Calendar instance = Calendar.getInstance();
    instance.set(2012, 3, 21);
    String hash = aggregationRecordHash("idp2", "sp1", instance.getTime());
 
    long entryCount = jdbcTemplate.queryForLong("select entrycount from aggregated_log_logins where datespidphash = ?", hash);
    assertEquals(2, entryCount);
    long timestamp = jdbcTemplate.queryForLong("select * from aggregate_meta_data");
    assertEquals(1335088121000L, timestamp);
  }

  @Test
  public void aggregateEmptyList() {
    aggregator.aggregateLogin(Collections.<LoginEntry>emptyList());
  }

  @Test
  public void aggregateList() {
    int rowCountBefore = jdbcTemplate.queryForInt(sqlRowCountAggregated);
    LoginEntry loginEntry = new LoginEntry("someIdp", "marker0", new Date(), "someSp", "", "", "", "");
    LoginEntry loginEntry2 = new LoginEntry("someIdp", "marker0", new Date(), "someSp", "", "", "", "");

    aggregator.aggregateLogin(Arrays.asList(loginEntry, loginEntry2));

    int rowCountAfter = jdbcTemplate.queryForInt(sqlRowCountAggregated);
    assertEquals("Aggregation of 2 records should result in 1 added rows", rowCountBefore + 1, rowCountAfter);
    int aggregatedCount = jdbcTemplate.queryForInt("select entrycount from aggregated_log_logins where idpentityname like 'marker0'");
    assertEquals("Aggegrated records should count 2", 2, aggregatedCount);
  }

  @Test
  public void aggregateDifferentSpIdp() {
    int rowCountBefore = jdbcTemplate.queryForInt(sqlRowCountAggregated);
    LoginEntry loginEntry1 = new LoginEntry("someIdp1", "marker1", new Date(), "someSp1", "", "", "", "");
    LoginEntry loginEntry2 = new LoginEntry("someIdp2", "marker1", new Date(), "someSp1", "", "", "", "");
    LoginEntry loginEntry3 = new LoginEntry("someIdp1", "marker1", new Date(), "someSp2", "", "", "", "");
    LoginEntry loginEntry4 = new LoginEntry("someIdp2", "marker1", new Date(), "someSp2", "", "", "", "");

    aggregator.aggregateLogin(Arrays.asList(loginEntry1, loginEntry2, loginEntry3, loginEntry4));

    int rowCountAfter = jdbcTemplate.queryForInt(sqlRowCountAggregated);
    assertEquals("Aggregation of 4 records of different sp/idps should result in 4 added rows", rowCountBefore + 4, rowCountAfter);
    LOG.debug("Contents of aggregated_log_logins: {}", jdbcTemplate.queryForList("select * from aggregated_log_logins"));
    List<Integer> aggregatedCount = jdbcTemplate.queryForList("select entrycount from aggregated_log_logins where idpentityname like 'marker1'", Integer.class);
    assertEquals("Aggegrated records should all count 1", new Integer(1), aggregatedCount.get(0));
    assertEquals("Aggegrated records should all count 1", new Integer(1), aggregatedCount.get(1));
    assertEquals("Aggegrated records should all count 1", new Integer(1), aggregatedCount.get(2));
    assertEquals("Aggegrated records should all count 1", new Integer(1), aggregatedCount.get(3));
  }

}