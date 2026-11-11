package com.example.wardrobe;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.signature.ObjectKey;
import com.example.wardrobe.db.WardrobeDao;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.ClothingRecognition;
import com.example.wardrobe.network.LLMService;
import com.example.wardrobe.network.dto.ChatResponse;
import com.example.wardrobe.util.ApiConfig;
import com.example.wardrobe.util.ClothingRecognitionParser;
import com.example.wardrobe.util.ImageStore;
import com.example.wardrobe.util.ItemDescriptionGenerator;
import com.example.wardrobe.util.NetworkUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;

/**
 * 添加/编辑衣物页。
 *
 * <p>图片流程：拍照或相册选择 -> 私有缓存 -> UCrop 自由比例裁剪 -> 压缩到
 * filesDir/images。页面还支持视觉大模型自动识别衣物信息。所有解码和压缩
 * 都在后台执行，主线程只负责 UI。</p>
 */
public class AddItemActivity extends BaseActivity {

    public static final String EXTRA_ITEM_ID = "extra_item_id";

    private static final String TAG = "AddItemActivity";
    private static final String STATE_PENDING_IMAGE = "state_pending_image";
    private static final String STATE_ITEM_SAVED = "state_item_saved";
    private static final String STATE_CROP_SOURCE = "state_crop_source";
    private static final String STATE_CROP_DESTINATION = "state_crop_destination";

