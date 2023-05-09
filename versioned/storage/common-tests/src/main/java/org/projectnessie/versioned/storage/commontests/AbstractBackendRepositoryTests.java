/*
 * Copyright (C) 2022 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.versioned.storage.commontests;

import static org.projectnessie.versioned.storage.common.logic.Logics.repositoryLogic;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.versioned.storage.common.config.StoreConfig;
import org.projectnessie.versioned.storage.common.logic.RepositoryLogic;
import org.projectnessie.versioned.storage.common.persist.Backend;
import org.projectnessie.versioned.storage.common.persist.CloseableIterator;
import org.projectnessie.versioned.storage.common.persist.Obj;
import org.projectnessie.versioned.storage.common.persist.ObjType;
import org.projectnessie.versioned.storage.common.persist.Persist;
import org.projectnessie.versioned.storage.common.persist.PersistFactory;
import org.projectnessie.versioned.storage.testextension.NessiePersist;
import org.projectnessie.versioned.storage.testextension.PersistExtension;

/** Basic {@link Persist} tests to be run by every implementation. */
@ExtendWith({PersistExtension.class, SoftAssertionsExtension.class})
public class AbstractBackendRepositoryTests {
  @InjectSoftAssertions protected SoftAssertions soft;

  @NessiePersist protected Backend backend;
  @NessiePersist protected PersistFactory persistFactory;

  @Test
  public void createEraseRepoViaPersist() {
    Persist repo1 = newRepo();

    RepositoryLogic repositoryLogic = repositoryLogic(repo1);
    repositoryLogic.initialize("foo-main");
    soft.assertThat(repositoryLogic.repositoryExists()).isTrue();
    repo1.erase();
    soft.assertThat(repositoryLogic.repositoryExists()).isFalse();
    try (CloseableIterator<Obj> scan = repo1.scanAllObjects(EnumSet.allOf(ObjType.class))) {
      soft.assertThat(scan).isExhausted();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 10})
  public void createEraseManyRepos(int numRepos) {
    List<Persist> repos =
        IntStream.range(0, numRepos).mapToObj(x -> newRepo()).collect(Collectors.toList());

    repos.forEach(r -> repositoryLogic(r).initialize("foo-meep"));
    soft.assertThat(repos).allMatch(r -> repositoryLogic(r).repositoryExists());
    backend.eraseRepositories(
        repos.stream().map(r -> r.config().repositoryId()).collect(Collectors.toSet()));
    soft.assertThat(repos).noneMatch(r -> repositoryLogic(r).repositoryExists());
    soft.assertThat(repos)
        .noneMatch(
            r -> {
              try (CloseableIterator<Obj> scan = r.scanAllObjects(EnumSet.allOf(ObjType.class))) {
                return scan.hasNext();
              }
            });
  }

  private Persist newRepo() {
    return persistFactory.newPersist(
        StoreConfig.Adjustable.empty().withRepositoryId("repo-" + UUID.randomUUID()));
  }
}