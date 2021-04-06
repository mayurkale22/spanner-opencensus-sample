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
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.opencensus.common.Duration;
import io.opencensus.common.Scope;
import io.opencensus.contrib.grpc.metrics.RpcViews;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.stats.Aggregation;
import io.opencensus.stats.Aggregation.Distribution;
import io.opencensus.stats.BucketBoundaries;
import io.opencensus.stats.Measure.MeasureLong;
import io.opencensus.stats.Stats;
import io.opencensus.stats.StatsRecorder;
import io.opencensus.stats.View;
import io.opencensus.stats.View.Name;
import io.opencensus.stats.ViewManager;
import io.opencensus.tags.TagContext;
import io.opencensus.tags.TagKey;
import io.opencensus.tags.TagValue;
import io.opencensus.tags.Tagger;
import io.opencensus.tags.Tags;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  private static final String MILLISECOND = "ms";
  private static final TagKey key = TagKey.create("grpc_client_method");
  /**
   * GFE t4t7 latency extracted from server-timing header.
   */
  public static final MeasureLong SPANNER_GFE_LATENCY =
      MeasureLong.create(
          "cloud.google.com/java/spanner/gfe_latency",
          "Latency between Google's network receives an RPC and reads back the first byte of the response",
          MILLISECOND);

  static final List<Double> RPC_MILLIS_BUCKET_BOUNDARIES =
      Collections.unmodifiableList(
          Arrays.asList(
              0.0, 0.01, 0.05, 0.1, 0.3, 0.6, 0.8, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 13.0,
              16.0, 20.0, 25.0, 30.0, 40.0, 50.0, 65.0, 80.0, 100.0, 130.0, 160.0, 200.0, 250.0,
              300.0, 400.0, 500.0, 650.0, 800.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 50000.0,
              100000.0));
  static final Aggregation AGGREGATION_WITH_MILLIS_HISTOGRAM =
      Distribution.create(BucketBoundaries.create(RPC_MILLIS_BUCKET_BOUNDARIES));
  static final View GFE_LATENCY_VIEW = View
      .create(Name.create("cloud.google.com/java/spanner/gfe_latency"),
          "Latency between Google's network receives an RPC and reads back the first byte of the response",
          SPANNER_GFE_LATENCY,
          AGGREGATION_WITH_MILLIS_HISTOGRAM,
          Collections.singletonList(key));

  ViewManager manager = Stats.getViewManager();

  // Get the global singleton Tracer object.
  private static final Tracer tracer = Tracing.getTracer();
  private static final Tagger tagger = Tags.getTagger();
  private static final StatsRecorder STATS_RECORDER = Stats.getStatsRecorder();

  PersonController() throws IOException {
    SpannerOptions options = SpannerOptions.newBuilder()
        .setInterceptorProvider(() -> Collections.singletonList(interceptor))
        .build();
    String projectId = options.getProjectId();
    spanner = options.getService();
    dbClient = spanner.getDatabaseClient(DatabaseId.of(projectId, instanceId, databaseId));

    // Register basic gRPC views.
    RpcViews.registerClientGrpcBasicViews();
    manager.registerView(GFE_LATENCY_VIEW);

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

  private static final HeaderClientInterceptor interceptor = new HeaderClientInterceptor();
  private static final Metadata.Key<String> SERVER_TIMING_HEADER_KEY =
      Metadata.Key.of("server-timing", Metadata.ASCII_STRING_MARSHALLER);
  private static final Pattern SERVER_TIMING_HEADER_PATTERN = Pattern.compile(".*dur=(?<dur>\\d+)");

  // ClientInterceptor to capture GFE header latency.
  private static class HeaderClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
        CallOptions callOptions, Channel next) {
      return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
          super.start(new SimpleForwardingClientCallListener<RespT>(responseListener) {
            @Override
            public void onHeaders(Metadata metadata) {
              processHeader(metadata, method.getFullMethodName());
              super.onHeaders(metadata);
            }
          }, headers);
        }
      };
    }

    private static void processHeader(Metadata metadata, String method) {
      if (metadata.get(SERVER_TIMING_HEADER_KEY) != null) {
        String serverTiming = metadata.get(SERVER_TIMING_HEADER_KEY);
        Matcher matcher = SERVER_TIMING_HEADER_PATTERN.matcher(serverTiming);
        if (matcher.find()) {
          long latency = Long.parseLong(matcher.group("dur"));

          TagContext tctx = tagger.emptyBuilder().put(key, TagValue.create(method)).build();
          try (Scope ss = tagger.withTagContext(tctx)) {
            STATS_RECORDER.newMeasureMap()
                .put(SPANNER_GFE_LATENCY, latency)
                .record();
          }
        }
      }
    }
  }

}
