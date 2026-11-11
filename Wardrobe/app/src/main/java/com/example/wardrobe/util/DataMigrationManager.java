package com.example.wardrobe.util;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.bumptech.glide.Glide;
import com.example.wardrobe.db.WardrobeContract;
import com.example.wardrobe.db.WardrobeDbHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 衣橱数据迁移工具。
 *
 * <p>迁移包是一个普通 ZIP 文件，内部包含：</p>
 * <pre>
 * manifest.json
 * database/wardrobe.db
 * images/&lt;衣物图片文件名&gt;
 * </pre>
 *
 * <p>导出前会关闭 SQLite 连接并执行 WAL checkpoint，保证数据库文件完整。
 * 导入时先在缓存目录完成解压、路径安全检查和 SQLite 完整性校验，确认无误
 * 后才替换正式数据库与图片目录。替换失败会自动回滚当前数据。</p>
 */
public final class DataMigrationManager {

    public static final int FORMAT_VERSION = 1;

    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String DATABASE_ENTRY =
            "database/" + WardrobeContract.DATABASE_NAME;
    private static final String IMAGE_PREFIX = "images/";
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024;

    private DataMigrationManager() {
        // 工具类禁止实例化。
    }

    /**
     * 迁移结果，用于向界面反馈实际备份或恢复的数据量。
     */
    public static final class MigrationResult {
        public final int itemCount;
        public final int outfitCount;
        public final int imageCount;

        MigrationResult(int itemCount, int outfitCount, int imageCount) {
            this.itemCount = itemCount;
            this.outfitCount = outfitCount;
            this.imageCount = imageCount;
        }
    }

    /**
     * 导出衣橱数据库和全部衣物图片到用户选择的目标文件。
     *
     * <p>本方法会执行磁盘 I/O，调用方必须放在后台线程。</p>
     */
    public static MigrationResult exportData(Context context, Uri destination)
            throws IOException {
        Context appContext = context.getApplicationContext();
        File databaseFile = appContext.getDatabasePath(
                WardrobeContract.DATABASE_NAME);

        // 先打开数据库完成计数，再 checkpoint 并关闭，避免导出半写状态。
        SQLiteDatabase database =
                WardrobeDbHelper.getInstance(appContext).getWritableDatabase();
        int itemCount;
        int outfitCount;
        int databaseVersion = database.getVersion();
        try {
            itemCount = (int) DatabaseUtils.queryNumEntries(
                    database, WardrobeContract.TABLE_ITEMS);
            outfitCount = (int) DatabaseUtils.queryNumEntries(
                    database, WardrobeContract.TABLE_OUTFITS);
        } finally {
            closeDatabaseForFileSwap(appContext);
        }

        if (!databaseFile.isFile() || databaseFile.length() <= 0) {
            throw new IOException("本地数据库文件不存在");
        }

        File imageDirectory = ImageStore.getImageDir(appContext);
        File[] imageFiles = imageDirectory.listFiles(File::isFile);
        if (imageFiles == null) {
            imageFiles = new File[0];
        }

        JsonObject manifest = new JsonObject();
        manifest.addProperty("app", "SmartWardrobe");
        manifest.addProperty("format_version", FORMAT_VERSION);
        manifest.addProperty("database_name", WardrobeContract.DATABASE_NAME);
        manifest.addProperty("database_version", databaseVersion);
        manifest.addProperty("item_count", itemCount);
        manifest.addProperty("outfit_count", outfitCount);
        manifest.addProperty("image_count", imageFiles.length);
        manifest.addProperty("exported_at", new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(new Date()));

        OutputStream rawOutput = appContext.getContentResolver()
                .openOutputStream(destination, "w");
        if (rawOutput == null) {
            throw new IOException("无法创建导出文件");
        }

        try (OutputStream output = rawOutput;
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.setLevel(Deflater.BEST_SPEED);

            putTextEntry(zip, MANIFEST_ENTRY, manifest.toString());
            putFileEntry(zip, DATABASE_ENTRY, databaseFile);

            for (File imageFile : imageFiles) {
                String safeName = safeImageName(imageFile.getName());
                if (safeName == null) {
                    continue;
                }
                putFileEntry(zip, IMAGE_PREFIX + safeName, imageFile);
            }
            zip.finish();
            zip.flush();
        }

        return new MigrationResult(itemCount, outfitCount, imageFiles.length);
    }

