package org.fentanylsolutions.wawelauth.client.gui;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.fentanylsolutions.wawelauth.WawelAuth;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Cross-platform OS open helpers.
 * <p>
 * Mirrors the fallback strategy used in Catalogue-Vintage:
 * 1) java.awt.Desktop
 * 2) lwjgl3ify Desktop redirect
 * 3) org.lwjglx.Sys.openURL
 * 4) org.lwjgl.Sys.openURL
 */
@SideOnly(Side.CLIENT)
public final class SystemOpenUtil {

    private SystemOpenUtil() {}

    public static final class FilePickerResult {

        public enum Status {
            SELECTED,
            CANCELLED,
            UNAVAILABLE,
            ERROR
        }

        private final Status status;
        private final File file;
        private final String message;

        private FilePickerResult(Status status, File file, String message) {
            this.status = status;
            this.file = file;
            this.message = message;
        }

        public static FilePickerResult selected(File file) {
            return new FilePickerResult(Status.SELECTED, file, null);
        }

        public static FilePickerResult cancelled() {
            return new FilePickerResult(Status.CANCELLED, null, null);
        }

        public static FilePickerResult unavailable(String message) {
            return new FilePickerResult(Status.UNAVAILABLE, null, message);
        }

        public static FilePickerResult error(String message) {
            return new FilePickerResult(Status.ERROR, null, message);
        }

        public Status getStatus() {
            return status;
        }

        public File getFile() {
            return file;
        }

        public String getMessage() {
            return message;
        }
    }

    public static boolean openFolder(File folder) {
        return FolderOpenUtil.openFolder(folder);
    }

    public static boolean openUri(URI uri) {
        if (uri == null) return false;

        if (openWithAwtDesktop(uri)) return true;
        if (openWithLwjgl3ifyDesktop(uri)) return true;
        if (openWithSys("org.lwjglx.Sys", uri.toString())) return true;
        return openWithSys("org.lwjgl.Sys", uri.toString());
    }

    public static FilePickerResult pickImageFile(String title, File initialDirectory) {
        if (isMacOs()) {
            FilePickerResult macResult = pickWithMacOsaScript(title, initialDirectory);
            if (macResult.getStatus() != FilePickerResult.Status.UNAVAILABLE) {
                return validateImageSelection(macResult);
            }
        }

        if (!isWindows()) {
            FilePickerResult tinyfdResult = pickWithTinyfd(title, initialDirectory);
            if (tinyfdResult.getStatus() != FilePickerResult.Status.UNAVAILABLE) {
                return validateImageSelection(tinyfdResult);
            }
        }

        FilePickerResult awtResult = pickWithAwt(title, initialDirectory);
        if (awtResult.getStatus() != FilePickerResult.Status.UNAVAILABLE) {
            return validateImageSelection(awtResult);
        }

        return FilePickerResult.unavailable(GuiText.tr("wawelauth.gui.file_picker.unavailable"));
    }

    public static File getDefaultFileSelectionDirectory() {
        String userHome = System.getProperty("user.home", "");
        if (userHome != null && !userHome.trim()
            .isEmpty()) {
            File home = new File(userHome);
            if (home.isDirectory()) {
                return home;
            }
        }
        return new File(".");
    }

    public static FilePickerResult pickWithAwt(String title, File initialDirectory) {
        try {
            FileDialog dialog = new FileDialog((Frame) null, safeTitle(title), FileDialog.LOAD);

            dialog.setDirectory(defaultPickerPath(initialDirectory));
            dialog.setFile("*.png;*.gif");
            dialog.setVisible(true);

            String directory = dialog.getDirectory();
            String file = dialog.getFile();

            if (file == null || file.isEmpty()) {
                return FilePickerResult.cancelled();
            }

            File selectedFile = new File(directory, file);
            return FilePickerResult.selected(selectedFile);
        } catch (Throwable t) {
            WawelAuth.LOG.error("Native file picker failed in AWT path", t);
            String message = t.getMessage() != null ? t.getMessage()
                : t.getClass()
                    .getSimpleName();
            return FilePickerResult.error(GuiText.tr("wawelauth.gui.common.failed_message", message));
        }
    }

