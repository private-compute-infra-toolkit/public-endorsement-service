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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.mbs.MbsCertificateFactory;
import com.google.mbs.domain.CertificateMonitor;
import com.google.mbs.domain.MeasurementBoundCertificateProvider;
import com.google.mbs.domain.MeasurementBoundCertificateReloader;
import com.google.mbs.qualifier.InstanceId;
import com.google.mbs.qualifier.KmsKeyArn;
import com.google.mbs.qualifier.PrivateBackupBucket;
import com.google.mbs.qualifier.PublicBackupBucket;
import com.google.pes.adapters.SystemMetrics;
import com.google.pes.adapters.oidc.OidcAudienceValidator;
import com.google.pes.adapters.policy.S3PolicyProvider;
import com.google.pes.adapters.signatures.SignatureGeneratorImpl;
import com.google.pes.adapters.signatures.SignatureVerifierImpl;
import com.google.pes.adapters.statementvalidation.JsonPublisherIdProvider;
import com.google.pes.adapters.tlog.TLedger;
import com.google.pes.adapters.tsa.RandomTsaUrlSelector;
import com.google.pes.adapters.tsa.S3TsaConfigProvider;
import com.google.pes.adapters.tsa.SimpleTsaClient;
import com.google.pes.adapters.tsa.TsaConfigBucketName;
import com.google.pes.adapters.tsa.TsaUrlSelector;
import com.google.pes.annotations.PolicyBucket;
import com.google.pes.annotations.TLedgerUrl;
import com.google.pes.domain.model.Statement;
import com.google.pes.domain.ports.PolicyProvider;
import com.google.pes.domain.ports.PublisherIdProvider;
import com.google.pes.domain.ports.SignatureGenerator;
import com.google.pes.domain.ports.SignatureVerifier;
import com.google.pes.domain.ports.TLog;
import com.google.pes.domain.ports.TsaClient;
import com.google.pes.domain.ports.TsaConfigProvider;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.security.cert.X509Certificate;
import java.util.Map;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import software.amazon.awssdk.services.s3.S3Client;

@RunWith(JUnit4.class)
public class PesModuleTest {

  private static final String TEST_TLEDGER_URL = "http://fake-tledger.example.com";
  private static final String TEST_CONFIG_BUCKET = "my-fake-bucket";
  private static final String TEST_AWS_REGION = "us-east-1";
  private static final String TEST_KEY_BUCKET = "mbs-key-bucket";
  private static final String TEST_CERT_BUCKET = "mbs-cert-bucket";
  private static final String TEST_MBS_KEY = "mbs-key";

  @Test
  public void configure_bindings_areSatisfied() {
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + TEST_TLEDGER_URL,
            "--configuration-bucket-prefix=" + TEST_CONFIG_BUCKET,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadata =
        AwsInstanceMetadata.builder()
            .setRegion(TEST_AWS_REGION)
            .setAccountId("123456789012")
            .setEnvironment("testenv")
            .setDomain("testdomain")
            .setInstanceId("i-testinstance")
            .build();
    com.google.inject.Module testModule =
        com.google.inject.util.Modules.override(new PesModule(args, metadata))
            .with(new com.google.mbs.dummy.DummyMbsModule());
    Injector injector = Guice.createInjector(testModule);

    assertThat(injector).isNotNull();
    assertThat(injector.getInstance(Key.get(String.class, TLedgerUrl.class)))
        .isEqualTo(TEST_TLEDGER_URL);
    assertThat(injector.getInstance(Key.get(String.class, PolicyBucket.class)))
        .isEqualTo("my-fake-bucket-123456789012-us-east-1");
    assertThat(injector.getInstance(Key.get(String.class, TsaConfigBucketName.class)))
        .isEqualTo("pes-tsa-config-123456789012-us-east-1");
    assertThat(injector.getInstance(TLog.class)).isInstanceOf(TLedger.class);
    assertThat(injector.getInstance(PolicyProvider.class)).isInstanceOf(S3PolicyProvider.class);
    assertThat(injector.getInstance(SignatureGenerator.class))
        .isInstanceOf(SignatureGeneratorImpl.class);
    assertThat(injector.getInstance(SignatureVerifier.class))
        .isInstanceOf(SignatureVerifierImpl.class);
    assertThat(injector.getInstance(TsaConfigProvider.class))
        .isInstanceOf(S3TsaConfigProvider.class);
    assertThat(injector.getInstance(TsaConfigProvider.class))
        .isSameInstanceAs(injector.getInstance(TsaConfigProvider.class));
    assertThat(injector.getInstance(TsaUrlSelector.class)).isInstanceOf(RandomTsaUrlSelector.class);
    assertThat(injector.getInstance(TsaUrlSelector.class))
        .isSameInstanceAs(injector.getInstance(TsaUrlSelector.class));
    assertThat(injector.getInstance(TsaClient.class)).isInstanceOf(SimpleTsaClient.class);
    assertThat(injector.getInstance(TsaClient.class))
        .isSameInstanceAs(injector.getInstance(TsaClient.class));
    java.net.http.HttpClient httpClient = injector.getInstance(java.net.http.HttpClient.class);
    assertThat(httpClient).isNotNull();
    assertThat(httpClient.connectTimeout()).hasValue(java.time.Duration.ofSeconds(5));
    assertThat(injector.getInstance(OidcAudienceValidator.class))
        .isInstanceOf(OidcAudienceValidator.class);
    assertThat(injector.getInstance(com.google.pes.domain.PublicEndorsementService.class))
        .isNotNull();
    assertThat(injector.getInstance(PesGrpcHandler.class)).isNotNull();

    Map<Statement.Format, PublisherIdProvider> validatorMap = injector.getInstance(new Key<>() {});
    assertThat(validatorMap).containsKey(Statement.Format.JSON_INTOTO);
    assertThat(validatorMap.get(Statement.Format.JSON_INTOTO))
        .isInstanceOf(JsonPublisherIdProvider.class);
  }

