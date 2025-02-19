/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.cli.subcommands.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.trielog.TrieLogPruner;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/** The Trie Log subcommand. */
@Command(
    name = "x-trie-log",
    description = "Manipulate trie logs",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {
      TrieLogSubCommand.CountTrieLog.class,
      TrieLogSubCommand.PruneTrieLog.class,
      TrieLogSubCommand.ExportTrieLog.class,
      TrieLogSubCommand.ImportTrieLog.class
    })
public class TrieLogSubCommand implements Runnable {

  @SuppressWarnings("UnusedVariable")
  @ParentCommand
  private static StorageSubCommand parentCommand;

  @SuppressWarnings("unused")
  @CommandLine.Spec
  private CommandLine.Model.CommandSpec spec; // Picocli injects reference to command spec

  @Override
  public void run() {
    final PrintWriter out = spec.commandLine().getOut();
    spec.commandLine().usage(out);
  }

  private static BesuController createBesuController() {
    return parentCommand.parentCommand.buildController();
  }

  @Command(
      name = "count",
      description = "This command counts all the trie logs",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class CountTrieLog implements Runnable {

    @SuppressWarnings("unused")
    @ParentCommand
    private TrieLogSubCommand parentCommand;

    @SuppressWarnings("unused")
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec; // Picocli injects reference to command spec

    @Override
    public void run() {
      TrieLogContext context = getTrieLogContext();

      final PrintWriter out = spec.commandLine().getOut();

      out.println("Counting trie logs...");
      TrieLogHelper.printCount(
          out,
          TrieLogHelper.getCount(
              context.rootWorldStateStorage, Integer.MAX_VALUE, context.blockchain));
    }
  }

  @Command(
      name = "prune",
      description =
          "This command prunes all trie log layers below the retention threshold, including orphaned trie logs.",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class PruneTrieLog implements Runnable {

    @SuppressWarnings("unused")
    @ParentCommand
    private TrieLogSubCommand parentCommand;

    @SuppressWarnings("unused")
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec; // Picocli injects reference to command spec

    @Override
    public void run() {
      TrieLogContext context = getTrieLogContext();
      final Path dataDirectoryPath =
          Paths.get(
              TrieLogSubCommand.parentCommand.parentCommand.dataDir().toAbsolutePath().toString());
      TrieLogHelper.prune(
          context.config(),
          context.rootWorldStateStorage(),
          context.blockchain(),
          dataDirectoryPath);
    }
  }

  @Command(
      name = "export",
      description = "This command exports the trie log of a determined block to a binary file",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class ExportTrieLog implements Runnable {

    @SuppressWarnings("unused")
    @ParentCommand
    private TrieLogSubCommand parentCommand;

    @SuppressWarnings("unused")
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec; // Picocli injects reference to command spec

    @CommandLine.Option(
        names = "--trie-log-block-hash",
        description =
            "Comma separated list of hashes from the blocks you want to export the trie logs of",
        split = " {0,1}, {0,1}",
        arity = "1..*")
    private List<String> trieLogBlockHashList;

    @CommandLine.Option(
        names = "--trie-log-file-path",
        description = "The file you want to export the trie logs to",
        arity = "1..1")
    private Path trieLogFilePath = null;

    @Override
    public void run() {
      if (trieLogFilePath == null) {
        trieLogFilePath =
            Paths.get(
                TrieLogSubCommand.parentCommand
                    .parentCommand
                    .dataDir()
                    .resolve("trie-logs.bin")
                    .toAbsolutePath()
                    .toString());
      }

      TrieLogContext context = getTrieLogContext();

      final List<Hash> listOfBlockHashes =
          trieLogBlockHashList.stream().map(Hash::fromHexString).toList();

      try {
        TrieLogHelper.exportTrieLog(
            context.rootWorldStateStorage(), listOfBlockHashes, trieLogFilePath);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Command(
      name = "import",
      description = "This command imports a trie log exported by another besu node",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class ImportTrieLog implements Runnable {

    @SuppressWarnings("unused")
    @ParentCommand
    private TrieLogSubCommand parentCommand;

    @SuppressWarnings("unused")
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec; // Picocli injects reference to command spec

    @CommandLine.Option(
        names = "--trie-log-file-path",
        description = "The file you want to import the trie logs from",
        arity = "1..1")
    private Path trieLogFilePath = null;

    @Override
    public void run() {
      if (trieLogFilePath == null) {
        trieLogFilePath =
            Paths.get(
                TrieLogSubCommand.parentCommand
                    .parentCommand
                    .dataDir()
                    .resolve("trie-logs.bin")
                    .toAbsolutePath()
                    .toString());
      }

      TrieLogContext context = getTrieLogContext();

      TrieLogHelper.importTrieLog(context.rootWorldStateStorage(), trieLogFilePath);
    }
  }

  record TrieLogContext(
      DataStorageConfiguration config,
      BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      MutableBlockchain blockchain) {}

  private static TrieLogContext getTrieLogContext() {
    Configurator.setLevel(LoggerFactory.getLogger(TrieLogPruner.class).getName(), Level.DEBUG);
    checkNotNull(parentCommand);
    BesuController besuController = createBesuController();
    final DataStorageConfiguration config = besuController.getDataStorageConfiguration();
    checkArgument(
        DataStorageFormat.BONSAI.equals(config.getDataStorageFormat()),
        "Subcommand only works with data-storage-format=BONSAI");

    final StorageProvider storageProvider = besuController.getStorageProvider();
    final BonsaiWorldStateKeyValueStorage rootWorldStateStorage =
        (BonsaiWorldStateKeyValueStorage) storageProvider.createWorldStateStorage(config);
    final MutableBlockchain blockchain = besuController.getProtocolContext().getBlockchain();
    return new TrieLogContext(config, rootWorldStateStorage, blockchain);
  }
}
