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
package tech.pegasys.web3signer.signing;

import tech.pegasys.teku.bls.BLSSignature;

public class BlsArtifactSignature implements ArtifactSignature<BLSSignature> {
  private final BLSSignature blsSignature;

  public BlsArtifactSignature(final BLSSignature blsSignature) {
    this.blsSignature = blsSignature;
  }

  @Override
  public KeyType getType() {
    return KeyType.BLS;
  }

  @Override
  public BLSSignature getSignatureData() {
    return blsSignature;
  }

  @Override
  public String asHex() {
    return blsSignature.toString();
  }
}
