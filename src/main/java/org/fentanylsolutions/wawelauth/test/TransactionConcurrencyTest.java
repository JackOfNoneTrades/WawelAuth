package org.fentanylsolutions.wawelauth.test;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelUser;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteDatabase;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteProfileDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.sqlite.SqliteUserDAO;

/**
 * Concurrency test that proves "no orphan user" under simultaneous register
 * attempts. Reproduces the CommandWawelAuth register pattern:
 * <p>
 * 1. Check username not taken (outside transaction)
 * 2. Check profile name not taken (outside transaction)
 * 3. runInTransaction { create user; create profile; }
 * <p>
 * Spawns N threads that all race to register the same username. Asserts:
 * - Exactly 1 thread succeeds
 * - user count == profile count (no orphans)
 * - No duplicate usernames
 * <p>
 * Run from server console or startup:
 * TransactionConcurrencyTest.run(configDir)
 */
public class TransactionConcurrencyTest {

    private static final int THREAD_COUNT = 20;

    public static void run(File configDir) {
        File dbFile = new File(configDir, "wawelauth_concurrency_test.db");
        SqliteDatabase db = new SqliteDatabase(dbFile);
        try {
            db.initialize();
            SqliteUserDAO userDAO = new SqliteUserDAO(db);
            SqliteProfileDAO profileDAO = new SqliteProfileDAO(db);

            boolean pass = true;

            pass &= testSameUsername(db, userDAO, profileDAO);
            pass &= testDistinctUsernames(db, userDAO, profileDAO);

            if (pass) {
                WawelAuth.LOG.info("[Concurrency Test] ALL TESTS PASSED");
            } else {
                WawelAuth.LOG.error("[Concurrency Test] SOME TESTS FAILED: see above");
            }
        } catch (Exception e) {
            WawelAuth.LOG.error("[Concurrency Test] Test suite failed with exception!", e);
        } finally {
            db.close();
            if (dbFile.exists()) dbFile.delete();
            new File(dbFile.getPath() + "-wal").delete();
            new File(dbFile.getPath() + "-shm").delete();
            WawelAuth.LOG.info("[Concurrency Test] Test DB cleaned up.");
        }
    }

    /**
     * N threads all race to register the same username.
     * Exactly 1 must win; user count must equal profile count.
     */
    private static boolean testSameUsername(SqliteDatabase db, SqliteUserDAO userDAO, SqliteProfileDAO profileDAO) {
        WawelAuth.LOG.info("[Concurrency Test] === testSameUsername ({} threads) ===", THREAD_COUNT);

        String username = "RaceTarget";
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                    return;
                }

