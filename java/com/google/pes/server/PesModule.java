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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.flogger.FluentLogger;
import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.MapBinder;
import com.google.mbs.MbsCertificateFactory;
import com.google.mbs.MbsModule;
import com.google.mbs.qualifier.AttestationUserData;
import com.google.mbs.qualifier.KmsKeyArn;
import com.google.mbs.qualifier.MbsRoot;
import com.google.mbs.qualifier.PrivateBackupBucket;
import com.google.mbs.qualifier.PublicBackupBucket;
import com.google.pes.adapters.SystemMetrics;
import com.google.pes.adapters.oidc.OidcDiscoveryFetcher;
import com.google.pes.adapters.oidc.OidcJwksKeyFetcher;
import com.google.pes.adapters.oidc.OidcJwksKeyLocator;
import com.google.pes.adapters.policy.S3PolicyProvider;
import com.google.pes.adapters.signatures.SignatureGeneratorImpl;
import com.google.pes.adapters.signatures.SignatureVerifierImpl;
import com.google.pes.adapters.statementvalidation.JsonPublisherIdProvider;
import com.google.pes.adapters.tlog.TLedger;
import com.google.pes.adapters.tlog.TLedgerCertBucketName;
import com.google.pes.adapters.tlog.TLedgerCertName;
import com.google.pes.adapters.tsa.RandomTsaUrlSelector;
import com.google.pes.adapters.tsa.S3TsaConfigProvider;
import com.google.pes.adapters.tsa.TsaConfigBucketName;
import com.google.pes.adapters.tsa.TsaUrlSelector;
import com.google.pes.annotations.PolicyBucket;
import com.google.pes.annotations.TLedgerUrl;
import com.google.pes.annotations.TrustDomain;
import com.google.pes.domain.JwtAuth;
import com.google.pes.domain.TrustDomainExtractor;
import com.google.pes.domain.metric.Metrics;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.ports.PolicyProvider;
import com.google.pes.domain.ports.PublisherIdProvider;
import com.google.pes.domain.ports.SignatureGenerator;
import com.google.pes.domain.ports.SignatureVerifier;
import com.google.pes.domain.ports.TLog;
import com.google.pes.domain.ports.TsaConfigProvider;
import io.jsonwebtoken.Locator;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.inject.Singleton;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** A Guice module for configuring bindings for the Public Endorsement Service. */
public class PesModule extends AbstractModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PesArgs pesArgs;
  private final AwsInstanceMetadata awsInstanceMetadata;
  private final AwsResourceNames awsResourceNames;

  public PesModule(PesArgs pesArgs, AwsInstanceMetadata awsInstanceMetadata) {
    this.pesArgs = pesArgs;
    this.awsInstanceMetadata = awsInstanceMetadata;

    AwsResourceNamesProvider provider = new AwsResourceNamesProvider(pesArgs, awsInstanceMetadata);
    this.awsResourceNames = provider.getRecord();
    logger.atInfo().log("Resolved AWS resource names: %s", awsResourceNames);
  }

  @Override
  protected void configure() {
    bind(String.class).annotatedWith(TLedgerUrl.class).toInstance(pesArgs.getTLedgerUrl());
    bind(String.class)
        .annotatedWith(PolicyBucket.class)
        .toInstance(awsResourceNames.configBucketName());
    bind(AwsInstanceMetadata.class).toInstance(awsInstanceMetadata);

    bind(String.class)
        .annotatedWith(TLedgerCertBucketName.class)
        .toInstance(awsResourceNames.tledgerCertBucketName());
    bind(String.class)
        .annotatedWith(TLedgerCertName.class)
        .toInstance("public/0/root_certificate.pem");
    bind(String.class)
        .annotatedWith(TsaConfigBucketName.class)
        .toInstance(awsResourceNames.tsaConfigBucketName());
    MapBinder<Statement.Format, PublisherIdProvider> validatorMapBinder =
        MapBinder.newMapBinder(binder(), Statement.Format.class, PublisherIdProvider.class);
    validatorMapBinder.addBinding(Statement.Format.JSON_INTOTO).to(JsonPublisherIdProvider.class);

    bind(InstantSource.class).toInstance(InstantSource.system());
    bind(HttpClient.class).toInstance(HttpClient.newHttpClient());

    bind(TLog.class).to(TLedger.class);
    bind(PolicyProvider.class).to(S3PolicyProvider.class);
    bind(SignatureGenerator.class).to(SignatureGeneratorImpl.class);
    bind(SignatureVerifier.class).to(SignatureVerifierImpl.class);
    bind(TsaConfigProvider.class).to(S3TsaConfigProvider.class);
    bind(TsaUrlSelector.class).to(RandomTsaUrlSelector.class);
    bind(Metrics.class).to(SystemMetrics.class);
    bind(com.google.mbs.Metrics.class).to(SystemMetrics.class);

    install(new MbsModule(awsInstanceMetadata.region()));
    bind(String.class).annotatedWith(KmsKeyArn.class).toInstance(awsResourceNames.kmsKeyArn());
    bind(String.class)
        .annotatedWith(PublicBackupBucket.class)
        .toInstance(awsResourceNames.certBackupBucketName());
    bind(String.class)
        .annotatedWith(PrivateBackupBucket.class)
        .toInstance(awsResourceNames.keyBackupBucketName());
  }

  @Provides
  @Singleton
  @AttestationUserData
  byte[] provideUserData() {
    String resourceNamesJson = new Gson().toJson(awsResourceNames);
    return resourceNamesJson.getBytes(StandardCharsets.UTF_8);
  }

  @Provides
  @Singleton
  MbsCertificateFactory provideMbsCertificateFactory() {
    String env = awsInstanceMetadata.environment();
    String domain = awsInstanceMetadata.domain();
    String operatorRole = awsInstanceMetadata.accountId();
    String trustDomain = constructTrustDomain(env, domain);
    String spiffeId =
        String.format(
            "spiffe://%s/operator/pcit.goog/%s/publisher/google.com/pcit-release-bot/workload/public-endorsement-service",
            trustDomain, operatorRole);
    GeneralName uriSan = new GeneralName(GeneralName.uniformResourceIdentifier, spiffeId);
    Optional<GeneralNames> san = Optional.of(new GeneralNames(uriSan));
    logger.atInfo().log("Setting root certificate Subject Alternative Name (SAN): %s", spiffeId);

    return MbsCertificateFactory.createSelfSignedCertificatesFactory(
        new MbsCertificateFactory.CertSignatureSpec("RSA", 4096, "SHA256withRSA"),
        new X500Name("C=US, O=Google LLC, CN=PES"),
        // The notAfter of TCA and Tledger is set to 2027-02-02 (14:00 CET): deadline for
        // initial PCIT root certs.
        // Because PES root cert's notAfter must be later than endorsement's notAfter,
        // set PES root cert expiration to 2027-03-02 (14:00 CET).
        Duration.between(Instant.now(), Instant.parse("2027-03-02T13:00:00Z")),
        san,
        KeyUsage.digitalSignature);
  }

  @Provides
  @Singleton
  @TrustDomain
  String provideTrustDomain(@MbsRoot X509Certificate rootCertificate)
      throws CertificateParsingException, java.net.URISyntaxException {
    return TrustDomainExtractor.extract(rootCertificate);
  }

  @Provides
  CloseableHttpClient providesHttpClient() {
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setResponseTimeout(Timeout.ofSeconds(10))
            .setConnectionRequestTimeout(Timeout.ofSeconds(5))
            .build();
    return HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
  }

  @Provides
  S3Client provideS3Client() {
    Region region = Region.of(awsInstanceMetadata.region());
    return S3Client.builder().region(region).build();
  }

  @Provides
  @Singleton
  @JwtAuth
  Locator<Key> provideJwtKeyLocator(@JwtAuth OidcJwksKeyFetcher jwksKeyFetcher) {
    return new OidcJwksKeyLocator(jwksKeyFetcher, "RS256");
  }

  @Provides
  @Singleton
  @JwtAuth
  OidcJwksKeyFetcher provideOidcJwksKeyFetcher(
      OidcDiscoveryFetcher discoveryFetcher,
      InstantSource instantSource,
      HttpClient httpClient,
      ObjectMapper objectMapper) {
    return new OidcJwksKeyFetcher(
        () ->
            discoveryFetcher.fetchJwksUri(
                "https://accounts.google.com/.well-known/openid-configuration"),
        instantSource,
        httpClient,
        objectMapper);
  }

  @Provides
  @Singleton
  OidcDiscoveryFetcher provideOidcDiscoveryFetcher(
      HttpClient httpClient, ObjectMapper objectMapper) {
    return new OidcDiscoveryFetcher(httpClient, objectMapper);
  }

  @Provides
  @Singleton
  public ObjectMapper provideObjectMapper() {
    return new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Provides
  @Singleton
  public PrometheusMeterRegistry providePrometheusMeterRegistry() {
    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    registry
        .config()
        .meterFilter(MeterFilter.acceptNameStartsWith("pes."))
        .meterFilter(MeterFilter.acceptNameStartsWith("armeria.server.connections"))
        .meterFilter(MeterFilter.deny());
    return registry;
  }

  private static String constructTrustDomain(String env, String domain) {
    if ("prod".equals(env)) {
      String cleanedDomain = domain;
      if (domain.startsWith("aws.")) {
        cleanedDomain = domain.substring("aws.".length());
      }
      return "pes." + cleanedDomain;
    }
    return String.format("pes.%s.%s", env, domain);
  }
}