    private final ExecutorService imageExecutor =
            Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.TakePicture(),
                    this::handleCameraResult);

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri != null) {
                            prepareGalleryCrop(uri);
                        }
                    });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            openCamera();
                        } else {
                            showToast(R.string.permission_camera_denied);
                        }
                    });

    private final ActivityResultLauncher<String> galleryPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!granted && !hasGalleryPermission()) {
                            // 系统照片选择器本身不依赖该权限，拒绝后仍可继续选择单张图片。
                            showToast(R.string.permission_gallery_denied);
                        }
                        openPhotoPicker();
                    });

    private View rootView;
    private MaterialToolbar toolbar;
    private TextInputLayout nameInput;
    private TextInputLayout categoryInput;
    private AutoCompleteTextView categoryDropdown;
    private TextInputLayout colorInput;
    private TextInputLayout noteInput;
    private ImageView photoPreview;
    private View photoPlaceholder;
    private View photoProgress;
    private TextView photoProgressText;
    private MaterialCardView photoCard;
    private MaterialButton aiRecognizeButton;
    private MaterialButton saveButton;

    private WardrobeDao wardrobeDao;
    private long editingItemId = -1L;
    private ClothingItem editingItem;
    private String originalImagePath;
    private String pendingImagePath;
    private boolean itemSaved;
    private boolean photoProcessing;
    private File cameraTempFile;
    private File cropSourceFile;
    private File cropDestinationFile;
    private Context appContext;

    private Call<ChatResponse> visionCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appContext = getApplicationContext();
        rootView = findViewById(R.id.root);
        wardrobeDao = new WardrobeDao(this);

        bindViews();
        setupToolbar();
        setupCategoryDropdown();
        setupActions();

        editingItemId = getIntent().getLongExtra(EXTRA_ITEM_ID, -1L);
        if (savedInstanceState != null) {
            pendingImagePath = savedInstanceState.getString(STATE_PENDING_IMAGE);
            itemSaved = savedInstanceState.getBoolean(STATE_ITEM_SAVED, false);
            String sourcePath = savedInstanceState.getString(STATE_CROP_SOURCE);
            String destinationPath =
                    savedInstanceState.getString(STATE_CROP_DESTINATION);
            cropSourceFile = sourcePath == null ? null : new File(sourcePath);
            cropDestinationFile = destinationPath == null
                    ? null : new File(destinationPath);
        }

        if (editingItemId > 0) {
            loadItemForEdit();
        }
        if (pendingImagePath != null) {
            displayImage(pendingImagePath);
        }
        updateImageActionState();
    }

    @Override
    protected int layoutRes() {
        return R.layout.activity_add_item;
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        nameInput = findViewById(R.id.nameInput);
        categoryInput = findViewById(R.id.categoryInput);
        categoryDropdown = findViewById(R.id.categoryDropdown);
        colorInput = findViewById(R.id.colorInput);
        noteInput = findViewById(R.id.noteInput);
        photoPreview = findViewById(R.id.photoPreview);
        photoPlaceholder = findViewById(R.id.photoPlaceholder);
        photoProgress = findViewById(R.id.photoProgress);
        photoProgressText = findViewById(R.id.photoProgressText);
        photoCard = findViewById(R.id.photoCard);
        aiRecognizeButton = findViewById(R.id.aiRecognizeButton);
        saveButton = findViewById(R.id.saveButton);
    }

    private void setupToolbar() {
        bindBackToolbar();
    }

    private void setupCategoryDropdown() {
        String[] categories =
                getResources().getStringArray(R.array.clothing_categories);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, R.layout.item_dropdown, categories);
        categoryDropdown.setAdapter(adapter);
        categoryDropdown.setOnItemClickListener((parent, view, position, id) ->
                categoryInput.setError(null));
    }

    private void setupActions() {
        photoCard.setOnClickListener(view -> showPhotoSourceDialog());
        aiRecognizeButton.setOnClickListener(view -> recognizeCurrentImage());
        saveButton.setOnClickListener(view -> saveItem());
    }

    /**
     * 拍照和相册入口合并到照片卡片中，点击卡片后从底部弹窗选择来源。
     */
    private void showPhotoSourceDialog() {
        if (photoProcessing) {
            return;
        }
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(
                R.layout.dialog_photo_source, null, false);
        content.findViewById(R.id.takePhotoOption).setOnClickListener(view -> {
            dialog.dismiss();
            requestCameraPermission();
        });
        content.findViewById(R.id.pickGalleryOption).setOnClickListener(view -> {
            dialog.dismiss();
            requestGalleryPermission();
        });
        dialog.setContentView(content);
        dialog.show();
    }

    private void loadItemForEdit() {
        editingItem = wardrobeDao.getItemById(editingItemId);
        if (editingItem == null) {
            showToast(R.string.item_not_found);
            finish();
            return;
        }

        toolbar.setTitle(R.string.edit_item_title);
        saveButton.setText(R.string.action_save_changes);
        setText(nameInput, editingItem.getName());
        categoryDropdown.setText(editingItem.getCategory(), false);
        setText(colorInput, editingItem.getColor());
        setText(noteInput, editingItem.getNote());

        originalImagePath = editingItem.getImagePath();
        if (pendingImagePath == null) {
            displayImage(originalImagePath);
        }
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        try {
            cameraTempFile = ImageStore.createCameraTempFile(this);
            Uri outputUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    cameraTempFile);
            takePictureLauncher.launch(outputUri);
        } catch (Exception exception) {
            ImageStore.deleteTempFile(cameraTempFile);
            cameraTempFile = null;
            showToast(R.string.camera_unavailable);
        }
    }

    private void handleCameraResult(boolean success) {
        if (!success) {
            ImageStore.deleteTempFile(cameraTempFile);
            cameraTempFile = null;
            showToast(R.string.photo_import_canceled);
            return;
        }
        if (cameraTempFile == null || !cameraTempFile.exists()) {
            showToast(R.string.photo_import_failed);
            return;
        }

        File sourceFile = cameraTempFile;
        cameraTempFile = null;
        startCrop(sourceFile);
    }

    private void requestGalleryPermission() {
        if (hasGalleryPermission()) {
            openPhotoPicker();
            return;
        }
        galleryPermissionLauncher.launch(getGalleryPermission());
    }

    private String getGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean fullAccess = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
            if (fullAccess) {
                return true;
            }
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openPhotoPicker() {
        PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                .setMediaType(
                        ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build();
        pickImageLauncher.launch(request);
    }

    /**
     * 相册图片先复制到私有缓存，再交给 UCrop，避免 Uri 授权过期。
     */
    private void prepareGalleryCrop(Uri uri) {
        if (photoProcessing) {
            return;
        }
        setPhotoProcessing(true, R.string.crop_preparing);
        imageExecutor.execute(() -> {
            File tempFile = null;
            Exception failure = null;
            try {
                tempFile = ImageStore.copyUriToCropTempFile(appContext, uri);
            } catch (Exception exception) {
                failure = exception;
            }
            File resultFile = tempFile;
            Exception resultFailure = failure;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    ImageStore.deleteTempFile(resultFile);
                    return;
                }
                setPhotoProcessing(false, R.string.photo_processing);
                if (resultFailure != null || resultFile == null) {
                    showToast(R.string.photo_import_failed);
                    return;
                }
                startCrop(resultFile);
            });
        });
    }

    /**
     * 拉起 UCrop。不预设宽高比，由用户自由框选，适配长裙、外套等不同版型。
     */
    private void startCrop(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            showToast(R.string.photo_import_failed);
            return;
        }
        try {
            cropSourceFile = sourceFile;
            cropDestinationFile =
                    ImageStore.createCropDestinationFile(this);
            String authority = getPackageName() + ".fileprovider";
            Uri sourceUri = FileProvider.getUriForFile(
                    this, authority, cropSourceFile);
            Uri destinationUri = FileProvider.getUriForFile(
                    this, authority, cropDestinationFile);

            UCrop.Options options = new UCrop.Options();
            options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
            options.setCompressionQuality(90);
            // 允许自由拖动裁剪框，不锁定宽高比。
            options.setFreeStyleCropEnabled(true);
            // 保留底部“裁剪 / 旋转”按钮，缩放只使用双指捏合。
            options.setHideBottomControls(false);
            options.setShowCropGrid(true);
            options.setToolbarTitle(getString(R.string.crop_title));

            // 工具栏和状态栏使用主题的 surface 色，浅色/深色模式都会自动适配。
            int barColor = MaterialColors.getColor(this,
                    com.google.android.material.R.attr.colorSurface, Color.WHITE);
            int barContentColor = MaterialColors.getColor(this,
                    com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
            int activeColor = MaterialColors.getColor(this,
                    com.google.android.material.R.attr.colorPrimary, Color.BLACK);
            options.setToolbarColor(barColor);
            options.setStatusBarColor(barColor);
            options.setToolbarWidgetColor(barContentColor);
            options.setActiveControlsWidgetColor(activeColor);

            Intent cropIntent = UCrop.of(sourceUri, destinationUri)
                    .withMaxResultSize(1200, 1600)
                    .withOptions(options)
                    .getIntent(this);
            cropIntent.setClass(this, CropActivity.class);
            startActivityForResult(cropIntent, UCrop.REQUEST_CROP);
        } catch (Exception exception) {
            Log.e(TAG, "启动裁剪失败", exception);
            cleanupCropFiles();
            showToast(R.string.crop_failed);
        }
    }

    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != UCrop.REQUEST_CROP) {
            return;
        }
        if (resultCode == RESULT_OK) {
            Uri outputUri = UCrop.getOutput(data);
            if (outputUri == null) {
                cleanupCropFiles();
                showToast(R.string.crop_failed);
                return;
            }
            importCroppedResult(outputUri);
            return;
        }
        if (resultCode == UCrop.RESULT_ERROR) {
            Throwable error = UCrop.getError(data);
            if (error != null) {
                Log.e(TAG, "裁剪失败", error);
            }
            cleanupCropFiles();
            showToast(R.string.crop_failed);
            return;
        }
        cleanupCropFiles();
        showToast(R.string.crop_canceled);
    }

    private void importCroppedResult(Uri outputUri) {
        runImageImport(() -> {
            try {
                return ImageStore.importFromUri(appContext, outputUri);
            } finally {
                cleanupCropFiles();
            }
        }, R.string.photo_processing);
    }

    /**
     * 所有图片解码和压缩都放到单线程执行器，避免阻塞主线程或并发 OOM。
     */
    private void runImageImport(ImageImportTask task, int progressTextResId) {
        if (photoProcessing) {
            return;
        }
        setPhotoProcessing(true, progressTextResId);

        imageExecutor.execute(() -> {
            String fileName = null;
            Exception failure = null;
            try {
                fileName = task.run();
            } catch (Exception exception) {
                failure = exception;
            }

            String resultName = fileName;
            Exception resultFailure = failure;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (resultName != null) {
                        ImageStore.deleteImage(appContext, resultName);
                    }
                    return;
                }

                setPhotoProcessing(false, R.string.photo_processing);
                if (resultFailure != null || resultName == null) {
                    showToast(R.string.photo_import_failed);
                    return;
                }
                onNewImageReady(resultName);
            });
        });
    }

    private void onNewImageReady(String fileName) {
        if (pendingImagePath != null
                && !pendingImagePath.equals(originalImagePath)) {
            ImageStore.deleteImage(this, pendingImagePath);
        }
        pendingImagePath = fileName;
        displayImage(fileName);
    }

    private void displayImage(String fileName) {
        File file = ImageStore.getImageFile(this, fileName);
        if (file != null && file.exists()) {
            Glide.with(this)
                    .load(file)
                    .signature(new ObjectKey(System.currentTimeMillis()))
                    .override(800, 800)
                    .centerCrop()
                    .placeholder(R.drawable.bg_item_image_placeholder)
                    .error(R.drawable.bg_item_image_placeholder)
                    .into(photoPreview);
            photoPreview.setVisibility(View.VISIBLE);
            photoPlaceholder.setVisibility(View.GONE);
        } else {
            Glide.with(this).clear(photoPreview);
            photoPreview.setVisibility(View.GONE);
            photoPlaceholder.setVisibility(View.VISIBLE);
        }
        updateImageActionState();
    }

    /**
     * 视觉识别：压缩当前预览图为 Base64，再调用通用大模型 Vision 接口。
     */
    private void recognizeCurrentImage() {
        if (photoProcessing) {
            return;
        }
        String imagePath = currentImagePath();
        if (imagePath == null) {
            showToast(R.string.vision_need_image);
            return;
        }
        if (!ApiConfig.isLlmConfigured(this)) {
            showToast(R.string.ai_need_llm_key);
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showToast(R.string.network_unavailable);
            return;
        }

        setPhotoProcessing(true, R.string.ai_recognizing);
        imageExecutor.execute(() -> {
            String base64 = null;
            Exception failure = null;
            try {
                base64 = ImageStore.toBase64Jpeg(appContext, imagePath);
            } catch (Exception exception) {
                failure = exception;
            }
            String result = base64;
            Exception resultFailure = failure;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (resultFailure != null || result == null) {
                    setPhotoProcessing(false, R.string.photo_processing);
                    showToast(R.string.ai_recognize_failed);
                    return;
                }
                startVisionRequest(result);
            });
        });
    }

    private void startVisionRequest(String base64Image) {
        visionCall = LLMService.getInstance().recognizeClothingImage(
                this, base64Image, new LLMService.Callback() {
                    @Override
                    public void onSuccess(String content) {
                        visionCall = null;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        setPhotoProcessing(false, R.string.photo_processing);
                        ClothingRecognition recognition =
                                ClothingRecognitionParser.parse(content);
                        if (recognition == null
                                || recognition.getName().isEmpty()) {
                            showToast(R.string.ai_recognize_invalid);
                            return;
                        }
                        setText(nameInput, recognition.getName());
                        if (!recognition.getCategory().isEmpty()
                                && isValidCategory(
                                recognition.getCategory())) {
                            categoryDropdown.setText(
                                    recognition.getCategory(), false);
                        }
                        setText(colorInput, recognition.getColor());
                        showToast(R.string.ai_recognize_success);
                    }

                    @Override
                    public void onError(String message) {
                        visionCall = null;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        setPhotoProcessing(false, R.string.photo_processing);
                        Toast.makeText(AddItemActivity.this,
                                message, Toast.LENGTH_SHORT).show();
                    }
                });
        if (visionCall == null && photoProcessing) {
            // 配置缺失等同步错误会直接回调 onError，这里只做兜底恢复。
            setPhotoProcessing(false, R.string.photo_processing);
        }
    }

    private void cleanupCropFiles() {
        ImageStore.deleteTempFile(cropSourceFile);
        ImageStore.deleteTempFile(cropDestinationFile);
        cropSourceFile = null;
        cropDestinationFile = null;
    }

    private String currentImagePath() {
        String path = pendingImagePath != null
                ? pendingImagePath : originalImagePath;
        return path == null || path.trim().isEmpty() ? null : path;
    }

    private void saveItem() {
        if (photoProcessing) {
            showToast(R.string.photo_processing);
            return;
        }

        clearErrors();
        String name = readText(nameInput);
        String category = categoryDropdown.getText().toString().trim();
        String color = readText(colorInput);
        String note = readText(noteInput);

        boolean valid = true;
        if (name.isEmpty()) {
            nameInput.setError(getString(R.string.validation_name_required));
            valid = false;
        }
        if (!isValidCategory(category)) {
            categoryInput.setError(
                    getString(R.string.validation_category_required));
            valid = false;
        }

        String imagePath = currentImagePath();
        if (imagePath == null) {
            showToast(R.string.validation_photo_required);
            valid = false;
        }
        if (!valid) {
            return;
        }

        boolean isEditing = editingItem != null;
        ClothingItem item = isEditing ? editingItem : new ClothingItem();
        item.setName(name);
        item.setCategory(category);
        item.setColor(color);
        item.setNote(note);
        // 保留数据库字段兼容性；是否适合天气交给 AI 判断。
        item.setMinTemp(ClothingItem.DEFAULT_MIN_TEMP);
        item.setMaxTemp(ClothingItem.DEFAULT_MAX_TEMP);
        item.setImagePath(imagePath);

        try {
            if (isEditing) {
                int rows = wardrobeDao.updateItem(item);
                if (rows > 0) {
                    ItemDescriptionGenerator.generate(this, item.getId());
                    onSaveSuccess(true);
                } else {
                    showToast(R.string.save_failed);
                }
            } else {
                long itemId = wardrobeDao.insertItem(item);
                if (itemId > 0) {
                    ItemDescriptionGenerator.generate(this, itemId);
                    onSaveSuccess(false);
                } else {
                    showToast(R.string.save_failed);
                }
            }
        } catch (Exception exception) {
            showToast(R.string.save_failed);
        }
    }

    private void onSaveSuccess(boolean updated) {
        itemSaved = true;
        if (updated && pendingImagePath != null
                && !pendingImagePath.equals(originalImagePath)) {
            ImageStore.deleteImage(this, originalImagePath);
        }
        showToast(updated ? R.string.item_updated : R.string.item_saved);
        setResult(RESULT_OK);
        finish();
    }

    private boolean isValidCategory(String category) {
        String[] categories =
                getResources().getStringArray(R.array.clothing_categories);
        for (String candidate : categories) {
            if (candidate.equals(category)) {
                return true;
            }
        }
        return false;
    }

    private void setPhotoProcessing(boolean processing, int textResId) {
        photoProcessing = processing;
        photoProgress.setVisibility(processing ? View.VISIBLE : View.GONE);
        photoProgressText.setText(textResId);
        photoCard.setEnabled(!processing);
        saveButton.setEnabled(!processing);
        updateImageActionState();
    }

    private void updateImageActionState() {
        boolean enabled = !photoProcessing && currentImagePath() != null;
        aiRecognizeButton.setEnabled(enabled);
    }

    private String readText(TextInputLayout layout) {
        TextInputEditText editText =
                (TextInputEditText) layout.getEditText();
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private void setText(TextInputLayout layout, String value) {
        TextInputEditText editText =
                (TextInputEditText) layout.getEditText();
        if (editText != null) {
            editText.setText(value == null ? "" : value);
            editText.setSelection(editText.length());
        }
    }

    private void clearErrors() {
        nameInput.setError(null);
        categoryInput.setError(null);
    }

    private void showToast(int stringResId) {
        Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_IMAGE, pendingImagePath);
        outState.putBoolean(STATE_ITEM_SAVED, itemSaved);
        outState.putString(STATE_CROP_SOURCE,
                cropSourceFile == null ? null : cropSourceFile.getAbsolutePath());
        outState.putString(STATE_CROP_DESTINATION,
                cropDestinationFile == null
                        ? null : cropDestinationFile.getAbsolutePath());
    }

    @Override
    protected void onDestroy() {
        if (visionCall != null) {
            visionCall.cancel();
            visionCall = null;
        }

        // 用户取消添加/编辑时删除新生成但尚未入库的图片，避免产生孤儿文件。
        if (isFinishing() && !itemSaved
                && pendingImagePath != null
                && !pendingImagePath.equals(originalImagePath)) {
            ImageStore.deleteImage(this, pendingImagePath);
        }
        if (isFinishing()) {
            ImageStore.deleteTempFile(cameraTempFile);
            cleanupCropFiles();
        }
        imageExecutor.shutdownNow();
        super.onDestroy();
    }

    private interface ImageImportTask {
        String run() throws Exception;
    }
}