                try {
                    simulateRegister(db, userDAO, profileDAO, username);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }, "reg-same-" + i).start();
        }

        try {
            ready.await();
            go.countDown();
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            WawelAuth.LOG.error("[Concurrency Test] Interrupted waiting for threads");
            return false;
        }

        long users = userDAO.count();
        long profiles = profileDAO.count();

        WawelAuth.LOG.info(
            "[Concurrency Test]   successes={} failures={} users={} profiles={}",
            successes.get(),
            failures.get(),
            users,
            profiles);

        boolean pass = true;

        if (successes.get() != 1) {
            WawelAuth.LOG.error("[Concurrency Test]   FAIL: expected exactly 1 success, got {}", successes.get());
            pass = false;
        }
        if (users != profiles) {
            WawelAuth.LOG.error("[Concurrency Test]   FAIL: orphan detected! users={} profiles={}", users, profiles);
            pass = false;
        }
        if (users != 1) {
            WawelAuth.LOG.error("[Concurrency Test]   FAIL: expected 1 user row, got {}", users);
            pass = false;
        }

        // Verify the winner's profile actually belongs to the winner
        WawelUser user = userDAO.findByUsername(username);
        if (user != null) {
            WawelProfile profile = profileDAO.findByName(username);
            if (profile == null) {
                WawelAuth.LOG.error("[Concurrency Test]   FAIL: user exists but profile is missing");
                pass = false;
            } else if (!profile.getOwnerUuid()
                .equals(user.getUuid())) {
                    WawelAuth.LOG.error("[Concurrency Test]   FAIL: profile owner mismatch");
                    pass = false;
                }
        } else {
            WawelAuth.LOG.error("[Concurrency Test]   FAIL: winning user not found by username");
            pass = false;
        }

        // Clean up for next test
        if (user != null) {
            profileDAO.delete(
                profileDAO.findByName(username)
                    .getUuid());
            userDAO.delete(user.getUuid());
        }

        if (pass) {
            WawelAuth.LOG.info("[Concurrency Test]   PASS");
        }
        return pass;
    }

    /**
     * N threads each register a distinct username concurrently.
     * All must succeed; user count must equal profile count must equal N.
     */
    private static boolean testDistinctUsernames(SqliteDatabase db, SqliteUserDAO userDAO,
        SqliteProfileDAO profileDAO) {
        WawelAuth.LOG.info("[Concurrency Test] === testDistinctUsernames ({} threads) ===", THREAD_COUNT);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            String username = "Player_" + i;
            new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                    return;
                }

                try {
                    simulateRegister(db, userDAO, profileDAO, username);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    WawelAuth.LOG.warn("[Concurrency Test]   Unexpected failure for {}: {}", username, e.getMessage());
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }, "reg-distinct-" + i).start();
        }

        try {
            ready.await();
            go.countDown();
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            WawelAuth.LOG.error("[Concurrency Test] Interrupted waiting for threads");
            return false;
        }

        long users = userDAO.count();
        long profiles = profileDAO.count();

        WawelAuth.LOG.info(
            "[Concurrency Test]   successes={} failures={} users={} profiles={}",
            successes.get(),
            failures.get(),
            users,
            profiles);

        boolean pass = true;

        if (successes.get() != THREAD_COUNT) {
            WawelAuth.LOG
                .error("[Concurrency Test]   FAIL: expected {} successes, got {}", THREAD_COUNT, successes.get());
            pass = false;
        }
        if (users != profiles) {
            WawelAuth.LOG.error("[Concurrency Test]   FAIL: orphan detected! users={} profiles={}", users, profiles);
            pass = false;
        }
        if (users != THREAD_COUNT) {
            WawelAuth.LOG.error("[Concurrency Test]   FAIL: expected {} user rows, got {}", THREAD_COUNT, users);
            pass = false;
        }

        // Verify every user has a matching profile
        for (WawelUser user : userDAO.listAll()) {
            WawelProfile profile = profileDAO.findByName(user.getUsername());
            if (profile == null) {
                WawelAuth.LOG.error("[Concurrency Test]   FAIL: user '{}' has no profile", user.getUsername());
                pass = false;
            } else if (!profile.getOwnerUuid()
                .equals(user.getUuid())) {
                    WawelAuth.LOG
                        .error("[Concurrency Test]   FAIL: profile owner mismatch for '{}'", user.getUsername());
                    pass = false;
                }
        }

        // Clean up
        for (WawelUser user : userDAO.listAll()) {
            WawelProfile profile = profileDAO.findByName(user.getUsername());
            if (profile != null) profileDAO.delete(profile.getUuid());
            userDAO.delete(user.getUuid());
        }

        if (pass) {
            WawelAuth.LOG.info("[Concurrency Test]   PASS");
        }
        return pass;
    }

    /**
     * Reproduces the exact CommandWawelAuth register flow:
     * check-then-act with the insert inside runInTransaction.
     */
    private static void simulateRegister(SqliteDatabase db, SqliteUserDAO userDAO, SqliteProfileDAO profileDAO,
        String username) {
        // Check outside transaction (mirrors CommandWawelAuth)
        if (userDAO.findByUsername(username) != null) {
            throw new RuntimeException("Username already taken");
        }
        if (profileDAO.findByName(username) != null) {
            throw new RuntimeException("Profile name already taken");
        }

        long now = System.currentTimeMillis();

        WawelUser user = new WawelUser();
        user.setUuid(UUID.randomUUID());
        user.setUsername(username);
        user.setPasswordHash("test-hash");
        user.setPasswordSalt("test-salt");
        user.setCreatedAt(now);

        WawelProfile profile = new WawelProfile();
        profile.setUuid(UUID.randomUUID());
        profile.setName(username);
        profile.setOwnerUuid(user.getUuid());
        profile.updateOfflineUuid();
        profile.setCreatedAt(now);

        db.runInTransaction(() -> {
            userDAO.create(user);
            profileDAO.create(profile);
        });
    }
}
