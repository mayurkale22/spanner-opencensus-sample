/*
 * Copyright 2017 Google Inc.
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

package com.sample.spanner;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.Statement;
import io.opencensus.common.Duration;
import io.opencensus.common.Scope;
import io.opencensus.contrib.grpc.metrics.RpcViews;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping(path = "/spanner")
public class PersonController {

  private Spanner spanner;
  private final DatabaseClient dbClient;
  private String instanceId = "sample-instance";
  private String databaseId = "sample-db";
  String table = "person";

  // Get the global singleton Tracer object.
  private static final Tracer tracer = Tracing.getTracer();

  PersonController() throws IOException {
    SpannerOptions options = SpannerOptions.newBuilder().build();
    String projectId = options.getProjectId();
    spanner = options.getService();
    dbClient = spanner.getDatabaseClient(DatabaseId.of(projectId, instanceId, databaseId));

    // Register basic gRPC views.
    RpcViews.registerClientGrpcBasicViews();

    // WARNING: Be careful before you set sampler value to always sample, especially in
    // production environment. Trace data is often very large in size and is expensive to
    // collect. This is why rather than collecting traces for every request(i.e. alwaysSample),
    // downsampling is preferred.
    //
    // By default, OpenCensus provides a probabilistic sampler that will trace once in every
    // 10,000 requests, that's why if default probabilistic sampler is used
    // you might not see trace data printed or exported and this is expected behavior.
    TraceConfig traceConfig = Tracing.getTraceConfig();
    traceConfig.updateActiveTraceParams(
        traceConfig.getActiveTraceParams().toBuilder().setSampler(Samplers.alwaysSample()).build());

    // Enable OpenCensus exporters to export metrics to Stackdriver Monitoring.
    // The default export interval is 60 seconds. The thread with the StackdriverStatsExporter must
    // live for at least the interval past any metrics that must be collected, or some risk being
    // lost if they are recorded after the last export.
    StackdriverStatsExporter.createAndRegister(
        StackdriverStatsConfiguration.builder()
            .setProjectId(projectId)
            .setExportInterval(Duration.create(60, 0))
            .build());

    // Enable OpenCensus exporters to export traces to Stackdriver Trace.
    StackdriverTraceExporter.createAndRegister(
        StackdriverTraceConfiguration.builder()
            .setProjectId(projectId)
            .build());
  }

  @GetMapping(path = "/read", produces = "application/json")
  public List<Person> read() {
    List<Person> list = new ArrayList<>();
    // Create a scoped span, a scoped span will automatically end when closed.
    try (Scope ss = tracer.spanBuilder("read").startScopedSpan()) {
      try (ResultSet resultSet =
          dbClient
              .singleUse()
              .read(
                  table,
                  KeySet.all(), // Read all rows in a table.
                  Arrays.asList("id", "name", "email"))) {
        while (resultSet.next()) {
          Person person = new Person();
          person.setId(resultSet.getString(0));
          person.setName(resultSet.getString(1));
          person.setEmail(resultSet.getString(2));
          list.add(person);
        }
      }
      return list;
    }
  }

  @GetMapping(path = "/query", produces = "application/json")
  public List<Person> getPersonQuery() throws InterruptedException {
    //  Create a scoped span, a scoped span will automatically end when closed.
    try (Scope ss = tracer.spanBuilder("query").startScopedSpan()) {
      List<Person> list = new ArrayList<>();
      try (ResultSet resultSet =
          dbClient
              .singleUse() // Execute a single read or query against Cloud Spanner.
              .executeQuery(Statement.of("SELECT id, name, email FROM " + table))) {
        while (resultSet.next()) {
          Person person = new Person();
          person.setId(resultSet.getString(0));
          person.setName(resultSet.getString(1));
          person.setEmail(resultSet.getString(2));
          list.add(person);
        }
      }
      return list;
    }
  }

}
