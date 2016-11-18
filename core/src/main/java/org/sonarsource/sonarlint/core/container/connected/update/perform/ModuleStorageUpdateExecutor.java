/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.container.connected.update.perform;

import java.nio.file.Path;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.sonar.api.utils.TempFolder;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.container.connected.IssueStoreFactory;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.update.IssueDownloader;
import org.sonarsource.sonarlint.core.container.connected.update.ModuleConfigurationDownloader;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.StorageStatus;
import org.sonarsource.sonarlint.core.util.VersionUtils;

public class ModuleStorageUpdateExecutor {

  private final StorageManager storageManager;
  private final SonarLintWsClient wsClient;
  private final IssueDownloader issueDownloader;
  private final IssueStoreFactory issueStoreFactory;
  private final TempFolder tempFolder;
  private final ModuleConfigurationDownloader moduleConfigurationDownloader;

  public ModuleStorageUpdateExecutor(StorageManager storageManager, SonarLintWsClient wsClient,
    IssueDownloader issueDownloader, IssueStoreFactory issueStoreFactory, TempFolder tempFolder, ModuleConfigurationDownloader moduleConfigurationDownloader) {
    this.storageManager = storageManager;
    this.wsClient = wsClient;
    this.issueDownloader = issueDownloader;
    this.issueStoreFactory = issueStoreFactory;
    this.tempFolder = tempFolder;
    this.moduleConfigurationDownloader = moduleConfigurationDownloader;
  }

  public void update(String moduleKey) {
    GlobalProperties globalProps = storageManager.readGlobalPropertiesFromStorage();

    Path temp = tempFolder.newDir().toPath();

    updateModuleConfiguration(moduleKey, globalProps, temp);

    updateRemoteIssues(moduleKey, temp);

    updateStatus(temp);

    Path dest = storageManager.getModuleStorageRoot(moduleKey);
    FileUtils.deleteDirectory(dest);
    FileUtils.forceMkDirs(dest.getParent());
    FileUtils.moveDir(temp, dest);
  }

  private void updateModuleConfiguration(String moduleKey, GlobalProperties globalProps, Path temp) {
    ModuleConfiguration moduleConfiguration = moduleConfigurationDownloader.fetchModuleConfiguration(moduleKey, globalProps);
    final Set<String> qProfileKeys = storageManager.readQProfilesFromStorage().getQprofilesByKeyMap().keySet();
    for (String qpKey : moduleConfiguration.getQprofilePerLanguageMap().values()) {
      if (!qProfileKeys.contains(qpKey)) {
        throw new IllegalStateException(
          "Module '" + moduleKey + "' is associated to quality profile '" + qpKey + "' that is not in storage. Global storage is probably outdated. Please update binding.");
      }
    }
    ProtobufUtil.writeToFile(moduleConfiguration, temp.resolve(StorageManager.MODULE_CONFIGURATION_PB));
  }

  private void updateRemoteIssues(String moduleKey, Path temp) {
    Iterator<ScannerInput.ServerIssue> issues = issueDownloader.apply(moduleKey);

    Path basedir = temp.resolve(StorageManager.SERVER_ISSUES_DIR);

    Spliterator<ScannerInput.ServerIssue> spliterator = Spliterators.spliteratorUnknownSize(issues, 0);
    issueStoreFactory.apply(basedir).save(StreamSupport.stream(spliterator, false).collect(Collectors.toList()));
  }

  private void updateStatus(Path temp) {
    StorageStatus storageStatus = StorageStatus.newBuilder()
      .setStorageVersion(StorageManager.STORAGE_VERSION)
      .setClientUserAgent(wsClient.getUserAgent())
      .setSonarlintCoreVersion(VersionUtils.getLibraryVersion())
      .setUpdateTimestamp(new Date().getTime())
      .build();
    ProtobufUtil.writeToFile(storageStatus, temp.resolve(StorageManager.STORAGE_STATUS_PB));
  }

}