    /**
     * 从用户选择的迁移包恢复衣橱数据库和图片。
     *
     * <p>本方法会执行磁盘 I/O，调用方必须放在后台线程。校验通过前不会
     * 修改正式数据；真正替换时如果发生异常，会回滚到导入前的数据库和图片。</p>
     */
    public static MigrationResult importData(Context context, Uri source)
            throws IOException {
        Context appContext = context.getApplicationContext();
        File workDirectory = createTempDirectory(
                appContext.getCacheDir(), "wardrobe_import_");
        try {
            File archiveFile = new File(workDirectory, "backup.zip");
            copyUriToFile(appContext, source, archiveFile);
            ImportedPackage imported = unpackAndValidate(
                    workDirectory, archiveFile);

            MigrationResult result = replaceCurrentData(appContext, imported);

            // 旧 AI 结果可能引用导入前不存在的衣物名称，必须清理。
            AiOutfitCache.clearAll(appContext);
            try {
                // 图片文件名可能在不同设备重复，清掉 Glide 磁盘缓存避免串图。
                Glide.get(appContext).clearDiskCache();
            } catch (RuntimeException ignored) {
                // 图片缓存清理失败不影响数据库恢复，后续加载会自动重建。
            }
            return result;
        } finally {
            deleteRecursively(workDirectory);
            WardrobeDbHelper.closeForMaintenance();
        }
    }

