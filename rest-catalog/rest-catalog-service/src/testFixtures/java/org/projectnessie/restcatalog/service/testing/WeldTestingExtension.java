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
package org.projectnessie.restcatalog.service.testing;

import java.net.URI;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import org.projectnessie.api.v2.params.ParsedReference;
import org.projectnessie.client.api.NessieApiV2;
import org.projectnessie.restcatalog.metadata.DelegatingMetadataIO;
import org.projectnessie.restcatalog.service.TenantSpecific;
import org.projectnessie.restcatalog.service.Warehouse;
import org.projectnessie.restcatalog.service.auth.OAuthHandler;

public class WeldTestingExtension implements Extension {

  private final OAuthHandler oauthHandler;
  private final NessieApiV2 api;
  private final URI nessieApiUri;
  private final ParsedReference defaultBranch;
  private final Warehouse defaultWarehouse;
  private final Map<String, String> clientCoreProperties;

  @SuppressWarnings("unused")
  public WeldTestingExtension() {
    throw new UnsupportedOperationException();
  }

  public WeldTestingExtension(
      OAuthHandler oauthHandler,
      NessieApiV2 api,
      URI nessieApiUri,
      ParsedReference defaultBranch,
      Warehouse defaultWarehouse,
      Map<String, String> clientCoreProperties) {
    this.oauthHandler = oauthHandler;
    this.api = api;
    this.nessieApiUri = nessieApiUri;
    this.defaultBranch = defaultBranch;
    this.defaultWarehouse = defaultWarehouse;
    this.clientCoreProperties = clientCoreProperties;
  }

  @SuppressWarnings("unused")
  public void afterBeanDiscovery(@Observes AfterBeanDiscovery abd, BeanManager bm) {
    String apiPath = nessieApiUri.getPath();
    URI apiBaseUri = nessieApiUri.resolve(apiPath + "/..").normalize();

    abd.addBean()
        .types(TenantSpecific.class)
        .qualifiers(Default.Literal.INSTANCE)
        .scope(ApplicationScoped.class)
        .produceWith(
            i ->
                new DummyTenantSpecific(
                    oauthHandler,
                    api,
                    apiBaseUri,
                    defaultBranch,
                    defaultWarehouse,
                    new DelegatingMetadataIO(new LocalFileIO()),
                    clientCoreProperties));
  }
}