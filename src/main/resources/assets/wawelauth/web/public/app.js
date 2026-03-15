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
        return {
            iconWrap: document.getElementById("iconWrap"),
            serverIcon: document.getElementById("serverIcon"),
            serverName: document.getElementById("serverName"),
            motdLine: document.getElementById("motdLine"),
            serverDescription: document.getElementById("serverDescription"),
            playerCountPill: document.getElementById("playerCountPill"),
            minecraftVersionPill: document.getElementById("minecraftVersionPill"),
            adminButton: document.getElementById("adminButton"),
            dynmapButton: document.getElementById("dynmapButton"),
            serverAddressButton: document.getElementById("serverAddressButton"),
            serverAddressText: document.getElementById("serverAddressText"),
            serverAddressWarning: document.getElementById("serverAddressWarning"),
            homepageButton: document.getElementById("homepageButton"),
            registerButton: document.getElementById("registerButton"),
            registrationPolicy: document.getElementById("registrationPolicy"),
            registrationDescription: document.getElementById("registrationDescription"),
            apiRootValue: document.getElementById("apiRootValue"),
            apiRootDescription: document.getElementById("apiRootDescription"),
            fallbackList: document.getElementById("fallbackList"),
            fallbackEmpty: document.getElementById("fallbackEmpty"),
            playerList: document.getElementById("playerList"),
            playerListEmpty: document.getElementById("playerListEmpty"),
            modlistToggle: document.getElementById("modlistToggle"),
            modlistCount: document.getElementById("modlistCount"),
            modlist: document.getElementById("modlist"),
            authlibText: document.getElementById("authlibText"),
            footerImplementationName: document.getElementById("footerImplementationName"),
            footerVersion: document.getElementById("footerVersion")
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
        el.registrationPolicy.textContent = registrationLabel;
        el.registrationDescription.textContent = registrationDescription;

        if (description) {
            el.serverDescription.textContent = description;
            el.serverDescription.classList.remove("hidden");
        } else {
            el.serverDescription.textContent = "";
            el.serverDescription.classList.add("hidden");
        }

        if (apiRoot) {
            el.apiRootValue.textContent = apiRoot;
            el.apiRootDescription.textContent = "Point authlib-injector at this URL.";
            el.authlibText.innerHTML = "Point authlib-injector at <code>" + escapeHtml(apiRoot) + "</code>.";
        } else {
            el.apiRootValue.textContent = "Not configured";
            el.apiRootDescription.textContent = "Required for authlib-injector and public API discovery.";
            el.authlibText.textContent = "This server has not configured a public authlib-injector API root yet.";
        }

        configureLink(el.homepageButton, homepage);
        configureLink(el.registerButton, registerUrl);
        configureLink(el.adminButton, nonEmpty(links.admin));
        configureLink(el.dynmapButton, nonEmpty(links.dynmap));
        configureServerAddress(el, serverAddress, serverAddressWarning);
        renderFallbacks(el, fallbacks);
        renderConnectedPlayers(el, connectedPlayers);
        renderModlist(el, modlist);
        configureIcon(el, icons);
    }

    function renderFailure(el, message) {
        document.title = "Wawel Auth Server";
        el.serverDescription.textContent = "Failed to load public server information: " + message;
        el.serverDescription.classList.remove("hidden");
        el.authlibText.textContent = "Public server information is unavailable right now.";
        el.iconWrap.classList.add("fallback");
        el.iconWrap.classList.remove("is-switchable");
        el.iconWrap.removeAttribute("title");
        el.iconWrap.onclick = null;
        el.serverIcon.src = FALLBACK_ICON_URL;
        el.motdLine.textContent = "Minecraft Server";
        el.playerCountPill.textContent = "Players: ? / ?";
        configureServerAddress(el, null, null);
        el.apiRootValue.textContent = "Unavailable";
        el.apiRootDescription.textContent = "Public server information could not be loaded.";
        el.fallbackList.classList.add("hidden");
        el.fallbackEmpty.classList.remove("hidden");
        el.playerList.classList.add("hidden");
        el.playerListEmpty.classList.remove("hidden");
        el.playerList.innerHTML = "";
        renderModlist(el, []);
    }

    function renderFallbacks(el, fallbacks) {
        el.fallbackList.innerHTML = "";
        if (!fallbacks.length) {
            el.fallbackList.classList.add("hidden");
            el.fallbackEmpty.classList.remove("hidden");
            return;
        }

        el.fallbackEmpty.classList.add("hidden");
        el.fallbackList.classList.remove("hidden");

        fallbacks.forEach(function (fallback) {
            const name = nonEmpty(fallback && fallback.name) || "fallback";
            const links = [
                makeFallbackLink("Account API", fallback && fallback.accountUrl),
                makeFallbackLink("Session API", fallback && fallback.sessionServerUrl),
                makeFallbackLink("Services API", fallback && fallback.servicesUrl)
            ].filter(Boolean);

            const row = document.createElement("div");
            row.className = "fallback-entry";

            const toggle = document.createElement("button");
            toggle.type = "button";
            toggle.className = "fallback-toggle";
            toggle.setAttribute("aria-expanded", "false");

            const arrow = document.createElement("span");
            arrow.className = "fallback-arrow";
            arrow.textContent = ">";

            const label = document.createElement("span");
            label.className = "fallback-name";
            label.textContent = name;

            toggle.appendChild(arrow);
            toggle.appendChild(label);

            const details = document.createElement("div");
            details.className = "fallback-details hidden";

            if (!links.length) {
                const empty = document.createElement("div");
                empty.className = "fallback-link-row muted";
                empty.textContent = "No public API links configured.";
                details.appendChild(empty);
            } else {
                links.forEach(function (item) {
                    const line = document.createElement("div");
                    line.className = "fallback-link-row";

                    const key = document.createElement("span");
                    key.className = "fallback-link-label";
                    key.textContent = item.label;

                    const value = document.createElement("a");
                    value.className = "fallback-link-value";
                    value.href = item.url;
                    value.target = "_blank";
                    value.rel = "noreferrer";
                    value.textContent = item.url;

                    line.appendChild(key);
                    line.appendChild(value);
                    details.appendChild(line);
                });
            }

            toggle.onclick = function () {
                const expanded = toggle.getAttribute("aria-expanded") === "true";
                toggle.setAttribute("aria-expanded", expanded ? "false" : "true");
                row.classList.toggle("expanded", !expanded);
                details.classList.toggle("hidden", expanded);
            };

            row.appendChild(toggle);
            row.appendChild(details);
            el.fallbackList.appendChild(row);
        });
    }

    function makeFallbackLink(label, value) {
        const url = nonEmpty(value);
        return url ? { label: label, url: url } : null;
    }

    function configureLink(anchor, url) {
        if (!url) {
            anchor.classList.add("hidden");
            anchor.removeAttribute("href");
            return;
        }
        anchor.href = url;
        anchor.classList.remove("hidden");
    }

    function configureServerAddress(el, address, warning) {
        if (address) {
            el.serverAddressText.textContent = address;
            el.serverAddressButton.setAttribute("href", "#");
            el.serverAddressButton.dataset.serverAddress = address;
            el.serverAddressButton.onclick = function (event) {
                event.preventDefault();
            };
            el.serverAddressButton.classList.remove("hidden");
            el.serverAddressWarning.classList.add("hidden");
            el.serverAddressWarning.textContent = "";
            return;
        }

        el.serverAddressButton.classList.add("hidden");
        el.serverAddressButton.removeAttribute("href");
        delete el.serverAddressButton.dataset.serverAddress;
        el.serverAddressButton.onclick = null;

        if (warning) {
            el.serverAddressWarning.textContent = warning;
            el.serverAddressWarning.classList.remove("hidden");
        } else {
            el.serverAddressWarning.textContent = "";
            el.serverAddressWarning.classList.add("hidden");
        }
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
        el.modlist.classList.add("hidden");
        el.modlistToggle.setAttribute("aria-expanded", "false");
        el.modlistToggle.parentElement.classList.remove("expanded");

        el.modlistToggle.onclick = function () {
            const expanded = el.modlistToggle.getAttribute("aria-expanded") === "true";
            el.modlistToggle.setAttribute("aria-expanded", expanded ? "false" : "true");
            el.modlist.classList.toggle("hidden", expanded);
            el.modlistToggle.parentElement.classList.toggle("expanded", !expanded);
        };

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
