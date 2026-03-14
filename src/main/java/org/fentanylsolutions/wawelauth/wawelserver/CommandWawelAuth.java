package org.fentanylsolutions.wawelauth.wawelserver;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.packet.ClipboardCopyPacket;
import org.fentanylsolutions.wawelauth.packet.PacketHandler;
import org.fentanylsolutions.wawelauth.test.TransactionConcurrencyTest;
import org.fentanylsolutions.wawelauth.wawelcore.crypto.PasswordHasher;
import org.fentanylsolutions.wawelauth.wawelcore.data.TextureType;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelInvite;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelUser;

/**
 * Server command for WawelAuth administration.
 * <p>
 * Usage: /wawelauth &lt;register|invite|test&gt; ...
 */
public class CommandWawelAuth extends CommandBase {

    private static final List<String> SUBCOMMANDS = Arrays.asList("register", "invite", "test");
    private static final List<String> INVITE_SUBCOMMANDS = Arrays.asList("create", "list", "delete", "purge");
    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom INVITE_RANDOM = new SecureRandom();

    @Override
    public String getCommandName() {
        return "wawelauth";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/wawelauth <register|invite|test> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        // Catch all exceptions here to prevent vanilla CommandHandler from
        // logging the full command string, which contains the plaintext password.
        try {
            if (args.length == 0) {
                sendError(sender, "Usage: " + getCommandUsage(sender));
                return;
            }

            if ("register".equalsIgnoreCase(args[0])) {
                handleRegister(sender, args);
            } else if ("invite".equalsIgnoreCase(args[0])) {
                handleInvite(sender, args);
            } else if ("test".equalsIgnoreCase(args[0])) {
                handleTest(sender);
            } else {
                sendError(sender, "Unknown subcommand: " + args[0]);
                sendError(sender, "Usage: " + getCommandUsage(sender));
            }
        } catch (Exception e) {
            WawelAuth.LOG.warn("Error executing /wawelauth command: {}", e.getMessage());
            sendError(sender, "Command failed: " + e.getMessage());
        }
    }

    private void handleRegister(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            sendError(sender, "Usage: /wawelauth register <username> <password>");
            return;
        }

        WawelServer server = WawelServer.instance();
        if (server == null) {
            sendError(sender, "Wawel Auth server module is not running.");
            return;
        }

        String username = args[1];
        String password = args[2];

        if (Config.server()
            .getFeatures()
            .isUsernameCheck()) {
            // Validate username against config regex
            String regex = Config.server()
                .getRegistration()
                .getPlayerNameRegex();
            if (!username.matches(regex)) {
                sendError(sender, "Invalid username. Must match: " + regex);
                return;
            }
        }

        // Check if username is taken
        if (server.getUserDAO()
            .findByUsername(username) != null) {
            sendError(sender, "Username '" + username + "' is already taken.");
            return;
        }

        // Check if profile name is taken
        if (server.getProfileDAO()
            .findByName(username) != null) {
            sendError(sender, "A profile with name '" + username + "' already exists.");
            return;
        }

        // Hash password
        PasswordHasher.HashResult hashResult = PasswordHasher.hash(password);

        // Create user
        long now = System.currentTimeMillis();
        WawelUser user = new WawelUser();
        user.setUuid(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash(hashResult.getHash());
        user.setPasswordSalt(hashResult.getSalt());
        user.setCreatedAt(now);

        // Create profile with uploadable textures from config
        Set<TextureType> uploadable = EnumSet.noneOf(TextureType.class);
        for (String name : Config.server()
            .getRegistration()
            .getDefaultUploadableTextures()) {
            TextureType type = TextureType.fromApiName(name);
            if (type != null) uploadable.add(type);
        }

        WawelProfile profile = new WawelProfile();
        profile.setUuid(UUID.randomUUID());
        profile.setName(username);
        profile.setOwnerUuid(user.getUuid());
        profile.updateOfflineUuid();
        profile.setUploadableTextures(uploadable);
        profile.setCreatedAt(now);

        // Insert both in a single transaction: rolls back on any failure
        server.runInTransaction(() -> {
            server.getUserDAO()
                .create(user);
            server.getProfileDAO()
                .create(profile);
        });

        sendSuccess(sender, "Registered user '" + username + "'");
        sendSuccess(sender, "Profile UUID: " + UuidUtil.toUnsigned(profile.getUuid()));
    }

    private void handleTest(ICommandSender sender) {
        sendSuccess(sender, "Running concurrency tests... check server log for results.");
        new Thread(() -> TransactionConcurrencyTest.run(Config.getConfigDir()), "wawelauth-test").start();
    }

    private void handleInvite(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Usage: /wawelauth invite <create|list|delete|purge> ...");
            return;
        }

        WawelServer server = WawelServer.instance();
        if (server == null) {
            sendError(sender, "Wawel Auth server module is not running.");
            return;
        }

