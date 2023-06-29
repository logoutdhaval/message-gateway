/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.fineract.messagegateway.service;

import com.googlecode.flyway.core.Flyway;
import org.fineract.messagegateway.tenants.domain.TenantServerConnection;
import org.fineract.messagegateway.tenants.domain.Tenants;
import org.fineract.messagegateway.tenants.repository.TenantServerConnectionRepository;
import org.fineract.messagegateway.tenants.repository.TenantsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TenantDatabaseUpgradeService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TenantServerConnectionRepository tenantServerConnectionRepository;
    @Autowired
    private TenantsRepository tenantsRepository;

    @Autowired
    private DataSourcePerTenantService dataSourcePerTenantService;

    @Value("${token.user.access-validity-seconds}")
    private String userTokenAccessValiditySeconds;

    @Value("${token.user.refresh-validity-seconds}")
    private String userTokenRefreshValiditySeconds;

    @Value("${token.client.access-validity-seconds}")
    private String clientAccessTokenValidity;

    @Value("${token.client.channel.secret}")
    private String channelClientSecret;
    @Value("#{'${tenants}'.split(',')}")
    private List<String> tenants;
    public static final String IDENTITY_PROVIDER_RESOURCE_ID = "identity-provider";

    @PostConstruct
    public void setupEnvironment() {
        flywayTenants();
    }

    private void flywayTenants() {
        for(String  tenantIdentifier: tenants){
            Optional<Tenants> getTenant = tenantsRepository.findOneByTenantIdentifier(tenantIdentifier);
            if(getTenant.isPresent()) {
                Long dbConnectionId = Long.parseLong(getTenant.get().getDbConnectionId());
                Optional<TenantServerConnection> tenantServerConnection = tenantServerConnectionRepository
                        .findById(dbConnectionId);
                if (tenantServerConnection.get().isAutoUpdateEnabled()) {
                    try {
                        ThreadLocalContextUtil.setTenant(tenantServerConnection.get());
                        final Flyway fw = new Flyway();
                        fw.setDataSource(dataSourcePerTenantService.retrieveDataSource());
                        fw.setLocations("db/migration");
                        fw.setInitOnMigrate(true);
                        fw.setOutOfOrder(true);
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("tenantDatabase", tenantServerConnection.get().getSchemaName()); // add tenant as aud claim
                        placeholders.put("userAccessTokenValidity", userTokenAccessValiditySeconds);
                        placeholders.put("userRefreshTokenValidity", userTokenRefreshValiditySeconds);
                        placeholders.put("clientAccessTokenValidity", clientAccessTokenValidity);
                        placeholders.put("channelClientSecret", channelClientSecret);
                        placeholders.put("identityProviderResourceId", IDENTITY_PROVIDER_RESOURCE_ID); // add identity provider as aud claim
                        fw.setPlaceholders(placeholders);
                        fw.migrate();
                    } catch (Exception e) {
                        logger.error("Error when running flyway on tenant: {}", tenantServerConnection.get().getSchemaName(), e);
                    } finally {
                        ThreadLocalContextUtil.clear();
                    }
                }
            }
        }
    }
}