  @Test
  public void provideHttpClient_returnsInstance() {
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + TEST_TLEDGER_URL,
            "--configuration-bucket-prefix=" + TEST_CONFIG_BUCKET,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadata =
        AwsInstanceMetadata.builder()
            .setRegion(TEST_AWS_REGION)
            .setAccountId("123456789012")
            .setEnvironment("testenv")
            .setDomain("testdomain")
            .setInstanceId("i-testinstance")
            .build();
    Injector injector = Guice.createInjector(new PesModule(args, metadata));
    CloseableHttpClient client = injector.getInstance(CloseableHttpClient.class);
    assertThat(client).isNotNull();
  }

  @Test
  public void provideObjectMapper_returnsInstance() {
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + TEST_TLEDGER_URL,
            "--configuration-bucket-prefix=" + TEST_CONFIG_BUCKET,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadata =
        AwsInstanceMetadata.builder()
            .setRegion(TEST_AWS_REGION)
            .setAccountId("123456789012")
            .setEnvironment("testenv")
            .setDomain("testdomain")
            .setInstanceId("i-testinstance")
            .build();
    Injector injector = Guice.createInjector(new PesModule(args, metadata));
    ObjectMapper mapper = injector.getInstance(ObjectMapper.class);
    assertThat(mapper).isNotNull();
  }

  @Test
  public void provideS3Client_returnsInstance() {
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + TEST_TLEDGER_URL,
            "--configuration-bucket-prefix=" + TEST_CONFIG_BUCKET,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadata =
        AwsInstanceMetadata.builder()
            .setRegion(TEST_AWS_REGION)
            .setAccountId("123456789012")
            .setEnvironment("testenv")
            .setDomain("testdomain")
            .setInstanceId("i-testinstance")
            .build();
    Injector injector = Guice.createInjector(new PesModule(args, metadata));
    S3Client client = injector.getInstance(S3Client.class);
    assertThat(client).isNotNull();
  }

  @Test
  public void constructor_setsValuesCorrectly() {
    String tLedgerUrl = "http://test.url";
    String configBucket = "test-bucket";
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + tLedgerUrl,
            "--configuration-bucket-prefix=" + configBucket,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadata =
        AwsInstanceMetadata.builder()
            .setRegion(TEST_AWS_REGION)
            .setAccountId("123456789012")
            .setEnvironment("testenv")
            .setDomain("testdomain")
            .setInstanceId("i-testinstance")
            .build();
    PesModule module = new PesModule(args, metadata);
    Injector injector = Guice.createInjector(module);

    assertThat(injector.getInstance(Key.get(String.class, TLedgerUrl.class))).isEqualTo(tLedgerUrl);
    assertThat(injector.getInstance(Key.get(String.class, PolicyBucket.class)))
        .isEqualTo(configBucket + "-123456789012-" + TEST_AWS_REGION);
  }

