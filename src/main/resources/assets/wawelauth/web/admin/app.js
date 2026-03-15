(() => {
    "use strict";

    const SESSION_STORAGE_KEY = "wawelauth_admin_session";

    const state = {
        bootstrap: null,
        sessionToken: null,
        sessionExpiresAt: 0,
        sessionTimer: null,
        activeView: "dashboardView",
        profileUuidDrafts: {},
        users: [],
        configLoaded: false,
        providers: [],
        opsEntries: [],
        whitelistEntries: [],
        opsResolved: null,
        whitelistResolved: null,
        serverProperties: [],
        serverPropertiesLoaded: false
    };

    const el = {};
    const BLANK_AVATAR = "data:image/gif;base64,R0lGODlhAQABAAAAACwAAAAAAQABAAA=";
    const AVATAR_CACHE_BUCKET_MS = 30 * 1000;
    // Glyphs from Nerd Fonts v3.4.0 css/nerd-fonts-generated.css.
    const NERD_FONT_ICON_CODEPOINTS = {
        key: 0xf030b,
        image: 0xf1418,
        trash: 0xf09e7,
        edit: 0xf090c,
        save: 0xf0818,
        close: 0xf0156,
        offline: 0xf0c9c
    };

    document.addEventListener("DOMContentLoaded", () => {
        cacheElements();
        bindEvents();
        clearTables();
        loadBootstrap();
    });

    function cacheElements() {
        el.serverLine = document.getElementById("serverLine");
        el.banner = document.getElementById("banner");
        el.loginCard = document.getElementById("loginCard");
        el.appCard = document.getElementById("appCard");
        el.toolbarCard = document.getElementById("toolbarCard");
        el.loginForm = document.getElementById("loginForm");
        el.tokenInput = document.getElementById("tokenInput");
        el.loginBtn = document.getElementById("loginBtn");
        el.transportHint = document.getElementById("transportHint");
        el.siteFooter = document.getElementById("siteFooter");
        el.footerVersionLine = document.getElementById("footerVersionLine");

        el.refreshBtn = document.getElementById("refreshBtn");
        el.logoutBtn = document.getElementById("logoutBtn");
        el.sessionLine = document.getElementById("sessionLine");

        el.navBar = document.getElementById("navBar");
        el.navButtons = Array.from(document.querySelectorAll("button[data-view-target]"));
        el.viewSections = Array.from(document.querySelectorAll(".view-section"));

        el.statUsers = document.getElementById("statUsers");
        el.statProfiles = document.getElementById("statProfiles");
        el.statTokens = document.getElementById("statTokens");
        el.statInvites = document.getElementById("statInvites");
        el.statDbSize = document.getElementById("statDbSize");
        el.statTexturesSize = document.getElementById("statTexturesSize");

        el.usersBody = document.getElementById("usersBody");
        el.usersHead = document.getElementById("usersHead");
        el.usersTable = document.getElementById("usersTable");
        el.userFilterInput = document.getElementById("userFilterInput");

        el.invitesBody = document.getElementById("invitesBody");
        el.inviteForm = document.getElementById("inviteForm");
        el.inviteUses = document.getElementById("inviteUses");
        el.invitePurgeBtn = document.getElementById("invitePurgeBtn");

        el.configForm = document.getElementById("configForm");
        el.cfgServerName = document.getElementById("cfgServerName");
        el.cfgApiRoot = document.getElementById("cfgApiRoot");
        el.cfgPublicPagePath = document.getElementById("cfgPublicPagePath");
        el.cfgPublicInfoApiPath = document.getElementById("cfgPublicInfoApiPath");
        el.cfgServerAddress = document.getElementById("cfgServerAddress");
        el.cfgImplementationName = document.getElementById("cfgImplementationName");
        el.cfgServerHomepage = document.getElementById("cfgServerHomepage");
        el.cfgServerRegister = document.getElementById("cfgServerRegister");
        el.cfgPublicDescription = document.getElementById("cfgPublicDescription");
        el.cfgLegacySkinApi = document.getElementById("cfgLegacySkinApi");
        el.cfgNoMojangNamespace = document.getElementById("cfgNoMojangNamespace");
        el.cfgUsernameCheck = document.getElementById("cfgUsernameCheck");
        el.cfgRegistrationPolicy = document.getElementById("cfgRegistrationPolicy");
        el.cfgPlayerNameRegex = document.getElementById("cfgPlayerNameRegex");
        el.cfgDefaultInviteUses = document.getElementById("cfgDefaultInviteUses");
        el.cfgTokenMaxPerUser = document.getElementById("cfgTokenMaxPerUser");
        el.cfgSessionTimeoutMs = document.getElementById("cfgSessionTimeoutMs");
        el.cfgHttpReadTimeoutSec = document.getElementById("cfgHttpReadTimeoutSec");
        el.cfgHttpMaxContentLen = document.getElementById("cfgHttpMaxContentLen");
        el.cfgMaxSkinWidth = document.getElementById("cfgMaxSkinWidth");
        el.cfgMaxSkinHeight = document.getElementById("cfgMaxSkinHeight");
        el.cfgMaxCapeWidth = document.getElementById("cfgMaxCapeWidth");
        el.cfgMaxCapeHeight = document.getElementById("cfgMaxCapeHeight");
        el.cfgMaxFileSizeBytes = document.getElementById("cfgMaxFileSizeBytes");
        el.cfgAllowElytra = document.getElementById("cfgAllowElytra");
        el.cfgSkinDomains = document.getElementById("cfgSkinDomains");
        el.cfgDefaultUploadableTextures = document.getElementById("cfgDefaultUploadableTextures");
        el.cfgAllowAnimatedCapes = document.getElementById("cfgAllowAnimatedCapes");
        el.cfgMaxCapeFrameCount = document.getElementById("cfgMaxCapeFrameCount");
        el.cfgMaxAnimatedCapeFileSize = document.getElementById("cfgMaxAnimatedCapeFileSize");

        el.propsForm = document.getElementById("propsForm");
        el.propsFilterInput = document.getElementById("propsFilterInput");
        el.propsAddRowBtn = document.getElementById("propsAddRowBtn");
        el.propsSaveBtn = document.getElementById("propsSaveBtn");
        el.propsBody = document.getElementById("propsBody");
        el.propsStatusLine = document.getElementById("propsStatusLine");

        el.opsAddForm = document.getElementById("opsAddForm");
        el.opsProviderSelect = document.getElementById("opsProviderSelect");
        el.opsUsernameInput = document.getElementById("opsUsernameInput");
        el.opsResolveBtn = document.getElementById("opsResolveBtn");
        el.opsAddBtn = document.getElementById("opsAddBtn");
        el.opsResolvedCard = document.getElementById("opsResolvedCard");
        el.opsResolvedAvatar = document.getElementById("opsResolvedAvatar");
        el.opsResolvedName = document.getElementById("opsResolvedName");
        el.opsResolvedUuid = document.getElementById("opsResolvedUuid");
        el.opsResolvedProvider = document.getElementById("opsResolvedProvider");
        el.opsBody = document.getElementById("opsBody");

        el.whitelistAddForm = document.getElementById("whitelistAddForm");
        el.whitelistProviderSelect = document.getElementById("whitelistProviderSelect");
        el.whitelistUsernameInput = document.getElementById("whitelistUsernameInput");
        el.whitelistResolveBtn = document.getElementById("whitelistResolveBtn");
        el.whitelistAddBtn = document.getElementById("whitelistAddBtn");
        el.whitelistResolvedCard = document.getElementById("whitelistResolvedCard");
        el.whitelistResolvedAvatar = document.getElementById("whitelistResolvedAvatar");
        el.whitelistResolvedName = document.getElementById("whitelistResolvedName");
        el.whitelistResolvedUuid = document.getElementById("whitelistResolvedUuid");
        el.whitelistResolvedProvider = document.getElementById("whitelistResolvedProvider");
        el.whitelistBody = document.getElementById("whitelistBody");
        el.whitelistEnabledToggle = document.getElementById("whitelistEnabledToggle");
    }

    function bindEvents() {
        el.loginForm.addEventListener("submit", onLoginSubmit);
        el.refreshBtn.addEventListener("click", () => refreshData(true));
        el.logoutBtn.addEventListener("click", onLogoutClick);

        el.navBar.addEventListener("click", onNavClick);

        el.userFilterInput.addEventListener("input", () => renderUsers(state.users));
        el.usersBody.addEventListener("input", onUsersTableInput);
        el.usersBody.addEventListener("click", onUsersTableClick);

        el.inviteForm.addEventListener("submit", onCreateInvite);
        el.invitePurgeBtn.addEventListener("click", onPurgeInvites);
        el.invitesBody.addEventListener("click", onInviteTableClick);

        el.configForm.addEventListener("submit", onConfigSave);
        el.propsForm.addEventListener("submit", onServerPropertiesSave);
        el.propsFilterInput.addEventListener("input", renderServerProperties);
        el.propsAddRowBtn.addEventListener("click", onAddServerPropertyRow);
        el.propsBody.addEventListener("input", onServerPropertiesTableInput);
        el.propsBody.addEventListener("click", onServerPropertiesTableClick);

        el.opsResolveBtn.addEventListener("click", onResolveOpsProfile);
        el.opsAddForm.addEventListener("submit", onAddOp);
        el.opsBody.addEventListener("click", onOpsTableClick);
        el.opsProviderSelect.addEventListener("change", clearOpsResolved);
        el.opsUsernameInput.addEventListener("input", clearOpsResolved);

        el.whitelistResolveBtn.addEventListener("click", onResolveWhitelistProfile);
        el.whitelistAddForm.addEventListener("submit", onAddWhitelistEntry);
        el.whitelistBody.addEventListener("click", onWhitelistTableClick);
        el.whitelistEnabledToggle.addEventListener("change", onWhitelistEnabledToggle);
        el.whitelistProviderSelect.addEventListener("change", clearWhitelistResolved);
        el.whitelistUsernameInput.addEventListener("input", clearWhitelistResolved);
    }

    async function loadBootstrap() {
        try {
            const data = await requestJson("/api/wawelauth/admin/bootstrap", { method: "GET" }, false);
            state.bootstrap = data;
            applyBootstrapMetadata(data);

            if (data.requireEncryption) {
                el.transportHint.classList.remove("hidden");
                el.transportHint.textContent = "HTTP transport detected. Login payload will be RSA-encrypted with the server public key.";
            } else {
                el.transportHint.classList.add("hidden");
            }

            if (!data.enabled) {
                showBanner("Admin Web UI is disabled in server configuration.", "warn");
                disableLogin(true);
                return;
            }
            if (!data.tokenConfigured) {
                showBanner("Admin token is not configured. Set server.admin.token or the configured env var.", "warn");
                disableLogin(true);
                return;
            }

            clearBanner();
            disableLogin(false);
            await tryResumeSession();
        } catch (err) {
            showBanner(`Bootstrap failed: ${err.message}`, "err");
            disableLogin(true);
        }
    }

    async function onLoginSubmit(event) {
        event.preventDefault();

        if (!state.bootstrap || !state.bootstrap.enabled || !state.bootstrap.tokenConfigured) {
            return;
        }

        const token = el.tokenInput.value || "";
        if (token.length === 0) {
            showBanner("Token is required.", "warn");
            return;
        }

        disableLogin(true);
        showBanner("Logging in...", "ok");

        try {
            let payload;
            if (state.bootstrap.requireEncryption) {
                payload = {
                    encryptedToken: await encryptToken(token, state.bootstrap.publicKeyBase64)
                };
            } else {
                payload = { token };
            }

            const data = await requestJson(
                "/api/wawelauth/admin/login",
                {
                    method: "POST",
                    body: JSON.stringify(payload)
                },
                false
            );

            state.sessionToken = data.sessionToken;
            state.sessionExpiresAt = Number(data.expiresAt) || 0;
            state.configLoaded = false;
            state.serverPropertiesLoaded = false;
            state.serverProperties = [];
            el.tokenInput.value = "";
            persistSession();

            enterAppMode();
            await refreshData(false);
            showBanner("Admin session established.", "ok");
        } catch (err) {
            showBanner(`Login failed: ${err.message}`, "err");
            disableLogin(false);
        }
    }

    function enterAppMode() {
        el.loginCard.classList.add("hidden");
        el.appCard.classList.remove("hidden");
        el.toolbarCard.classList.remove("hidden");
        setActiveView("dashboardView");
        startSessionTimer();
        updateSessionLine();
    }

    function leaveAppMode() {
        state.sessionToken = null;
        state.sessionExpiresAt = 0;
        state.configLoaded = false;
        state.users = [];
        state.providers = [];
        state.opsEntries = [];
        state.whitelistEntries = [];
        state.opsResolved = null;
        state.whitelistResolved = null;
        state.serverProperties = [];
        state.serverPropertiesLoaded = false;
        stopSessionTimer();
        clearPersistedSession();

        el.appCard.classList.add("hidden");
        el.toolbarCard.classList.add("hidden");
        el.loginCard.classList.remove("hidden");
        disableLogin(false);
        updateSessionLine();
    }

    async function onLogoutClick() {
        try {
            if (state.sessionToken) {
                await requestJson(
                    "/api/wawelauth/admin/logout",
                    {
                        method: "POST",
                        body: "{}"
                    },
                    true
                );
            }
        } catch (_err) {
            // No-op: local session is dropped regardless.
        }
        leaveAppMode();
        clearTables();
        showBanner("Logged out.", "ok");
    }

    function onNavClick(event) {
        const button = event.target.closest("button[data-view-target]");
        if (!button) {
            return;
        }
        const target = button.getAttribute("data-view-target");
        if (!target) {
            return;
        }
        setActiveView(target);
        if (target === "configView" && state.sessionToken && !state.configLoaded) {
            loadServerConfig(false);
        }
        if (target === "propsView" && state.sessionToken && !state.serverPropertiesLoaded) {
            loadServerProperties(false);
        }
    }

    function setActiveView(viewId) {
        state.activeView = viewId;
        for (const button of el.navButtons) {
            const target = button.getAttribute("data-view-target");
            button.classList.toggle("active", target === viewId);
        }
        for (const section of el.viewSections) {
            section.classList.toggle("hidden", section.id !== viewId);
        }
    }

    async function refreshData(manual) {
        if (!state.sessionToken) {
            return;
        }

        if (manual) {
            showBanner("Refreshing data...", "ok");
        }

        try {
            const [bootstrapResp, statsResp, usersResp, invitesResp, sessionResp, providersResp, whitelistResp, opsResp] = await Promise.all([
                requestJson("/api/wawelauth/admin/bootstrap", { method: "GET" }, false),
                requestJson("/api/wawelauth/admin/stats", { method: "GET" }, true),
                requestJson("/api/wawelauth/admin/users", { method: "GET" }, true),
                requestJson("/api/wawelauth/admin/invites", { method: "GET" }, true),
                requestJson("/api/wawelauth/admin/session", { method: "GET" }, true),
                requestJson("/api/wawelauth/admin/providers", { method: "GET" }, true),
                requestJson("/api/wawelauth/admin/whitelist", { method: "GET" }, true),
                requestJson("/api/wawelauth/admin/ops", { method: "GET" }, true)
            ]);

            state.bootstrap = bootstrapResp || state.bootstrap;
            applyBootstrapMetadata(state.bootstrap);
            state.sessionExpiresAt = Number(sessionResp.expiresAt) || state.sessionExpiresAt;
            updateSessionLine();
            renderStats(statsResp || {});
            renderUsers((usersResp && usersResp.users) || []);
            renderInvites((invitesResp && invitesResp.invites) || []);
            renderProviders((providersResp && providersResp.providers) || []);
            renderWhitelist(whitelistResp || {});
            renderOps((opsResp && opsResp.entries) || []);

            if (state.activeView === "configView" || !state.configLoaded) {
                await loadServerConfig(false);
            }
            if (state.activeView === "propsView" || !state.serverPropertiesLoaded) {
                await loadServerProperties(false);
            }

            if (manual) {
                showBanner("Data refreshed.", "ok");
            }
        } catch (err) {
            if (/session/i.test(err.message)) {
                leaveAppMode();
                clearTables();
            }
            showBanner(`Refresh failed: ${err.message}`, "err");
        }
    }

    async function loadServerConfig(showNotice) {
        if (!state.sessionToken) {
            return;
        }
        try {
            const config = await requestJson("/api/wawelauth/admin/config/server", { method: "GET" }, true);
            populateConfigForm(config || {});
            state.configLoaded = true;
            if (showNotice) {
                showBanner("Loaded server config.", "ok");
            }
        } catch (err) {
            showBanner(`Failed to load config: ${err.message}`, "err");
        }
    }

    async function loadServerProperties(showNotice) {
        if (!state.sessionToken) {
            return;
        }
        try {
            const data = await requestJson("/api/wawelauth/admin/config/server-properties", { method: "GET" }, true);
            state.serverProperties = normalizeServerProperties((data && data.properties) || []);
            state.serverPropertiesLoaded = true;
            renderServerProperties();
            el.propsStatusLine.textContent = String(
                (data && data.statusMessage) || "Most server.properties changes require reload/restart to take effect."
            );
            if (showNotice) {
                showBanner("Loaded server.properties.", "ok");
            }
        } catch (err) {
            showBanner(`Failed to load server.properties: ${err.message}`, "err");
        }
    }

    async function onConfigSave(event) {
        event.preventDefault();
        if (!state.sessionToken) {
            return;
        }

        let payload;
        try {
            payload = buildConfigPayload();
        } catch (err) {
            showBanner(err.message, "warn");
            return;
        }

        try {
            const response = await requestJson(
                "/api/wawelauth/admin/config/server",
                {
                    method: "POST",
                    body: JSON.stringify(payload)
                },
                true
            );

            populateConfigForm(response || {});
            state.configLoaded = true;
            state.bootstrap = Object.assign({}, state.bootstrap || {}, {
                serverName: (response && response.serverName) || "",
                apiRoot: (response && response.apiRoot) || "",
                implementationName: (response && response.meta && response.meta.implementationName) || "",
                registrationPolicy: (response && response.registration && response.registration.policy) || ""
            });
            applyBootstrapMetadata(state.bootstrap);

            showBanner("server.json saved.", "ok");
        } catch (err) {
            showBanner(`Failed to save config: ${err.message}`, "err");
        }
    }

    function applyBootstrapMetadata(data) {
        if (!data) {
            el.siteFooter.classList.add("hidden");
            return;
        }

        const serverName = data.serverName || "Unknown Server";
        const apiRoot = data.apiRoot || "(unset apiRoot)";
        el.serverLine.textContent = `${serverName} · ${apiRoot}`;
        renderFooter(data);
    }

    function renderFooter(data) {
        const modVersion = String(data.modVersion || "-");
        el.footerVersionLine.textContent = `Wawel Auth version ${modVersion}`;
        el.siteFooter.classList.remove("hidden");
    }

    function normalizeServerProperties(entries) {
        if (!Array.isArray(entries)) {
            return [];
        }
        return entries.map((entry) => ({
            key: String((entry && entry.key) || ""),
            value: String((entry && entry.value) || "")
        }));
    }

    function renderServerProperties() {
        const filter = (el.propsFilterInput.value || "").trim().toLowerCase();
        const rows = [];

        for (let i = 0; i < state.serverProperties.length; i += 1) {
            const entry = state.serverProperties[i];
            const key = String(entry.key || "");
            const value = String(entry.value || "");
            if (
                filter.length === 0 ||
                key.toLowerCase().includes(filter) ||
                value.toLowerCase().includes(filter)
            ) {
                rows.push({ index: i, key, value });
            }
        }

        if (!rows.length) {
            el.propsBody.innerHTML = `<tr><td colspan="3" class="empty">No matching properties</td></tr>`;
            return;
        }

        el.propsBody.innerHTML = rows.map((row) => `
            <tr>
                <td><input type="text" data-action="prop-key" data-index="${row.index}" value="${escapeAttr(row.key)}"></td>
                <td><input type="text" data-action="prop-value" data-index="${row.index}" value="${escapeAttr(row.value)}"></td>
                <td><button type="button" class="small danger" data-action="prop-remove" data-index="${row.index}">Remove</button></td>
            </tr>
        `).join("");
    }

    function onAddServerPropertyRow() {
        state.serverProperties.push({ key: "", value: "" });
        renderServerProperties();
        const index = state.serverProperties.length - 1;
        const selector = `input[data-action='prop-key'][data-index='${index}']`;
        const input = el.propsBody.querySelector(selector);
        if (input) {
            input.focus();
        }
    }

    function onServerPropertiesTableInput(event) {
        const input = event.target.closest("input[data-action][data-index]");
        if (!input) {
            return;
        }
        const action = input.getAttribute("data-action");
        const index = Number(input.getAttribute("data-index"));
        if (!Number.isInteger(index) || index < 0 || index >= state.serverProperties.length) {
            return;
        }

        if (action === "prop-key") {
            state.serverProperties[index].key = input.value;
        } else if (action === "prop-value") {
            state.serverProperties[index].value = input.value;
        }
    }

    function onServerPropertiesTableClick(event) {
        const button = event.target.closest("button[data-action='prop-remove'][data-index]");
        if (!button) {
            return;
        }
        const index = Number(button.getAttribute("data-index"));
        if (!Number.isInteger(index) || index < 0 || index >= state.serverProperties.length) {
            return;
        }
        state.serverProperties.splice(index, 1);
        renderServerProperties();
    }

    function buildServerPropertiesPayload() {
        const seen = new Set();
        const entries = [];

        for (let i = 0; i < state.serverProperties.length; i += 1) {
            const row = state.serverProperties[i];
            const key = String((row && row.key) || "").trim();
            const value = String((row && row.value) || "");
            if (key.length === 0) {
                throw new Error(`Property key at row ${i + 1} cannot be empty.`);
            }
            if (seen.has(key)) {
                throw new Error(`Duplicate property key: ${key}`);
            }
            seen.add(key);
            entries.push({ key, value });
        }

        return { properties: entries };
    }

    async function onServerPropertiesSave(event) {
        event.preventDefault();
        if (!state.sessionToken) {
            return;
        }

        let payload;
        try {
            payload = buildServerPropertiesPayload();
        } catch (err) {
            showBanner(err.message, "warn");
            return;
        }

        try {
            const response = await requestJson(
                "/api/wawelauth/admin/config/server-properties",
                {
                    method: "POST",
                    body: JSON.stringify(payload)
                },
                true
            );

            state.serverProperties = normalizeServerProperties((response && response.properties) || []);
            state.serverPropertiesLoaded = true;
            renderServerProperties();
            const status = String(
                (response && response.statusMessage) ||
                "Saved server.properties. Reload/restart server for changes to take effect."
            );
            el.propsStatusLine.textContent = status;
            showBanner(status, "ok");
        } catch (err) {
            showBanner(`Failed to save server.properties: ${err.message}`, "err");
        }
    }

    function buildConfigPayload() {
        const payload = {
            serverName: (el.cfgServerName.value || "").trim(),
            apiRoot: (el.cfgApiRoot.value || "").trim(),
            publicPagePath: (el.cfgPublicPagePath.value || "").trim(),
            publicInfoApiPath: (el.cfgPublicInfoApiPath.value || "").trim(),
            serverAddress: (el.cfgServerAddress.value || "").trim(),
            skinDomains: parseCommaSeparatedList(el.cfgSkinDomains.value),
            meta: {
                implementationName: (el.cfgImplementationName.value || "").trim(),
                serverHomepage: (el.cfgServerHomepage.value || "").trim(),
                serverRegister: (el.cfgServerRegister.value || "").trim(),
                publicDescription: (el.cfgPublicDescription.value || "").trim()
            },
            features: {
                legacySkinApi: Boolean(el.cfgLegacySkinApi.checked),
                noMojangNamespace: Boolean(el.cfgNoMojangNamespace.checked),
                usernameCheck: Boolean(el.cfgUsernameCheck.checked)
            },
            registration: {
                policy: el.cfgRegistrationPolicy.value || "INVITE_ONLY",
                playerNameRegex: (el.cfgPlayerNameRegex.value || "").trim(),
                defaultUploadableTextures: parseCommaSeparatedList(el.cfgDefaultUploadableTextures.value)
            },
            invites: {
                defaultUses: parseIntField(el.cfgDefaultInviteUses, "Default Invite Uses")
            },
            tokens: {
                maxPerUser: parseIntField(el.cfgTokenMaxPerUser, "Max Tokens Per User"),
                sessionTimeoutMs: parseIntField(el.cfgSessionTimeoutMs, "Session Timeout")
            },
            http: {
                readTimeoutSeconds: parseIntField(el.cfgHttpReadTimeoutSec, "HTTP Read Timeout"),
                maxContentLengthBytes: parseIntField(el.cfgHttpMaxContentLen, "HTTP Max Content")
            },
            textures: {
                maxSkinWidth: parseIntField(el.cfgMaxSkinWidth, "Max Skin Width"),
                maxSkinHeight: parseIntField(el.cfgMaxSkinHeight, "Max Skin Height"),
                maxCapeWidth: parseIntField(el.cfgMaxCapeWidth, "Max Cape Width"),
                maxCapeHeight: parseIntField(el.cfgMaxCapeHeight, "Max Cape Height"),
                maxFileSizeBytes: parseIntField(el.cfgMaxFileSizeBytes, "Max Texture File Size"),
                allowElytra: Boolean(el.cfgAllowElytra.checked),
                allowAnimatedCapes: Boolean(el.cfgAllowAnimatedCapes.checked),
                maxCapeFrameCount: parseIntField(el.cfgMaxCapeFrameCount, "Max Cape Frame Count"),
                maxAnimatedCapeFileSizeBytes: parseIntField(el.cfgMaxAnimatedCapeFileSize, "Max Animated Cape File Size")
            }
        };

        return payload;
    }

    function parseCommaSeparatedList(value) {
        return (value || "").split(",").map(s => s.trim()).filter(s => s.length > 0);
    }

    function parseIntField(input, label) {
        const value = Number(input.value);
        if (!Number.isFinite(value) || !Number.isInteger(value)) {
            throw new Error(`${label} must be an integer.`);
        }
        return value;
    }

    function populateConfigForm(cfg) {
        const meta = cfg.meta || {};
        const features = cfg.features || {};
        const registration = cfg.registration || {};
        const invites = cfg.invites || {};
        const tokens = cfg.tokens || {};
        const http = cfg.http || {};
        const textures = cfg.textures || {};

        el.cfgServerName.value = cfg.serverName || "";
        el.cfgApiRoot.value = cfg.apiRoot || "";
        el.cfgPublicPagePath.value = cfg.publicPagePath || "/";
        el.cfgPublicInfoApiPath.value = cfg.publicInfoApiPath || "";
        el.cfgServerAddress.value = cfg.serverAddress || "";

        el.cfgImplementationName.value = meta.implementationName || "";
        el.cfgServerHomepage.value = meta.serverHomepage || "";
        el.cfgServerRegister.value = meta.serverRegister || "";
        el.cfgPublicDescription.value = meta.publicDescription || "";

        el.cfgLegacySkinApi.checked = Boolean(features.legacySkinApi);
        el.cfgNoMojangNamespace.checked = Boolean(features.noMojangNamespace);
        el.cfgUsernameCheck.checked = Boolean(features.usernameCheck);

        el.cfgRegistrationPolicy.value = registration.policy || "INVITE_ONLY";
        el.cfgPlayerNameRegex.value = registration.playerNameRegex || "";

        el.cfgDefaultInviteUses.value = safeNumber(invites.defaultUses, 1);

        el.cfgTokenMaxPerUser.value = safeNumber(tokens.maxPerUser, 10);
        el.cfgSessionTimeoutMs.value = safeNumber(tokens.sessionTimeoutMs, 30000);

        el.cfgHttpReadTimeoutSec.value = safeNumber(http.readTimeoutSeconds, 10);
        el.cfgHttpMaxContentLen.value = safeNumber(http.maxContentLengthBytes, 1048576);

        el.cfgMaxSkinWidth.value = safeNumber(textures.maxSkinWidth, 64);
        el.cfgMaxSkinHeight.value = safeNumber(textures.maxSkinHeight, 64);
        el.cfgMaxCapeWidth.value = safeNumber(textures.maxCapeWidth, 64);
        el.cfgMaxCapeHeight.value = safeNumber(textures.maxCapeHeight, 32);
        el.cfgMaxFileSizeBytes.value = safeNumber(textures.maxFileSizeBytes, 1048576);
        el.cfgAllowElytra.checked = Boolean(textures.allowElytra);

        el.cfgSkinDomains.value = Array.isArray(cfg.skinDomains) ? cfg.skinDomains.join(", ") : "";
        el.cfgDefaultUploadableTextures.value = Array.isArray(registration.defaultUploadableTextures)
            ? registration.defaultUploadableTextures.join(", ") : "";
        el.cfgAllowAnimatedCapes.checked = Boolean(textures.allowAnimatedCapes);
        el.cfgMaxCapeFrameCount.value = safeNumber(textures.maxCapeFrameCount, 256);
        el.cfgMaxAnimatedCapeFileSize.value = safeNumber(textures.maxAnimatedCapeFileSizeBytes, 10485760);
    }

    function renderStats(stats) {
        el.statUsers.textContent = safeStat(stats.users);
        el.statProfiles.textContent = safeStat(stats.profiles);
        el.statTokens.textContent = safeStat(stats.tokens);
        el.statInvites.textContent = safeStat(stats.invites);
        el.statDbSize.textContent = formatBytes(stats.databaseSizeBytes);
        const texSize = formatBytes(stats.textureStorageSizeBytes);
        const texCount = Number.isFinite(Number(stats.textureFileCount)) ? Number(stats.textureFileCount) : 0;
        el.statTexturesSize.textContent = `${texSize} (${texCount} file${texCount !== 1 ? "s" : ""})`;
    }

    function formatBytes(bytes) {
        const n = Number(bytes);
        if (!Number.isFinite(n) || n < 0) return "-";
        if (n === 0) return "0 B";
        const units = ["B", "KB", "MB", "GB"];
        let i = 0;
        let val = n;
        while (val >= 1024 && i < units.length - 1) {
            val /= 1024;
            i++;
        }
        return (i === 0 ? String(val) : val.toFixed(1)) + " " + units[i];
    }

    function renderUsers(users) {
        state.users = Array.isArray(users) ? users : [];

        const filter = (el.userFilterInput.value || "").trim().toLowerCase();
        const filtered = state.users.filter((user) => {
            const name = String(user.username || "").toLowerCase();
            return filter.length === 0 || name.includes(filter);
        });

        renderUsersHeader();

        if (!filtered.length) {
            el.usersBody.innerHTML = `<tr><td colspan="5" class="empty">No matching users</td></tr>`;
            return;
        }

        el.usersBody.innerHTML = filtered.map((user) => {
            const profiles = renderUserProfiles(user.profiles || []);
            const flags = [
                user.admin ? `<span class="chip admin">admin</span>` : "",
                user.locked ? `<span class="chip locked">locked</span>` : ""
            ].join("");
            const uuid = String(user.uuid || "");
            const username = String(user.username || "");

            return `<tr>
                <td data-label="Username">${escapeHtml(username)}</td>
                <td data-label="UUID"><code class="uuid-code" title="${escapeAttr(uuid)}">${escapeHtml(uuid)}</code></td>
                <td data-label="Profiles">${profiles}</td>
                <td data-label="Flags">${flags || "<span class=\"empty\">none</span>"}</td>
                <td data-label="Actions">
                    <div class="user-action-panel">
                        <div class="action-buttons action-icons user-actions">
                            ${renderIconButton("user-reset-password", "Reset password", "key", {
                                uuid,
                                username
                            })}
                            ${renderIconButton("user-reset-textures", "Reset skin and cape", "image", {
                                uuid,
                                username
                            })}
                            ${renderIconButton("user-delete", "Delete user", "trash", {
                                uuid,
                                username
                            }, "danger")}
                        </div>
                    </div>
                </td>
            </tr>`;
        }).join("");
    }

    function renderUsersHeader() {
        el.usersHead.innerHTML = `<tr>
            <th>Username</th>
            <th>UUID</th>
            <th>Profiles</th>
            <th>Flags</th>
            <th>Actions</th>
        </tr>`;
    }

    function renderUserProfiles(profiles) {
        if (!Array.isArray(profiles) || profiles.length === 0) {
            return `<span class="empty">none</span>`;
        }

        return `<div class="profile-list">${profiles.map((profile) => {
            const profileName = String((profile && profile.name) || "");
            const profileUuid = String((profile && profile.uuid) || "");
            const offlineUuid = String((profile && profile.offlineUuid) || "");
            const avatarUrl = profile && profile.avatarUrl ? String(profile.avatarUrl) : null;
            const draft = Object.prototype.hasOwnProperty.call(state.profileUuidDrafts, profileUuid)
                ? state.profileUuidDrafts[profileUuid]
                : null;
            const editing = draft != null;
            const draftValue = editing ? draft : profileUuid;
            const dirty = editing && draftValue.trim() !== profileUuid;
            const sameAsOffline = offlineUuid.length > 0 && offlineUuid === profileUuid;

            return `<div class="profile-entry">
                <div class="profile-entry-row">
                    ${avatarHtml(avatarUrl, "profile-avatar")}
                    ${editing
                        ? `<input type="text" class="profile-uuid-input" data-action="profile-uuid-input" data-profile-uuid="${escapeAttr(profileUuid)}" data-current-uuid="${escapeAttr(profileUuid)}" value="${escapeAttr(draftValue)}" autocomplete="off" spellcheck="false">`
                        : `<code class="uuid-code profile-uuid-code" title="${escapeAttr(profileUuid)}">${escapeHtml(profileUuid || "-")}</code>`
                    }
                    ${editing
                        ? renderIconButton("profile-save-uuid", "Save UUID", "save", {
                            profileUuid,
                            currentUuid: profileUuid,
                            profileName
                        }, dirty ? "" : "hidden")
                        : renderIconButton("profile-start-edit-uuid", "Edit UUID", "edit", {
                            profileUuid,
                            currentUuid: profileUuid,
                            profileName
                        })
                    }
                    ${editing
                        ? renderIconButton("profile-cancel-edit-uuid", "Cancel editing", "close", {
                            profileUuid,
                            currentUuid: profileUuid,
                            profileName
                        })
                        : ""
                    }
                    ${renderIconButton("profile-use-offline-uuid", sameAsOffline ? "Already using offline UUID" : "Set to offline UUID", "offline", {
                        profileUuid,
                        currentUuid: profileUuid,
                        profileName,
                        offlineUuid
                    }, sameAsOffline ? "muted" : "")}
                </div>
            </div>`;
        }).join("")}</div>`;
    }

    function renderIconButton(action, title, icon, dataset, extraClass) {
        const attrs = [`type="button"`, `class="icon-btn${extraClass ? ` ${extraClass}` : ""}"`, `data-action="${escapeAttr(action)}"`, `title="${escapeAttr(title)}"`, `aria-label="${escapeAttr(title)}"`];
        if (dataset) {
            Object.keys(dataset).forEach((key) => {
                const value = dataset[key];
                if (value == null) {
                    return;
                }
                attrs.push(`data-${camelToKebab(key)}="${escapeAttr(String(value))}"`);
            });
        }
        return `<button ${attrs.join(" ")}>${renderIcon(icon)}</button>`;
    }

    function renderIcon(icon) {
        const codepoint = NERD_FONT_ICON_CODEPOINTS[icon];
        if (!codepoint) {
            return "";
        }
        return `<span class="nf-icon nf-icon-${escapeAttr(icon)}" aria-hidden="true">${String.fromCodePoint(codepoint)}</span>`;
    }

    function camelToKebab(value) {
        return String(value).replace(/[A-Z]/g, (match) => `-${match.toLowerCase()}`);
    }

    function onUsersTableInput(event) {
        const input = event.target.closest("input[data-action='profile-uuid-input'][data-profile-uuid]");
        if (!input) {
            return;
        }
        const profileUuid = input.getAttribute("data-profile-uuid");
        if (!profileUuid) {
            return;
        }
        state.profileUuidDrafts[profileUuid] = input.value;
        const currentUuid = input.getAttribute("data-current-uuid") || "";
        const container = input.closest(".profile-entry-row");
        if (!container) {
            return;
        }
        const saveButton = container.querySelector("button[data-action='profile-save-uuid']");
        if (saveButton) {
            saveButton.classList.toggle("hidden", input.value.trim() === currentUuid);
        }
    }

    async function onUsersTableClick(event) {
        const button = event.target.closest("button[data-action]");
        if (!button) {
            return;
        }
        if (!state.sessionToken) {
            return;
        }

        const action = button.getAttribute("data-action");
        if (!action) {
            return;
        }

        try {
            if (action === "profile-start-edit-uuid" || action === "profile-save-uuid" || action === "profile-cancel-edit-uuid"
                || action === "profile-use-offline-uuid") {
                const profileUuid = button.getAttribute("data-profile-uuid");
                const profileName = button.getAttribute("data-profile-name") || profileUuid;
                const currentUuid = button.getAttribute("data-current-uuid") || "";
                if (!profileUuid) {
                    return;
                }

                if (action === "profile-start-edit-uuid") {
                    state.profileUuidDrafts[profileUuid] = currentUuid;
                    renderUsers(state.users);
                    const input = el.usersBody.querySelector(`input[data-action='profile-uuid-input'][data-profile-uuid='${profileUuid}']`);
                    if (input) {
                        input.focus();
                        input.select();
                    }
                    return;
                }

                if (action === "profile-cancel-edit-uuid") {
                    delete state.profileUuidDrafts[profileUuid];
                    renderUsers(state.users);
                    return;
                }

                if (action === "profile-save-uuid") {
                    const nextUuid = String(state.profileUuidDrafts[profileUuid] || "").trim();
                    if (nextUuid.length === 0) {
                        showBanner("UUID cannot be empty.", "warn");
                        return;
                    }

                    const response = await requestJson(
                        `/api/wawelauth/admin/profiles/${encodeURIComponent(profileUuid)}/set-uuid`,
                        {
                            method: "POST",
                            body: JSON.stringify({ newUuid: nextUuid })
                        },
                        true
                    );
                    delete state.profileUuidDrafts[profileUuid];

                    if (response && response.changed) {
                        showBanner(
                            `Updated UUID for ${profileName} to ${response.newUuid}. Kicked ${Number(response.kickedPlayers) || 0} player(s) and invalidated ${Number(response.invalidatedTokens) || 0} token(s).`,
                            "ok"
                        );
                    } else {
                        showBanner(`${profileName} is already using ${(response && response.newUuid) || currentUuid}.`, "warn");
                    }
                } else {
                    if (!window.confirm(`Set ${profileName} to its offline UUID? This will kick the player and invalidate all tokens for that local account.`)) {
                        return;
                    }

                    const response = await requestJson(
                        `/api/wawelauth/admin/profiles/${encodeURIComponent(profileUuid)}/use-offline-uuid`,
                        {
                            method: "POST",
                            body: "{}"
                        },
                        true
                    );
                    delete state.profileUuidDrafts[profileUuid];

                    if (response && response.changed) {
                        showBanner(
                            `Set ${profileName} to offline UUID ${response.newUuid}. Kicked ${Number(response.kickedPlayers) || 0} player(s) and invalidated ${Number(response.invalidatedTokens) || 0} token(s).`,
                            "ok"
                        );
                    } else {
                        showBanner(`${profileName} is already using its offline UUID.`, "warn");
                    }
                }
            } else {
                const uuid = button.getAttribute("data-uuid");
                const username = button.getAttribute("data-username") || uuid;
                if (!uuid) {
                    return;
                }

                if (action === "user-reset-password") {
                    const nextPassword = window.prompt(`Enter new password for ${username}:`, "");
                    if (nextPassword == null) {
                        return;
                    }
                    if (nextPassword.length === 0) {
                        showBanner("Password cannot be empty.", "warn");
                        return;
                    }
                    await requestJson(
                        `/api/wawelauth/admin/users/${encodeURIComponent(uuid)}/reset-password`,
                        {
                            method: "POST",
                            body: JSON.stringify({ newPassword: nextPassword })
                        },
                        true
                    );
                    showBanner(`Password reset for ${username}.`, "ok");
                } else if (action === "user-reset-textures") {
                    if (!window.confirm(`Reset skin and cape for ${username}?`)) {
                        return;
                    }
                    await requestJson(
                        `/api/wawelauth/admin/users/${encodeURIComponent(uuid)}/reset-textures`,
                        {
                            method: "POST",
                            body: "{}"
                        },
                        true
                    );
                    showBanner(`Skin/cape reset for ${username}.`, "ok");
                } else if (action === "user-delete") {
                    if (!window.confirm(`Delete user ${username}? This cannot be undone.`)) {
                        return;
                    }
                    await requestJson(
                        `/api/wawelauth/admin/users/${encodeURIComponent(uuid)}/delete`,
                        {
                            method: "POST",
                            body: "{}"
                        },
                        true
                    );
                    showBanner(`Deleted user ${username}.`, "ok");
                }
            }

            await refreshData(false);
        } catch (err) {
            showBanner(`User action failed: ${err.message}`, "err");
        }
    }

    function renderInvites(invites) {
        if (!invites.length) {
            el.invitesBody.innerHTML = `<tr><td colspan="4" class="empty">No invites</td></tr>`;
            return;
        }

        el.invitesBody.innerHTML = invites.map((invite) => {
            const uses = Number(invite.usesRemaining);
            const usesLabel = uses === -1 ? "unlimited" : String(uses);
            const createdAt = Number(invite.createdAt) > 0
                ? new Date(Number(invite.createdAt)).toLocaleString()
                : "-";
            const code = String(invite.code || "");
            return `<tr>
                <td><code>${escapeHtml(code)}</code></td>
                <td>${escapeHtml(usesLabel)}</td>
                <td>${escapeHtml(createdAt)}</td>
                <td>
                    <div class="action-buttons">
                        <button type="button" class="small" data-action="copy-invite" data-code="${escapeAttr(code)}">Copy</button>
                        <button type="button" class="small danger" data-action="delete-invite" data-code="${escapeAttr(code)}">Delete</button>
                    </div>
                </td>
            </tr>`;
        }).join("");
    }

    async function onCreateInvite(event) {
        event.preventDefault();
        if (!state.sessionToken) return;

        const uses = Number(el.inviteUses.value);
        if (!Number.isFinite(uses) || !Number.isInteger(uses)) {
            showBanner("Invite uses must be an integer.", "warn");
            return;
        }

        try {
            const created = await requestJson(
                "/api/wawelauth/admin/invites",
                {
                    method: "POST",
                    body: JSON.stringify({ uses })
                },
                true
            );
            const code = String((created && created.code) || "");
            if (code.length > 0) {
                try {
                    await copyTextToClipboard(code);
                    showBanner(`Created invite ${code}. Copied token to clipboard.`, "ok");
                } catch (copyErr) {
                    showBanner(`Created invite ${code}. Copy failed: ${copyErr.message}`, "warn");
                }
            } else {
                showBanner("Created invite.", "ok");
            }
            await refreshData(false);
        } catch (err) {
            showBanner(`Failed to create invite: ${err.message}`, "err");
        }
    }

    async function onPurgeInvites() {
        if (!state.sessionToken) return;
        try {
            const out = await requestJson(
                "/api/wawelauth/admin/invites/purge",
                {
                    method: "POST",
                    body: "{}"
                },
                true
            );
            showBanner(`Purged ${safeStat(out.purged)} consumed invite(s).`, "ok");
            await refreshData(false);
        } catch (err) {
            showBanner(`Purge failed: ${err.message}`, "err");
        }
    }

    async function onInviteTableClick(event) {
        const btn = event.target.closest("button[data-action]");
        if (!btn) return;

        const action = btn.getAttribute("data-action");
        const code = btn.getAttribute("data-code");
        if (!code) return;

        if (action === "copy-invite") {
            try {
                await copyTextToClipboard(code);
                showBanner(`Copied invite ${code}.`, "ok");
            } catch (err) {
                showBanner(`Copy failed: ${err.message}`, "warn");
            }
            return;
        }

        if (action !== "delete-invite") return;
        if (!window.confirm(`Delete invite ${code}?`)) return;

        try {
            await requestJson(
                `/api/wawelauth/admin/invites/${encodeURIComponent(code)}`,
                { method: "DELETE" },
                true
            );
            showBanner(`Deleted invite ${code}.`, "ok");
            await refreshData(false);
        } catch (err) {
            showBanner(`Delete failed: ${err.message}`, "err");
        }
    }

    function renderProviders(providers) {
        state.providers = Array.isArray(providers) ? providers.slice() : [];
        const hasProviders = state.providers.length > 0;

        const optionsHtml = hasProviders
            ? state.providers.map((provider) => {
                const key = String(provider.key || "");
                const label = String(provider.label || key);
                return `<option value="${escapeAttr(key)}">${escapeHtml(label)}</option>`;
            }).join("")
            : `<option value="">No providers available</option>`;

        const prevOps = el.opsProviderSelect.value || "";
        const prevWhitelist = el.whitelistProviderSelect.value || "";

        el.opsProviderSelect.innerHTML = optionsHtml;
        el.whitelistProviderSelect.innerHTML = optionsHtml;

        if (hasProviders) {
            const keys = state.providers.map((provider) => String(provider.key || ""));
            el.opsProviderSelect.value = keys.includes(prevOps) ? prevOps : keys[0];
            el.whitelistProviderSelect.value = keys.includes(prevWhitelist) ? prevWhitelist : keys[0];
        } else {
            el.opsProviderSelect.value = "";
            el.whitelistProviderSelect.value = "";
        }

        el.opsProviderSelect.disabled = !hasProviders;
        el.opsUsernameInput.disabled = !hasProviders;
        el.opsResolveBtn.disabled = !hasProviders;
        el.whitelistProviderSelect.disabled = !hasProviders;
        el.whitelistUsernameInput.disabled = !hasProviders;
        el.whitelistResolveBtn.disabled = !hasProviders;

        clearOpsResolved();
        clearWhitelistResolved();
    }

    function clearOpsResolved() {
        state.opsResolved = null;
        el.opsAddBtn.disabled = true;
        el.opsResolvedCard.classList.add("hidden");
        el.opsResolvedName.textContent = "";
        el.opsResolvedUuid.textContent = "";
        el.opsResolvedProvider.textContent = "";
        setAvatar(el.opsResolvedAvatar, null);
    }

    function clearWhitelistResolved() {
        state.whitelistResolved = null;
        el.whitelistAddBtn.disabled = true;
        el.whitelistResolvedCard.classList.add("hidden");
        el.whitelistResolvedName.textContent = "";
        el.whitelistResolvedUuid.textContent = "";
        el.whitelistResolvedProvider.textContent = "";
        setAvatar(el.whitelistResolvedAvatar, null);
    }

    function setAvatar(imgEl, avatarUrl) {
        if (avatarUrl) {
            imgEl.src = withCacheBuster(avatarUrl);
            imgEl.classList.remove("placeholder");
            return;
        }
        imgEl.src = BLANK_AVATAR;
        imgEl.classList.add("placeholder");
    }

    function withCacheBuster(url) {
        if (!url) return BLANK_AVATAR;
        const separator = url.indexOf("?") >= 0 ? "&" : "?";
        return `${url}${separator}ts=${Math.floor(Date.now() / AVATAR_CACHE_BUCKET_MS)}`;
    }

    async function resolveProfileByProvider(provider, username) {
        const response = await requestJson(
            "/api/wawelauth/admin/resolve-profile",
            {
                method: "POST",
                body: JSON.stringify({ provider, username })
            },
            true
        );
        return response && response.profile ? response.profile : null;
    }

    async function onResolveOpsProfile() {
        if (!state.sessionToken) return;
        const provider = (el.opsProviderSelect.value || "").trim();
        const username = (el.opsUsernameInput.value || "").trim();

        if (!provider) {
            showBanner("Select a provider first.", "warn");
            return;
        }
        if (!username) {
            showBanner("Username is required.", "warn");
            return;
        }

        try {
            const profile = await resolveProfileByProvider(provider, username);
            if (!profile) {
                throw new Error("No profile returned.");
            }
            state.opsResolved = profile;
            el.opsResolvedName.textContent = String(profile.name || "");
            el.opsResolvedUuid.textContent = String(profile.uuid || "");
            el.opsResolvedProvider.textContent = String(profile.providerLabel || profile.provider || "");
            setAvatar(el.opsResolvedAvatar, profile.avatarUrl || null);
            el.opsResolvedCard.classList.remove("hidden");
            el.opsAddBtn.disabled = false;
            showBanner(`Resolved ${profile.name}.`, "ok");
        } catch (err) {
            clearOpsResolved();
            showBanner(`Resolve failed: ${err.message}`, "err");
        }
    }

    async function onAddOp(event) {
        event.preventDefault();
        if (!state.sessionToken) return;
        if (!state.opsResolved) {
            showBanner("Resolve a profile before adding op.", "warn");
            return;
        }

        try {
            await requestJson(
                "/api/wawelauth/admin/ops/add",
                {
                    method: "POST",
                    body: JSON.stringify({
                        provider: state.opsResolved.provider,
                        username: state.opsResolved.name
                    })
                },
                true
            );
            showBanner(`Added operator ${state.opsResolved.name}.`, "ok");
            el.opsUsernameInput.value = "";
            clearOpsResolved();
            await refreshData(false);
        } catch (err) {
            showBanner(`Failed to add op: ${err.message}`, "err");
        }
    }

    async function onOpsTableClick(event) {
        const button = event.target.closest("button[data-action='remove-op']");
        if (!button || !state.sessionToken) return;

        const uuid = button.getAttribute("data-uuid");
        const name = button.getAttribute("data-name") || uuid;
        if (!uuid) return;
        if (!window.confirm(`Remove operator ${name}?`)) return;

        try {
            await requestJson(
                "/api/wawelauth/admin/ops/remove",
                {
                    method: "POST",
                    body: JSON.stringify({ uuid })
                },
                true
            );
            showBanner(`Removed operator ${name}.`, "ok");
            await refreshData(false);
        } catch (err) {
            showBanner(`Failed to remove op: ${err.message}`, "err");
        }
    }

    function renderOps(entries) {
        state.opsEntries = Array.isArray(entries) ? entries.slice() : [];
        if (!state.opsEntries.length) {
            el.opsBody.innerHTML = `<tr><td colspan="5" class="empty">No operators</td></tr>`;
            return;
        }

        el.opsBody.innerHTML = state.opsEntries.map((entry) => {
            const name = String(entry.name || "");
            const uuid = String(entry.uuid || "");
            const providerLabel = String(entry.providerLabel || "Unknown");
            const providerKnown = Boolean(entry.providerKnown);
            const avatarUrl = entry.avatarUrl ? String(entry.avatarUrl) : null;

            return `<tr>
                <td class="face-cell">${avatarCellHtml(avatarUrl)}</td>
                <td>${escapeHtml(name)}</td>
                <td><code>${escapeHtml(uuid)}</code></td>
                <td class="${providerKnown ? "" : "provider-unknown"}">${escapeHtml(providerLabel)}</td>
                <td><button type="button" class="small danger" data-action="remove-op" data-uuid="${escapeAttr(uuid)}" data-name="${escapeAttr(name)}">Remove</button></td>
            </tr>`;
        }).join("");
    }

    async function onResolveWhitelistProfile() {
        if (!state.sessionToken) return;
        const provider = (el.whitelistProviderSelect.value || "").trim();
        const username = (el.whitelistUsernameInput.value || "").trim();

        if (!provider) {
            showBanner("Select a provider first.", "warn");
            return;
        }
        if (!username) {
            showBanner("Username is required.", "warn");
            return;
        }

        try {
            const profile = await resolveProfileByProvider(provider, username);
            if (!profile) {
                throw new Error("No profile returned.");
            }
            state.whitelistResolved = profile;
            el.whitelistResolvedName.textContent = String(profile.name || "");
            el.whitelistResolvedUuid.textContent = String(profile.uuid || "");
            el.whitelistResolvedProvider.textContent = String(profile.providerLabel || profile.provider || "");
            setAvatar(el.whitelistResolvedAvatar, profile.avatarUrl || null);
            el.whitelistResolvedCard.classList.remove("hidden");
            el.whitelistAddBtn.disabled = false;
            showBanner(`Resolved ${profile.name}.`, "ok");
        } catch (err) {
            clearWhitelistResolved();
            showBanner(`Resolve failed: ${err.message}`, "err");
        }
    }

    async function onAddWhitelistEntry(event) {
        event.preventDefault();
        if (!state.sessionToken) return;
        if (!state.whitelistResolved) {
            showBanner("Resolve a profile before adding whitelist entry.", "warn");
            return;
        }

        try {
            await requestJson(
                "/api/wawelauth/admin/whitelist/add",
                {
                    method: "POST",
                    body: JSON.stringify({
                        provider: state.whitelistResolved.provider,
                        username: state.whitelistResolved.name
                    })
                },
                true
            );
            showBanner(`Added ${state.whitelistResolved.name} to whitelist.`, "ok");
            el.whitelistUsernameInput.value = "";
            clearWhitelistResolved();
            await refreshData(false);
        } catch (err) {
            showBanner(`Failed to add whitelist entry: ${err.message}`, "err");
        }
    }

    async function onWhitelistTableClick(event) {
        const button = event.target.closest("button[data-action='remove-whitelist']");
        if (!button || !state.sessionToken) return;

        const uuid = button.getAttribute("data-uuid");
        const name = button.getAttribute("data-name") || uuid;
        if (!uuid) return;
        if (!window.confirm(`Remove ${name} from whitelist?`)) return;

        try {
            await requestJson(
                "/api/wawelauth/admin/whitelist/remove",
                {
                    method: "POST",
                    body: JSON.stringify({ uuid })
                },
                true
            );
            showBanner(`Removed ${name} from whitelist.`, "ok");
            await refreshData(false);
        } catch (err) {
            showBanner(`Failed to remove whitelist entry: ${err.message}`, "err");
        }
    }

    async function onWhitelistEnabledToggle() {
        if (!state.sessionToken) return;
        const target = Boolean(el.whitelistEnabledToggle.checked);
        try {
            const response = await requestJson(
                "/api/wawelauth/admin/whitelist/enabled",
                {
                    method: "POST",
                    body: JSON.stringify({ enabled: target })
                },
                true
            );
            const enabled = Boolean(response && response.enabled);
            el.whitelistEnabledToggle.checked = enabled;
            showBanner(`Whitelist ${enabled ? "enabled" : "disabled"}.`, "ok");
        } catch (err) {
            el.whitelistEnabledToggle.checked = !target;
            showBanner(`Failed to toggle whitelist: ${err.message}`, "err");
        }
    }

    function renderWhitelist(payload) {
        const entries = Array.isArray(payload && payload.entries) ? payload.entries : [];
        state.whitelistEntries = entries.slice();
        el.whitelistEnabledToggle.checked = Boolean(payload && payload.enabled);

        if (!entries.length) {
            el.whitelistBody.innerHTML = `<tr><td colspan="5" class="empty">Whitelist is empty</td></tr>`;
            return;
        }

        el.whitelistBody.innerHTML = entries.map((entry) => {
            const name = String(entry.name || "");
            const uuid = String(entry.uuid || "");
            const providerLabel = String(entry.providerLabel || "Unknown");
            const providerKnown = Boolean(entry.providerKnown);
            const avatarUrl = entry.avatarUrl ? String(entry.avatarUrl) : null;

            return `<tr>
                <td class="face-cell">${avatarCellHtml(avatarUrl)}</td>
                <td>${escapeHtml(name)}</td>
                <td><code>${escapeHtml(uuid)}</code></td>
                <td class="${providerKnown ? "" : "provider-unknown"}">${escapeHtml(providerLabel)}</td>
                <td><button type="button" class="small danger" data-action="remove-whitelist" data-uuid="${escapeAttr(uuid)}" data-name="${escapeAttr(name)}">Remove</button></td>
            </tr>`;
        }).join("");
    }

    function avatarCellHtml(avatarUrl) {
        return avatarHtml(avatarUrl, "");
    }

    function avatarHtml(avatarUrl, extraClass) {
        const className = `avatar-2d${extraClass ? ` ${extraClass}` : ""}${avatarUrl ? "" : " placeholder"}`;
        if (avatarUrl) {
            return `<img class="${className}" src="${escapeAttr(withCacheBuster(avatarUrl))}" alt="">`;
        }
        return `<img class="${className}" src="${BLANK_AVATAR}" alt="">`;
    }

    async function requestJson(path, options, withAuth) {
        const opts = Object.assign({}, options || {});
        opts.headers = Object.assign({}, opts.headers || {});
        if (opts.body != null && !opts.headers["Content-Type"]) {
            opts.headers["Content-Type"] = "application/json";
        }
        if (withAuth && state.sessionToken) {
            opts.headers.Authorization = `Bearer ${state.sessionToken}`;
        }

        const response = await fetch(path, opts);
        const text = await response.text();
        let payload = null;
        if (text) {
            try {
                payload = JSON.parse(text);
            } catch (_err) {
                payload = null;
            }
        }

        if (!response.ok) {
            const message = payload && payload.errorMessage
                ? payload.errorMessage
                : `${response.status} ${response.statusText}`;
            throw new Error(message);
        }

        return payload;
    }

    async function encryptToken(token, publicKeyBase64) {
        if (!publicKeyBase64) {
            throw new Error("Server did not provide admin public key.");
        }
        if (!window.crypto || !window.crypto.subtle) {
            throw new Error("Browser does not support WebCrypto required for encrypted login.");
        }

        const keyData = base64ToBytes(publicKeyBase64);
        const key = await window.crypto.subtle.importKey(
            "spki",
            keyData.buffer,
            { name: "RSA-OAEP", hash: "SHA-1" },
            false,
            ["encrypt"]
        );
        const encodedToken = new TextEncoder().encode(token);
        const encrypted = await window.crypto.subtle.encrypt(
            { name: "RSA-OAEP" },
            key,
            encodedToken
        );
        return bytesToBase64(new Uint8Array(encrypted));
    }

    function base64ToBytes(base64) {
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i += 1) {
            bytes[i] = binary.charCodeAt(i);
        }
        return bytes;
    }

    function bytesToBase64(bytes) {
        let binary = "";
        for (let i = 0; i < bytes.length; i += 1) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    }

    function disableLogin(disabled) {
        el.tokenInput.disabled = disabled;
        el.loginBtn.disabled = disabled;
    }

    function startSessionTimer() {
        stopSessionTimer();
        state.sessionTimer = window.setInterval(updateSessionLine, 1000);
    }

    function stopSessionTimer() {
        if (state.sessionTimer != null) {
            window.clearInterval(state.sessionTimer);
            state.sessionTimer = null;
        }
    }

    async function tryResumeSession() {
        const persisted = readPersistedSession();
        if (!persisted) {
            return;
        }

            state.sessionToken = persisted;
            try {
                const sessionResp = await requestJson("/api/wawelauth/admin/session", { method: "GET" }, true);
                state.sessionExpiresAt = Number(sessionResp.expiresAt) || 0;
                syncSessionCookie();
                enterAppMode();
                await refreshData(false);
                showBanner("Admin session restored.", "ok");
            } catch (_err) {
            state.sessionToken = null;
            state.sessionExpiresAt = 0;
            clearPersistedSession();
        }
    }

    function updateSessionLine() {
        if (!state.sessionToken || !state.sessionExpiresAt) {
            el.sessionLine.textContent = "";
            return;
        }

        const remaining = state.sessionExpiresAt - Date.now();
        if (remaining <= 0) {
            el.sessionLine.textContent = "Session expired";
            return;
        }

        el.sessionLine.textContent = `Session expires in ${formatDuration(remaining)}`;
    }

    function formatDuration(ms) {
        let totalSeconds = Math.floor(ms / 1000);
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }

        const hours = Math.floor(totalSeconds / 3600);
        const minutes = Math.floor((totalSeconds % 3600) / 60);
        const seconds = totalSeconds % 60;

        if (hours > 0) {
            return `${hours}h ${minutes}m ${seconds}s`;
        }
        if (minutes > 0) {
            return `${minutes}m ${seconds}s`;
        }
        return `${seconds}s`;
    }

    function clearTables() {
        state.profileUuidDrafts = {};
        renderUsersHeader();
        el.usersBody.innerHTML = `<tr><td colspan="5" class="empty">Not loaded</td></tr>`;
        el.invitesBody.innerHTML = `<tr><td colspan="4" class="empty">Not loaded</td></tr>`;
        el.opsBody.innerHTML = `<tr><td colspan="5" class="empty">Not loaded</td></tr>`;
        el.whitelistBody.innerHTML = `<tr><td colspan="5" class="empty">Not loaded</td></tr>`;
        el.propsBody.innerHTML = `<tr><td colspan="3" class="empty">Not loaded</td></tr>`;
        el.propsStatusLine.textContent = "";
        el.whitelistEnabledToggle.checked = false;
        el.statUsers.textContent = "-";
        el.statProfiles.textContent = "-";
        el.statTokens.textContent = "-";
        el.statInvites.textContent = "-";
        el.statDbSize.textContent = "-";
        el.statTexturesSize.textContent = "-";
        clearOpsResolved();
        clearWhitelistResolved();
    }

    function showBanner(message, level) {
        el.banner.classList.remove("hidden", "ok", "warn", "err");
        el.banner.classList.add(level || "ok");
        el.banner.textContent = message;
    }

    function clearBanner() {
        el.banner.classList.add("hidden");
        el.banner.classList.remove("ok", "warn", "err");
        el.banner.textContent = "";
    }

    function safeStat(value) {
        return Number.isFinite(Number(value)) ? String(value) : "-";
    }

    function safeNumber(value, fallback) {
        return Number.isFinite(Number(value)) ? Number(value) : fallback;
    }

    function persistSession() {
        if (!state.sessionToken) {
            return;
        }
        try {
            window.sessionStorage.setItem(SESSION_STORAGE_KEY, state.sessionToken);
        } catch (_err) {
            // No-op
        }
        syncSessionCookie();
    }

    function readPersistedSession() {
        try {
            return window.sessionStorage.getItem(SESSION_STORAGE_KEY);
        } catch (_err) {
            return null;
        }
    }

    function clearPersistedSession() {
        try {
            window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
        } catch (_err) {
            // No-op
        }
        clearSessionCookie();
    }

    function syncSessionCookie() {
        if (!state.sessionToken) {
            clearSessionCookie();
            return;
        }
        document.cookie = `wawelauth_admin_session=${encodeURIComponent(state.sessionToken)}; Path=/; SameSite=Strict`;
    }

    function clearSessionCookie() {
        document.cookie = "wawelauth_admin_session=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=Strict";
    }

    async function copyTextToClipboard(text) {
        const value = String(text || "");
        if (value.length === 0) {
            throw new Error("Nothing to copy.");
        }

        if (window.navigator && window.navigator.clipboard && window.isSecureContext) {
            await window.navigator.clipboard.writeText(value);
            return;
        }

        const textarea = document.createElement("textarea");
        textarea.value = value;
        textarea.setAttribute("readonly", "readonly");
        textarea.style.position = "fixed";
        textarea.style.top = "-1000px";
        textarea.style.left = "-1000px";
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();
        textarea.setSelectionRange(0, textarea.value.length);
        let copied = false;
        try {
            copied = document.execCommand("copy");
        } finally {
            document.body.removeChild(textarea);
        }
        if (!copied) {
            throw new Error("Browser blocked clipboard access.");
        }
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }

    function escapeAttr(value) {
        return escapeHtml(value).replace(/'/g, "&#39;");
    }
})();
