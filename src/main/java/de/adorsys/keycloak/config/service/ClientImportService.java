/*-
 * ---license-start
 * keycloak-config-cli
 * ---
 * Copyright (C) 2017 - 2020 adorsys GmbH & Co. KG @ https://adorsys.com
 * ---
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
 * ---license-end
 */

package de.adorsys.keycloak.config.service;

import de.adorsys.keycloak.config.exception.ImportProcessingException;
import de.adorsys.keycloak.config.model.RealmImport;
import de.adorsys.keycloak.config.properties.ImportConfigProperties;
import de.adorsys.keycloak.config.provider.KeycloakProvider;
import de.adorsys.keycloak.config.repository.AuthenticationFlowRepository;
import de.adorsys.keycloak.config.repository.ClientRepository;
import de.adorsys.keycloak.config.util.*;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;

@Service
public class ClientImportService {
    private static final String[] propertiesWithDependencies = new String[]{
            "authenticationFlowBindingOverrides",
            "authorizationSettings",
    };
    private static final Logger logger = LoggerFactory.getLogger(ClientImportService.class);

    private final KeycloakProvider keycloakProvider;
    private final ClientRepository clientRepository;
    private final AuthenticationFlowRepository authenticationFlowRepository;
    private final ImportConfigProperties importConfigProperties;

    @Autowired
    public ClientImportService(
            KeycloakProvider keycloakProvider,
            ClientRepository clientRepository,
            AuthenticationFlowRepository authenticationFlowRepository,
            ImportConfigProperties importConfigProperties) {
        this.keycloakProvider = keycloakProvider;
        this.clientRepository = clientRepository;
        this.authenticationFlowRepository = authenticationFlowRepository;
        this.importConfigProperties = importConfigProperties;
    }

    public void doImport(RealmImport realmImport) {
        List<ClientRepresentation> clients = realmImport.getClients();
        if (clients == null) {
            return;
        }

        createOrUpdateClients(realmImport, clients);
    }

    public void doImportDependencies(RealmImport realmImport) {
        List<ClientRepresentation> clients = realmImport.getClients();
        if (clients == null) {
            return;
        }
        updateClientAuthorizationSettings(realmImport, clients);
        updateClientAuthenticationFlowBindingOverrides(realmImport, clients);
    }

    private void createOrUpdateClients(RealmImport realmImport, List<ClientRepresentation> clients) {
        Consumer<ClientRepresentation> loop = client -> createOrUpdateClient(realmImport, client);
        if (importConfigProperties.isParallel()) {
            clients.parallelStream().forEach(loop);
        } else {
            clients.forEach(loop);
        }
    }

    private void createOrUpdateClient(RealmImport realmImport, ClientRepresentation client) {
        String clientId = client.getClientId();
        String realmName = realmImport.getRealm();

        Optional<ClientRepresentation> maybeClient = clientRepository.searchByClientId(realmName, clientId);

        if (maybeClient.isPresent()) {
            updateClientIfNeeded(realmName, client, maybeClient.get());
        } else {
            logger.debug("Create client '{}' in realm '{}'", clientId, realmName);
            createClient(realmName, client);
        }
    }

    private void updateClientIfNeeded(String realmName, ClientRepresentation clientToUpdate, ClientRepresentation existingClient) {
        String[] propertiesToIgnore = ArrayUtil.concat(propertiesWithDependencies, "id", "access");

        ClientRepresentation patchedClient = CloneUtil.patch(existingClient, clientToUpdate, propertiesToIgnore);

        if (!isClientEqual(realmName, existingClient, patchedClient)) {
            logger.debug("Update client '{}' in realm '{}'", clientToUpdate.getClientId(), realmName);
            updateClient(realmName, patchedClient);
        } else {
            logger.debug("No need to update client '{}' in realm '{}'", clientToUpdate.getClientId(), realmName);
        }
    }

    private void createClient(String realmName, ClientRepresentation client) {
        ClientRepresentation clientToImport = CloneUtil.deepClone(client, ClientRepresentation.class, propertiesWithDependencies);
        clientRepository.create(realmName, clientToImport);
    }

