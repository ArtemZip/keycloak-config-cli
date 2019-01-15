package de.adorsys.keycloak.config;

import de.adorsys.keycloak.config.configuration.TestConfiguration;
import de.adorsys.keycloak.config.model.KeycloakImport;
import de.adorsys.keycloak.config.model.RealmImport;
import de.adorsys.keycloak.config.service.KeycloakImportProvider;
import de.adorsys.keycloak.config.service.KeycloakProvider;
import de.adorsys.keycloak.config.service.RealmImportService;
import de.adorsys.keycloak.config.util.ResourceLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.representations.idm.AuthenticationExecutionExportRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(
        classes = { TestConfiguration.class },
        initializers = { ConfigFileApplicationContextInitializer.class }
)
@ActiveProfiles("IT")
@DirtiesContext
public class ImportAuthenticationFlowsIT {
    private static final String REALM_NAME = "realmWithFlow";

    @Autowired
    RealmImportService realmImportService;

    @Autowired
    KeycloakImportProvider keycloakImportProvider;

    @Autowired
    KeycloakProvider keycloakProvider;

    KeycloakImport keycloakImport;

    @Before
    public void setup() throws Exception {
        File configsFolder = ResourceLoader.loadResource("import-files/auth-flows");
        this.keycloakImport = keycloakImportProvider.readRealmImportsFromDirectory(configsFolder);
    }

    @After
    public void cleanup() throws Exception {
        keycloakProvider.close();
    }

    @Test
    public void shouldReadImports() {
        assertThat(keycloakImport, is(not(nullValue())));
    }

    @Test
    public void integrationTests() throws Exception {
        shouldCreateRealmWithFlows();
        shouldAddExecutionToFlow();
        shouldChangeExecutionRequirement();
        shouldChangeExecutionPriorities();
        shouldAddFlowWithExecutionFlow();
        shouldChangeFlowRequirementWithExecutionFlow();
        shouldSetRegistrationFlow();
        shouldChangeRegistrationFlow();
        shouldAddAndSetResetCredentialsFlow();
        shouldChangeResetCredentialsFlow();
        shouldAddAndSetBrowserFlow();
        shouldChangeBrowserFlow();
        shouldAddAndSetDirectGrantFlow();
        shouldChangeDirectGrantFlow();
        shouldAddAndSetClientAuthenticationFlow();
        shouldChangeClientAuthenticationFlow();
        shouldAddAndSetDockerAuthenticationFlow();
        shouldChangeDockerAuthenticationFlow();
    }

    private void shouldCreateRealmWithFlows() throws Exception {
        doImport("0_create_realm_with_flows.json");

        RealmRepresentation createdRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(createdRealm.getRealm(), is(REALM_NAME));
        assertThat(createdRealm.isEnabled(), is(true));

        AuthenticationFlowRepresentation importedFlow = getAuthenticationFlow(createdRealm, "my auth flow");
        assertThat(importedFlow.getDescription(), is("My auth flow for testing"));
        assertThat(importedFlow.getProviderId(), is("basic-flow"));
        assertThat(importedFlow.isBuiltIn(), is(false));
        assertThat(importedFlow.isTopLevel(), is(true));

        List<AuthenticationExecutionExportRepresentation> importedExecutions = importedFlow.getAuthenticationExecutions();
        assertThat(importedExecutions, hasSize(1));

        AuthenticationExecutionExportRepresentation importedExecution = getExecutionFromFlow(importedFlow, "docker-http-basic-authenticator");
        assertThat(importedExecution.getAuthenticator(), is("docker-http-basic-authenticator"));
        assertThat(importedExecution.getRequirement(), is("DISABLED"));
        assertThat(importedExecution.getPriority(), is(0));
        assertThat(importedExecution.isAutheticatorFlow(), is(false));
    }