  @Test
  public void providePrometheusMeterRegistry_returnsInstance() {
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + TEST_TLEDGER_URL,
            "--configuration-bucket-prefix=" + TEST_CONFIG_BUCKET,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadata =
        new AwsInstanceMetadata(
            TEST_AWS_REGION, "123456789012", "testenv", "testdomain", "i-testinstance");
    Injector injector = Guice.createInjector(new PesModule(args, metadata));
    PrometheusMeterRegistry registry = injector.getInstance(PrometheusMeterRegistry.class);
    assertThat(registry).isNotNull();
  }

  @Test
  public void configure_bindsMeasurementBoundCertificateProvider() {
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + TEST_TLEDGER_URL,
            "--configuration-bucket-prefix=" + TEST_CONFIG_BUCKET,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadata =
        AwsInstanceMetadata.builder()
            .setRegion(TEST_AWS_REGION)
            .setAccountId("123456789012")
            .setEnvironment("testenv")
            .setDomain("testdomain")
            .setInstanceId("i-testinstance")
            .build();
    com.google.inject.Module testModule =
        com.google.inject.util.Modules.override(new PesModule(args, metadata))
            .with(new com.google.mbs.dummy.DummyMbsModule());
    Injector injector = Guice.createInjector(testModule);

    MeasurementBoundCertificateProvider provider =
        injector.getInstance(MeasurementBoundCertificateProvider.class);
    MeasurementBoundCertificateReloader reloader =
        injector.getInstance(MeasurementBoundCertificateReloader.class);
    assertThat(provider).isNotNull();
    assertThat(reloader).isNotNull();

    assertThrows(IllegalStateException.class, provider::getActiveTrustPackage);

    reloader.reloadCertificate();
    assertThat(provider.getActiveTrustPackage().bundles().get(0)).isNotNull();
    assertThat(provider.getActiveTrustPackage().bundles().get(0).getCertificate()).isNotNull();
    assertThat(provider.getActiveTrustPackage().bundles().get(0).getPrivateKey()).isNotNull();
  }

  @Test
  public void configure_bindsCertificateMonitor() {
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + TEST_TLEDGER_URL,
            "--configuration-bucket-prefix=" + TEST_CONFIG_BUCKET,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadata =
        AwsInstanceMetadata.builder()
            .setRegion(TEST_AWS_REGION)
            .setAccountId("123456789012")
            .setEnvironment("testenv")
            .setDomain("testdomain")
            .setInstanceId("i-testinstance")
            .build();
    com.google.inject.Module testModule =
        com.google.inject.util.Modules.override(new PesModule(args, metadata))
            .with(new com.google.mbs.dummy.DummyMbsModule());
    Injector injector = Guice.createInjector(testModule);

    CertificateMonitor monitor = injector.getInstance(CertificateMonitor.class);
    assertThat(monitor).isNotNull();
  }

  @Test
  public void configure_kmsMode_bindsRequiredDependencies() {
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + TEST_TLEDGER_URL,
            "--configuration-bucket-prefix=" + TEST_CONFIG_BUCKET,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadata =
        AwsInstanceMetadata.builder()
            .setRegion(TEST_AWS_REGION)
            .setAccountId("123456789012")
            .setEnvironment("testenv")
            .setDomain("testdomain")
            .setInstanceId("i-testinstance")
            .build();

    Injector kmsInjector = Guice.createInjector(new PesModule(args, metadata));

    assertThat(kmsInjector).isNotNull();
    assertThat(kmsInjector.getInstance(Key.get(String.class, InstanceId.class)))
        .isEqualTo("i-testinstance");
    assertThat(kmsInjector.getInstance(Key.get(String.class, KmsKeyArn.class))).isNotNull();
    assertThat(kmsInjector.getInstance(Key.get(String.class, PublicBackupBucket.class)))
        .isNotNull();
    assertThat(kmsInjector.getInstance(Key.get(String.class, PrivateBackupBucket.class)))
        .isNotNull();
    assertThat(kmsInjector.getInstance(com.google.mbs.domain.Metrics.class))
        .isInstanceOf(SystemMetrics.class);
    assertThat(kmsInjector.getInstance(MeasurementBoundCertificateProvider.class)).isNotNull();
    assertThat(kmsInjector.getInstance(MeasurementBoundCertificateReloader.class)).isNotNull();
    assertThat(kmsInjector.getInstance(CertificateMonitor.class)).isNotNull();
  }