    private boolean isClientEqual(String realmName, ClientRepresentation existingClient, ClientRepresentation patchedClient) {
        String[] propertiesToIgnore = ArrayUtil.concat(propertiesWithDependencies, "id", "secret", "access", "protocolMappers");

        if (!CloneUtil.deepEquals(existingClient, patchedClient, propertiesToIgnore)) {
            return false;
        }

        if (!ProtocolMapperUtil.areProtocolMappersEqual(patchedClient.getProtocolMappers(), existingClient.getProtocolMappers())) {
            return false;
        }

        String patchedClientSecret = patchedClient.getSecret();
        if (patchedClientSecret == null) {
            return true;
        }

        String clientSecret = clientRepository.getClientSecret(realmName, patchedClient.getClientId());
        return Objects.equals(clientSecret, patchedClientSecret);
    }

    private void updateClient(String realmName, ClientRepresentation patchedClient) {
        try {
            clientRepository.update(realmName, patchedClient);
        } catch (WebApplicationException error) {
            String errorMessage = ResponseUtil.getErrorMessage(error);
            throw new ImportProcessingException("Cannot update client '" + patchedClient.getClientId() + "' for realm '" + realmName + "': " + errorMessage, error);
        }

        // https://github.com/keycloak/keycloak/pull/7017
        if (VersionUtil.lt("11", keycloakProvider.getKeycloakVersion())) {
            List<ProtocolMapperRepresentation> protocolMappers = patchedClient.getProtocolMappers();

            if (protocolMappers != null) {
                updateProtocolMappers(realmName, patchedClient.getId(), protocolMappers);
            }
        }
    }

    private void updateProtocolMappers(String realmName, String clientId, List<ProtocolMapperRepresentation> protocolMappers) {
        ClientRepresentation existingClient = clientRepository.getById(realmName, clientId);

        List<ProtocolMapperRepresentation> existingProtocolMappers = existingClient.getProtocolMappers();

        List<ProtocolMapperRepresentation> protocolMappersToAdd = ProtocolMapperUtil.estimateProtocolMappersToAdd(protocolMappers, existingProtocolMappers);
        List<ProtocolMapperRepresentation> protocolMappersToRemove = ProtocolMapperUtil.estimateProtocolMappersToRemove(protocolMappers, existingProtocolMappers);
        List<ProtocolMapperRepresentation> protocolMappersToUpdate = ProtocolMapperUtil.estimateProtocolMappersToUpdate(protocolMappers, existingProtocolMappers);

        clientRepository.addProtocolMappers(realmName, clientId, protocolMappersToAdd);
        clientRepository.removeProtocolMappers(realmName, clientId, protocolMappersToRemove);
        clientRepository.updateProtocolMappers(realmName, clientId, protocolMappersToUpdate);
    }

    private void updateClientAuthorizationSettings(RealmImport realmImport, List<ClientRepresentation> clients) {
        String realmName = realmImport.getRealm();

        List<ClientRepresentation> clientsWithAuthorization = clients.stream()
                .filter(client -> client.getAuthorizationSettings() != null)
                .collect(Collectors.toList());

        for (ClientRepresentation client : clientsWithAuthorization) {
            ClientRepresentation existingClient = clientRepository.getByClientId(realmName, client.getClientId());
            updateAuthorization(realmName, existingClient, client.getAuthorizationSettings());
        }
    }

    private void updateAuthorization(String realmName, ClientRepresentation client, ResourceServerRepresentation authorizationSettingsToImport) {
        ResourceServerRepresentation existingAuthorization = clientRepository.getAuthorizationConfigById(realmName, client.getId());

        handleAuthorizationSettings(realmName, client, existingAuthorization, authorizationSettingsToImport);

        createOrUpdateAuthorizationResources(realmName, client,
                existingAuthorization.getResources(), authorizationSettingsToImport.getResources());
        removeAuthorizationResources(realmName, client,
                existingAuthorization.getResources(), authorizationSettingsToImport.getResources());

        createOrUpdateAuthorizationScopes(realmName, client,
                existingAuthorization.getScopes(), authorizationSettingsToImport.getScopes());
        removeAuthorizationScopes(realmName, client,
                existingAuthorization.getScopes(), authorizationSettingsToImport.getScopes());

        createOrUpdateAuthorizationPolicies(realmName, client,
                existingAuthorization.getPolicies(), authorizationSettingsToImport.getPolicies());
        removeAuthorizationPolicies(realmName, client,
                existingAuthorization.getPolicies(), authorizationSettingsToImport.getPolicies());
    }

