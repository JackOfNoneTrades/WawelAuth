package org.fentanylsolutions.wawelauth.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.sqlite.util.LibraryLoaderUtil;
import org.sqlite.util.OSInfo;

import com.falsepattern.deploader.DependencyLoaderImpl;
import com.falsepattern.deploader.Library;
import com.falsepattern.deploader.version.Version;

/** Called only after DepLoader's stub has made its programmatic API available. */
final class SqliteDependencies {

    private SqliteDependencies() {}

    static void load() {
        // Inspect resources, not classes: a failed class lookup can poison LaunchClassLoader's cache.
        if (SqliteDependencies.class.getResource("/org/sqlite/JDBC.class") == null) {
            download("without-natives");
        }
        NativeDependencies.load();
    }

    private static void download(String classifier) {
        Properties properties = new Properties();
        try (
            InputStream input = SqliteDependencies.class.getResourceAsStream("/META-INF/wawelauth-sqlite.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing Wawel Auth SQLite dependency version");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Wawel Auth SQLite dependency version", e);
        }
        String versionString = properties.getProperty("version");
        if (versionString == null || versionString.trim()
            .isEmpty() || versionString.contains("${")) {
            throw new IllegalStateException("Invalid Wawel Auth SQLite dependency version: " + versionString);
        }
        Version version = Version.parse(versionString);
        DependencyLoaderImpl.addMavenRepo("https://repo.maven.apache.org/maven2/");
        DependencyLoaderImpl.loadLibraries(
            Library.builder()
                .loadingModId("wawelauth")
                .groupId("org.xerial")
                .artifactId("sqlite-jdbc")
                .minVersion(version)
                .preferredVersion(version)
                .regularSuffix(classifier)
                .devSuffix(classifier)
                .build());
    }

    // SQLite types must not be resolved until the Java-only JDBC jar has been injected.
    private static class NativeDependencies {

        static void load() {
            String path = LibraryLoaderUtil.getNativeLibResourcePath();
            String name = LibraryLoaderUtil.getNativeLibName();
            if (LibraryLoaderUtil.hasNativeLib(path, name)) {
                return;
            }
            String os = OSInfo.getOSName();
            String classifier;
            switch (os) {
                case "Windows":
                    classifier = "natives-windows";
                    break;
                case "Mac":
                    classifier = "natives-mac";
                    break;
                case "Linux":
                case "Linux-Musl":
                    classifier = "natives-linux";
                    break;
                case "Linux-Android":
                    classifier = "natives-android";
                    break;
                case "FreeBSD":
                    classifier = "natives-freebsd";
                    break;
                default:
                    throw new IllegalStateException("No SQLite native artifact for platform " + os);
            }
            download(classifier);
            if (!LibraryLoaderUtil.hasNativeLib(path, name)) {
                throw new IllegalStateException("SQLite " + classifier + " does not contain " + path + "/" + name);
            }
        }
    }
}