  @Test
  public void provideMbsCertificateFactory_generatesCertificateWithCorrectSpiffeIdInSan()
      throws Exception {
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + TEST_TLEDGER_URL,
            "--configuration-bucket-prefix=" + TEST_CONFIG_BUCKET,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadata =
        AwsInstanceMetadata.builder()
            .setRegion(TEST_AWS_REGION)
            .setAccountId("123456789012")
            .setEnvironment("testenv")
            .setDomain("testdomain")
            .setInstanceId("i-testinstance")
            .build();

    PesModule module = new PesModule(args, metadata);
    MbsCertificateFactory factory = module.provideMbsCertificateFactory();
    X509Certificate rootCert = factory.generate().certificate();

    assertThat(rootCert.getSubjectX500Principal().getName()).isEqualTo("CN=PES,O=Google LLC,C=US");

    String expectedSpiffeId =
        "spiffe://pes.testenv.testdomain/operator/pcit.goog/123456789012/publisher/google.com/pcit-release-bot/workload/public-endorsement-service";

    byte[] sanExtensionValue = rootCert.getExtensionValue(Extension.subjectAlternativeName.getId());
    assertThat(sanExtensionValue).isNotNull();

    GeneralNames names =
        GeneralNames.getInstance(ASN1OctetString.getInstance(sanExtensionValue).getOctets());
    assertThat(names.getNames()).hasLength(1);

    GeneralName sanEntry = names.getNames()[0];
    assertThat(sanEntry.getTagNo()).isEqualTo(GeneralName.uniformResourceIdentifier);
    assertThat(sanEntry.getName().toString()).isEqualTo(expectedSpiffeId);
  }

  @Test
  public void provideMbsCertificateFactory_prodEnv_stripsAwsSubdomain() throws Exception {
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + TEST_TLEDGER_URL,
            "--configuration-bucket-prefix=" + TEST_CONFIG_BUCKET,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadata =
        AwsInstanceMetadata.builder()
            .setRegion(TEST_AWS_REGION)
            .setAccountId("123456789012")
            .setEnvironment("prod")
            .setDomain("aws.pcit.goog")
            .setInstanceId("i-testinstance")
            .build();

    PesModule module = new PesModule(args, metadata);
    MbsCertificateFactory factory = module.provideMbsCertificateFactory();
    X509Certificate rootCert = factory.generate().certificate();

    String expectedSpiffeId =
        "spiffe://pes.pcit.goog/operator/pcit.goog/123456789012/publisher/google.com/pcit-release-bot/workload/public-endorsement-service";

    byte[] sanExtensionValue = rootCert.getExtensionValue(Extension.subjectAlternativeName.getId());
    assertThat(sanExtensionValue).isNotNull();

    GeneralNames names =
        GeneralNames.getInstance(ASN1OctetString.getInstance(sanExtensionValue).getOctets());
    GeneralName sanEntry = names.getNames()[0];
    assertThat(sanEntry.getTagNo()).isEqualTo(GeneralName.uniformResourceIdentifier);
    assertThat(sanEntry.getName().toString()).isEqualTo(expectedSpiffeId);
  }

  @Test
  public void configure_nullInstanceId_throwsNullPointerException() {
    PesArgs args = new PesArgs();
    JCommander.newBuilder()
        .addObject(args)
        .build()
        .parse(
            "--tledger-url=" + TEST_TLEDGER_URL,
            "--configuration-bucket-prefix=" + TEST_CONFIG_BUCKET,
            "--mbs-kms-key-suffix=" + TEST_MBS_KEY,
            "--cert-backup-bucket-prefix=" + TEST_CERT_BUCKET,
            "--key-backup-bucket-prefix=" + TEST_KEY_BUCKET);
    AwsInstanceMetadata metadataWithNullInstanceId =
        new AwsInstanceMetadata(
            TEST_AWS_REGION, "123456789012", "testenv", "testdomain", /* instanceId= */ null);

    PesModule module = new PesModule(args, metadataWithNullInstanceId);
    com.google.inject.CreationException thrown =
        org.junit.Assert.assertThrows(
            com.google.inject.CreationException.class, () -> Guice.createInjector(module));
    assertThat(thrown).hasCauseThat().isInstanceOf(NullPointerException.class);
    assertThat(thrown).hasCauseThat().hasMessageThat().contains("instanceId cannot be null");
  }
}