    private void handleAuthorizationSettings(String realmName, ClientRepresentation client, ResourceServerRepresentation existingClientAuthorizationResources, ResourceServerRepresentation authorizationResourcesToImport) {
        if (!CloneUtil.deepEquals(authorizationResourcesToImport, existingClientAuthorizationResources, "policies", "resources", "permissions", "scopes")) {
            ResourceServerRepresentation patchedAuthorizationSettings = CloneUtil
                    .deepPatch(existingClientAuthorizationResources, authorizationResourcesToImport);
            logger.debug("Update authorization settings for client '{}' in realm '{}'", client.getClientId(), realmName);
            clientRepository.updateAuthorizationSettings(realmName, client.getId(), patchedAuthorizationSettings);
        }
    }

    private void createOrUpdateAuthorizationResources(String realmName, ClientRepresentation client, List<ResourceRepresentation> existingClientAuthorizationResources, List<ResourceRepresentation> authorizationResourcesToImport) {
        Map<String, ResourceRepresentation> existingClientAuthorizationResourcesMap = existingClientAuthorizationResources
                .stream()
                .collect(Collectors.toMap(ResourceRepresentation::getName, resource -> resource));

        for (ResourceRepresentation authorizationResourceToImport : authorizationResourcesToImport) {
            createOrUpdateAuthorizationResource(realmName, client, existingClientAuthorizationResourcesMap, authorizationResourceToImport);
        }
    }

    private void createOrUpdateAuthorizationResource(String realmName, ClientRepresentation client, Map<String, ResourceRepresentation> existingClientAuthorizationResourcesMap, ResourceRepresentation authorizationResourceToImport) {
        if (!existingClientAuthorizationResourcesMap.containsKey(authorizationResourceToImport.getName())) {
            logger.debug("Create authorization resource '{}' for client '{}' in realm '{}'", authorizationResourceToImport.getName(), client.getClientId(), realmName);
            clientRepository.createAuthorizationResource(realmName, client.getId(), authorizationResourceToImport);
        } else {
            updateAuthorizationResource(realmName, client, existingClientAuthorizationResourcesMap, authorizationResourceToImport);
        }
    }

    private void updateAuthorizationResource(String realmName, ClientRepresentation client, Map<String, ResourceRepresentation> existingClientAuthorizationResourcesMap, ResourceRepresentation authorizationResourceToImport) {
        ResourceRepresentation existingClientAuthorizationResource = existingClientAuthorizationResourcesMap
                .get(authorizationResourceToImport.getName());

        if (!CloneUtil.deepEquals(authorizationResourceToImport, existingClientAuthorizationResource, "id", "_id")) {
            authorizationResourceToImport.setId(existingClientAuthorizationResource.getId());
            logger.debug("Update authorization resource '{}' for client '{}' in realm '{}'", authorizationResourceToImport.getName(), client.getClientId(), realmName);
            clientRepository.updateAuthorizationResource(realmName, client.getId(), authorizationResourceToImport);
        }
    }

    private void removeAuthorizationResources(String realmName, ClientRepresentation client, List<ResourceRepresentation> existingClientAuthorizationResources, List<ResourceRepresentation> authorizationResourcesToImport) {
        List<String> authorizationResourceNamesToImport = authorizationResourcesToImport
                .stream().map(ResourceRepresentation::getName)
                .collect(Collectors.toList());

        for (ResourceRepresentation existingClientAuthorizationResource : existingClientAuthorizationResources) {
            if (!authorizationResourceNamesToImport.contains(existingClientAuthorizationResource.getName())) {
                removeAuthorizationResource(realmName, client, existingClientAuthorizationResource);
            }
        }
    }

    private void removeAuthorizationResource(String realmName, ClientRepresentation client, ResourceRepresentation existingClientAuthorizationResource) {
        logger.debug("Remove authorization resource '{}' for client '{}' in realm '{}'", existingClientAuthorizationResource.getName(), client.getClientId(), realmName);
        clientRepository.removeAuthorizationResource(realmName, client.getId(), existingClientAuthorizationResource.getId());
    }

