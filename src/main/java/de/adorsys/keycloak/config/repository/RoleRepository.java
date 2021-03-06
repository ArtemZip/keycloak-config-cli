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

package de.adorsys.keycloak.config.repository;

import de.adorsys.keycloak.config.exception.ImportProcessingException;
import de.adorsys.keycloak.config.exception.KeycloakRepositoryException;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;

@Service
public class RoleRepository {

    private final RealmRepository realmRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    @Autowired
    public RoleRepository(
            RealmRepository realmRepository,
            ClientRepository clientRepository,
            UserRepository userRepository
    ) {
        this.realmRepository = realmRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
    }

    public Optional<RoleRepresentation> searchRealmRole(String realmName, String name) {
        Optional<RoleRepresentation> maybeRole;

        RolesResource rolesResource = realmRepository.getResource(realmName).roles();
        RoleResource roleResource = rolesResource.get(name);

        try {
            maybeRole = Optional.of(roleResource.toRepresentation());
        } catch (NotFoundException e) {
            maybeRole = Optional.empty();
        }

        return maybeRole;
    }

    public void createRealmRole(String realmName, RoleRepresentation role) {
        RolesResource rolesResource = realmRepository.getResource(realmName).roles();
        rolesResource.create(role);
    }

    public void updateRealmRole(String realmName, RoleRepresentation roleToUpdate) {
        RoleResource roleResource = realmRepository.getResource(realmName)
                .roles()
                .get(roleToUpdate.getName());

        roleResource.update(roleToUpdate);
    }

    public RoleRepresentation getRealmRole(String realmName, String roleName) {
        return searchRealmRole(realmName, roleName)
                .orElseThrow(
                        () -> new KeycloakRepositoryException(
                                "Cannot find realm role '" + roleName + "' within realm '" + realmName + "'"
                        )
                );
    }

    public List<RoleRepresentation> getRealmRoles(String realmName, Collection<String> roles) {
        return roles.stream()
                .map(role -> getRealmRole(realmName, role))
                .collect(Collectors.toList());
    }

    public final Optional<RoleRepresentation> searchClientRole(String realmName, String clientId, String roleName) {
        ClientRepresentation client = clientRepository.getByClientId(realmName, clientId);
        RealmResource realmResource = realmRepository.getResource(realmName);

        List<RoleRepresentation> clientRoles = realmResource.clients()
                .get(client.getId())
                .roles()
                .list();

        return clientRoles.stream()
                .filter(r -> Objects.equals(r.getName(), roleName))
                .findFirst();
    }

    public RoleRepresentation getClientRole(String realmName, String clientId, String roleName) {
        return searchClientRole(realmName, clientId, roleName)
                .orElse(null);
    }

    public List<RoleRepresentation> getClientRoles(String realmName, String clientId, List<String> roles) {
        ClientRepresentation foundClient = clientRepository.getByClientId(realmName, clientId);

        ClientResource clientResource = realmRepository.getResource(realmName)
                .clients()
                .get(foundClient.getId());

        return roles.stream()
                .map(role -> clientResource.roles()
                        .get(role)
                        .toRepresentation()
                ).collect(Collectors.toList());
    }

    public void createClientRole(String realmName, String clientId, RoleRepresentation role) {
        ClientRepresentation client = clientRepository.getByClientId(realmName, clientId);
        RolesResource rolesResource = realmRepository.getResource(realmName)
                .clients()
                .get(client.getId())
                .roles();

        rolesResource.create(role);
    }

    public void updateClientRole(String realmName, String clientId, RoleRepresentation roleToUpdate) {
        RoleResource roleResource = loadClientRole(realmName, clientId, roleToUpdate.getName());
        roleResource.update(roleToUpdate);
    }

    public List<RoleRepresentation> searchRealmRoles(String realmName, List<String> roleNames) {
        List<RoleRepresentation> roles = new ArrayList<>();
        RealmResource realmResource = realmRepository.getResource(realmName);

        for (String roleName : roleNames) {
            try {
                RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();

                roles.add(role);
            } catch (NotFoundException e) {
                throw new ImportProcessingException("Could not find role '" + roleName + "' in realm '" + realmName + "'!");
            }
        }

        return roles;
    }

    public List<String> getUserRealmLevelRoles(String realmName, String username) {
        UserRepresentation user = userRepository.get(realmName, username);
        UserResource userResource = realmRepository.getResource(realmName)
                .users()
                .get(user.getId());

        List<RoleRepresentation> roles = userResource.roles()
                .realmLevel()
                .listEffective();

        return roles.stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toList());
    }

    public void addRealmRolesToUser(String realmName, String username, List<RoleRepresentation> realmRoles) {
        UserResource userResource = userRepository.getResource(realmName, username);
        userResource.roles().realmLevel().add(realmRoles);
    }

    public void removeRealmRolesForUser(String realmName, String username, List<RoleRepresentation> realmRoles) {
        UserResource userResource = userRepository.getResource(realmName, username);
        userResource.roles().realmLevel().remove(realmRoles);
    }

    public void addClientRolesToUser(String realmName, String username, String clientId, List<RoleRepresentation> clientRoles) {
        ClientRepresentation client = clientRepository.getByClientId(realmName, clientId);
        UserResource userResource = userRepository.getResource(realmName, username);

        RoleScopeResource userClientRoles = userResource.roles()
                .clientLevel(client.getId());

        userClientRoles.add(clientRoles);
    }

    public void removeClientRolesForUser(String realmName, String username, String clientId, List<RoleRepresentation> clientRoles) {
        ClientRepresentation client = clientRepository.getByClientId(realmName, clientId);
        UserResource userResource = userRepository.getResource(realmName, username);

        RoleScopeResource userClientRoles = userResource.roles()
                .clientLevel(client.getId());

        userClientRoles.remove(clientRoles);
    }

    public List<String> getUserClientLevelRoles(String realmName, String username, String clientId) {
        ClientRepresentation client = clientRepository.getByClientId(realmName, clientId);
        UserResource userResource = userRepository.getResource(realmName, username);

        List<RoleRepresentation> roles = userResource.roles()
                .clientLevel(client.getId())
                .listEffective();

        return roles.stream().map(RoleRepresentation::getName).collect(Collectors.toList());
    }

    final RoleResource loadRealmRole(String realmName, String roleName) {
        RealmResource realmResource = realmRepository.getResource(realmName);
        return realmResource
                .roles()
                .get(roleName);
    }

    final RoleResource loadClientRole(String realmName, String roleClientId, String roleName) {
        ClientRepresentation client = clientRepository.getByClientId(realmName, roleClientId);

        return realmRepository.getResource(realmName)
                .clients()
                .get(client.getId())
                .roles()
                .get(roleName);
    }
}
