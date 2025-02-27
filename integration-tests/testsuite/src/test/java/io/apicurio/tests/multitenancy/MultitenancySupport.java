/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.tests.multitenancy;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;

import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.tests.common.RegistryFacade;
import io.apicurio.tests.common.auth.CustomJWTAuth;

/**
 * @author Fabian Martinez
 */
public class MultitenancySupport {

    private RegistryFacade registryFacade = RegistryFacade.getInstance();

    private TenantManagerClient tenantManager;

    public MultitenancySupport() {
        //singleton
    }

    public TenantUserClient createTenant() throws Exception {
        TenantUser user = new TenantUser(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
        return createTenant(user);
    }

    public TenantUserClient createTenant(TenantUser user) throws Exception {
        String tenantAppUrl = registerTenant(user);
        var client = createUserClient(user, tenantAppUrl);
        return new TenantUserClient(user, tenantAppUrl, client);
    }

    private String registerTenant(TenantUser user) throws Exception {

        String tenantAppUrl = TestUtils.getRegistryBaseUrl() + "/t/" + user.tenantId;

        NewRegistryTenantRequest tenantReq = new NewRegistryTenantRequest();
        tenantReq.setOrganizationId(user.organizationId);
        tenantReq.setTenantId(user.tenantId);
        tenantReq.setCreatedBy(user.principalId);

        TenantManagerClient tenantManager = getTenantManagerClient();
        tenantManager.createTenant(tenantReq);

        TestUtils.retry(() -> Assertions.assertNotNull(tenantManager.getTenant(user.tenantId)));

        return tenantAppUrl;
    }

    public RegistryClient createUserClient(TenantUser user, String tenantAppUrl) {
        return RegistryClientFactory.create(tenantAppUrl, Collections.emptyMap(), new CustomJWTAuth(user.principalId, user.organizationId));
    }

    public synchronized TenantManagerClient getTenantManagerClient() {
        if (tenantManager == null) {
            var keycloak = registryFacade.getMTOnlyKeycloakMock();
            tenantManager = new TenantManagerClientImpl(registryFacade.getTenantManagerUrl(), Collections.emptyMap(),
                    new OidcAuth(keycloak.tokenEndpoint, keycloak.clientId, keycloak.clientSecret));
        }
        return tenantManager;
    }

}
