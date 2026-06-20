/* WAWEL_AUTH_DEFAULT_PUBLIC_PAGE */
(function () {
    "use strict";

    const PUBLIC_INFO_URL = "__WAWEL_PUBLIC_INFO_API_PATH__";
    const REQUEST_TIMEOUT_MS = 3500;
    const REFRESH_INTERVAL_MS = 30000;
    const FALLBACK_ICON_URL = "./pack-fallback.png";

    document.addEventListener("DOMContentLoaded", init);

    async function init() {
        const el = cacheElements();

        if (!PUBLIC_INFO_URL) {
            renderFailure(el, "Public server info API is disabled.");
            return;
        }

        await refresh(el);
        window.setInterval(function () {
            refresh(el);
        }, REFRESH_INTERVAL_MS);
    }

    async function refresh(el) {
        try {
            const data = await fetchJsonWithTimeout(PUBLIC_INFO_URL, REQUEST_TIMEOUT_MS);
            render(el, data || {});
        } catch (err) {
            console.error("[WawelAuth public-page] Failed to load public info:", err);
            renderFailure(el, err && err.message ? err.message : "Unknown error");
        }
    }

    function cacheElements() {
        const byId = function (id) {
            return document.getElementById(id);
        };
        return {
            iconWrap: byId("iconWrap"),
            serverIcon: byId("serverIcon"),
            serverName: byId("serverName"),
            motdLine: byId("motdLine"),
            serverDescription: byId("serverDescription"),
            playerCountPill: byId("playerCountPill"),
            registrationPill: byId("registrationPill"),
            minecraftVersionPill: byId("minecraftVersionPill"),
            adminButton: byId("adminButton"),
            dynmapButton: byId("dynmapButton"),
            serverAddressButton: byId("serverAddressButton"),
            serverAddressText: byId("serverAddressText"),
            serverAddressWarning: byId("serverAddressWarning"),
            joinHint: byId("joinHint"),
            homepageButton: byId("homepageButton"),
            registerButton: byId("registerButton"),
            registrationPolicy: byId("registrationPolicy"),
            registrationDescription: byId("registrationDescription"),
            apiRootValue: byId("apiRootValue"),
            apiRootDescription: byId("apiRootDescription"),
            fallbackList: byId("fallbackList"),
            fallbackEmpty: byId("fallbackEmpty"),
            playerList: byId("playerList"),
            playerListEmpty: byId("playerListEmpty"),
            modlistCount: byId("modlistCount"),
            modlist: byId("modlist"),
            footerImplementationName: byId("footerImplementationName"),
            footerVersion: byId("footerVersion")
        };
    }

    async function fetchJsonWithTimeout(url, timeoutMs) {
        const controller = typeof AbortController === "function" ? new AbortController() : null;
        let timeoutId = null;
        try {
            if (controller) {
                timeoutId = window.setTimeout(function () {
                    controller.abort();
                }, timeoutMs);
            }

            const response = await fetch(url, {
                cache: "no-store",
                signal: controller ? controller.signal : undefined
            });
            if (!response.ok) {
                throw new Error("Server info request failed with HTTP " + response.status);
            }
            return await response.json();
        } catch (err) {
            if (err && err.name === "AbortError") {
                throw new Error("Server info request timed out.");
            }
            throw err;
        } finally {
            if (timeoutId !== null) {
                window.clearTimeout(timeoutId);
            }
        }
    }

    function render(el, data) {
        const branding = data && data.branding ? data.branding : {};
        const server = data && data.server ? data.server : {};
        const links = data && data.links ? data.links : {};
        const icons = data && data.icons ? data.icons : {};
        const registration = server && server.registration ? server.registration : {};
        const fallbacks = Array.isArray(server.fallbacks) ? server.fallbacks.filter(Boolean) : [];
        const connectedPlayers = Array.isArray(server.connectedPlayers) ? server.connectedPlayers.filter(Boolean) : [];
        const modlist = Array.isArray(data.modlist) ? data.modlist.filter(Boolean) : [];

        const serverName = nonEmpty(server.name) || "Wawel Auth Server";
        const implementationName = nonEmpty(branding.implementationName) || "Wawel Auth";
        const implementationVersion = nonEmpty(branding.implementationVersion) || "unknown";
        const minecraftVersion = nonEmpty(branding.minecraftVersion) || "1.7.10";
        const description = nonEmpty(server.description);
        const apiRoot = nonEmpty(links.apiRoot);
        const homepage = nonEmpty(links.homepage);
        const registerUrl = nonEmpty(links.register);
        const registrationLabel = nonEmpty(registration.label) || "Unknown";
        const registrationDescription = nonEmpty(registration.description) || "Registration settings are unavailable.";
        const motd = nonEmpty(server.motd) || "Minecraft Server";
        const playersOnline = numberOrNull(server.playersOnline);
        const maxPlayers = numberOrNull(server.maxPlayers);
        const serverAddress = nonEmpty(server.address);
        const serverAddressWarning = nonEmpty(server.addressWarning);

        document.title = serverName;

        el.serverName.textContent = serverName;
        el.footerImplementationName.textContent = implementationName;
        el.footerVersion.textContent = implementationVersion;
        el.motdLine.textContent = motd;
        el.playerCountPill.textContent = "Players: " + formatPlayerCount(playersOnline, maxPlayers);
        el.minecraftVersionPill.textContent = "Minecraft " + minecraftVersion;
        el.registrationPill.textContent = registrationLabel;
        el.registrationPolicy.textContent = registrationLabel;
        el.registrationDescription.textContent = registrationDescription;

        if (description) {
            el.serverDescription.textContent = description;
            el.serverDescription.classList.remove("hidden");
        } else {
            el.serverDescription.textContent = "";
            el.serverDescription.classList.add("hidden");
        }

        configureApiRoot(el, apiRoot);

        configureLink(el.homepageButton, homepage);
        configureLink(el.registerButton, registerUrl);
        configureLink(el.adminButton, nonEmpty(links.admin));
        configureLink(el.dynmapButton, nonEmpty(links.dynmap));
        configureServerAddress(el, serverName, serverAddress, serverAddressWarning);
        renderFallbacks(el, buildAuthorizedProviders(fallbacks, apiRoot));
        renderConnectedPlayers(el, connectedPlayers);
        renderModlist(el, modlist);
        configureIcon(el, icons);
    }

    function renderFailure(el, message) {
        document.title = "Wawel Auth Server";
        el.serverDescription.textContent = "Failed to load public server information: " + message;
        el.serverDescription.classList.remove("hidden");
        el.iconWrap.classList.add("fallback");
        el.iconWrap.classList.remove("is-switchable");
        el.iconWrap.removeAttribute("title");
        el.iconWrap.onclick = null;
        el.serverIcon.src = FALLBACK_ICON_URL;
        el.motdLine.textContent = "Minecraft Server";
        el.playerCountPill.textContent = "Players: ? / ?";
        configureServerAddress(el, null, null);
        el.apiRootValue.textContent = "Unavailable";
        el.apiRootValue.disabled = true;
        el.apiRootValue.onclick = null;
        el.apiRootValue.removeAttribute("title");
        el.apiRootDescription.textContent = "Public server information could not be loaded.";
        el.fallbackList.innerHTML = "";
        el.fallbackEmpty.classList.remove("hidden");
        el.playerList.classList.add("hidden");
        el.playerListEmpty.classList.remove("hidden");
        el.playerList.innerHTML = "";
        renderModlist(el, []);
    }

    function renderFallbacks(el, fallbacks) {
        el.fallbackList.innerHTML = "";
        if (!fallbacks.length) {
            el.fallbackEmpty.classList.remove("hidden");
            return;
        }
        el.fallbackEmpty.classList.add("hidden");

        fallbacks.forEach(function (fallback) {
            const name = nonEmpty(fallback && fallback.name) || "fallback";
            const links = [
                makeFallbackLink("Account API", fallback && fallback.accountUrl),
                makeFallbackLink("Session API", fallback && fallback.sessionServerUrl),
                makeFallbackLink("Services API", fallback && fallback.servicesUrl)
            ].filter(Boolean);

            const entry = document.createElement("details");
            entry.className = "fallback-entry";

            const summary = document.createElement("summary");
            summary.textContent = name;
            entry.appendChild(summary);

            const details = document.createElement("div");
            details.className = "fallback-details";

            if (!links.length) {
                const empty = document.createElement("p");
                empty.className = "muted small";
                empty.textContent = "No public API links configured.";
                details.appendChild(empty);
            } else {
                links.forEach(function (item) {
                    const row = document.createElement("div");
                    row.className = "fallback-link-row";

                    const key = document.createElement("span");
                    key.className = "fallback-link-label";
                    key.textContent = item.label;

                    const value = document.createElement("a");
                    value.className = "fallback-link-value";
                    value.href = item.url;
                    value.target = "_blank";
                    value.rel = "noreferrer";
                    value.textContent = item.url;

                    row.appendChild(key);
                    row.appendChild(value);
                    details.appendChild(row);
                });
            }

            entry.appendChild(details);
            el.fallbackList.appendChild(entry);
        });
    }

    function buildAuthorizedProviders(fallbacks, apiRoot) {
        const providers = Array.isArray(fallbacks) ? fallbacks.slice() : [];
        const base = stripTrailingSlash(nonEmpty(apiRoot));
        if (!base || hasProviderForApiRoot(providers, base)) {
            return providers;
        }

        providers.unshift({
            name: "Local (Wawel Auth)",
            accountUrl: base + "/authserver",
            sessionServerUrl: base + "/sessionserver",
            servicesUrl: base
        });
        return providers;
    }

    function hasProviderForApiRoot(providers, apiRoot) {
        const authUrl = apiRoot + "/authserver";
        const sessionUrl = apiRoot + "/sessionserver";
        return providers.some(function (provider) {
            const name = String((provider && provider.name) || "").toLowerCase();
            return name === "local" || name === "localauth" || name === "wawelauth" || name === "self"
                || stripTrailingSlash(nonEmpty(provider && provider.servicesUrl)) === apiRoot
                || stripTrailingSlash(nonEmpty(provider && provider.accountUrl)) === authUrl
                || stripTrailingSlash(nonEmpty(provider && provider.sessionServerUrl)) === sessionUrl;
        });
    }

    function makeFallbackLink(label, value) {
        const url = nonEmpty(value);
        return url ? { label: label, url: url } : null;
    }

    function configureApiRoot(el, apiRoot) {
        if (!apiRoot) {
            el.apiRootValue.textContent = "Not configured";
            el.apiRootValue.disabled = true;
            el.apiRootValue.onclick = null;
            el.apiRootValue.removeAttribute("title");
            el.apiRootDescription.textContent = "Required for authlib-injector and public API discovery.";
            return;
        }

        el.apiRootValue.textContent = apiRoot;
        el.apiRootValue.disabled = false;
        el.apiRootValue.title = "Copy API root";
        el.apiRootValue.onclick = function () {
            copyText(apiRoot);
            flashText(el.apiRootDescription, "Copied API root.");
        };
        el.apiRootDescription.textContent = "Click to copy. Point authlib-injector at this URL.";
    }

    function configureLink(anchor, url) {
        if (!anchor) {
            return;
        }
        if (!url) {
            anchor.classList.add("hidden");
            anchor.removeAttribute("href");
            return;
        }
        anchor.href = url;
        anchor.classList.remove("hidden");
    }

    function configureServerAddress(el, serverName, address, warning) {
        const button = el.serverAddressButton;

        if (address) {
            const payload = buildServerDragPayload(serverName || address, address);
            el.serverAddressText.textContent = address;
            button.setAttribute("href", "#");
            button.dataset.serverAddress = address;
            button.dataset.serverName = serverName || address;
            button.dataset.dragPayload = payload;
            button.setAttribute("draggable", "true");
            button.onclick = function (event) {
                event.preventDefault();
                copyText(address);
                flashJoinHint(el, "Copied " + address);
            };
            button.ondragstart = function (event) {
                if (!event.dataTransfer) {
                    return;
                }
                event.dataTransfer.effectAllowed = "copy";
                safeSetDragData(event.dataTransfer, "text/plain", payload);
                safeSetDragData(event.dataTransfer, "text/uri-list", payload);
                safeSetDragData(event.dataTransfer, "application/x-wawelauth-server", payload);
            };
            button.classList.remove("hidden");
            el.serverAddressWarning.classList.add("hidden");
            el.serverAddressWarning.textContent = "";
            if (el.joinHint) {
                el.joinHint.classList.remove("hidden");
            }
            return;
        }

        button.classList.add("hidden");
        button.removeAttribute("href");
        button.setAttribute("draggable", "false");
        delete button.dataset.serverAddress;
        delete button.dataset.serverName;
        delete button.dataset.dragPayload;
        button.onclick = null;
        button.ondragstart = null;
        if (el.joinHint) {
            el.joinHint.classList.add("hidden");
        }

        if (warning) {
            el.serverAddressWarning.textContent = warning;
            el.serverAddressWarning.classList.remove("hidden");
        } else {
            el.serverAddressWarning.textContent = "";
            el.serverAddressWarning.classList.add("hidden");
        }
    }

    function flashJoinHint(el, message) {
        if (!el.joinHint) {
            return;
        }
        flashText(el.joinHint, message);
    }

    function flashText(node, message) {
        if (!node) {
            return;
        }
        const original = node.dataset.original || node.textContent;
        node.dataset.original = original;
        node.textContent = message;
        window.clearTimeout(node._resetTimer);
        node._resetTimer = window.setTimeout(function () {
            node.textContent = node.dataset.original;
        }, 2000);
    }

    function renderConnectedPlayers(el, players) {
        el.playerList.innerHTML = "";

        if (!players.length) {
            el.playerList.classList.add("hidden");
            el.playerListEmpty.classList.remove("hidden");
            return;
        }

        el.playerListEmpty.classList.add("hidden");
        el.playerList.classList.remove("hidden");

        players.forEach(function (player) {
            const name = nonEmpty(player && player.name) || "Unknown Player";
            const avatarUrl = nonEmpty(player && player.avatarUrl);

            const row = document.createElement("div");
            row.className = "player-entry";

            const avatar = document.createElement("img");
            avatar.className = "player-avatar";
            avatar.alt = name + " head";
            avatar.loading = "lazy";
            avatar.width = 40;
            avatar.height = 40;
            avatar.src = avatarUrl || FALLBACK_ICON_URL;

            const text = document.createElement("div");
            text.className = "player-name";
            text.textContent = name;
            text.title = name;

            row.appendChild(avatar);
            row.appendChild(text);
            el.playerList.appendChild(row);
        });
    }

    function renderModlist(el, modlist) {
        el.modlist.innerHTML = "";
        el.modlistCount.textContent = modlist.length + (modlist.length === 1 ? " mod" : " mods");

        if (!modlist.length) {
            const empty = document.createElement("div");
            empty.className = "mod-entry muted";
            empty.textContent = "No Forge mods reported.";
            el.modlist.appendChild(empty);
            return;
        }

        modlist.forEach(function (mod) {
            const name = nonEmpty(mod && mod.name) || "Unknown Mod";
            const version = nonEmpty(mod && mod.version);
            const filename = nonEmpty(mod && mod.filename);

            const row = document.createElement("div");
            row.className = "mod-entry";

            const main = document.createElement("div");
            main.className = "mod-mainline";
            main.textContent = version ? (name + " " + version) : name;
            row.appendChild(main);

            if (filename) {
                const sub = document.createElement("div");
                sub.className = "mod-filename";
                sub.textContent = filename;
                row.appendChild(sub);
            }

            el.modlist.appendChild(row);
        });
    }

    function configureIcon(el, icons) {
        const staticUrl = nonEmpty(icons.staticUrl);
        const animatedUrl = nonEmpty(icons.animatedUrl);
        const preferred = nonEmpty(icons.preferred) || (animatedUrl ? "animated" : (staticUrl ? "static" : "fallback"));

        el.iconWrap.classList.remove("fallback");
        el.iconWrap.classList.remove("is-switchable");
        el.iconWrap.removeAttribute("title");
        el.iconWrap.onclick = null;

        if (!staticUrl && !animatedUrl) {
            el.iconWrap.classList.add("fallback");
            el.serverIcon.src = FALLBACK_ICON_URL;
            return;
        }

        if (staticUrl && animatedUrl) {
            let mode = preferred === "static" ? "static" : "animated";
            el.iconWrap.classList.add("is-switchable");
            el.iconWrap.title = "Click to switch between animated and static server icons.";

            const update = function () {
                el.serverIcon.src = mode === "animated" ? animatedUrl : staticUrl;
            };
            update();

            el.iconWrap.onclick = function () {
                mode = mode === "animated" ? "static" : "animated";
                update();
            };
            return;
        }

        el.serverIcon.src = animatedUrl || staticUrl;
    }

    function formatPlayerCount(playersOnline, maxPlayers) {
        const online = playersOnline === null ? "?" : String(playersOnline);
        const max = maxPlayers === null ? "?" : String(maxPlayers);
        return online + " / " + max;
    }

    function buildServerDragPayload(serverName, address) {
        return "wawelauth-server://add?name="
            + encodeURIComponent(serverName)
            + "&address="
            + encodeURIComponent(address);
    }

    function stripTrailingSlash(value) {
        const text = nonEmpty(value);
        return text ? text.replace(/\/+$/, "") : null;
    }

    function safeSetDragData(dataTransfer, mimeType, value) {
        try {
            dataTransfer.setData(mimeType, value);
        } catch (err) {
            console.debug("[WawelAuth public-page] Failed to set drag data", mimeType, err);
        }
    }

    function copyText(value) {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(value).catch(function () {
                legacyCopy(value);
            });
            return;
        }
        legacyCopy(value);
    }

    function legacyCopy(value) {
        try {
            const textarea = document.createElement("textarea");
            textarea.value = value;
            textarea.setAttribute("readonly", "");
            textarea.className = "offscreen-clipboard";
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand("copy");
            document.body.removeChild(textarea);
        } catch (err) {
            console.debug("[WawelAuth public-page] Clipboard copy failed", err);
        }
    }

    function numberOrNull(value) {
        return typeof value === "number" && Number.isFinite(value) ? value : null;
    }

    function nonEmpty(value) {
        if (value === null || value === undefined) {
            return null;
        }
        const text = String(value).trim();
        return text ? text : null;
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }
})();
