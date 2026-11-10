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

package com.google.pes.adapters.tlog;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.pes.domain.ports.TLogException;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RunWith(JUnit4.class)
public class TLedgerCertificatFetcherTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private S3Client s3Client;
  private TLedgerCertificatFetcher fetcher;

  private static final String BUCKET = "test-bucket";
  private static final int KEY_ID = 7;
  private static final byte[] CERT_DATA = "fake-cert".getBytes(UTF_8);

  @Before
  public void setUp() {
    fetcher = new TLedgerCertificatFetcher(s3Client, BUCKET);
  }

  @Test
  public void fetch_success_readsCertificateFromKeyIdPath() {
    stubS3(new ByteArrayInputStream(CERT_DATA));

    ByteString result = fetcher.fetch(KEY_ID);

    assertThat(result.toByteArray()).isEqualTo(CERT_DATA);
    verify(s3Client)
        .getObject(
            GetObjectRequest.builder().bucket(BUCKET).key("public/7/root_certificate.pem").build());
  }

  @Test
  public void fetch_s3Exception_throwsTLogException() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(S3Exception.builder().message("S3 error").build());

    assertThrows(TLogException.class, () -> fetcher.fetch(KEY_ID));
  }

  @Test
  public void fetch_ioException_throwsTLogException() {
    stubS3(
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("IO error");
          }

          @Override
          public int read(byte[] b, int off, int len) throws IOException {
            throw new IOException("IO error");
          }
        });

    TLogException e = assertThrows(TLogException.class, () -> fetcher.fetch(KEY_ID));

    assertThat(e).hasMessageThat().contains("Failed to read");
  }

  private void stubS3(InputStream content) {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(), content));
  }
}
