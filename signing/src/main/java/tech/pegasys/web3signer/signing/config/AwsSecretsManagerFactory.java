/*
 * Copyright 2022 ConsenSys AG.
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
package tech.pegasys.web3signer.signing.config;

import tech.pegasys.signers.aws.AwsSecretsManager;
import tech.pegasys.signers.aws.AwsSecretsManagerProvider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public class AwsSecretsManagerFactory {

  private static URI getEndpointURI(final String endpointUrl) {
    try {
      return new URI(endpointUrl);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public static AwsSecretsManager createAwsSecretsManager(
      final AwsSecretsManagerProvider awsSecretsManagerProvider,
      final AwsSecretsManagerParameters awsSecretsManagerParameters) {
    final Optional<String> endpointUrlOptional =
        Optional.ofNullable(awsSecretsManagerParameters.getEndpointUrl());
    final URI endpointUri =
        endpointUrlOptional.isPresent() ? getEndpointURI(endpointUrlOptional.get()) : null;
    switch (awsSecretsManagerParameters.getAuthenticationMode()) {
      case SPECIFIED:
        return endpointUrlOptional.isPresent()
            ? awsSecretsManagerProvider.createAwsSecretsManager(
                awsSecretsManagerParameters.getAccessKeyId(),
                awsSecretsManagerParameters.getSecretAccessKey(),
                awsSecretsManagerParameters.getRegion(),
                endpointUri)
            : awsSecretsManagerProvider.createAwsSecretsManager(
                awsSecretsManagerParameters.getAccessKeyId(),
                awsSecretsManagerParameters.getSecretAccessKey(),
                awsSecretsManagerParameters.getRegion());
      default:
        return awsSecretsManagerProvider.createAwsSecretsManager();
    }
  }
}