    private void createOrUpdateAuthorizationScopes(String realmName, ClientRepresentation client, List<ScopeRepresentation> existingClientAuthorizationScopes, List<ScopeRepresentation> authorizationScopesToImport) {
        Map<String, ScopeRepresentation> existingClientAuthorizationScopesMap = existingClientAuthorizationScopes
                .stream()
                .collect(Collectors.toMap(ScopeRepresentation::getName, scope -> scope));

        for (ScopeRepresentation authorizationScopeToImport : authorizationScopesToImport) {
            createOrUpdateAuthorizationScope(realmName, client, existingClientAuthorizationScopesMap, authorizationScopeToImport);
        }
    }

    private void createOrUpdateAuthorizationScope(String realmName, ClientRepresentation client, Map<String, ScopeRepresentation> existingClientAuthorizationScopesMap, ScopeRepresentation authorizationScopeToImport) {
        String authorizationScopeNameToImport = authorizationScopeToImport.getName();
        if (!existingClientAuthorizationScopesMap.containsKey(authorizationScopeToImport.getName())) {
            logger.debug("Add authorization scope '{}' for client '{}' in realm '{}'", authorizationScopeNameToImport, client.getClientId(), realmName);
            clientRepository.addAuthorizationScope(realmName, client.getId(), authorizationScopeNameToImport);
        } else {
            updateAuthorizationScope(realmName, client, existingClientAuthorizationScopesMap, authorizationScopeToImport, authorizationScopeNameToImport);
        }
    }

    private void updateAuthorizationScope(String realmName, ClientRepresentation client, Map<String, ScopeRepresentation> existingClientAuthorizationScopesMap, ScopeRepresentation authorizationScopeToImport, String authorizationScopeNameToImport) {
        ScopeRepresentation existingClientAuthorizationScope = existingClientAuthorizationScopesMap
                .get(authorizationScopeNameToImport);

        if (!CloneUtil.deepEquals(authorizationScopeToImport, existingClientAuthorizationScope, "id")) {
            authorizationScopeToImport.setId(existingClientAuthorizationScope.getId());
            logger.debug("Update authorization scope '{}' for client '{}' in realm '{}'",
                    authorizationScopeNameToImport, client.getClientId(), realmName);

            clientRepository.updateAuthorizationScope(realmName, client.getId(), authorizationScopeToImport);
        }
    }

    private void removeAuthorizationScopes(String realmName, ClientRepresentation client, List<ScopeRepresentation> existingClientAuthorizationScopes, List<ScopeRepresentation> authorizationScopesToImport) {
        List<String> authorizationScopeNamesToImport = authorizationScopesToImport
                .stream().map(ScopeRepresentation::getName)
                .collect(Collectors.toList());

        for (ScopeRepresentation existingClientAuthorizationScope : existingClientAuthorizationScopes) {
            if (!authorizationScopeNamesToImport.contains(existingClientAuthorizationScope.getName())) {
                removeAuthorizationScope(realmName, client, existingClientAuthorizationScope);
            }
        }
    }

    private void removeAuthorizationScope(String realmName, ClientRepresentation client, ScopeRepresentation existingClientAuthorizationScope) {
        logger.debug("Remove authorization scope '{}' for client '{}' in realm '{}'", existingClientAuthorizationScope.getName(), client.getClientId(), realmName);
        clientRepository.removeAuthorizationScope(realmName, client.getId(), existingClientAuthorizationScope.getId());
    }

    private void createOrUpdateAuthorizationPolicies(String realmName, ClientRepresentation client, List<PolicyRepresentation> existingClientAuthorizationPolicies, List<PolicyRepresentation> authorizationPoliciesToImport) {
        Map<String, PolicyRepresentation> existingClientAuthorizationPoliciesMap = existingClientAuthorizationPolicies
                .stream()
                .collect(Collectors.toMap(PolicyRepresentation::getName, resource -> resource));

        for (PolicyRepresentation authorizationPolicyToImport : authorizationPoliciesToImport) {
            createOrUpdateAuthorizationPolicy(realmName, client, existingClientAuthorizationPoliciesMap, authorizationPolicyToImport);
        }
    }

    private void createOrUpdateAuthorizationPolicy(String realmName, ClientRepresentation client, Map<String, PolicyRepresentation> existingClientAuthorizationPoliciesMap, PolicyRepresentation authorizationPolicyToImport) {
        if (!existingClientAuthorizationPoliciesMap.containsKey(authorizationPolicyToImport.getName())) {
            logger.debug("Create authorization policy '{}' for client '{}' in realm '{}'", authorizationPolicyToImport.getName(), client.getClientId(), realmName);
            clientRepository.createAuthorizationPolicy(realmName, client.getId(), authorizationPolicyToImport);
        } else {
            updateAuthorizationPolicy(realmName, client, existingClientAuthorizationPoliciesMap, authorizationPolicyToImport);
        }
    }

