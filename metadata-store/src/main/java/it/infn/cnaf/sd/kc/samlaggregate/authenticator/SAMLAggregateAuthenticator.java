package it.infn.cnaf.sd.kc.samlaggregate.authenticator;

import static it.infn.cnaf.sd.kc.wayf.resources.SAMLAggregateWayfResource.ENTITY_ID_PARAM;
import static it.infn.cnaf.sd.kc.wayf.resources.SAMLAggregateWayfResource.RETURN_PARAM;

import java.net.URI;
import java.util.UUID;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.jboss.logging.Logger;
import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.constants.AdapterConstants;
import org.keycloak.constants.ServiceUrlConstants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.oidc.utils.PkceUtils;
import org.keycloak.services.resources.RealmsResource;

import it.infn.cnaf.sd.kc.samlaggregate.resources.SAMLAggregateBrokerResource;
import it.infn.cnaf.sd.kc.wayf.resources.SAMLAggregateWayfResource;

public class SAMLAggregateAuthenticator implements Authenticator {

  public final static String SAML_AGGREGATE_AUTH_PROVIDER = "samlaggregate";
  public final static String SAML_AGGREGATE_AUTH_IDP = "idp";

  private final String PKCE_METHOD = "S256";
  private final String RESPONSE_TYPE = "code";
  private final String SCOPE = "openid";

  private static final Logger LOG = Logger.getLogger(SAMLAggregateAuthenticator.class);

  @Override
  public void authenticate(AuthenticationFlowContext context) {

    if (hasProvider(context)) {
      String provider = getProvider(context);
      if (hasIdp(context)) {
        String idp = getIdp(context);
        // Redirect to SAML aggregate login end-point with IDP
        redirectToIdentityBrokerServiceLogin(context, provider, idp);
      } else {
        // Redirect to WAYF: which is your PROVIDER's IDP?
        redirectToWayf(context, provider);
      }
    } else {
      // nothing to do
      context.attempted();
    }
  }

  private boolean hasProvider(AuthenticationFlowContext context) {
    // return context.getUriInfo().getQueryParameters().containsKey(AdapterConstants.KC_IDP_HINT);
    return context.getUriInfo().getQueryParameters().containsKey(SAMLAggregateAuthenticator.SAML_AGGREGATE_AUTH_PROVIDER);
  }

  private String getProvider(AuthenticationFlowContext context) {
    // return context.getUriInfo().getQueryParameters().getFirst(AdapterConstants.KC_IDP_HINT);
    return context.getUriInfo().getQueryParameters().getFirst(SAMLAggregateAuthenticator.SAML_AGGREGATE_AUTH_PROVIDER);
  }

  private boolean hasIdp(AuthenticationFlowContext context) {
    return context.getUriInfo().getQueryParameters().containsKey(SAML_AGGREGATE_AUTH_IDP);
  }

  private String getIdp(AuthenticationFlowContext context) {
    return context.getUriInfo().getQueryParameters().getFirst(SAML_AGGREGATE_AUTH_IDP);
  }

  protected void redirectToWayf(AuthenticationFlowContext context, String provider) {

    URI redirectUri = UriBuilder.fromUri(context.getUriInfo().getBaseUri())
        .path(RealmsResource.class)
        .path(RealmsResource.class, "getRealmResource")
        .path("saml-aggregate-broker")
        .path(SAMLAggregateBrokerResource.class, "login")
        .build(context.getRealm().getName(), provider);

    URI location = UriBuilder.fromUri(context.getUriInfo().getBaseUri())
      .path(RealmsResource.class)
      .path(RealmsResource.class, "getRealmResource")
      .path("saml-wayf")
      .path(SAMLAggregateWayfResource.class, "discover")
      .queryParam(ENTITY_ID_PARAM, getEntityId(context))
      .queryParam(RETURN_PARAM, redirectUri)
      .build(context.getRealm().getName(), provider);

    Response response = Response.seeOther(location).build();
    context.forceChallenge(response);
  }

  private String getEntityId(AuthenticationFlowContext context) {
    return context.getHttpRequest().getUri().getBaseUri().toString();
  }

  private void redirectToIdentityBrokerServiceLogin(AuthenticationFlowContext context, String provider, String idp) {

    String state = UUID.randomUUID().toString();
    String codeVerifier = PkceUtils.generateCodeVerifier();
    String codeChallenge = PkceUtils.encodeCodeChallenge(codeVerifier, PKCE_METHOD);
    String clientId = "boh";
    String redirectUri = UriBuilder.fromUri(context.getUriInfo().getBaseUri())
      .path(ServiceUrlConstants.ACCOUNT_SERVICE_PATH)
      .build(context.getRealm().getName())
      .toString();

    URI location = UriBuilder.fromPath(ServiceUrlConstants.AUTH_PATH)
      .queryParam("samlaggregate", provider)
      .queryParam(OAuth2Constants.CODE_CHALLENGE, codeChallenge)
      .queryParam(OAuth2Constants.CODE_CHALLENGE_METHOD, PKCE_METHOD)
      .queryParam(OAuth2Constants.CLIENT_ID, clientId)
      .queryParam(OAuth2Constants.STATE, state)
      .queryParam(OAuth2Constants.SCOPE, SCOPE)
      .queryParam(OAuth2Constants.RESPONSE_TYPE, RESPONSE_TYPE)
      .queryParam(OAuth2Constants.REDIRECT_URI, redirectUri)
      .queryParam(AdapterConstants.KC_IDP_HINT, provider)
      .build(context.getRealm().getName());

    Response response = Response.seeOther(location).build();
    context.forceChallenge(response);

  }

  @Override
  public void action(AuthenticationFlowContext context) {

  }

  @Override
  public boolean requiresUser() {
    return false;
  }

  @Override
  public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
    return true;
  }

  @Override
  public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {

  }

  @Override
  public void close() {

  }
}