    private static FilePickerResult pickWithTinyfd(String title, File initialDirectory) {
        try {
            Class<?> pointerBufferClass = Class.forName("org.lwjgl.PointerBuffer");
            Class<?> tinyDialogsClass = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Object imageFilters = createTinyfdImageFilters(pointerBufferClass);
            Object selectedPath = tinyDialogsClass
                .getMethod(
                    "tinyfd_openFileDialog",
                    CharSequence.class,
                    CharSequence.class,
                    pointerBufferClass,
                    CharSequence.class,
                    boolean.class)
                .invoke(
                    null,
                    safeTitle(title),
                    defaultPickerPath(initialDirectory),
                    imageFilters,
                    GuiText.tr("wawelauth.gui.file_picker.filter_label"),
                    false);

            if (selectedPath == null) {
                return FilePickerResult.cancelled();
            }
            String path = selectedPath.toString()
                .trim();
            if (path.isEmpty()) {
                return FilePickerResult.cancelled();
            }
            return FilePickerResult.selected(new File(path));
        } catch (ClassNotFoundException e) {
            return FilePickerResult.unavailable(GuiText.tr("wawelauth.gui.file_picker.tinyfd_missing"));
        } catch (Throwable t) {
            WawelAuth.LOG.error("Native file picker failed in tinyfd path", t);
            String message = t.getMessage() != null ? t.getMessage()
                : t.getClass()
                    .getSimpleName();
            return FilePickerResult.error(GuiText.tr("wawelauth.gui.common.failed_message", message));
        }
    }