        String mode = args[1].toLowerCase();
        if ("create".equals(mode)) {
            int uses = Config.server()
                .getInvites()
                .getDefaultUses();
            if (args.length >= 3) {
                try {
                    uses = parseInviteUses(args[2]);
                } catch (NumberFormatException e) {
                    sendError(sender, "Invalid uses value: " + args[2]);
                    return;
                }
            }
            if (uses == 0 || uses < -1) {
                sendError(sender, "Invite uses must be -1 (unlimited) or a positive number.");
                return;
            }

            UUID createdBy = null;
            WawelUser creator = server.getUserDAO()
                .findByUsername(sender.getCommandSenderName());
            if (creator != null) {
                createdBy = creator.getUuid();
            }

            String code = createUniqueInviteCode(server);
            WawelInvite invite = new WawelInvite();
            invite.setCode(code);
            invite.setCreatedAt(System.currentTimeMillis());
            invite.setCreatedBy(createdBy);
            invite.setUsesRemaining(uses);
            server.getInviteDAO()
                .create(invite);

            sendSuccess(sender, "Created invite: " + code + " (uses: " + formatInviteUses(uses) + ")");
            tryCopyInviteToClipboard(sender, code);
            return;
        }

        if ("list".equals(mode)) {
            List<WawelInvite> invites = server.getInviteDAO()
                .listAll();
            if (invites.isEmpty()) {
                sendSuccess(sender, "No invites.");
                return;
            }
            sendSuccess(sender, "Invites (" + invites.size() + "):");
            for (WawelInvite invite : invites) {
                sendSuccess(
                    sender,
                    " - " + invite.getCode()
                        + " | uses: "
                        + formatInviteUses(invite.getUsesRemaining())
                        + " | createdAt: "
                        + invite.getCreatedAt());
            }
            return;
        }

        if ("delete".equals(mode)) {
            if (args.length < 3) {
                sendError(sender, "Usage: /wawelauth invite delete <code>");
                return;
            }
            String code = args[2];
            if (server.getInviteDAO()
                .findByCode(code) == null) {
                sendError(sender, "Invite not found: " + code);
                return;
            }
            server.getInviteDAO()
                .delete(code);
            sendSuccess(sender, "Deleted invite: " + code);
            return;
        }

        if ("purge".equals(mode)) {
            server.getInviteDAO()
                .purgeConsumed();
            sendSuccess(sender, "Purged fully consumed invites.");
            return;
        }

        sendError(sender, "Unknown invite subcommand: " + args[1]);
        sendError(sender, "Usage: /wawelauth invite <create|list|delete|purge> ...");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS.toArray(new String[0]));
        }
        if (args.length == 2 && "invite".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, INVITE_SUBCOMMANDS.toArray(new String[0]));
        }
        if (args.length == 3 && "invite".equalsIgnoreCase(args[0]) && "delete".equalsIgnoreCase(args[1])) {
            WawelServer server = WawelServer.instance();
            if (server == null) return new ArrayList<>();
            List<WawelInvite> invites = server.getInviteDAO()
                .listAll();
            List<String> codes = new ArrayList<>();
            for (WawelInvite invite : invites) {
                codes.add(invite.getCode());
            }
            return getListOfStringsMatchingLastWord(args, codes.toArray(new String[0]));
        }
        return new ArrayList<>();
    }

    private static String createUniqueInviteCode(WawelServer server) {
        for (int attempt = 0; attempt < 16; attempt++) {
            String code = randomInviteCode(4, 5);
            if (server.getInviteDAO()
                .findByCode(code) == null) {
                return code;
            }
        }
        throw new RuntimeException("Failed to generate a unique invite code.");
    }

    private static String randomInviteCode(int groups, int charsPerGroup) {
        StringBuilder out = new StringBuilder(groups * charsPerGroup + (groups - 1));
        for (int g = 0; g < groups; g++) {
            if (g > 0) {
                out.append('-');
            }
            for (int i = 0; i < charsPerGroup; i++) {
                int idx = INVITE_RANDOM.nextInt(INVITE_ALPHABET.length());
                out.append(INVITE_ALPHABET.charAt(idx));
            }
        }
        return out.toString();
    }

    private static int parseInviteUses(String raw) {
        if ("unlimited".equalsIgnoreCase(raw)) {
            return -1;
        }
        return Integer.parseInt(raw);
    }

    private static String formatInviteUses(int uses) {
        return uses == -1 ? "unlimited" : String.valueOf(uses);
    }

    private static void tryCopyInviteToClipboard(ICommandSender sender, String inviteCode) {
        if (!(sender instanceof EntityPlayerMP) || inviteCode == null || inviteCode.isEmpty()) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        try {
            PacketHandler.sendToPlayer(
                new ClipboardCopyPacket(inviteCode, EnumChatFormatting.YELLOW + "Invite code copied to clipboard."),
                player);
        } catch (Exception e) {
            WawelAuth.debug("Failed to send clipboard packet: " + e.getMessage());
        }
    }

    private static void sendError(ICommandSender sender, String message) {
        ChatComponentText text = new ChatComponentText(message);
        text.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        sender.addChatMessage(text);
    }

    private static void sendSuccess(ICommandSender sender, String message) {
        ChatComponentText text = new ChatComponentText(message);
        text.getChatStyle()
            .setColor(EnumChatFormatting.GREEN);
        sender.addChatMessage(text);
    }
}
