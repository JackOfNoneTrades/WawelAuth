package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Transactional storage for one ServerList snapshot, not a general-purpose NBT writer. */
public final class ServerListStorage {

    // Serializes compare-and-replace across all ServerList instances in this client.
    private static final Object FILE_LOCK = new Object();
    private final Path file;
    private final FileOperations operations;
    private byte[] loadedBytes;
    private NBTTagCompound loadedRoot;
    private boolean loaded;

    public ServerListStorage(Path file) {
        this(file, new FileOperations());
    }

    ServerListStorage(Path file, FileOperations operations) {
        this.file = file.toAbsolutePath();
        this.operations = operations;
    }

    public List<ServerData> load() throws IOException {
        synchronized (FILE_LOCK) {
            loaded = false;
            byte[] bytes = readExisting();
            NBTTagCompound root = new NBTTagCompound();
            List<ServerData> entries = new ArrayList<>();
            if (bytes != null) {
                try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                    root = CompressedStreamTools.read(input);
                    if (!root.hasKey("servers", 9)) {
                        throw new IOException("servers.dat does not contain a server list");
                    }
                    NBTTagList list = (NBTTagList) root.getTag("servers");
                    if (list.tagCount() > 0 && list.func_150303_d() != 10) {
                        throw new IOException("servers.dat contains non-compound server entries");
                    }
                    for (int i = 0; i < list.tagCount(); i++) {
                        NBTTagCompound entry = list.getCompoundTagAt(i);
                        if (!entry.hasKey("name", 8) || !entry.hasKey("ip", 8)) {
                            throw new IOException("Invalid server entry at index " + i);
                        }
                        entries.add(ServerData.getServerDataFromNBTCompound(entry));
                    }
                } catch (RuntimeException | LinkageError e) {
                    throw new IOException("Could not deserialize the complete server list", e);
                }
            }
            // Publish the new snapshot only after every entry, including mod metadata, loaded.
            loadedBytes = bytes;
            loadedRoot = root;
            loaded = true;
            return entries;
        }
    }

    public boolean isLoaded() {
        synchronized (FILE_LOCK) {
            return loaded;
        }
    }

    public boolean isCurrent() {
        synchronized (FILE_LOCK) {
            try {
                return loaded && Arrays.equals(loadedBytes, readExisting());
            } catch (IOException e) {
                return false;
            }
        }
    }

    public void save(List<ServerData> entries) throws IOException {
        synchronized (FILE_LOCK) {
            if (!loaded) {
                throw new IOException("Refusing to save a server list whose load did not succeed");
            }
            if (!Arrays.equals(loadedBytes, readExisting())) {
                throw new IOException(
                    "Refusing to overwrite a newer or missing servers.dat; reopen Multiplayer to reload it");
            }
            NBTTagList list = new NBTTagList();
            for (ServerData entry : entries) {
                list.appendTag(entry.getNBTCompound());
            }
            NBTTagCompound root = (NBTTagCompound) loadedRoot.copy();
            root.setTag("servers", list);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                CompressedStreamTools.write(root, output);
            }
            byte[] bytes = buffer.toByteArray();
            Path temporary = Files.createTempFile(file.getParent(), "servers.dat-", ".tmp");
            try {
                operations.write(temporary, bytes);
                // No delete-first fallback: if atomic replacement is unavailable, retain the old file.
                operations.replace(temporary, file);
                loadedBytes = bytes;
                loadedRoot = root;
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private byte[] readExisting() throws IOException {
        try {
            return Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    /** Injectable failure points for tests; production always flushes and atomically replaces. */
    static class FileOperations {

        void write(Path temporary, byte[] bytes) throws IOException {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
        }

        void replace(Path temporary, Path target) throws IOException {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
