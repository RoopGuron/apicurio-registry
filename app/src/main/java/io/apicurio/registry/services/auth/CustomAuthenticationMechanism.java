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

package io.apicurio.registry.services.auth;

import io.apicurio.rest.client.auth.OidcAuth;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.oidc.runtime.BearerAuthenticationMechanism;
import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class CustomAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Inject
    OidcAuthenticationMechanism oidcAuthenticationMechanism;

    @ConfigProperty(name = "registry.auth.token.endpoint")
    String authServerUrl;

    @ConfigProperty(name = "registry.auth.client-secret")
    Optional<String> clientSecret;

    @ConfigProperty(name = "quarkus.oidc.client-id")
    String clientId;

    @ConfigProperty(name = "registry.auth.enabled")
    boolean authEnabled;

    private final BearerAuthenticationMechanism bearerAuth = new BearerAuthenticationMechanism();;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (authEnabled) {
            if (clientSecret.isEmpty()) {
                //if no secret is present, try to authenticate with oidc provider
                return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
            } else {
                final Pair<String, String> credentialsFromContext = CredentialsHelper.extractCredentialsFromContext(context);
                if (credentialsFromContext != null) {
                    String jwtToken = new OidcAuth(authServerUrl, clientId, clientSecret.get()).obtainAccessTokenWithBasicCredentials(credentialsFromContext.getLeft(), credentialsFromContext.getRight());

                    if (jwtToken != null) {
                        //If we manage to get a token from basic credentials, try to authenticate it using the fetched token using the identity provider manager
                        return identityProviderManager
                                .authenticate(new TokenAuthenticationRequest(new AccessTokenCredential(jwtToken, context)));
                    }
                } else {
                    //If we cannot get a token, then try to authenticate using oidc provider as last resource
                    return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
                }
            }
        }
        return Uni.createFrom().nullItem();
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return bearerAuth.getChallenge(context);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Collections.singleton(TokenAuthenticationRequest.class);
    }

    @Override
    public HttpCredentialTransport getCredentialTransport() {
        return new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "bearer");
    }
}
