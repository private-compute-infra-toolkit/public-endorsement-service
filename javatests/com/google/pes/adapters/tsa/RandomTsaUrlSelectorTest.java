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

package com.google.pes.adapters.tsa;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.pes.domain.model.TsaConfig;
import com.google.pes.domain.ports.TsaConfigProvider;
import com.google.pes.domain.ports.TsaException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RandomTsaUrlSelectorTest {
  private static final URI URL_1 = URI.create("https://tsa1.example.com/timestamp");
  private static final URI URL_2 = URI.create("https://tsa2.example.com/timestamp");
  private static final URI URL_3 = URI.create("https://tsa3.example.com/timestamp");

  @Test
  public void selectUrl_singleUrl_alwaysReturnsUrlWithoutInvokingRandomGenerator() {
    TsaConfigProvider mockProvider = mock(TsaConfigProvider.class);
    when(mockProvider.getConfig()).thenReturn(new TsaConfig(true, List.of(URL_1)));
    RandomGenerator mockRandom = mock(RandomGenerator.class);

    RandomTsaUrlSelector selector = new RandomTsaUrlSelector(mockProvider, mockRandom);
    for (int i = 0; i < 10; i++) {
      assertThat(selector.selectUrl()).hasValue(URL_1);
    }
    verifyNoInteractions(mockRandom);
  }

  @Test
  public void selectUrl_multipleUrls_usesProvidedRandomGenerator() {
    List<URI> urls = List.of(URL_1, URL_2, URL_3);
    TsaConfigProvider mockProvider = mock(TsaConfigProvider.class);
    when(mockProvider.getConfig()).thenReturn(new TsaConfig(true, urls));

    RandomGenerator mockRandom = mock(RandomGenerator.class);
    when(mockRandom.nextInt(3)).thenReturn(0, 1, 2);

    RandomTsaUrlSelector selector = new RandomTsaUrlSelector(mockProvider, mockRandom);

    assertThat(selector.selectUrl()).hasValue(URL_1);
    assertThat(selector.selectUrl()).hasValue(URL_2);
    assertThat(selector.selectUrl()).hasValue(URL_3);
  }

  @Test
  public void selectUrl_multipleUrls_returnsConfiguredUrls() {
    List<URI> urls = List.of(URL_1, URL_2, URL_3);
    TsaConfigProvider mockProvider = mock(TsaConfigProvider.class);
    when(mockProvider.getConfig()).thenReturn(new TsaConfig(true, urls));

    // Use a fixed seed to guarantee deterministic, non-flaky test execution across 100 iterations.
    RandomTsaUrlSelector selector = new RandomTsaUrlSelector(mockProvider, new Random(42L));

    Set<URI> seen = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      Optional<URI> selected = selector.selectUrl();
      assertThat(selected).isPresent();
      URI uri = selected.orElseThrow();
      assertThat(uri).isIn(urls);
      seen.add(uri);
    }
    assertThat(seen).containsExactlyElementsIn(urls);
  }

  @Test
  public void selectUrl_defaultConstructorWithMultipleUrls_returnsConfiguredUrl() {
    List<URI> urls = List.of(URL_1, URL_2, URL_3);
    TsaConfigProvider mockProvider = mock(TsaConfigProvider.class);
    when(mockProvider.getConfig()).thenReturn(new TsaConfig(true, urls));

    RandomTsaUrlSelector selector = new RandomTsaUrlSelector(mockProvider);

    for (int i = 0; i < 20; i++) {
      Optional<URI> selected = selector.selectUrl();
      assertThat(selected).isPresent();
      assertThat(selected.orElseThrow()).isIn(urls);
    }
  }

  @Test
  public void selectUrl_emptyUrls_throwsTsaException() {
    TsaConfigProvider mockProvider = mock(TsaConfigProvider.class);
    when(mockProvider.getConfig()).thenReturn(new TsaConfig(true, List.of()));

    RandomTsaUrlSelector selector = new RandomTsaUrlSelector(mockProvider);

    TsaException thrown = assertThrows(TsaException.class, selector::selectUrl);
    assertThat(thrown).hasMessageThat().contains("No TSA URLs available");
  }

  @Test
  public void selectUrl_disabledConfigWithUrls_returnsEmptyOptional() {
    TsaConfigProvider mockProvider = mock(TsaConfigProvider.class);
    when(mockProvider.getConfig()).thenReturn(new TsaConfig(false, List.of(URL_1)));

    RandomTsaUrlSelector selector = new RandomTsaUrlSelector(mockProvider);

    assertThat(selector.selectUrl()).isEmpty();
  }

  @Test
  public void selectUrl_disabledConfigWithEmptyUrls_returnsEmptyOptional() {
    TsaConfigProvider mockProvider = mock(TsaConfigProvider.class);
    when(mockProvider.getConfig()).thenReturn(TsaConfig.DISABLED);

    RandomTsaUrlSelector selector = new RandomTsaUrlSelector(mockProvider);

    assertThat(selector.selectUrl()).isEmpty();
  }

  @Test
  public void selectUrl_nullConfig_throwsTsaException() {
    TsaConfigProvider mockProvider = mock(TsaConfigProvider.class);
    when(mockProvider.getConfig()).thenReturn(null);

    RandomTsaUrlSelector selector = new RandomTsaUrlSelector(mockProvider);

    TsaException thrown = assertThrows(TsaException.class, selector::selectUrl);
    assertThat(thrown).hasMessageThat().contains("TSA configuration is null");
  }

  @Test
  public void selectUrl_dynamicConfigUpdates_reflectsLatestConfigurationOnEachCall() {
    TsaConfigProvider mockProvider = mock(TsaConfigProvider.class);
    when(mockProvider.getConfig())
        .thenReturn(new TsaConfig(true, List.of(URL_1)))
        .thenReturn(new TsaConfig(true, List.of(URL_2)))
        .thenReturn(TsaConfig.DISABLED);

    RandomTsaUrlSelector selector = new RandomTsaUrlSelector(mockProvider);

    assertThat(selector.selectUrl()).hasValue(URL_1);
    assertThat(selector.selectUrl()).hasValue(URL_2);
    assertThat(selector.selectUrl()).isEmpty();
  }

  @Test
  public void constructor_nullConfigProvider_throwsException() {
    assertThrows(NullPointerException.class, () -> new RandomTsaUrlSelector(null));
  }

  @Test
  public void constructor_nullRandomGenerator_throwsException() {
    TsaConfigProvider mockProvider = mock(TsaConfigProvider.class);
    assertThrows(NullPointerException.class, () -> new RandomTsaUrlSelector(mockProvider, null));
  }
}