    private void updateAuthorizationPolicy(String realmName, ClientRepresentation client, Map<String, PolicyRepresentation> existingClientAuthorizationPoliciesMap, PolicyRepresentation authorizationPolicyToImport) {
        PolicyRepresentation existingClientAuthorizationPolicy = existingClientAuthorizationPoliciesMap
                .get(authorizationPolicyToImport.getName());

        if (!CloneUtil.deepEquals(authorizationPolicyToImport, existingClientAuthorizationPolicy, "id")) {
            authorizationPolicyToImport.setId(existingClientAuthorizationPolicy.getId());
            logger.debug("Update authorization policy '{}' for client '{}' in realm '{}'", authorizationPolicyToImport.getName(), client.getClientId(), realmName);
            clientRepository.updateAuthorizationPolicy(realmName, client.getId(), authorizationPolicyToImport);
        }
    }

    private void removeAuthorizationPolicies(String realmName, ClientRepresentation client, List<PolicyRepresentation> existingClientAuthorizationPolicies, List<PolicyRepresentation> authorizationPoliciesToImport) {
        List<String> authorizationPolicyNamesToImport = authorizationPoliciesToImport
                .stream().map(PolicyRepresentation::getName)
                .collect(Collectors.toList());

        for (PolicyRepresentation existingClientAuthorizationPolicy : existingClientAuthorizationPolicies) {
            if (!authorizationPolicyNamesToImport.contains(existingClientAuthorizationPolicy.getName())) {
                removeAuthorizationPolicy(realmName, client, existingClientAuthorizationPolicy);
            }
        }
    }

    private void removeAuthorizationPolicy(String realmName, ClientRepresentation client, PolicyRepresentation existingClientAuthorizationPolicy) {
        logger.debug("Remove authorization policy '{}' for client '{}' in realm '{}'", existingClientAuthorizationPolicy.getName(), client.getClientId(), realmName);
        try {
            clientRepository.removeAuthorizationPolicy(realmName, client.getId(), existingClientAuthorizationPolicy.getId());
        } catch (NotFoundException ignored) {
            // policies got deleted if linked resources are deleted, too.
        }
    }

    private void updateClientAuthenticationFlowBindingOverrides(RealmImport realmImport, List<ClientRepresentation> clients) {
        String realmName = realmImport.getRealm();

        for (ClientRepresentation client : clients) {
            ClientRepresentation existingClient = clientRepository.getByClientId(realmName, client.getClientId());
            updateAuthenticationFlowBindingOverrides(realmName, existingClient, client.getAuthenticationFlowBindingOverrides());
        }
    }

    private void updateAuthenticationFlowBindingOverrides(String realmName, ClientRepresentation existingClient, Map<String, String> authenticationFlowBindingOverrides) {
        if (Objects.equals(authenticationFlowBindingOverrides, existingClient.getAuthenticationFlowBindingOverrides())) {
            return;
        }

        Map<String, String> authFlowUpdates = new HashMap<>(existingClient.getAuthenticationFlowBindingOverrides());

        // Be sure that all existing values will be cleared
        // See: https://github.com/keycloak/keycloak/blob/790b549cf99dbbba109e145654ee4a4cd1a047c9/server-spi-private/src/main/java/org/keycloak/models/utils/RepresentationToModel.java#L1516
        authFlowUpdates.replaceAll((k, v) -> v = null);

        // Compute new values
        if (authenticationFlowBindingOverrides != null) {
            for (Map.Entry<String, String> override : authenticationFlowBindingOverrides.entrySet()) {
                if (
                        override.getValue() == null || override.getValue().isEmpty()
                                || authenticationFlowRepository.exists(realmName, override.getValue())
                ) {
                    authFlowUpdates.put(override.getKey(), override.getValue());
                } else {
                    String flowId = authenticationFlowRepository.getByAlias(realmName, override.getValue()).getId();
                    authFlowUpdates.put(override.getKey(), flowId);
                }
            }
        }

        existingClient.setAuthenticationFlowBindingOverrides(authFlowUpdates);
        updateClient(realmName, existingClient);
    }
}
