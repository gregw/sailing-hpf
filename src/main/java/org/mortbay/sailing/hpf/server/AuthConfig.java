package org.mortbay.sailing.hpf.server;

record AuthConfig(String clientId, String clientSecret, String baseUrl, String allowedDomain)
{
    boolean devMode()
    {
        return clientId == null || clientId.isBlank();
    }

}
