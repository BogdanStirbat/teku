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

package tech.pegasys.teku.validator.client;

import java.nio.file.Path;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.signatures.LocalSlashingProtector;
import tech.pegasys.teku.core.signatures.SlashingProtector;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.io.SystemSignalListener;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyFactory;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.loader.PublicKeyLoader;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.eventadapter.InProcessBeaconNodeApi;
import tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi;

public class ValidatorClientService extends Service {
  private final EventChannels eventChannels;
  private final ValidatorLoader validatorLoader;
  private final BeaconNodeApi beaconNodeApi;
  private final ForkProvider forkProvider;
  private final Spec spec;

  private ValidatorTimingChannel attestationTimingChannel;
  private ValidatorTimingChannel blockProductionTimingChannel;
  private ValidatorStatusLogger validatorStatusLogger;
  private ValidatorIndexProvider validatorIndexProvider;

  private final SafeFuture<Void> initializationComplete = new SafeFuture<>();

  private MetricsSystem metricsSystem;

  private ValidatorClientService(
      final EventChannels eventChannels,
      final ValidatorLoader validatorLoader,
      final BeaconNodeApi beaconNodeApi,
      final ForkProvider forkProvider,
      final Spec spec,
      final MetricsSystem metricsSystem) {
    this.eventChannels = eventChannels;
    this.validatorLoader = validatorLoader;
    this.beaconNodeApi = beaconNodeApi;
    this.forkProvider = forkProvider;
    this.spec = spec;
    this.metricsSystem = metricsSystem;
  }

  public static ValidatorClientService create(
      final ServiceConfig services, final ValidatorClientConfiguration config) {
    final EventChannels eventChannels = services.getEventChannels();
    final AsyncRunner asyncRunner = services.createAsyncRunner("validator");
    final boolean useDependentRoots = config.getValidatorConfig().useDependentRoots();
    final BeaconNodeApi beaconNodeApi =
        config
            .getValidatorConfig()
            .getBeaconNodeApiEndpoint()
            .map(
                endpoint ->
                    RemoteBeaconNodeApi.create(
                        services, asyncRunner, endpoint, config.getSpec(), useDependentRoots))
            .orElseGet(
                () ->
                    InProcessBeaconNodeApi.create(
                        services, asyncRunner, useDependentRoots, config.getSpec()));
    final ValidatorApiChannel validatorApiChannel = beaconNodeApi.getValidatorApi();
    final GenesisDataProvider genesisDataProvider =
        new GenesisDataProvider(asyncRunner, validatorApiChannel);
    final ForkProvider forkProvider =
        new ForkProvider(asyncRunner, validatorApiChannel, genesisDataProvider);

    final ValidatorLoader validatorLoader = createValidatorLoader(config, asyncRunner, services);

    ValidatorClientService validatorClientService =
        new ValidatorClientService(
            eventChannels,
            validatorLoader,
            beaconNodeApi,
            forkProvider,
            config.getSpec(),
            services.getMetricsSystem());

    asyncRunner
        .runAsync(
            () ->
                validatorClientService.initializeValidators(
                    config, validatorApiChannel, asyncRunner))
        .propagateTo(validatorClientService.initializationComplete);
    return validatorClientService;
  }

  private static ValidatorLoader createValidatorLoader(
      final ValidatorClientConfiguration config,
      final AsyncRunner asyncRunner,
      final ServiceConfig services) {
    final Path slashingProtectionPath = getSlashingProtectionPath(services.getDataDirLayout());
    final SlashingProtector slashingProtector =
        new LocalSlashingProtector(new SyncDataAccessor(), slashingProtectionPath);
    return ValidatorLoader.create(
        config.getSpec(),
        config.getValidatorConfig(),
        config.getInteropConfig(),
        slashingProtector,
        new PublicKeyLoader(),
        asyncRunner,
        services.getMetricsSystem());
  }

  private void initializeValidators(
      ValidatorClientConfiguration config,
      ValidatorApiChannel validatorApiChannel,
      AsyncRunner asyncRunner) {
    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();
    this.validatorIndexProvider = new ValidatorIndexProvider(validators, validatorApiChannel);
    final ValidatorDutyFactory validatorDutyFactory =
        new ValidatorDutyFactory(forkProvider, validatorApiChannel, spec);
    final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions =
        new BeaconCommitteeSubscriptions(validatorApiChannel);
    final DutyLoader attestationDutyLoader =
        new RetryingDutyLoader(
            asyncRunner,
            new AttestationDutyLoader(
                validatorApiChannel,
                forkProvider,
                dependentRoot ->
                    new ScheduledDuties(validatorDutyFactory, dependentRoot, metricsSystem),
                validators,
                validatorIndexProvider,
                beaconCommitteeSubscriptions,
                spec));
    final DutyLoader blockDutyLoader =
        new RetryingDutyLoader(
            asyncRunner,
            new BlockProductionDutyLoader(
                validatorApiChannel,
                dependentRoot ->
                    new ScheduledDuties(validatorDutyFactory, dependentRoot, metricsSystem),
                validators,
                validatorIndexProvider));
    final boolean useDependentRoots = config.getValidatorConfig().useDependentRoots();
    this.attestationTimingChannel =
        new AttestationDutyScheduler(metricsSystem, attestationDutyLoader, useDependentRoots, spec);
    this.blockProductionTimingChannel =
        new BlockDutyScheduler(metricsSystem, blockDutyLoader, useDependentRoots, spec);
    addValidatorCountMetric(metricsSystem, validators);
    this.validatorStatusLogger =
        new DefaultValidatorStatusLogger(validators, validatorApiChannel, asyncRunner);
  }

  public static Path getSlashingProtectionPath(final DataDirLayout dataDirLayout) {
    return dataDirLayout.getValidatorDataDirectory().resolve("slashprotection");
  }

  private static void addValidatorCountMetric(
      final MetricsSystem metricsSystem, final OwnedValidators validators) {
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "local_validator_count",
        "Current number of validators running in this validator client",
        validators::getValidatorCount);
  }

  @Override
  protected SafeFuture<?> doStart() {
    return initializationComplete.thenCompose(
        (__) -> {
          SystemSignalListener.registerReloadConfigListener(validatorLoader::loadValidators);
          forkProvider.start().reportExceptions();
          validatorIndexProvider.lookupValidators();
          eventChannels.subscribe(
              ValidatorTimingChannel.class,
              new ValidatorTimingActions(
                  validatorStatusLogger,
                  validatorIndexProvider,
                  blockProductionTimingChannel,
                  attestationTimingChannel,
                  spec,
                  metricsSystem));
          validatorStatusLogger.printInitialValidatorStatuses().reportExceptions();
          return beaconNodeApi.subscribeToEvents();
        });
  }

  @Override
  protected SafeFuture<?> doStop() {
    return beaconNodeApi.unsubscribeFromEvents();
  }
}
