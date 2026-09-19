package org.fentanylsolutions.wawelauth.wawelclient;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ServerListStorageTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private Path file() {
        return temporary.getRoot()
            .toPath()
            .resolve("servers.dat");
    }

    private List<ServerData> entries() {
        return Arrays.asList(new ServerData("One", "one.invalid"), new ServerData("Two", "two.invalid"));
    }

    private ServerListStorage seed() throws IOException {
        ServerListStorage store = new ServerListStorage(file());
        Assert.assertTrue(
            store.load()
                .isEmpty());
        store.save(entries());
        return store;
    }

    @Test
    public void roundTripsVanillaFieldsAndAllowsIntentionalDeletionOfLastServer() throws Exception {
        ServerListStorage store = seed();
        List<ServerData> loaded = store.load();
        Assert.assertEquals(2, loaded.size());
        Assert.assertEquals("two.invalid", loaded.get(1).serverIP);
        store.save(Collections.emptyList());
        Assert.assertTrue(
            new ServerListStorage(file()).load()
                .isEmpty());
        Assert.assertTrue(Files.isRegularFile(file()));
    }

    @Test
    public void rejectsStaleSnapshotsInsteadOfWipingOrResurrectingEntries() throws Exception {
        ServerListStorage current = seed();
        ServerListStorage oldGui = new ServerListStorage(file());
        oldGui.load();
        current.save(Collections.singletonList(entries().get(0)));
        byte[] expected = Files.readAllBytes(file());
        Assert.assertFalse(oldGui.isCurrent());
        Assert.assertThrows(IOException.class, () -> oldGui.save(Collections.emptyList()));
        Assert.assertThrows(IOException.class, () -> oldGui.save(entries()));
        Assert.assertArrayEquals(expected, Files.readAllBytes(file()));
        Assert.assertEquals(
            1,
            oldGui.load()
                .size());
        Assert.assertTrue(oldGui.isCurrent());
        oldGui.save(Collections.emptyList());
    }

    @Test
    public void corruptLoadInvalidatesPreviousSnapshotAndCannotOverwriteTheFile() throws Exception {
        ServerListStorage store = seed();
        byte[] broken = { 10, 0, 0, 9 };
        Files.write(file(), broken);
        Assert.assertThrows(IOException.class, store::load);
        Assert.assertFalse(store.isLoaded());
        Assert.assertThrows(IOException.class, () -> store.save(Collections.emptyList()));
        Assert.assertArrayEquals(broken, Files.readAllBytes(file()));
    }

    @Test
    public void malformedSecondEntryCannotPublishAPartiallyLoadedSnapshot() throws Exception {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        list.appendTag(
            entries().get(0)
                .getNBTCompound());
        NBTTagCompound invalid = new NBTTagCompound();
        invalid.setString("name", "Missing address");
        list.appendTag(invalid);
        root.setTag("servers", list);
        CompressedStreamTools.write(root, file().toFile());
        byte[] expected = Files.readAllBytes(file());
        ServerListStorage store = new ServerListStorage(file());
        Assert.assertThrows(IOException.class, store::load);
        Assert.assertFalse(store.isLoaded());
        Assert.assertThrows(IOException.class, () -> store.save(entries().subList(0, 1)));
        Assert.assertArrayEquals(expected, Files.readAllBytes(file()));
    }

    @Test
    public void rejectsMissingAndWrongTypeListTagsInsteadOfTreatingThemAsEmpty() throws Exception {
        NBTTagCompound root = new NBTTagCompound();
        ServerListStorage store = new ServerListStorage(file());
        CompressedStreamTools.write(root, file().toFile());
        Assert.assertThrows(IOException.class, store::load);
        NBTTagList strings = new NBTTagList();
        strings.appendTag(new NBTTagString("not a server"));
        root.setTag("servers", strings);
        CompressedStreamTools.write(root, file().toFile());
        Assert.assertThrows(IOException.class, store::load);
        Assert.assertThrows(IOException.class, () -> store.save(Collections.emptyList()));
    }

    @Test
    public void preservesUnknownRootMetadata() throws Exception {
        seed();
        NBTTagCompound root = CompressedStreamTools.read(file().toFile());
        root.setString("anotherModsSetting", "retained");
        CompressedStreamTools.write(root, file().toFile());
        ServerListStorage store = new ServerListStorage(file());
        List<ServerData> entries = store.load();
        store.save(entries);
        Assert.assertEquals(
            "retained",
            CompressedStreamTools.read(file().toFile())
                .getString("anotherModsSetting"));
    }

    @Test
    public void refusesSaveBeforeLoadOrAfterThePreviouslyLoadedFileDisappears() throws Exception {
        ServerListStorage unloaded = new ServerListStorage(file());
        Assert.assertThrows(IOException.class, () -> unloaded.save(entries()));
        ServerListStorage store = seed();
        Files.delete(file());
        Assert.assertFalse(store.isCurrent());
        Assert.assertThrows(IOException.class, () -> store.save(entries()));
        Assert.assertFalse(Files.exists(file()));
    }

    @Test
    public void partialTemporaryWriteLeavesOriginalIntactAndRemovesTemporaryFile() throws Exception {
        seed();
        byte[] original = Files.readAllBytes(file());
        ServerListStorage store = new ServerListStorage(file(), new ServerListStorage.FileOperations() {

            @Override
            void write(Path path, byte[] bytes) throws IOException {
                Files.write(path, new byte[] { 10, 0 });
                throw new IOException("Simulated disk full");
            }
        });
        store.load();
        Assert.assertThrows(IOException.class, () -> store.save(Collections.emptyList()));
        Assert.assertArrayEquals(original, Files.readAllBytes(file()));
        Assert.assertTrue(store.isCurrent());
        assertNoTemporaryFiles();
    }

    @Test
    public void failedAtomicReplacementDoesNotDeleteOriginalOrFallBackToUnsafeRename() throws Exception {
        seed();
        byte[] original = Files.readAllBytes(file());
        ServerListStorage store = new ServerListStorage(file(), new ServerListStorage.FileOperations() {

            @Override
            void replace(Path from, Path to) throws IOException {
                throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "Simulated failure");
            }
        });
        store.load();
        Assert.assertThrows(IOException.class, () -> store.save(Collections.emptyList()));
        Assert.assertArrayEquals(original, Files.readAllBytes(file()));
        Assert.assertTrue(store.isCurrent());
        assertNoTemporaryFiles();
    }

    @Test
    public void competingSnapshotsCannotDeleteTheFileOrSilentlyOverwriteEachOther() throws Exception {
        ServerListStorage first = seed();
        ServerListStorage second = new ServerListStorage(file());
        second.load();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = executor.submit(() -> saveAfter(start, first, entries().get(0)));
            Future<Boolean> b = executor.submit(() -> saveAfter(start, second, entries().get(1)));
            start.countDown();
            Assert.assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            Assert.assertEquals(
                1,
                new ServerListStorage(file()).load()
                    .size());
            assertNoTemporaryFiles();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void vanillaReaderNeverSeesDeleteRenameGapDuringRepeatedSaves() throws Exception {
        ServerListStorage store = seed();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> writer = executor.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    try {
                        store.save(entries());
                    } catch (IOException e) {
                        throw new AssertionError(e);
                    }
                }
            });
            for (int i = 0; i < 500; i++) {
                NBTTagCompound root = CompressedStreamTools.read(file().toFile());
                Assert.assertNotNull(root);
                Assert.assertEquals(
                    2,
                    root.getTagList("servers", 10)
                        .tagCount());
            }
            writer.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean saveAfter(CountDownLatch start, ServerListStorage store, ServerData entry) throws Exception {
        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Writer timed out");
        try {
            store.save(Collections.singletonList(entry));
            return true;
        } catch (IOException expectedConflict) {
            return false;
        }
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (Stream<Path> files = Files.list(file().getParent())) {
            Assert.assertEquals(1L, files.count());
        }
    }
}
