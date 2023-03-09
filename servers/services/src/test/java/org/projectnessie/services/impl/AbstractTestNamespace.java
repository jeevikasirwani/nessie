/*
 * Copyright (C) 2023 Dremio
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
package org.projectnessie.services.impl;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.projectnessie.model.CommitMeta.fromMessage;
import static org.projectnessie.model.FetchOption.MINIMAL;
import static org.projectnessie.model.MergeBehavior.NORMAL;
import static org.projectnessie.services.impl.AbstractTestContents.contentAndOperationTypes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.iterable.ThrowingExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.error.BaseNessieClientServerException;
import org.projectnessie.error.NessieNamespaceAlreadyExistsException;
import org.projectnessie.error.NessieNamespaceNotEmptyException;
import org.projectnessie.error.NessieNamespaceNotFoundException;
import org.projectnessie.error.NessieReferenceConflictException;
import org.projectnessie.model.Branch;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.EntriesResponse;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.LogResponse.LogEntry;
import org.projectnessie.model.Namespace;
import org.projectnessie.model.Operation;
import org.projectnessie.model.Operation.Put;
import org.projectnessie.model.Reference;
import org.projectnessie.services.impl.AbstractTestContents.ContentAndOperationType;

public abstract class AbstractTestNamespace extends BaseTestServiceImpl {

  @ParameterizedTest
  @ValueSource(strings = {"a.b.c", "a.b\u001Dc.d", "a.b.c.d", "a.b\u0000c.d"})
  public void testNamespaces(String namespaceName) throws BaseNessieClientServerException {
    Branch branch = createBranch("testNamespaces");
    Namespace ns = Namespace.parse(namespaceName);
    Namespace namespace = namespaceApi().createNamespace(branch.getName(), ns);

    soft.assertThat(namespace)
        .isNotNull()
        .extracting(Namespace::getElements, Namespace::toPathString)
        .containsExactly(ns.getElements(), ns.toPathString());

    Namespace got = namespaceApi().getNamespace(branch.getName(), null, ns);
    soft.assertThat(got).isEqualTo(namespace);

    // the namespace in the error message will contain the representation with u001D
    String namespaceInErrorMsg = namespaceName.replace("\u0000", "\u001D");

    soft.assertThatThrownBy(() -> namespaceApi().createNamespace(branch.getName(), ns))
        .cause()
        .isInstanceOf(NessieNamespaceAlreadyExistsException.class)
        .hasMessage(String.format("Namespace '%s' already exists", namespaceInErrorMsg));

    namespaceApi().deleteNamespace(branch.getName(), ns);
    soft.assertThatThrownBy(() -> namespaceApi().deleteNamespace(branch.getName(), ns))
        .isInstanceOf(NessieNamespaceNotFoundException.class)
        .hasMessage(String.format("Namespace '%s' does not exist", namespaceInErrorMsg));

    soft.assertThatThrownBy(() -> namespaceApi().getNamespace(branch.getName(), null, ns))
        .isInstanceOf(NessieNamespaceNotFoundException.class)
        .hasMessage(String.format("Namespace '%s' does not exist", namespaceInErrorMsg));

    soft.assertThatThrownBy(
            () -> namespaceApi().deleteNamespace(branch.getName(), Namespace.parse("nonexisting")))
        .isInstanceOf(NessieNamespaceNotFoundException.class)
        .hasMessage("Namespace 'nonexisting' does not exist");
  }

  @Test
  public void testNamespacesRetrieval() throws BaseNessieClientServerException {
    Branch branch = createBranch("namespace");

    ThrowingExtractor<String, Namespace, ?> createNamespace =
        identifier -> namespaceApi().createNamespace(branch.getName(), Namespace.parse(identifier));

    Namespace one = createNamespace.apply("a.b.c");
    Namespace two = createNamespace.apply("a.b.d");
    Namespace three = createNamespace.apply("x.y.z");
    Namespace four = createNamespace.apply("one.two");
    for (Namespace namespace : Arrays.asList(one, two, three, four)) {
      soft.assertThat(namespace).isNotNull();
      soft.assertThat(namespace.getId()).isNotNull();
    }

    soft.assertThat(
            namespaceApi().getNamespaces(branch.getName(), null, Namespace.EMPTY).getNamespaces())
        .containsExactlyInAnyOrder(one, two, three, four);

    soft.assertThat(
            namespaceApi().getNamespaces(branch.getName(), null, Namespace.EMPTY).getNamespaces())
        .containsExactlyInAnyOrder(one, two, three, four);

    soft.assertThat(
            namespaceApi().getNamespaces(branch.getName(), null, Namespace.of("a")).getNamespaces())
        .containsExactlyInAnyOrder(one, two);
    soft.assertThat(
            namespaceApi()
                .getNamespaces(branch.getName(), null, Namespace.of("a", "b"))
                .getNamespaces())
        .containsExactlyInAnyOrder(one, two);
    soft.assertThat(
            namespaceApi()
                .getNamespaces(branch.getName(), null, Namespace.of("a", "b", "c"))
                .getNamespaces())
        .containsExactlyInAnyOrder(one);
    soft.assertThat(
            namespaceApi()
                .getNamespaces(branch.getName(), null, Namespace.of("a", "b", "d"))
                .getNamespaces())
        .containsExactlyInAnyOrder(two);

    soft.assertThat(
            namespaceApi().getNamespaces(branch.getName(), null, Namespace.of("x")).getNamespaces())
        .containsExactly(three);
    soft.assertThat(
            namespaceApi().getNamespaces(branch.getName(), null, Namespace.of("z")).getNamespaces())
        .isEmpty();
    soft.assertThat(
            namespaceApi()
                .getNamespaces(branch.getName(), null, Namespace.of("one"))
                .getNamespaces())
        .containsExactly(four);
  }

  @Test
  public void testNamespaceDeletion() throws BaseNessieClientServerException {
    Branch init = createBranch("testNamespaceDeletion");

    List<ContentAndOperationType> contentAndOps =
        contentAndOperationTypes().collect(Collectors.toList());

    Branch branch =
        commit(
                init,
                fromMessage("verifyAllContentAndOperationTypes prepare"),
                contentAndOps.stream()
                    .filter(co -> co.prepare != null)
                    .map(co -> co.prepare)
                    .toArray(Operation[]::new))
            .getTargetBranch();

    commit(
        branch,
        fromMessage("verifyAllContentAndOperationTypes"),
        contentAndOps.stream().map(c -> c.operation).toArray(Operation[]::new));

    List<ContentKey> entries =
        contentAndOps.stream()
            .filter(c -> c.operation instanceof Put)
            .map(c -> c.operation.getKey())
            .collect(Collectors.toList());

    commit(
        branch,
        fromMessage("create namespaces"),
        entries.stream()
            .map(ContentKey::getNamespace)
            .distinct()
            .map(ns -> Put.of(ContentKey.of(ns.getElements()), ns))
            .toArray(Operation[]::new));

    for (ContentKey contentKey : entries) {
      Namespace namespace = contentKey.getNamespace();
      soft.assertThat(namespaceApi().getNamespace(branch.getName(), null, namespace).getElements())
          .isEqualTo(namespace.getElements());

      soft.assertThatThrownBy(() -> namespaceApi().deleteNamespace(branch.getName(), namespace))
          .cause()
          .isInstanceOf(NessieNamespaceNotEmptyException.class)
          .hasMessage(String.format("Namespace '%s' is not empty", namespace));
    }
  }

  @Test
  public void testNamespaceMerge() throws BaseNessieClientServerException {
    Branch base = createBranch("merge-base");
    base =
        commit(
                base,
                fromMessage("root"),
                Put.of(ContentKey.of("root"), IcebergTable.of("/dev/null", 42, 42, 42, 42)))
            .getTargetBranch();

    Branch branch = createBranch("merge-branch", base);
    Namespace ns = Namespace.parse("a.b.c");
    // create the same namespace on both branches
    namespaceApi().createNamespace(branch.getName(), ns);

    base = (Branch) getReference(base.getName());
    branch = (Branch) getReference(branch.getName());
    treeApi()
        .mergeRefIntoBranch(
            base.getName(),
            base.getHash(),
            branch.getName(),
            branch.getHash(),
            true,
            null,
            emptyList(),
            NORMAL,
            false,
            false,
            false);

    List<LogEntry> log = commitLog(base.getName(), MINIMAL, base.getHash(), null, null);
    String expectedCommitMsg = "create namespace a.b.c";
    soft.assertThat(log.stream().map(LogEntry::getCommitMeta).map(CommitMeta::getMessage))
        .containsExactly(expectedCommitMsg, "root");

    soft.assertThat(entries(base.getName(), null).stream().map(EntriesResponse.Entry::getName))
        .contains(ns.toContentKey());

    soft.assertThat(namespaceApi().getNamespace(base.getName(), null, ns)).isNotNull();
  }

  @Test
  public void testNamespaceMergeWithConflict() throws BaseNessieClientServerException {
    Branch base = createBranch("merge-base");
    base =
        commit(
                base,
                fromMessage("root"),
                Put.of(ContentKey.of("root"), IcebergTable.of("/dev/null", 42, 42, 42, 42)))
            .getTargetBranch();

    Branch branch = createBranch("merge-branch", base);
    Namespace ns = Namespace.parse("a.b.c");
    // create a namespace on the base branch
    namespaceApi().createNamespace(base.getName(), ns);
    base = (Branch) getReference(base.getName());

    // create a table with the same name on the other branch
    IcebergTable table = IcebergTable.of("merge-table1", 42, 42, 42, 42);
    branch =
        commit(
                branch,
                fromMessage("test-merge-branch1"),
                Put.of(ContentKey.of("a", "b", "c"), table))
            .getTargetBranch();
    Branch finalBase = base;
    Branch finalBranch = branch;
    soft.assertThatThrownBy(
            () ->
                treeApi()
                    .mergeRefIntoBranch(
                        finalBase.getName(),
                        finalBase.getHash(),
                        finalBranch.getName(),
                        finalBranch.getHash(),
                        false,
                        "foo",
                        emptyList(),
                        NORMAL,
                        false,
                        false,
                        false))
        .isInstanceOf(NessieReferenceConflictException.class)
        .hasMessage("The following keys have been changed in conflict: 'a.b.c'");

    List<LogEntry> log = commitLog(base.getName(), MINIMAL, base.getHash(), null, null);
    // merging should not have been possible ("test-merge-branch1" shouldn't be in the commits)
    soft.assertThat(log.stream().map(LogEntry::getCommitMeta).map(CommitMeta::getMessage))
        .containsExactly("create namespace a.b.c");

    List<EntriesResponse.Entry> entries = entries(base.getName(), null);
    soft.assertThat(entries.stream().map(EntriesResponse.Entry::getName))
        .contains(ns.toContentKey());

    soft.assertThat(namespaceApi().getNamespace(base.getName(), null, ns)).isNotNull();
  }

  @Test
  public void testNamespaceConflictWithOtherContent() throws BaseNessieClientServerException {
    Branch branch = createBranch("testNamespaceConflictWithOtherContent");
    IcebergTable icebergTable = IcebergTable.of("icebergTable", 42, 42, 42, 42);

    List<String> elements = Arrays.asList("a", "b", "c");
    ContentKey key = ContentKey.of(elements);
    commit(branch, fromMessage("add table"), Put.of(key, icebergTable));

    Namespace ns = Namespace.of(elements);
    soft.assertThatThrownBy(() -> namespaceApi().createNamespace(branch.getName(), ns))
        .cause()
        .isInstanceOf(NessieNamespaceAlreadyExistsException.class)
        .hasMessage("Another content object with name 'a.b.c' already exists");

    soft.assertThatThrownBy(() -> namespaceApi().getNamespace(branch.getName(), null, ns))
        .isInstanceOf(NessieNamespaceNotFoundException.class)
        .hasMessage("Namespace 'a.b.c' does not exist");

    soft.assertThatThrownBy(() -> namespaceApi().deleteNamespace(branch.getName(), ns))
        .isInstanceOf(NessieNamespaceNotFoundException.class);
  }

  @Test
  public void testNamespacesWithAndWithoutZeroBytes() throws BaseNessieClientServerException {
    Branch branch = createBranch("testNamespacesWithAndWithoutZeroBytes");
    String firstName = "a.b\u0000c.d";
    String secondName = "a.b.c.d";

    // perform creation and retrieval
    ThrowingExtractor<String, Namespace, ?> creator =
        identifier -> {
          Namespace namespace = Namespace.parse(identifier);

          Namespace created = namespaceApi().createNamespace(branch.getName(), namespace);
          soft.assertThat(created)
              .isNotNull()
              .extracting(Namespace::getElements, Namespace::toPathString)
              .containsExactly(namespace.getElements(), namespace.toPathString());

          soft.assertThat(namespaceApi().getNamespace(branch.getName(), null, namespace))
              .isEqualTo(created);

          soft.assertThatThrownBy(() -> namespaceApi().createNamespace(branch.getName(), namespace))
              .cause()
              .isInstanceOf(NessieNamespaceAlreadyExistsException.class)
              .hasMessage(String.format("Namespace '%s' already exists", namespace.name()));

          soft.assertAll();

          return created;
        };

    Namespace first = creator.apply(firstName);
    Namespace second = creator.apply(secondName);
    List<Namespace> namespaces = Arrays.asList(first, second);

    // retrieval by prefix
    soft.assertThat(
            namespaceApi().getNamespaces(branch.getName(), null, Namespace.EMPTY).getNamespaces())
        .containsExactlyInAnyOrderElementsOf(namespaces);

    soft.assertThat(
            namespaceApi().getNamespaces(branch.getName(), null, Namespace.of("a")).getNamespaces())
        .containsExactlyInAnyOrderElementsOf(namespaces);

    soft.assertThat(
            namespaceApi()
                .getNamespaces(branch.getName(), null, Namespace.of("a", "b"))
                .getNamespaces())
        .containsExactly(second);

    soft.assertThat(
            namespaceApi()
                .getNamespaces(branch.getName(), null, Namespace.of("a", "b", "c"))
                .getNamespaces())
        .containsExactly(second);

    // deletion
    for (Namespace namespace : namespaces) {
      namespaceApi().deleteNamespace(branch.getName(), namespace);

      soft.assertThatThrownBy(() -> namespaceApi().deleteNamespace(branch.getName(), namespace))
          .isInstanceOf(NessieNamespaceNotFoundException.class)
          .hasMessage(String.format("Namespace '%s' does not exist", namespace.name()));
    }

    soft.assertThat(
            namespaceApi().getNamespaces(branch.getName(), null, Namespace.EMPTY).getNamespaces())
        .isEmpty();
  }

  @Test
  public void testEmptyNamespace() throws BaseNessieClientServerException {
    Branch branch = createBranch("emptyNamespace");
    // can't create/fetch/delete an empty namespace due to empty REST path
    soft.assertThatThrownBy(() -> namespaceApi().createNamespace(branch.getName(), Namespace.EMPTY))
        .isInstanceOf(Exception.class);

    soft.assertThatThrownBy(
            () -> namespaceApi().getNamespace(branch.getName(), null, Namespace.EMPTY))
        .isInstanceOf(Exception.class);

    soft.assertThatThrownBy(() -> namespaceApi().deleteNamespace(branch.getName(), Namespace.EMPTY))
        .isInstanceOf(Exception.class);

    soft.assertThat(
            namespaceApi().getNamespaces(branch.getName(), null, Namespace.EMPTY).getNamespaces())
        .isEmpty();

    ContentKey keyWithoutNamespace = ContentKey.of("icebergTable");
    commit(
        branch,
        fromMessage("add table"),
        Put.of(keyWithoutNamespace, IcebergTable.of("icebergTable", 42, 42, 42, 42)));

    soft.assertThat(
            namespaceApi().getNamespaces(branch.getName(), null, Namespace.EMPTY).getNamespaces())
        .isEmpty();
  }

  @Test
  public void testNamespaceWithProperties() throws BaseNessieClientServerException {
    Branch branch = createBranch("namespaceWithProperties");
    Map<String, String> properties = ImmutableMap.of("key1", "val1", "key2", "val2");
    Namespace namespace = Namespace.of(properties, "a", "b", "c");

    Namespace ns =
        namespaceApi()
            .createNamespace(branch.getName(), Namespace.of(namespace.getElements(), properties));
    soft.assertThat(ns.getProperties()).isEqualTo(properties);
    soft.assertThat(ns.getId()).isNotNull();
    String nsId = ns.getId();

    soft.assertThatThrownBy(
            () ->
                namespaceApi()
                    .updateProperties(
                        branch.getName(), Namespace.of("non-existing"), properties, emptySet()))
        .isInstanceOf(NessieNamespaceNotFoundException.class)
        .hasMessage("Namespace 'non-existing' does not exist");

    // Re-run with invalid name, but different parameters to ensure that missing parameters do not
    // fail the request before the name is validated.
    soft.assertThatThrownBy(
            () ->
                namespaceApi()
                    .updateProperties(
                        branch.getName(),
                        Namespace.of("non-existing"),
                        emptyMap(),
                        properties.keySet()))
        .isInstanceOf(NessieNamespaceNotFoundException.class)
        .hasMessage("Namespace 'non-existing' does not exist");

    namespaceApi().updateProperties(branch.getName(), namespace, properties, emptySet());

    // namespace does not exist at the previous hash
    soft.assertThatThrownBy(
            () -> namespaceApi().getNamespace(branch.getName(), branch.getHash(), namespace))
        .isInstanceOf(NessieNamespaceNotFoundException.class);

    Reference updated = getReference(branch.getName());
    ns = namespaceApi().getNamespace(updated.getName(), updated.getHash(), namespace);
    soft.assertThat(ns.getProperties()).isEqualTo(properties);
    soft.assertThat(ns.getId()).isEqualTo(nsId);

    namespaceApi()
        .updateProperties(
            updated.getName(),
            namespace,
            ImmutableMap.of("key3", "val3", "key1", "xyz"),
            ImmutableSet.of("key2", "key5"));

    // "updated" still points to the hash prior to the update
    soft.assertThat(
            namespaceApi()
                .getNamespace(updated.getName(), updated.getHash(), namespace)
                .getProperties())
        .isEqualTo(properties);

    updated = getReference(branch.getName());
    ns = namespaceApi().getNamespace(updated.getName(), null, namespace);
    soft.assertThat(ns.getProperties()).isEqualTo(ImmutableMap.of("key1", "xyz", "key3", "val3"));
    soft.assertThat(ns.getId()).isEqualTo(nsId);
  }
}
