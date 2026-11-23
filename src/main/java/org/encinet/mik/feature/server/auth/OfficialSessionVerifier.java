package org.encinet.mik.feature.server.auth;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.HttpDiscoveryService;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.MinecraftClientException;
import com.mojang.authlib.exceptions.MinecraftClientHttpException;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.minecraft.client.ObjectMapper;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.services.ProfileActionType;
import com.mojang.authlib.services.ProfileResult;
import com.mojang.authlib.services.response.ErrorResponse;
import com.mojang.authlib.services.response.HasJoinedMinecraftServerResponse;
import com.mojang.authlib.services.response.discovery.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

/** Keeps HTTP status visible where Authlib's hasJoinedServer otherwise collapses 429 into a rejection. */
final class OfficialSessionVerifier implements FallbackSessionService.Verifier {
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private final MinecraftServicesDiscoveryService discovery;
    private final ObjectMapper json = ObjectMapper.create();

    OfficialSessionVerifier(MinecraftServicesDiscoveryService discovery) {
        this.discovery = discovery;
    }

    @Override public ProfileResult verify(String name, String serverId, InetAddress address)
            throws AuthenticationUnavailableException {
        var query = new HashMap<String, Object>();
        query.put("username", name);
        query.put("serverId", serverId);
        if (address != null) query.put("ip", address.getHostAddress());

        HttpURLConnection connection;
        try {
            var endpoint = URI.create(discovery.getUrl(Service.SESSION, "verify")).toURL();
            var url = HttpDiscoveryService.concatenateURL(endpoint, HttpDiscoveryService.buildQuery(query));
            if (!(url.openConnection(discovery.getProxy()) instanceof HttpURLConnection http)) return null;
            connection = http;
        } catch (MinecraftClientException failure) {
            if (failure.toAuthenticationException() instanceof AuthenticationUnavailableException unavailable) throw unavailable;
            return null;
        } catch (MalformedURLException | IllegalArgumentException invalidEndpoint) {
            return null;
        } catch (IOException failure) {
            throw new AuthenticationUnavailableException("Could not open official session verification", failure);
        }

        try {
            connection.setConnectTimeout(MinecraftClient.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(MinecraftClient.READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            int status;
            try { status = connection.getResponseCode(); }
            catch (IOException failure) {
                throw new AuthenticationUnavailableException("Official session verification did not respond", failure);
            }
            if (status == HttpURLConnection.HTTP_OK) return readProfile(connection, name);
            if (status == TOO_MANY_REQUESTS || (status >= 500 && status < 600)) {
                var error = readBody(connection.getErrorStream(), ErrorResponse.class);
                var failure = new MinecraftClientHttpException(status, error).toAuthenticationException();
                // Authlib gives explicit account restrictions precedence over transport status.
                if (failure instanceof AuthenticationUnavailableException
                        || (status == TOO_MANY_REQUESTS && failure.getClass() == AuthenticationException.class)) {
                    throw new AuthenticationUnavailableException("Official session verification returned HTTP " + status);
                }
            }
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private ProfileResult readProfile(HttpURLConnection connection, String name) {
        HasJoinedMinecraftServerResponse result;
        try { result = readBody(connection.getInputStream(), HasJoinedMinecraftServerResponse.class); }
        catch (IOException invalidResponse) { return null; }
        if (result == null || result.id() == null) return null;

        var actions = EnumSet.noneOf(ProfileActionType.class);
        for (var action : result.profileActions()) {
            if (action == null || action.type() == null) return null;
            actions.add(action.type());
        }
        return new ProfileResult(new GameProfile(result.id(), name,
                Objects.requireNonNullElse(result.properties(), PropertyMap.EMPTY)), Set.copyOf(actions));
    }

    private <T> T readBody(InputStream stream, Class<T> type) {
        if (stream == null) return null;
        try (stream) {
            byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length == 0 || bytes.length > MAX_RESPONSE_BYTES) return null;
            return json.readValue(new String(bytes, StandardCharsets.UTF_8), type);
        } catch (IOException | MinecraftClientException invalidResponse) {
            return null;
        }
    }
}