    private static Object createTinyfdImageFilters(Class<?> pointerBufferClass) {
        try {
            Class<?> bufferUtilsClass = Class.forName("org.lwjgl.BufferUtils");
            Class<?> memoryUtilClass = Class.forName("org.lwjgl.system.MemoryUtil");
            Object filters = bufferUtilsClass.getMethod("createPointerBuffer", int.class)
                .invoke(null, 2);
            Object pngPattern = memoryUtilClass.getMethod("memUTF8", CharSequence.class)
                .invoke(null, "*.png");
            Object gifPattern = memoryUtilClass.getMethod("memUTF8", CharSequence.class)
                .invoke(null, "*.gif");
            pointerBufferClass.getMethod("put", java.nio.ByteBuffer.class)
                .invoke(filters, pngPattern);
            pointerBufferClass.getMethod("put", java.nio.ByteBuffer.class)
                .invoke(filters, gifPattern);
            pointerBufferClass.getMethod("flip")
                .invoke(filters);
            return filters;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static FilePickerResult pickWithMacOsaScript(String title, File initialDirectory) {
        if (!isMacOs()) {
            return FilePickerResult.unavailable(GuiText.tr("wawelauth.gui.file_picker.not_macos"));
        }

        String initialPath = initialDirectory != null ? initialDirectory.getAbsolutePath() : "";
        List<String> command = new ArrayList<>();
        command.add("osascript");
        command.add("-e");
        command.add("try");
        command.add("-e");
        if (!initialPath.isEmpty()) {
            command.add(
                "set _picked to choose file with prompt \"" + escapeAppleScript(safeTitle(title))
                    + "\" default location POSIX file \""
                    + escapeAppleScript(initialPath)
                    + "\" of type {\"png\", \"gif\"}");
        } else {
            command.add(
                "set _picked to choose file with prompt \"" + escapeAppleScript(safeTitle(title))
                    + "\" of type {\"png\", \"gif\"}");
        }
        command.add("-e");
        command.add("return POSIX path of _picked");
        command.add("-e");
        command.add("on error number -128");
        command.add("-e");
        command.add("return \"\"");
        command.add("-e");
        command.add("end try");

        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            String output = readAll(process.getInputStream());
            int code = process.waitFor();
            if (code != 0) {
                String err = readAll(process.getErrorStream());
                if (err != null && !err.trim()
                    .isEmpty()) {
                    return FilePickerResult.error(GuiText.tr("wawelauth.gui.common.failed_message", err.trim()));
                }
                return FilePickerResult.error(GuiText.tr("wawelauth.gui.file_picker.macos_failed"));
            }

            String path = output != null ? output.trim() : "";
            if (path.isEmpty()) {
                return FilePickerResult.cancelled();
            }
            return FilePickerResult.selected(new File(path));
        } catch (IOException e) {
            return FilePickerResult.unavailable(GuiText.tr("wawelauth.gui.file_picker.osascript_missing"));
        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            return FilePickerResult.error(GuiText.tr("wawelauth.gui.file_picker.interrupted"));
        } catch (Throwable t) {
            WawelAuth.LOG.error("Native file picker failed in macOS osascript path", t);
            String message = t.getMessage() != null ? t.getMessage()
                : t.getClass()
                    .getSimpleName();
            return FilePickerResult.error(GuiText.tr("wawelauth.gui.common.failed_message", message));
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static FilePickerResult validateImageSelection(FilePickerResult result) {
        if (result == null) {
            return FilePickerResult.error(GuiText.tr("wawelauth.gui.file_picker.unknown_error"));
        }

        if (result.getStatus() != FilePickerResult.Status.SELECTED) {
            return result;
        }

        File file = result.getFile();
        if (file == null) {
            return FilePickerResult.error(GuiText.tr("wawelauth.gui.file_picker.no_file"));
        }
        if (!file.isFile()) {
            return FilePickerResult.error(GuiText.tr("wawelauth.gui.file_picker.not_a_file"));
        }
        if (!file.canRead()) {
            return FilePickerResult.error(GuiText.tr("wawelauth.gui.file_picker.not_readable"));
        }
        if (!hasSupportedImageExtension(file.getName())) {
            return FilePickerResult.error(GuiText.tr("wawelauth.gui.account_manager.file_types_supported"));
        }
        return result;
    }

    private static boolean hasSupportedImageExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".gif");
    }

    private static String defaultPickerPath(File initialDirectory) {
        if (initialDirectory == null) {
            return "";
        }
        File target = initialDirectory;
        while (target != null && !target.exists()) {
            target = target.getParentFile();
        }
        if (target == null) {
            return "";
        }
        if (target.isFile()) {
            File parent = target.getParentFile();
            return parent != null ? parent.getAbsolutePath() : "";
        }
        return target.getAbsolutePath();
    }

    private static String safeTitle(String title) {
        return title != null && !title.trim()
            .isEmpty() ? title.trim() : GuiText.tr("wawelauth.gui.file_picker.select_file");
    }

    private static boolean isMacOs() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase()
            .contains("mac");
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase()
            .contains("win");
    }

    private static String escapeAppleScript(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"");
    }

    private static String readAll(java.io.InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.toString();
    }

    private static boolean openWithAwtDesktop(URI uri) {
        try {
            Class<?> desktopCls = Class.forName("java.awt.Desktop");
            Object desktop = desktopCls.getMethod("getDesktop")
                .invoke(null);
            desktopCls.getMethod("browse", URI.class)
                .invoke(desktop, uri);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean openWithLwjgl3ifyDesktop(URI uri) {
        try {
            Class<?> desktopCls = Class.forName("me.eigenraven.lwjgl3ify.redirects.Desktop");
            Object desktop = desktopCls.getMethod("getDesktop")
                .invoke(null);
            desktopCls.getMethod("browse", URI.class)
                .invoke(desktop, uri);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean openWithSys(String className, String url) {
        try {
            Class<?> sysCls = Class.forName(className);
            Object result = sysCls.getMethod("openURL", String.class)
                .invoke(null, url);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