    private void shouldAddExecutionToFlow() {
        doImport("1_update_realm__add_execution_to_flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        AuthenticationFlowRepresentation unchangedFlow = getAuthenticationFlow(updatedRealm, "my auth flow");
        assertThat(unchangedFlow.getDescription(), is("My auth flow for testing"));
        assertThat(unchangedFlow.getProviderId(), is("basic-flow"));
        assertThat(unchangedFlow.isBuiltIn(), is(false));
        assertThat(unchangedFlow.isTopLevel(), is(true));

        List<AuthenticationExecutionExportRepresentation> importedExecutions = unchangedFlow.getAuthenticationExecutions();
        assertThat(importedExecutions, hasSize(2));

        AuthenticationExecutionExportRepresentation importedExecution = getExecutionFromFlow(unchangedFlow, "docker-http-basic-authenticator");
        assertThat(importedExecution.getAuthenticator(), is("docker-http-basic-authenticator"));
        assertThat(importedExecution.getRequirement(), is("DISABLED"));
        assertThat(importedExecution.getPriority(), is(0));
        assertThat(importedExecution.isAutheticatorFlow(), is(false));

        importedExecution = getExecutionFromFlow(unchangedFlow, "http-basic-authenticator");
        assertThat(importedExecution.getAuthenticator(), is("http-basic-authenticator"));
        assertThat(importedExecution.getRequirement(), is("DISABLED"));
        assertThat(importedExecution.getPriority(), is(1));
        assertThat(importedExecution.isAutheticatorFlow(), is(false));
    }

    private void shouldChangeExecutionRequirement() {
        doImport("2_update_realm__change_execution_requirement.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        AuthenticationFlowRepresentation unchangedFlow = getAuthenticationFlow(updatedRealm, "my auth flow");
        assertThat(unchangedFlow.getDescription(), is("My auth flow for testing"));
        assertThat(unchangedFlow.getProviderId(), is("basic-flow"));
        assertThat(unchangedFlow.isBuiltIn(), is(false));
        assertThat(unchangedFlow.isTopLevel(), is(true));

        List<AuthenticationExecutionExportRepresentation> importedExecutions = unchangedFlow.getAuthenticationExecutions();
        assertThat(importedExecutions, hasSize(2));

        AuthenticationExecutionExportRepresentation importedExecution = getExecutionFromFlow(unchangedFlow, "docker-http-basic-authenticator");
        assertThat(importedExecution.getAuthenticator(), is("docker-http-basic-authenticator"));
        assertThat(importedExecution.getRequirement(), is("REQUIRED"));
        assertThat(importedExecution.getPriority(), is(0));
        assertThat(importedExecution.isAutheticatorFlow(), is(false));

        importedExecution = getExecutionFromFlow(unchangedFlow, "http-basic-authenticator");
        assertThat(importedExecution.getAuthenticator(), is("http-basic-authenticator"));
        assertThat(importedExecution.getRequirement(), is("DISABLED"));
        assertThat(importedExecution.getPriority(), is(1));
        assertThat(importedExecution.isAutheticatorFlow(), is(false));
    }

    private void shouldChangeExecutionPriorities() {
        doImport("3_update_realm__change_execution_priorities.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        AuthenticationFlowRepresentation unchangedFlow = getAuthenticationFlow(updatedRealm, "my auth flow");
        assertThat(unchangedFlow.getDescription(), is("My auth flow for testing"));
        assertThat(unchangedFlow.getProviderId(), is("basic-flow"));
        assertThat(unchangedFlow.isBuiltIn(), is(false));
        assertThat(unchangedFlow.isTopLevel(), is(true));

        List<AuthenticationExecutionExportRepresentation> importedExecutions = unchangedFlow.getAuthenticationExecutions();
        assertThat(importedExecutions, hasSize(2));

        AuthenticationExecutionExportRepresentation importedExecution = getExecutionFromFlow(unchangedFlow, "docker-http-basic-authenticator");
        assertThat(importedExecution.getAuthenticator(), is("docker-http-basic-authenticator"));
        assertThat(importedExecution.getRequirement(), is("REQUIRED"));
        assertThat(importedExecution.getPriority(), is(1));
        assertThat(importedExecution.isAutheticatorFlow(), is(false));

        importedExecution = getExecutionFromFlow(unchangedFlow, "http-basic-authenticator");
        assertThat(importedExecution.getAuthenticator(), is("http-basic-authenticator"));
        assertThat(importedExecution.getRequirement(), is("DISABLED"));
        assertThat(importedExecution.getPriority(), is(0));
        assertThat(importedExecution.isAutheticatorFlow(), is(false));
    }

    private void shouldAddFlowWithExecutionFlow() {
        doImport("4_update_realm__add_flow_with_execution_flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my registration");
        assertThat(topLevelFlow.getDescription(), is("My registration flow"));
        assertThat(topLevelFlow.getProviderId(), is("basic-flow"));
        assertThat(topLevelFlow.isBuiltIn(), is(false));
        assertThat(topLevelFlow.isTopLevel(), is(true));

        List<AuthenticationExecutionExportRepresentation> executionFlows = topLevelFlow.getAuthenticationExecutions();
        assertThat(executionFlows, hasSize(1));

        AuthenticationExecutionExportRepresentation execution = getExecutionFromFlow(topLevelFlow, "registration-page-form");
        assertThat(execution.getAuthenticator(), is("registration-page-form"));
        assertThat(execution.getRequirement(), is("REQUIRED"));
        assertThat(execution.getPriority(), is(0));
        assertThat(execution.isAutheticatorFlow(), is(true));

        AuthenticationFlowRepresentation nonTopLevelFlow = getAuthenticationFlow(updatedRealm, "my registration form");

        List<AuthenticationExecutionExportRepresentation> nonTopLevelFlowExecutions = nonTopLevelFlow.getAuthenticationExecutions();
        assertThat(nonTopLevelFlowExecutions, hasSize(2));

        execution = getExecutionFromFlow(nonTopLevelFlow, "registration-user-creation");
        assertThat(execution.getAuthenticator(), is("registration-user-creation"));
        assertThat(execution.getRequirement(), is("REQUIRED"));
        assertThat(execution.getPriority(), is(0));
        assertThat(execution.isAutheticatorFlow(), is(false));

        execution = getExecutionFromFlow(nonTopLevelFlow, "registration-profile-action");
        assertThat(execution.getAuthenticator(), is("registration-profile-action"));
        assertThat(execution.getRequirement(), is("DISABLED"));
        assertThat(execution.getPriority(), is(1));
        assertThat(execution.isAutheticatorFlow(), is(false));
    }

    private void shouldChangeFlowRequirementWithExecutionFlow() {
        doImport("5_update_realm__change_requirement_flow_with_execution_flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my registration");
        assertThat(topLevelFlow.getDescription(), is("My registration flow"));
        assertThat(topLevelFlow.getProviderId(), is("basic-flow"));
        assertThat(topLevelFlow.isBuiltIn(), is(false));
        assertThat(topLevelFlow.isTopLevel(), is(true));

        List<AuthenticationExecutionExportRepresentation> executionFlows = topLevelFlow.getAuthenticationExecutions();
        assertThat(executionFlows, hasSize(1));

        AuthenticationExecutionExportRepresentation execution = getExecutionFromFlow(topLevelFlow, "registration-page-form");
        assertThat(execution.getAuthenticator(), is("registration-page-form"));
        assertThat(execution.getRequirement(), is("REQUIRED"));
        assertThat(execution.getPriority(), is(0));
        assertThat(execution.isAutheticatorFlow(), is(true));

        AuthenticationFlowRepresentation nonTopLevelFlow = getAuthenticationFlow(updatedRealm, "my registration form");

        List<AuthenticationExecutionExportRepresentation> nonTopLevelFlowExecutions = nonTopLevelFlow.getAuthenticationExecutions();
        assertThat(nonTopLevelFlowExecutions, hasSize(2));

        execution = getExecutionFromFlow(nonTopLevelFlow, "registration-user-creation");
        assertThat(execution.getAuthenticator(), is("registration-user-creation"));
        assertThat(execution.getRequirement(), is("REQUIRED"));
        assertThat(execution.getPriority(), is(0));
        assertThat(execution.isAutheticatorFlow(), is(false));

        execution = getExecutionFromFlow(nonTopLevelFlow, "registration-profile-action");
        assertThat(execution.getAuthenticator(), is("registration-profile-action"));
        assertThat(execution.getRequirement(), is("REQUIRED"));
        assertThat(execution.getPriority(), is(1));
        assertThat(execution.isAutheticatorFlow(), is(false));
    }

    private void shouldChangeFlowPriorityWithExecutionFlow() {
        doImport("6_update_realm__change_priority_flow_with_execution_flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my registration");
        assertThat(topLevelFlow.getDescription(), is("My registration flow"));
        assertThat(topLevelFlow.getProviderId(), is("basic-flow"));
        assertThat(topLevelFlow.isBuiltIn(), is(false));
        assertThat(topLevelFlow.isTopLevel(), is(true));

        List<AuthenticationExecutionExportRepresentation> executionFlows = topLevelFlow.getAuthenticationExecutions();
        assertThat(executionFlows, hasSize(1));

        AuthenticationExecutionExportRepresentation execution = getExecutionFromFlow(topLevelFlow, "registration-page-form");
        assertThat(execution.getAuthenticator(), is("registration-page-form"));
        assertThat(execution.getRequirement(), is("REQUIRED"));
        assertThat(execution.getPriority(), is(0));
        assertThat(execution.isAutheticatorFlow(), is(true));

        AuthenticationFlowRepresentation nonTopLevelFlow = getAuthenticationFlow(updatedRealm, "my registration form");

        List<AuthenticationExecutionExportRepresentation> nonTopLevelFlowExecutions = nonTopLevelFlow.getAuthenticationExecutions();
        assertThat(nonTopLevelFlowExecutions, hasSize(2));

        execution = getExecutionFromFlow(nonTopLevelFlow, "registration-user-creation");
        assertThat(execution.getAuthenticator(), is("registration-user-creation"));
        assertThat(execution.getRequirement(), is("REQUIRED"));
        assertThat(execution.getPriority(), is(1));
        assertThat(execution.isAutheticatorFlow(), is(false));

        execution = getExecutionFromFlow(nonTopLevelFlow, "registration-profile-action");
        assertThat(execution.getAuthenticator(), is("registration-profile-action"));
        assertThat(execution.getRequirement(), is("REQUIRED"));
        assertThat(execution.getPriority(), is(0));
        assertThat(execution.isAutheticatorFlow(), is(false));
    }

    private void shouldSetRegistrationFlow() {
        doImport("7_update_realm__set_registration_flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getRegistrationFlow(), is("my registration"));
    }

    private void shouldChangeRegistrationFlow() {
        doImport("8_update_realm__change_registration_flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getRegistrationFlow(), is("my registration"));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my registration");
        assertThat(topLevelFlow.getDescription(), is("My changed registration flow"));
    }

    private void shouldAddAndSetResetCredentialsFlow() {
        doImport("9_update_realm__add_and_set_custom_reset-credentials-flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getResetCredentialsFlow(), is("my reset credentials"));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my reset credentials");
        assertThat(topLevelFlow.getDescription(), is("My reset credentials for a user if they forgot their password or something"));
    }

    private void shouldChangeResetCredentialsFlow() {
        doImport("10_update_realm__change_custom_reset-credentials-flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getResetCredentialsFlow(), is("my reset credentials"));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my reset credentials");
        assertThat(topLevelFlow.getDescription(), is("My changed reset credentials for a user if they forgot their password or something"));
    }

    private void shouldAddAndSetBrowserFlow() {
        doImport("11_update_realm__add_and_set_custom_browser-flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getBrowserFlow(), is("my browser"));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my browser");
        assertThat(topLevelFlow.getDescription(), is("My browser based authentication"));
    }

    private void shouldChangeBrowserFlow() {
        doImport("12_update_realm__change_custom_browser-flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getBrowserFlow(), is("my browser"));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my browser");
        assertThat(topLevelFlow.getDescription(), is("My changed browser based authentication"));
    }

    private void shouldAddAndSetDirectGrantFlow() {
        doImport("13_update_realm__add_and_set_custom_direct-grant-flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getDirectGrantFlow(), is("my direct grant"));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my direct grant");
        assertThat(topLevelFlow.getDescription(), is("My OpenID Connect Resource Owner Grant"));
    }

    private void shouldChangeDirectGrantFlow() {
        doImport("14_update_realm__change_custom_direct-grant-flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getDirectGrantFlow(), is("my direct grant"));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my direct grant");
        assertThat(topLevelFlow.getDescription(), is("My changed OpenID Connect Resource Owner Grant"));
    }

    private void shouldAddAndSetClientAuthenticationFlow() {
        doImport("15_update_realm__add_and_set_custom_client-authentication-flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getClientAuthenticationFlow(), is("my clients"));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my clients");
        assertThat(topLevelFlow.getDescription(), is("My Base authentication for clients"));
    }

    private void shouldChangeClientAuthenticationFlow() {
        doImport("16_update_realm__change_custom_client-authentication-flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getClientAuthenticationFlow(), is("my clients"));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my clients");
        assertThat(topLevelFlow.getDescription(), is("My changed Base authentication for clients"));
    }

    private void shouldAddAndSetDockerAuthenticationFlow() {
        doImport("17_update_realm__add_and_set_custom_docker-authentication-flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getDockerAuthenticationFlow(), is("my docker auth"));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my docker auth");
        assertThat(topLevelFlow.getDescription(), is("My Used by Docker clients to authenticate against the IDP"));
    }

    private void shouldChangeDockerAuthenticationFlow() {
        doImport("18_update_realm__change_custom_docker-authentication-flow.json");

        RealmRepresentation updatedRealm = keycloakProvider.get().realm(REALM_NAME).partialExport(true, true);

        assertThat(updatedRealm.getRealm(), is(REALM_NAME));
        assertThat(updatedRealm.isEnabled(), is(true));

        assertThat(updatedRealm.getDockerAuthenticationFlow(), is("my docker auth"));

        AuthenticationFlowRepresentation topLevelFlow = getAuthenticationFlow(updatedRealm, "my docker auth");
        assertThat(topLevelFlow.getDescription(), is("My changed Used by Docker clients to authenticate against the IDP"));
    }

    private AuthenticationExecutionExportRepresentation getExecutionFromFlow(AuthenticationFlowRepresentation unchangedFlow, String executionAuthenticator) {
        List<AuthenticationExecutionExportRepresentation> importedExecutions = unchangedFlow.getAuthenticationExecutions();

        Optional<AuthenticationExecutionExportRepresentation> maybeImportedExecution = importedExecutions.stream()
                .filter(e -> e.getAuthenticator().equals(executionAuthenticator))
                .findFirst();

        assertThat(maybeImportedExecution.isPresent(), is(true));

        return maybeImportedExecution.get();
    }

    private AuthenticationFlowRepresentation getAuthenticationFlow(RealmRepresentation updatedRealm, String flowAlias) {
        List<AuthenticationFlowRepresentation> authenticationFlows = updatedRealm.getAuthenticationFlows();
        Optional<AuthenticationFlowRepresentation> maybeImportedFlow = authenticationFlows.stream()
                .filter(f -> f.getAlias().equals(flowAlias))
                .findFirst();

        assertThat("Cannot find authentication-flow '" + flowAlias + "'", maybeImportedFlow.isPresent(), is(true));

        return maybeImportedFlow.get();
    }

    private void doImport(String realmImport) {
        RealmImport foundImport = getImport(realmImport);
        realmImportService.doImport(foundImport);
    }

    private RealmImport getImport(String importName) {
        Map<String, RealmImport> realmImports = keycloakImport.getRealmImports();

        return realmImports.entrySet()
                .stream()
                .filter(e -> e.getKey().equals(importName))
                .map(Map.Entry::getValue)
                .findFirst()
                .get();
    }
}