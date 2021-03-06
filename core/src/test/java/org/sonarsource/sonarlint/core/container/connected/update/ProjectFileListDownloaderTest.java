/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProjectFileListDownloaderTest {
  private SonarLintWsClient wsClient = WsClientTestUtils.createMock();
  private final static String PROJECT_KEY = "project1";

  private final ProgressWrapper progressWrapper = mock(ProgressWrapper.class);

  @Test
  public void should_get_files() throws IOException {
    try (InputStream in = this.getClass().getResourceAsStream("/update/component_tree.pb")) {
      WsClientTestUtils.addResponse(wsClient, "api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&ps=500&p=1", in);
      ProjectFileListDownloader underTest = new ProjectFileListDownloader(wsClient);
      List<String> files = underTest.get(PROJECT_KEY, progressWrapper);
      assertThat(files.size()).isEqualTo(187);

      assertThat(files.get(0)).isEqualTo("org.sonarsource.sonarlint.intellij:sonarlint-intellij:src/main/java/org/sonarlint/intellij/ui/AbstractIssuesPanel.java");
    }
  }

  @Test
  public void should_get_files_with_organization() throws IOException {
    try (InputStream in = this.getClass().getResourceAsStream("/update/component_tree.pb")) {
      when(wsClient.getOrganizationKey()).thenReturn(Optional.of("myorg"));
      WsClientTestUtils.addResponse(wsClient, "api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&organization=myorg&ps=500&p=1", in);
      ProjectFileListDownloader underTest = new ProjectFileListDownloader(wsClient);
      List<String> files = underTest.get(PROJECT_KEY, progressWrapper);
      assertThat(files.size()).isEqualTo(187);

      assertThat(files.get(0)).isEqualTo("org.sonarsource.sonarlint.intellij:sonarlint-intellij:src/main/java/org/sonarlint/intellij/ui/AbstractIssuesPanel.java");
    }
  }

  @Test
  public void should_get_empty_files_if_tree_is_empty() throws IOException {
    try (InputStream in = this.getClass().getResourceAsStream("/update/empty_component_tree.pb")) {
      WsClientTestUtils.addResponse(wsClient, "api/components/tree.protobuf?qualifiers=FIL,UTS&component=project1&ps=500&p=1", in);
      ProjectFileListDownloader underTest = new ProjectFileListDownloader(wsClient);
      List<String> files = underTest.get(PROJECT_KEY, progressWrapper);
      assertThat(files.size()).isEqualTo(0);
    }
  }
}
