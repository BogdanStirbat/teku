/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common;

import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.statetransition.StateTransition;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.CommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;

public abstract class AbstractSpecLogic implements SpecLogic {
  // Helpers
  protected final Predicates predicates;
  protected final MiscHelpers miscHelpers;
  protected final BeaconStateAccessors beaconStateAccessors;
  protected final BeaconStateMutators beaconStateMutators;
  // Utils
  protected final CommitteeUtil committeeUtil;
  protected final ValidatorsUtil validatorsUtil;
  protected final BeaconStateUtil beaconStateUtil;
  protected final AttestationUtil attestationUtil;
  protected final ValidatorStatusFactory validatorStatusFactory;
  protected final EpochProcessor epochProcessor;
  protected final BlockProcessor blockProcessor;
  protected final StateTransition stateTransition;
  protected final ForkChoiceUtil forkChoiceUtil;
  protected final BlockProposalUtil blockProposalUtil;

  protected AbstractSpecLogic(
      final Predicates predicates,
      final MiscHelpers miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final CommitteeUtil committeeUtil,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final EpochProcessor epochProcessor,
      final BlockProcessor blockProcessor,
      final StateTransition stateTransition,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil) {
    this.predicates = predicates;
    this.miscHelpers = miscHelpers;
    this.beaconStateAccessors = beaconStateAccessors;
    this.beaconStateMutators = beaconStateMutators;
    this.committeeUtil = committeeUtil;
    this.validatorsUtil = validatorsUtil;
    this.beaconStateUtil = beaconStateUtil;
    this.attestationUtil = attestationUtil;
    this.validatorStatusFactory = validatorStatusFactory;
    this.epochProcessor = epochProcessor;
    this.blockProcessor = blockProcessor;
    this.stateTransition = stateTransition;
    this.forkChoiceUtil = forkChoiceUtil;
    this.blockProposalUtil = blockProposalUtil;
  }

  @Override
  public CommitteeUtil getCommitteeUtil() {
    return committeeUtil;
  }

  @Override
  public ValidatorsUtil getValidatorsUtil() {
    return validatorsUtil;
  }

  @Override
  public BeaconStateUtil getBeaconStateUtil() {
    return beaconStateUtil;
  }

  @Override
  public AttestationUtil getAttestationUtil() {
    return attestationUtil;
  }

  @Override
  public EpochProcessor getEpochProcessor() {
    return epochProcessor;
  }

  @Override
  public BlockProcessor getBlockProcessor() {
    return blockProcessor;
  }

  @Override
  public StateTransition getStateTransition() {
    return stateTransition;
  }

  @Override
  public ForkChoiceUtil getForkChoiceUtil() {
    return forkChoiceUtil;
  }

  @Override
  public BlockProposalUtil getBlockProposalUtil() {
    return blockProposalUtil;
  }

  @Override
  public ValidatorStatusFactory getValidatorStatusFactory() {
    return validatorStatusFactory;
  }

  @Override
  public Predicates predicates() {
    return predicates;
  }

  @Override
  public MiscHelpers miscHelpers() {
    return miscHelpers;
  }

  @Override
  public BeaconStateAccessors beaconStateAccessors() {
    return beaconStateAccessors;
  }

  @Override
  public BeaconStateMutators beaconStateMutators() {
    return beaconStateMutators;
  }
}
