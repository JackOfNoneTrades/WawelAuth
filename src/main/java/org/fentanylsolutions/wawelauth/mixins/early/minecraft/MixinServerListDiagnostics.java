package org.fentanylsolutions.wawelauth.mixins.early.minecraft;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Temporary diagnostics for servers.dat data loss. Logs every load/save with
 * entry counts and callers, and flags saves that shrink or lose the file.
 */
@Mixin(ServerList.class)
public abstract class MixinServerListDiagnostics {

    @Inject(method = "saveServerList", at = @At("HEAD"))
    private void wawelauth$logSave(CallbackInfo ci) {
        int memory = ((ServerList) (Object) this).countServers();
        int disk = wawelauth$countServersOnDisk();
        WawelAuth.LOG.info(
            "[ServerListDiag] save: {} entries in memory, {} on disk, thread={}, caller={}",
            memory,
            wawelauth$describeDiskCount(disk),
            Thread.currentThread()
                .getName(),
            wawelauth$callerChain());
        if (disk > memory) {
            WawelAuth.LOG.warn(
                "[ServerListDiag] this save SHRINKS servers.dat from " + disk + " to " + memory + " entries",
                new Throwable("servers.dat shrink stack"));
            if (memory == 0) {
                wawelauth$preserveFile(wawelauth$serversDatFile(), "servers.dat.pre-wipe-");
            }
        }
        if (disk == -2 || disk == -3) {
            WawelAuth.LOG.warn(
                "[ServerListDiag] this save OVERWRITES an unreadable servers.dat",
                new Throwable("servers.dat corrupt overwrite stack"));
            wawelauth$preserveFile(wawelauth$serversDatFile(), "servers.dat.corrupt-");
        }
    }

    @Inject(method = "saveServerList", at = @At("RETURN"))
    private void wawelauth$verifySave(CallbackInfo ci) {
        File file = wawelauth$serversDatFile();
        if (file != null && !file.isFile()) {
            WawelAuth.LOG.error(
                "[ServerListDiag] servers.dat is MISSING after save",
                new Throwable("servers.dat missing stack"));
        }
    }

    @Inject(method = "loadServerList", at = @At("RETURN"))
    private void wawelauth$logLoad(CallbackInfo ci) {
        int memory = ((ServerList) (Object) this).countServers();
        int disk = wawelauth$countServersOnDisk();
        File file = wawelauth$serversDatFile();
        if (disk > memory) {
            WawelAuth.LOG.warn(
                "[ServerListDiag] load TRUNCATED: loaded " + memory
                    + " of "
                    + disk
                    + " disk entries (an entry threw during deserialization and vanilla swallowed it), thread="
                    + Thread.currentThread()
                        .getName(),
                new Throwable("servers.dat truncated load stack"));
        } else if (memory == 0 && file != null && file.isFile() && file.length() > 19L) {
            WawelAuth.LOG.warn(
                "[ServerListDiag] load returned 0 entries but servers.dat has " + file.length()
                    + " bytes (disk="
                    + wawelauth$describeDiskCount(disk)
                    + "), file is likely corrupt",
                new Throwable("servers.dat corrupt load stack"));
            wawelauth$preserveFile(file, "servers.dat.corrupt-");
        } else {
            WawelAuth.debug(
                "[ServerListDiag] load: " + memory
                    + " entries, disk="
                    + wawelauth$describeDiskCount(disk)
                    + ", thread="
                    + Thread.currentThread()
                        .getName()
                    + ", caller="
                    + wawelauth$callerChain());
        }
    }

    @Unique
    private static void wawelauth$preserveFile(File file, String prefix) {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            File copy = new File(file.getParentFile(), prefix + System.currentTimeMillis());
            java.nio.file.Files.copy(file.toPath(), copy.toPath());
            WawelAuth.LOG.warn("[ServerListDiag] preserved servers.dat as {}", copy.getName());
        } catch (Exception e) {
            WawelAuth.LOG.warn("[ServerListDiag] failed to preserve servers.dat: {}", e.getMessage());
        }
    }

    @Unique
    private static File wawelauth$serversDatFile() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.mcDataDir == null) {
            return null;
        }
        return new File(minecraft.mcDataDir, "servers.dat");
    }

    @Unique
    private static int wawelauth$countServersOnDisk() {
        try {
            File file = wawelauth$serversDatFile();
            if (file == null || !file.isFile()) {
                return -1;
            }
            NBTTagCompound nbt = CompressedStreamTools.read(file);
            if (nbt == null) {
                return -2;
            }
            return nbt.getTagList("servers", 10)
                .tagCount();
        } catch (Exception e) {
            WawelAuth.LOG.warn("[ServerListDiag] servers.dat is unreadable: {}", e.getMessage());
            return -3;
        }
    }

    @Unique
    private static String wawelauth$describeDiskCount(int disk) {
        switch (disk) {
            case -1:
                return "no file";
            case -2:
                return "empty nbt";
            case -3:
                return "unreadable";
            default:
                return String.valueOf(disk);
        }
    }

    @Unique
    private static String wawelauth$callerChain() {
        StackTraceElement[] stack = Thread.currentThread()
            .getStackTrace();
        StringBuilder chain = new StringBuilder();
        int taken = 0;
        for (StackTraceElement element : stack) {
            String cls = element.getClassName();
            if (cls.startsWith("java.lang.Thread") || cls.endsWith(".ServerList")
                || cls.contains("MixinServerListDiagnostics")) {
                continue;
            }
            if (taken > 0) {
                chain.append(" <- ");
            }
            chain.append(cls.substring(cls.lastIndexOf('.') + 1))
                .append('.')
                .append(element.getMethodName())
                .append(':')
                .append(element.getLineNumber());
            if (++taken == 4) {
                break;
            }
        }
        return taken == 0 ? "?" : chain.toString();
    }
}