    /**
     * 解压迁移包到缓存目录，并验证清单、ZIP 路径和 SQLite 文件。
     */
    private static ImportedPackage unpackAndValidate(
            File workDirectory, File archiveFile) throws IOException {
        File extractDirectory = new File(workDirectory, "extracted");
        ensureDirectory(extractDirectory);
        File imageDirectory = new File(extractDirectory, "images");
        ensureDirectory(imageDirectory);

        File databaseFile = new File(extractDirectory, DATABASE_ENTRY);
        String manifestText = null;
        long declaredUncompressedBytes = 0L;
        long[] extractedBytes = new long[]{0L};

        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                long entrySize = entry.getSize();
                if (entrySize > 0) {
                    declaredUncompressedBytes += entrySize;
                    if (declaredUncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
                        throw new IOException("迁移包解压后体积过大");
                    }
                }

                String entryName = normalizeZipEntryName(entry.getName());
                if (MANIFEST_ENTRY.equals(entryName)) {
                    manifestText = readTextEntry(
                            zipFile, entry, MAX_MANIFEST_BYTES);
                    extractedBytes[0] += manifestText
                            .getBytes(StandardCharsets.UTF_8).length;
                } else if (DATABASE_ENTRY.equals(entryName)) {
                    extractEntry(
                            zipFile, entry, databaseFile, extractedBytes);
                } else if (entryName.startsWith(IMAGE_PREFIX)) {
                    String imageName = entryName.substring(
                            IMAGE_PREFIX.length());
                    String safeName = safeImageName(imageName);
                    if (safeName == null) {
                        throw new IOException("迁移包包含非法图片文件名");
                    }
                    extractEntry(
                            zipFile,
                            entry,
                            new File(imageDirectory, safeName),
                            extractedBytes);
                }
                // 未知条目直接忽略，便于后续格式增加非关键元数据。
            }
        }

        if (manifestText == null) {
            throw new IOException("迁移包缺少 manifest.json");
        }
        if (!databaseFile.isFile() || databaseFile.length() <= 0) {
            throw new IOException("迁移包缺少衣橱数据库");
        }

        validateManifest(manifestText);
        DatabaseStats stats = validateDatabase(databaseFile);
        int imageCount = countFiles(imageDirectory);
        return new ImportedPackage(
                databaseFile, imageDirectory, stats, imageCount);
    }

    /**
     * 清单只接受当前或更早格式，并拒绝来自未来版本、无法安全降级的备份。
     */
    private static void validateManifest(String manifestText)
            throws IOException {
        try {
            JsonObject manifest = JsonParser.parseString(manifestText)
                    .getAsJsonObject();
            String app = manifest.has("app")
                    ? manifest.get("app").getAsString() : "";
            int formatVersion = manifest.has("format_version")
                    ? manifest.get("format_version").getAsInt() : -1;
            if (!"SmartWardrobe".equals(app)) {
                throw new IOException("迁移包来源不受支持");
            }
            if (formatVersion < 1 || formatVersion > FORMAT_VERSION) {
                throw new IOException("迁移包版本与当前应用不兼容");
            }
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("manifest.json 内容无效", exception);
        }
    }

    /**
     * 校验 SQLite 文件完整性、表结构和数据库版本。
     */
    private static DatabaseStats validateDatabase(File databaseFile)
            throws IOException {
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    databaseFile.getAbsolutePath(),
                    null,
                    SQLiteDatabase.OPEN_READONLY);

            int databaseVersion = database.getVersion();
            if (databaseVersion < 1
                    || databaseVersion > WardrobeContract.DATABASE_VERSION) {
                throw new IOException("备份数据库版本与当前应用不兼容");
            }

            String schemaSql = "SELECT COUNT(*) FROM sqlite_master "
                    + "WHERE type = 'table' AND name IN (?, ?, ?)";
            try (Cursor cursor = database.rawQuery(schemaSql, new String[]{
                    WardrobeContract.TABLE_ITEMS,
                    WardrobeContract.TABLE_OUTFITS,
                    WardrobeContract.TABLE_OUTFIT_ITEMS
            })) {
                if (!cursor.moveToFirst() || cursor.getInt(0) != 3) {
                    throw new IOException("备份数据库缺少必要数据表");
                }
            }

            try (Cursor cursor = database.rawQuery(
                    "PRAGMA integrity_check", null)) {
                if (!cursor.moveToFirst()
                        || !"ok".equalsIgnoreCase(cursor.getString(0))) {
                    throw new IOException("备份数据库完整性校验失败");
                }
            }

            int itemCount = (int) DatabaseUtils.queryNumEntries(
                    database, WardrobeContract.TABLE_ITEMS);
            int outfitCount = (int) DatabaseUtils.queryNumEntries(
                    database, WardrobeContract.TABLE_OUTFITS);
            return new DatabaseStats(itemCount, outfitCount);
        } catch (android.database.sqlite.SQLiteException exception) {
            throw new IOException("所选文件不是有效的 SQLite 数据库", exception);
        } finally {
            if (database != null) {
                database.close();
            }
        }
    }

    /**
     * 原子替换数据库和图片目录；失败时恢复导入前的数据。
     */
    private static MigrationResult replaceCurrentData(
            Context context, ImportedPackage imported) throws IOException {
        closeDatabaseForFileSwap(context);

        File databaseFile = context.getDatabasePath(
                WardrobeContract.DATABASE_NAME);
        File databaseDirectory = databaseFile.getParentFile();
        if (databaseDirectory == null) {
            throw new IOException("无法定位应用数据库目录");
        }
        ensureDirectory(databaseDirectory);

        File imageDirectory = ImageStore.getImageDir(context);
        File rollbackDirectory = createTempDirectory(
                context.getCacheDir(), "wardrobe_rollback_");
        File rollbackDatabase = new File(
                rollbackDirectory, WardrobeContract.DATABASE_NAME);
        File rollbackWal = new File(
                rollbackDirectory, WardrobeContract.DATABASE_NAME + "-wal");
        File rollbackShm = new File(
                rollbackDirectory, WardrobeContract.DATABASE_NAME + "-shm");
        File rollbackJournal = new File(
                rollbackDirectory, WardrobeContract.DATABASE_NAME + "-journal");
        File rollbackImages = new File(rollbackDirectory, "images");

        boolean databaseMoved = false;
        boolean walMoved = false;
        boolean shmMoved = false;
        boolean journalMoved = false;
        boolean imagesMoved = false;
        boolean committed = false;
        boolean restoreComplete = true;

        try {
            databaseMoved = moveIfExists(databaseFile, rollbackDatabase);
            walMoved = moveIfExists(
                    new File(databaseFile.getPath() + "-wal"), rollbackWal);
            shmMoved = moveIfExists(
                    new File(databaseFile.getPath() + "-shm"), rollbackShm);
            journalMoved = moveIfExists(
                    new File(databaseFile.getPath() + "-journal"),
                    rollbackJournal);
            imagesMoved = moveDirectoryIfExists(
                    imageDirectory, rollbackImages);

            copyFile(imported.databaseFile, databaseFile);
            ensureDirectory(imageDirectory);
            copyDirectoryContents(imported.imageDirectory, imageDirectory);
            committed = true;
            return new MigrationResult(
                    imported.databaseStats.itemCount,
                    imported.databaseStats.outfitCount,
                    imported.imageCount);
        } catch (Exception exception) {
            // 清理写入一半的新数据，再把旧数据库和旧图片移回原位。
            if (databaseMoved) {
                deleteQuietly(databaseFile);
            }
            if (walMoved) {
                deleteQuietly(new File(databaseFile.getPath() + "-wal"));
            }
            if (shmMoved) {
                deleteQuietly(new File(databaseFile.getPath() + "-shm"));
            }
            if (journalMoved) {
                deleteQuietly(new File(databaseFile.getPath() + "-journal"));
            }
            if (imagesMoved) {
                deleteDirectoryContents(imageDirectory);
            }

            try {
                if (databaseMoved) {
                    moveIfExists(rollbackDatabase, databaseFile);
                }
                if (walMoved) {
                    moveIfExists(
                            rollbackWal,
                            new File(databaseFile.getPath() + "-wal"));
                }
                if (shmMoved) {
                    moveIfExists(
                            rollbackShm,
                            new File(databaseFile.getPath() + "-shm"));
                }
                if (journalMoved) {
                    moveIfExists(
                            rollbackJournal,
                            new File(databaseFile.getPath() + "-journal"));
                }
                if (imagesMoved) {
                    deleteRecursively(imageDirectory);
                    moveDirectoryIfExists(rollbackImages, imageDirectory);
                }
            } catch (IOException restoreException) {
                // 回滚目录必须保留，避免原数据只剩一份却因清理再次丢失。
                restoreComplete = false;
                exception.addSuppressed(restoreException);
            }

            if (exception instanceof IOException) {
                throw (IOException) exception;
            }
            throw new IOException("替换本地数据失败", exception);
        } finally {
            if (committed || restoreComplete) {
                deleteRecursively(rollbackDirectory);
            }
        }
    }

    /**
     * 关闭全局 SQLiteOpenHelper，并尽量把 WAL 写回主数据库文件。
     */
    private static void closeDatabaseForFileSwap(Context context) {
        try {
            SQLiteDatabase database = WardrobeDbHelper
                    .getInstance(context)
                    .getWritableDatabase();
            try (Cursor cursor = database.rawQuery(
                    "PRAGMA wal_checkpoint(FULL)", null)) {
                cursor.moveToFirst();
            }
        } catch (RuntimeException ignored) {
            // 数据库未创建或已关闭时仍继续执行 closeForMaintenance。
        } finally {
            WardrobeDbHelper.closeForMaintenance();
        }
    }

    private static void putTextEntry(
            ZipOutputStream zip, String entryName, String text) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void putFileEntry(
            ZipOutputStream zip, String entryName, File source) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        try (InputStream input = new FileInputStream(source)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = input.read(buffer)) != -1) {
                zip.write(buffer, 0, length);
            }
        }
        zip.closeEntry();
    }

    private static String readTextEntry(
            ZipFile zipFile, ZipEntry entry, int maxBytes) throws IOException {
        if (entry.getSize() > maxBytes) {
            throw new IOException("manifest.json 体积异常");
        }
        try (InputStream input = zipFile.getInputStream(entry)) {
            byte[] data = readLimited(input, maxBytes);
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private static void extractEntry(
            ZipFile zipFile,
            ZipEntry entry,
            File target,
            long[] extractedBytes) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        long remaining = MAX_UNCOMPRESSED_BYTES - extractedBytes[0];
        if (remaining <= 0L) {
            throw new IOException("迁移包解压后体积过大");
        }
        try (InputStream input = zipFile.getInputStream(entry);
             OutputStream output = new FileOutputStream(target)) {
            extractedBytes[0] += copyWithLimit(input, output, remaining);
        }
    }

    private static byte[] readLimited(InputStream input, int maxBytes)
            throws IOException {
        java.io.ByteArrayOutputStream output =
                new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int total = 0;
        int length;
        while ((length = input.read(buffer)) != -1) {
            total += length;
            if (total > maxBytes) {
                throw new IOException("文件内容超过允许大小");
            }
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    private static void copyUriToFile(
            Context context, Uri source, File destination) throws IOException {
        InputStream input = context.getContentResolver().openInputStream(source);
        if (input == null) {
            throw new IOException("无法读取所选迁移包");
        }
        try (InputStream sourceInput = input;
             OutputStream output = new FileOutputStream(destination)) {
            copyWithLimit(sourceInput, output, MAX_ARCHIVE_BYTES);
        }
    }

    private static long copyWithLimit(
            InputStream input, OutputStream output, long maxBytes)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0L;
        int length;
        while ((length = input.read(buffer)) != -1) {
            total += length;
            if (total > maxBytes) {
                throw new IOException("文件超过允许大小");
            }
            output.write(buffer, 0, length);
        }
        return total;
    }

    private static void copyFile(File source, File destination)
            throws IOException {
        File parent = destination.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
        }
    }

    private static void copyDirectoryContents(
            File sourceDirectory, File targetDirectory) throws IOException {
        File[] files = sourceDirectory.listFiles(File::isFile);
        if (files == null) {
            return;
        }
        for (File source : files) {
            String safeName = safeImageName(source.getName());
            if (safeName == null) {
                continue;
            }
            copyFile(source, new File(targetDirectory, safeName));
        }
    }

    private static boolean moveIfExists(File source, File destination)
            throws IOException {
        if (!source.exists()) {
            return false;
        }
        File parent = destination.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        deleteQuietly(destination);
        if (source.renameTo(destination)) {
            return true;
        }
        copyFile(source, destination);
        if (!source.delete()) {
            throw new IOException("无法移动旧数据文件");
        }
        return true;
    }

    private static boolean moveDirectoryIfExists(
            File source, File destination) throws IOException {
        if (!source.exists()) {
            return false;
        }
        deleteRecursively(destination);
        if (source.renameTo(destination)) {
            return true;
        }

        ensureDirectory(destination);
        copyDirectoryContents(source, destination);
        deleteRecursively(source);
        return true;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory == null) {
            throw new IOException("目录路径无效");
        }
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("无法创建目录：" + directory.getAbsolutePath());
        }
    }

    private static File createTempDirectory(File parent, String prefix)
            throws IOException {
        ensureDirectory(parent);
        File directory = File.createTempFile(prefix, "", parent);
        if (!directory.delete() || !directory.mkdirs()) {
            throw new IOException("无法创建临时目录");
        }
        return directory;
    }

    private static void deleteDirectoryContents(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            deleteRecursively(file);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file != null && file.exists() && !file.delete()) {
            throw new IOException("无法删除旧文件：" + file.getName());
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private static int countFiles(File directory) {
        File[] files = directory.listFiles(File::isFile);
        return files == null ? 0 : files.length;
    }

    /**
     * ZIP 合法路径不能以斜杠开头，也不能包含反斜杠和 .. 路径段。
     */
    private static String normalizeZipEntryName(String entryName)
            throws IOException {
        if (entryName == null || entryName.isEmpty()
                || entryName.startsWith("/")
                || entryName.startsWith("\\")
                || entryName.contains("\\")) {
            throw new IOException("迁移包包含非法路径");
        }
        String[] parts = entryName.split("/");
        for (String part : parts) {
            if ("..".equals(part) || ".".equals(part)) {
                throw new IOException("迁移包包含非法路径");
            }
        }
        return entryName;
    }

    /**
     * 只允许 App 自己生成的简单文件名，避免路径穿越和特殊字符问题。
     */
    private static String safeImageName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()
                || trimmed.length() > 180
                || ".".equals(trimmed)
                || "..".equals(trimmed)
                || !trimmed.matches("[A-Za-z0-9._-]+")) {
            return null;
        }
        return trimmed;
    }

    private static final class DatabaseStats {
        final int itemCount;
        final int outfitCount;

        DatabaseStats(int itemCount, int outfitCount) {
            this.itemCount = itemCount;
            this.outfitCount = outfitCount;
        }
    }

    private static final class ImportedPackage {
        final File databaseFile;
        final File imageDirectory;
        final DatabaseStats databaseStats;
        final int imageCount;

        ImportedPackage(File databaseFile,
                        File imageDirectory,
                        DatabaseStats databaseStats,
                        int imageCount) {
            this.databaseFile = databaseFile;
            this.imageDirectory = imageDirectory;
            this.databaseStats = databaseStats;
            this.imageCount = imageCount;
        }
    }
}
