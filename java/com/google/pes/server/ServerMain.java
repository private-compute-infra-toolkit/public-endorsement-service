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
import com.google.mbs.domain.CertificateMonitor;
import com.google.mbs.domain.MeasurementBoundCertificateProvider;
import com.google.pes.adapters.tlog.TLedger;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

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
    PrometheusMeterRegistry meterRegistry = injector.getInstance(PrometheusMeterRegistry.class);
    CertificateValidityReporter validityReporter =
        injector.getInstance(CertificateValidityReporter.class);
    validityReporter.startAsync();

    CertificateMonitor certMonitor = injector.getInstance(CertificateMonitor.class);
    certMonitor.start();

    MeasurementBoundCertificateProvider certProvider =
        injector.getInstance(MeasurementBoundCertificateProvider.class);

    int port = 50051;
    PesServer pesServer =
        new PesServer(port, service, jwtInterceptor, tLedger, meterRegistry, certProvider);

    pesServer.start().join();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread() {
              @Override
              public void run() {
                System.err.println("*** shutting down Armeria server since JVM is shutting down");
                pesServer.stop().join();
                validityReporter.stopAsync();
                certMonitor.stop();
                System.err.println("*** server shut down");
              }
            });

    try {
      pesServer.blockUntilShutdown();
    } catch (InterruptedException e) {
      logger.atInfo().log("Server interrupted.");
    }
  }
}
