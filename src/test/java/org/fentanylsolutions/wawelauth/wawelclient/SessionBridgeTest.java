package org.fentanylsolutions.wawelauth.wawelclient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.util.Session;

import org.fentanylsolutions.wawelauth.wawelclient.data.AccountStatus;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientAccount;
import org.fentanylsolutions.wawelauth.wawelclient.data.ClientProvider;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientAccountDAO;
import org.fentanylsolutions.wawelauth.wawelclient.storage.ClientProviderDAO;
import org.junit.Assert;
import org.junit.Test;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

public class SessionBridgeTest {

    @Test
    public void pingProfileContextExpiresAfterTtl() {
        SessionBridge bridge = new SessionBridge(null, null, null, null, launcherSession("token"));
        UUID profileId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        bridge.rememberPingProfiles(
            ServerCapabilities.unadvertised(System.currentTimeMillis()),
            new GameProfile[] { new GameProfile(profileId, "Sample") });

        long now = System.currentTimeMillis();
        Assert.assertTrue(bridge.hasFreshPingProfileContext(profileId, now));
        Assert.assertFalse(bridge.hasFreshPingProfileContext(profileId, now + 31_000L));
    }

    @Test
    public void createServerLookupContextUsesLauncherSessionForUnknownServers() {
        InMemoryProviderDao providerDao = new InMemoryProviderDao();
        providerDao.create(provider("Mojang"));

        SessionBridge bridge = new SessionBridge(
            null,
            providerDao,
            new InMemoryAccountDao(),
            null,
            launcherSession("launcher-token"));

        SessionBridge.LookupContext context = bridge
            .createServerLookupContext(ServerCapabilities.unadvertised(System.currentTimeMillis()));

        Assert.assertTrue(context.isVanillaFallbackAllowed());
        Assert.assertNotNull(context.getProvider());
        Assert.assertEquals(
            "Mojang",
            context.getProvider()
                .getName());
        Assert.assertEquals(
            1,
            context.getTrustedProviders()
                .size());
    }

    @Test
    public void explicitEmptyLookupContextDoesNotFallBackToLocalAccounts() {
        UUID profileId = UUID.fromString("12345678-1234-1234-1234-1234567890ab");
        InMemoryProviderDao providerDao = new InMemoryProviderDao();
        providerDao.create(provider("Alpha"));

        InMemoryAccountDao accountDao = new InMemoryAccountDao();
        accountDao.create(account("Alpha", profileId));

        SessionBridge bridge = new SessionBridge(
            null,
            providerDao,
            accountDao,
            null,
            launcherSession("launcher-token"));

        SessionBridge.LookupContext missingProviderContext = bridge.createProviderLookupContext("Missing", false);

        Assert.assertFalse(bridge.hasProviderForProfile(profileId, missingProviderContext));
    }

    @Test
    public void unsignedProfilesDoNotPopulateSignedCacheEntries() {
        UUID profileId = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");
        GameProfile unsignedProfile = profileWithTextures(profileId, null);

        java.util.Map<String, GameProfile> cache = new java.util.HashMap<>();
        SessionBridge.putFetchedProfileInCache(cache, "provider:alpha|" + profileId + "|U", "provider:alpha|" + profileId + "|S", unsignedProfile, false);

        Assert.assertSame(unsignedProfile, cache.get("provider:alpha|" + profileId + "|U"));
        Assert.assertNull(cache.get("provider:alpha|" + profileId + "|S"));
    }

    @Test
    public void signedProfilesCanSatisfyUnsignedCacheEntries() {
        UUID profileId = UUID.fromString("cccccccc-1111-2222-3333-dddddddddddd");
        GameProfile signedProfile = profileWithTextures(profileId, "sig");

        java.util.Map<String, GameProfile> cache = new java.util.HashMap<>();
        SessionBridge.putFetchedProfileInCache(cache, "provider:alpha|" + profileId + "|S", "provider:alpha|" + profileId + "|U", signedProfile, true);

        Assert.assertSame(signedProfile, cache.get("provider:alpha|" + profileId + "|S"));
        Assert.assertSame(signedProfile, cache.get("provider:alpha|" + profileId + "|U"));
    }

    private static Session launcherSession(String token) {
        return new Session(
            "LauncherUser",
            UUID.randomUUID()
                .toString(),
            token,
            "mojang");
    }

    private static ClientProvider provider(String name) {
        ClientProvider provider = new ClientProvider();
        provider.setName(name);
        return provider;
    }

    private static GameProfile profileWithTextures(UUID profileId, String signature) {
        GameProfile profile = new GameProfile(profileId, "Profile");
        profile.getProperties()
            .put("textures", new Property("textures", "value", signature));
        return profile;
    }

    private static ClientAccount account(String providerName, UUID profileId) {
        ClientAccount account = new ClientAccount();
        account.setProviderName(providerName);
        account.setProfileUuid(profileId);
        account.setProfileName("Profile");
        account.setStatus(AccountStatus.VALID);
        return account;
    }

    private static final class InMemoryProviderDao implements ClientProviderDAO {

        private final List<ClientProvider> providers = new ArrayList<>();

        @Override
        public ClientProvider findByName(String name) {
            for (ClientProvider provider : providers) {
                if (provider.getName() != null && provider.getName()
                    .equals(name)) {
                    return provider;
                }
            }
            return null;
        }

        @Override
        public List<ClientProvider> listAll() {
            return new ArrayList<>(providers);
        }

        @Override
        public void create(ClientProvider provider) {
            providers.add(provider);
        }

        @Override
        public void update(ClientProvider provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rename(String oldName, String newName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            return providers.size();
        }
    }

    private static final class InMemoryAccountDao implements ClientAccountDAO {

        private final List<ClientAccount> accounts = new ArrayList<>();
        private long nextId = 1L;

        @Override
        public ClientAccount findById(long id) {
            for (ClientAccount account : accounts) {
                if (account.getId() == id) {
                    return account;
                }
            }
            return null;
        }

        @Override
        public ClientAccount findByProviderAndUser(String providerName, String userUuid) {
            for (ClientAccount account : accounts) {
                if (equals(providerName, account.getProviderName()) && equals(userUuid, account.getUserUuid())) {
                    return account;
                }
            }
            return null;
        }

        @Override
        public ClientAccount findByProviderAndProfile(String providerName, UUID profileUuid) {
            for (ClientAccount account : accounts) {
                if (equals(providerName, account.getProviderName()) && equals(profileUuid, account.getProfileUuid())) {
                    return account;
                }
            }
            return null;
        }

        @Override
        public ClientAccount findUnboundByProviderAndUser(String providerName, String userUuid) {
            for (ClientAccount account : accounts) {
                if (account.getProfileUuid() == null && equals(providerName, account.getProviderName())
                    && equals(userUuid, account.getUserUuid())) {
                    return account;
                }
            }
            return null;
        }

        @Override
        public List<ClientAccount> findByProvider(String providerName) {
            List<ClientAccount> matches = new ArrayList<>();
            for (ClientAccount account : accounts) {
                if (equals(providerName, account.getProviderName())) {
                    matches.add(account);
                }
            }
            return matches;
        }

        @Override
        public List<ClientAccount> listAll() {
            return new ArrayList<>(accounts);
        }

        @Override
        public long create(ClientAccount account) {
            account.setId(nextId++);
            accounts.add(account);
            return account.getId();
        }

        @Override
        public void update(ClientAccount account) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByProvider(String providerName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            return accounts.size();
        }

        private static boolean equals(Object left, Object right) {
            return left == null ? right == null : left.equals(right);
        }
    }
}
