/*
 * Copyright 2025 Google LLC
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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.flogger.FluentLogger;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.pes.adapters.tlog.TLedger;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.server.logging.LoggingService;
import io.grpc.ServerInterceptors;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.util.concurrent.atomic.AtomicReference;

/** A gRPC and HTTP server that hosts the Public Endorsement Service. */
public class ServerMain {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The main entry point for the Armeria server application. */
  public static void main(String[] args) {
    PesArgs pesArgs = new PesArgs();
    JCommander jc = JCommander.newBuilder().addObject(pesArgs).build();

    try {
      jc.parse(args);

      if (pesArgs.isHelp()) {
        jc.usage();
        return;
      }
    } catch (ParameterException e) {
      jc.usage();
      throw e;
    }

    AwsInstanceMetadata awsInstanceMetadata = new ImdsClient().getAwsInstanceMetadata();
    logger.atInfo().log(
        "Resolved AWS environment via IMDS: region=%s, accountId=%s",
        awsInstanceMetadata.region(), awsInstanceMetadata.accountId());

    Injector injector = Guice.createInjector(new PesModule(pesArgs, awsInstanceMetadata));
    PesGrpcHandler service = injector.getInstance(PesGrpcHandler.class);
    JwtInterceptor jwtInterceptor = injector.getInstance(JwtInterceptor.class);
    TLedger tLedger = injector.getInstance(TLedger.class);

    HealthManager healthManager = new HealthManager(tLedger);

    int port = 50051;
    GrpcService grpcService =
        GrpcService.builder()
            .addService(ServerInterceptors.intercept(service, jwtInterceptor))
            .addService(ProtoReflectionService.newInstance())
            .enableHttpJsonTranscoding(true)
            .build();

    Server server =
        Server.builder()
            .http(port)
            .service(grpcService)
            .service(
                "/healthz",
                (ctx, req) -> {
                  if (healthManager.isServing()) {
                    return HttpResponse.of(HttpStatus.OK);
                  }
                  return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE);
                })
            .decorator(LoggingService.newDecorator())
            .build();

    server.start().join();

    logger.atInfo().log("Server started, listening on %d.", port);

    healthManager.setStatus(ServingStatus.SERVING);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                System.err.println("*** shutting down Armeria server since JVM is shutting down");
                healthManager.setStatus(ServingStatus.NOT_SERVING);
                server.stop().join();
                System.err.println("*** server shut down");
              }
            });

    try {
      server.blockUntilShutdown();
    } catch (InterruptedException e) {
      logger.atInfo().log("Server interrupted.");
    }
  }

  private static class HealthManager {
    private final AtomicReference<ServingStatus> currentStatus;
    private final TLedger tLedger;

    HealthManager(TLedger tLedger) {
      this.tLedger = tLedger;
      this.currentStatus = new AtomicReference<>(ServingStatus.UNKNOWN);
    }

    synchronized void setStatus(ServingStatus status) {
      currentStatus.set(status);
    }

    boolean isServing() {
      return currentStatus.get() == ServingStatus.SERVING && tLedger.isHealthy();
    }
  }
}
