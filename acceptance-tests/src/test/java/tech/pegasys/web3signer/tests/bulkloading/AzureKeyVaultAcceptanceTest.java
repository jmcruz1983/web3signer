/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.web3signer.tests.bulkloading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import tech.pegasys.web3signer.dsl.signer.SignerConfigurationBuilder;
import tech.pegasys.web3signer.dsl.utils.DefaultAzureKeyVaultParameters;
import tech.pegasys.web3signer.signing.KeyType;
import tech.pegasys.web3signer.signing.config.AzureKeyVaultParameters;
import tech.pegasys.web3signer.tests.AcceptanceTestBase;

import java.util.Map;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class AzureKeyVaultAcceptanceTest extends AcceptanceTestBase {

  private static final String CLIENT_ID = System.getenv("AZURE_CLIENT_ID");
  private static final String CLIENT_SECRET = System.getenv("AZURE_CLIENT_SECRET");
  private static final String TENANT_ID = System.getenv("AZURE_TENANT_ID");
  private static final String VAULT_NAME = System.getenv("AZURE_KEY_VAULT_NAME");
  private static final String EXPECTED_KEY =
      "0x989d34725a2bfc3f15105f3f5fc8741f436c25ee1ee4f948e425d6bcb8c56bce6e06c269635b7e985a7ffa639e2409bf";
  private static final String EXPECTED_TAGGED_KEY =
      "0xb3b6fb8dab2a4c9d00247c18c4b7e91c62da3f7ad31c822c00097f93ac8ff2c4a526611f7d0a9c85946e93f371852c69";

  @BeforeAll
  public static void setup() {
    Assumptions.assumeTrue(CLIENT_ID != null, "Set AZURE_CLIENT_ID environment variable");
    Assumptions.assumeTrue(CLIENT_SECRET != null, "Set AZURE_CLIENT_SECRET environment variable");
    Assumptions.assumeTrue(TENANT_ID != null, "Set AZURE_TENANT_ID environment variable");
    Assumptions.assumeTrue(VAULT_NAME != null, "Set AZURE_KEY_VAULT_NAME environment variable");
  }

  @Test
  void ensureSecretsInKeyVaultAreLoadedAndReportedViaPublicKeysApi() {
    final AzureKeyVaultParameters azureParams =
        new DefaultAzureKeyVaultParameters(VAULT_NAME, CLIENT_ID, TENANT_ID, CLIENT_SECRET);

    final SignerConfigurationBuilder configBuilder =
        new SignerConfigurationBuilder().withMode("eth2").withAzureKeyVaultParameters(azureParams);

    startSigner(configBuilder.build());

    final Response response = signer.callApiPublicKeys(KeyType.BLS);
    response
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("", hasItems(EXPECTED_KEY, EXPECTED_TAGGED_KEY));

    final Response healthcheckResponse = signer.healthcheck();
    healthcheckResponse
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("status", equalTo("UP"));

    final String jsonBody = healthcheckResponse.body().asString();
    int keysLoaded = getAzureBulkLoadingData(jsonBody, "keys-loaded");
    assertThat(keysLoaded)
        .isEqualTo(202); // ACCTEST-MULTILINE-KEY (200) + TEST-KEY (1) + TEST-KEY-2 (1)
  }

  @ParameterizedTest(name = "{index} - Using config file: {0}")
  @ValueSource(booleans = {true, false})
  void azureSecretsViaTag(boolean useConfigFile) {
    final AzureKeyVaultParameters azureParams =
        new DefaultAzureKeyVaultParameters(
            VAULT_NAME, CLIENT_ID, TENANT_ID, CLIENT_SECRET, Map.of("ENV", "TEST"));

    final SignerConfigurationBuilder configBuilder =
        new SignerConfigurationBuilder()
            .withMode("eth2")
            .withUseConfigFile(useConfigFile)
            .withAzureKeyVaultParameters(azureParams);

    startSigner(configBuilder.build());

    final Response response = signer.callApiPublicKeys(KeyType.BLS);
    response
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("", hasItem(EXPECTED_TAGGED_KEY));

    // the tag filter will return only valid keys. The healtcheck should be UP
    final Response healthcheckResponse = signer.healthcheck();
    healthcheckResponse
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("status", equalTo("UP"));

    // keys loaded should be 1 as well.
    final String jsonBody = healthcheckResponse.body().asString();
    int keysLoaded = getAzureBulkLoadingData(jsonBody, "keys-loaded");
    int errorCount = getAzureBulkLoadingData(jsonBody, "error-count");
    assertThat(keysLoaded).isOne();
    assertThat(errorCount).isZero();
  }

  private static int getAzureBulkLoadingData(String healthCheckJsonBody, String dataKey) {
    JsonObject jsonObject = new JsonObject(healthCheckJsonBody);
    int keysLoaded =
        jsonObject.getJsonArray("checks").stream()
            .filter(o -> "keys-check".equals(((JsonObject) o).getString("id")))
            .flatMap(o -> ((JsonObject) o).getJsonArray("checks").stream())
            .filter(o -> "azure-bulk-loading".equals(((JsonObject) o).getString("id")))
            .mapToInt(o -> ((JsonObject) ((JsonObject) o).getValue("data")).getInteger(dataKey))
            .findFirst()
            .orElse(-1);
    return keysLoaded;
  }

  @Test
  void invalidVaultParametersFailsToLoadKeys() {
    final AzureKeyVaultParameters azureParams =
        new DefaultAzureKeyVaultParameters("nonExistentVault", CLIENT_ID, TENANT_ID, CLIENT_SECRET);

    final SignerConfigurationBuilder configBuilder =
        new SignerConfigurationBuilder().withMode("eth2").withAzureKeyVaultParameters(azureParams);

    startSigner(configBuilder.build());

    final Response response = signer.callApiPublicKeys(KeyType.BLS);
    response.then().statusCode(200).contentType(ContentType.JSON).body("", hasSize(0));

    signer
        .healthcheck()
        .then()
        .statusCode(503)
        .contentType(ContentType.JSON)
        .body("status", equalTo("DOWN"));
  }

  @Test
  void envVarsAreUsedToDefaultAzureParams() {
    // This ensures env vars correspond to the WEB3SIGNER_<subcommand>_<option> syntax
    final Map<String, String> env =
        Map.of(
            "WEB3SIGNER_ETH2_AZURE_VAULT_ENABLED", "true",
            "WEB3SIGNER_ETH2_AZURE_VAULT_NAME", VAULT_NAME,
            "WEB3SIGNER_ETH2_AZURE_CLIENT_ID", CLIENT_ID,
            "WEB3SIGNER_ETH2_AZURE_CLIENT_SECRET", CLIENT_SECRET,
            "WEB3SIGNER_ETH2_AZURE_TENANT_ID", TENANT_ID);

    final SignerConfigurationBuilder configBuilder =
        new SignerConfigurationBuilder().withMode("eth2").withEnvironment(env);

    startSigner(configBuilder.build());

    final Response response = signer.callApiPublicKeys(KeyType.BLS);
    response.then().statusCode(200).contentType(ContentType.JSON).body("", hasItem(EXPECTED_KEY));
  }
}
