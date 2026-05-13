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

package com.google.pes.adapters.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.flogger.FluentLogger;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Fetches OIDC configuration from a discovery endpoint. */
public final class OidcDiscoveryFetcher {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Inject
  public OidcDiscoveryFetcher(HttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  /**
   * Fetches the JWKS URI from the given OIDC discovery endpoint.
   *
   * @param discoveryUri the URI of the OIDC discovery endpoint.
   * @return the JWKS URI.
   * @throws IOException if fetching or parsing the discovery document fails.
   */
  public String fetchJwksUri(String discoveryUri) throws IOException {
    try {
      logger.atInfo().log("Fetching OIDC configuration from: %s", discoveryUri);
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(discoveryUri)).build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new IOException("Failed to fetch OIDC configuration: " + response.body());
      }

      JsonNode config = objectMapper.readTree(response.body());
      if (!config.has("jwks_uri")) {
        throw new IOException("OIDC configuration missing 'jwks_uri'");
      }

      String jwksUri = config.get("jwks_uri").asText();
      logger.atInfo().log("Discovered JWKS URI: %s", jwksUri);
      return jwksUri;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Discovery fetch interrupted", e);
    }
  }
}
