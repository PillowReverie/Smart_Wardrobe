package com.example.wardrobe.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Base64;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * 衣物图片存储工具。
 *
 * <p>数据库只保存图片文件名，不保存绝对路径。真实文件统一放在
 * {@code filesDir/images} 中，读取时通过 {@link #getImageFile(Context, String)}
 * 动态拼接当前应用的私有目录。照片在入库前会先按最长边 1600px 采样、
 * 旋转校正并压缩为 JPEG，避免原图过大导致 OOM。</p>
 */
public final class ImageStore {

    private static final String IMAGE_DIR = "images";
    private static final String CAMERA_DIR = "camera";
    private static final String CROP_DIR = "crop";
    private static final int MAX_DIMENSION = 1600;
    private static final int JPEG_QUALITY = 85;
    private static final int VISION_MAX_DIMENSION = 1024;
    private static final int VISION_JPEG_QUALITY = 80;

    private ImageStore() {
        // 工具类禁止实例化。
    }

    /**
     * 返回 App 私有图片目录，不存在时自动创建。
     */
    public static File getImageDir(Context context) {
        File dir = new File(context.getFilesDir(), IMAGE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 根据数据库中的文件名得到真实文件。会自动去掉路径部分，防止路径穿越。
     */
    public static File getImageFile(Context context, String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        String safeName = new File(fileName).getName();
        return new File(getImageDir(context), safeName);
    }

    /**
     * 创建相机临时输出文件，配合 FileProvider 使用。
     */
    public static File createCameraTempFile(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), CAMERA_DIR);
        ensureDirectory(dir);
        return File.createTempFile("camera_", ".jpg", dir);
    }

    /**
     * 为相册图片创建裁剪用源文件。先复制到私有缓存，确保 UCrop
     * 通过 FileProvider 读取时不会受相册 Uri 临时授权影响。
     */
    public static File copyUriToCropTempFile(Context context, Uri uri)
            throws IOException {
        if (uri == null) {
            throw new IOException("图片 Uri 为空");
        }
        File dir = new File(context.getCacheDir(), CROP_DIR);
        ensureDirectory(dir);
        File tempFile = File.createTempFile("crop_source_", ".jpg", dir);
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(tempFile)) {
                if (input == null) {
                    throw new IOException("无法读取相册图片");
                }
                copyStream(input, output);
            }
            return tempFile;
        } catch (IOException exception) {
            deleteQuietly(tempFile);
            throw exception;
        }
    }

    /**
     * 创建 UCrop 输出文件。只返回尚未创建的路径，交由 UCrop 写入，
     * 避免某些机型因目标文件已存在而保存失败。
     */
    public static File createCropDestinationFile(Context context)
            throws IOException {
        File dir = new File(context.getCacheDir(), CROP_DIR);
        ensureDirectory(dir);
        return new File(dir, "crop_result_"
                + UUID.randomUUID().toString().replace("-", "") + ".jpg");
    }

    /**
     * 相册 Uri 导入入口。先复制到缓存文件，再走统一的压缩流程。
     */
    public static String importFromUri(Context context, Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("图片 Uri 为空");
        }
        File tempDir = new File(context.getCacheDir(), CAMERA_DIR);
        ensureDirectory(tempDir);
        File tempFile = File.createTempFile("import_", ".jpg", tempDir);
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(tempFile)) {
                if (input == null) {
                    throw new IOException("无法读取相册图片");
                }
                copyStream(input, output);
            }
            return compressToPrivateStorage(context, tempFile);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    /**
     * 相机临时文件导入入口。
     */
    public static String importFromFile(Context context, File sourceFile) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IOException("相机照片文件不存在");
        }
        return compressToPrivateStorage(context, sourceFile);
    }

    /**
     * 删除数据库记录对应的私有图片。
     */
    public static void deleteImage(Context context, String fileName) {
        File file = getImageFile(context, fileName);
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    public static void deleteTempFile(File file) {
        deleteQuietly(file);
    }

    /**
     * 将当前衣物图片压缩成适合视觉模型的小尺寸 Base64 JPEG。
     * 限制最长边 1024px，避免几 MB 原图导致请求体过大或接口拒绝。
     */
    public static String toBase64Jpeg(Context context, String fileName)
            throws IOException {
        File sourceFile = getImageFile(context, fileName);
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IOException("图片文件不存在");
        }
        Bitmap bitmap = decodeBitmap(sourceFile);
        Bitmap scaled = scaleDown(bitmap, VISION_MAX_DIMENSION);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!scaled.compress(
                    Bitmap.CompressFormat.JPEG, VISION_JPEG_QUALITY, output)) {
                throw new IOException("图片编码失败");
            }
            return Base64.encodeToString(
                    output.toByteArray(), Base64.NO_WRAP);
        } finally {
            if (!scaled.isRecycled()) {
                scaled.recycle();
            }
        }
    }

    /**
     * 采样解码、旋转、缩放并压缩，最终写入 filesDir/images。
     */
    private static String compressToPrivateStorage(Context context, File sourceFile)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("无法解析图片尺寸");
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(
                bounds.outWidth, bounds.outHeight);
        Bitmap bitmap = BitmapFactory.decodeFile(
                sourceFile.getAbsolutePath(), decodeOptions);
        if (bitmap == null) {
            throw new IOException("图片解码失败");
        }

        Bitmap oriented = rotateByExif(bitmap, sourceFile);
        Bitmap scaled = scaleDown(oriented);

        try {
            return saveBitmapAsNewImage(context, scaled);
        } finally {
            if (!scaled.isRecycled()) {
                scaled.recycle();
            }
        }
    }

    /**
     * 解码图片并按 EXIF 修正方向。调用方负责回收返回的 Bitmap。
     */
    private static Bitmap decodeBitmap(File sourceFile) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("无法解析图片尺寸");
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(
                bounds.outWidth, bounds.outHeight);
        Bitmap bitmap = BitmapFactory.decodeFile(
                sourceFile.getAbsolutePath(), decodeOptions);
        if (bitmap == null) {
            throw new IOException("图片解码失败");
        }
        return rotateByExif(bitmap, sourceFile);
    }

    /**
     * 把 Bitmap 压缩成新的私有图片文件。调用方负责回收 bitmap。
     */
    private static String saveBitmapAsNewImage(Context context, Bitmap bitmap)
            throws IOException {
        File target = new File(getImageDir(context),
                UUID.randomUUID().toString().replace("-", "") + ".jpg");
        try {
            try (FileOutputStream output = new FileOutputStream(target)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG,
                        JPEG_QUALITY, output)) {
                    throw new IOException("图片压缩失败");
                }
            }
            return target.getName();
        } catch (IOException exception) {
            deleteQuietly(target);
            throw exception;
        }
    }

    /**
     * 按 2 的幂计算采样率，先大幅降低内存占用，再做精确尺寸缩放。
     */
    private static int calculateInSampleSize(int width, int height) {
        int sampleSize = 1;
        while (width / sampleSize > MAX_DIMENSION
                || height / sampleSize > MAX_DIMENSION) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    /**
     * 根据 EXIF 方向旋转照片，修正竖拍照片被横向保存的问题。
     */
    private static Bitmap rotateByExif(Bitmap bitmap, File sourceFile) {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            ExifInterface exif = new ExifInterface(sourceFile.getAbsolutePath());
            orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {
            // 部分图片没有 EXIF，按正常方向处理即可。
        }

        int rotation = 0;
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
            rotation = 90;
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
            rotation = 180;
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            rotation = 270;
        }
        if (rotation == 0) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    /**
     * 最长边超过 1600px 时等比缩放。
     */
    private static Bitmap scaleDown(Bitmap bitmap) {
        return scaleDown(bitmap, MAX_DIMENSION);
    }

    /**
     * 按指定最长边等比缩放。
     */
    private static Bitmap scaleDown(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longestSide = Math.max(width, height);
        if (longestSide <= maxDimension) {
            return bitmap;
        }

        float scale = (float) maxDimension / longestSide;
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(
                bitmap, targetWidth, targetHeight, true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }

    private static void ensureDirectory(File dir) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录：" + dir.getAbsolutePath());
        }
    }

    private static void copyStream(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[8 * 1024];
        int length;
        while ((length = input.read(buffer)) != -1) {
            output.write(buffer, 0, length);
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}
