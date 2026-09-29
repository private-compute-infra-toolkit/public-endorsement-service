/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.pes.server;

import com.google.common.flogger.FluentLogger;
import com.google.mbs.domain.MeasurementBoundCertificateProvider;
import com.google.pes.adapters.tlog.TLedger;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.grpc.GrpcMeterIdPrefixFunction;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.server.logging.LoggingService;
import com.linecorp.armeria.server.metric.MetricCollectingService;
import com.linecorp.armeria.server.prometheus.PrometheusExpositionService;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** An Armeria server that hosts the Public Endorsement Service. */
public class PesServer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Server server;
  private final HealthManager healthManager;
  private final MeasurementBoundCertificateProvider certificateProvider;
  private final int port;

  public PesServer(
      int port,
      PesGrpcHandler service,
      ServerInterceptor jwtInterceptor,
      TLedger tLedger,
      PrometheusMeterRegistry meterRegistry,
      MeasurementBoundCertificateProvider certificateProvider) {
    this.port = port;
    this.certificateProvider = certificateProvider;
    this.healthManager = new HealthManager(tLedger);

    final GrpcService grpcService =
        GrpcService.builder()
            .addService(ServerInterceptors.intercept(service, jwtInterceptor))
            .addService(ProtoReflectionService.newInstance())
            .enableHttpJsonTranscoding(true)
            .build();

    this.server =
        Server.builder()
            .http(port)
            .meterRegistry(meterRegistry)
            .service(
                grpcService,
                MetricCollectingService.newDecorator(GrpcMeterIdPrefixFunction.of("pes.server")))
            .service(
                "/healthz",
                (ctx, req) -> {
                  if (isHealthy()) {
                    return HttpResponse.of(HttpStatus.OK);
                  }
                  return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE);
                })
            .service(
                "/metrics", PrometheusExpositionService.of(meterRegistry.getPrometheusRegistry()))
            .decorator(LoggingService.newDecorator())
            .build();
  }

  boolean isHealthy() {
    if (!healthManager.isServing()) {
      return false;
    }
    try {
      return certificateProvider.getActiveTrustPackage().bundles().get(0) != null;
    } catch (IllegalStateException e) {
      logger.atFine().log("Healthcheck failed: certificate is not available yet.");
      return false;
    } catch (Exception e) {
      logger.atWarning().withCause(e).log("Healthcheck failed with unexpected exception");
      return false;
    }
  }

  public CompletableFuture<Void> start() {
    return server
        .start()
        .thenRun(
            () -> {
              logger.atInfo().log("Server started, listening on %d.", port);
              healthManager.setStatus(ServingStatus.SERVING);
            });
  }

  public CompletableFuture<Void> stop() {
    healthManager.setStatus(ServingStatus.NOT_SERVING);
    return server.stop();
  }

  public int port() {
    return server.activeLocalPort();
  }

  public void blockUntilShutdown() throws InterruptedException {
    server.blockUntilShutdown();
  }

  public Server getServer() {
    return server;
  }

  public static class HealthManager {
    private final AtomicReference<ServingStatus> currentStatus;
    private final TLedger tLedger;

    public HealthManager(TLedger tLedger) {
      this.tLedger = tLedger;
      this.currentStatus = new AtomicReference<>(ServingStatus.UNKNOWN);
    }

    public synchronized void setStatus(ServingStatus status) {
      currentStatus.set(status);
    }

    public boolean isServing() {
      return currentStatus.get() == ServingStatus.SERVING && tLedger.isHealthy();
    }
  }
}
