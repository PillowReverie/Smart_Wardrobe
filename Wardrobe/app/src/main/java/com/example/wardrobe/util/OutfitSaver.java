package com.example.wardrobe.util;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.example.wardrobe.R;
import com.example.wardrobe.db.WardrobeDao;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;
import com.example.wardrobe.model.WeatherInfo;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 搭配保存工具。
 *
 * <p>首页和详情页共用同一个保存弹窗。用户确认名称后，把 AI 文本、来源、
 * 天气快照、温度、时间戳写入 outfits 表，并通过 outfit_items 关联本次
 * Prompt 实际使用的衣物。</p>
 */
public final class OutfitSaver {

    public interface SaveResultListener {
        /**
         * @param saved true 表示已保存或数据库中已存在相同搭配
         */
        void onSaveResult(boolean saved);
    }

    private OutfitSaver() {
        // 工具类禁止实例化。
    }

    public static void showSaveDialog(Activity activity,
                                      String defaultName,
                                      String content,
                                      String source,
                                      WeatherInfo weather,
                                      List<ClothingItem> usedItems) {
        showSaveDialog(activity, defaultName, content, source,
                weather, usedItems, null);
    }

    public static void showSaveDialog(Activity activity,
                                      String defaultName,
                                      String content,
                                      String source,
                                      WeatherInfo weather,
                                      List<ClothingItem> usedItems,
                                      SaveResultListener listener) {
        if (activity == null || TextUtils.isEmpty(content)) {
            return;
        }

        View dialogView = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_save_outfit, null, false);
        TextInputEditText nameEdit = dialogView.findViewById(
                R.id.outfitNameEditText);
        nameEdit.setText(defaultName);
        nameEdit.setSelection(nameEdit.length());

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.save_outfit_title)
                .setView(dialogView)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String title = nameEdit.getText() == null
                            ? "" : nameEdit.getText().toString().trim();
                    if (title.isEmpty()) {
                        title = defaultName;
                    }
                    saveOutfit(activity, title, content, source,
                            weather, usedItems, listener);
                })
                .show();
    }

    /**
     * 写入 outfits 与 outfit_items。insertOutfit 内部使用事务保证一致性。
     */
    private static void saveOutfit(Activity activity,
                                   String title,
                                   String content,
                                   String source,
                                   WeatherInfo weather,
                                   List<ClothingItem> usedItems,
                                   SaveResultListener listener) {
        String cleanedContent = AiTextFormatter.clean(content);
        Outfit outfit = new Outfit();
        outfit.setTitle(title);
        outfit.setContent(cleanedContent);
        outfit.setSource(source);
        outfit.setWeatherText(weather == null
                ? "" : weather.getConditionText());
        outfit.setTemperature(weather == null
                ? null : weather.getTemperature());
        outfit.setCreatedAt(System.currentTimeMillis());

        List<Long> itemIds = new ArrayList<>();
        if (usedItems != null) {
            for (ClothingItem item : usedItems) {
                if (item != null && item.getId() > 0) {
                    itemIds.add(item.getId());
                }
            }
        }

        WardrobeDao dao = new WardrobeDao(activity);
        if (isDuplicate(dao, cleanedContent, itemIds)) {
            Toast.makeText(activity, R.string.outfit_duplicate,
                    Toast.LENGTH_SHORT).show();
            if (listener != null) {
                listener.onSaveResult(true);
            }
            return;
        }

        try {
            long id = dao.insertOutfit(outfit, itemIds);
            boolean saved = id > 0;
            UiUtils.showMessage(activity.findViewById(android.R.id.content),
                    activity.getString(saved
                            ? R.string.outfit_saved
                            : R.string.outfit_save_failed));
            if (listener != null) {
                listener.onSaveResult(saved);
            }
        } catch (Exception exception) {
            Toast.makeText(activity, R.string.outfit_save_failed,
                    Toast.LENGTH_SHORT).show();
            if (listener != null) {
                listener.onSaveResult(false);
            }
        }
    }

    /**
     * 同一套方案可能被重复点击保存。正文相同且关联衣物完全一致时，
     * 视为同一条搭配，不再重复写入数据库。
     */
    private static boolean isDuplicate(WardrobeDao dao,
                                       String content,
                                       List<Long> itemIds) {
        String target = normalize(content);
        if (target.isEmpty()) {
            return false;
        }
        for (Outfit existing : dao.getAllOutfitsWithItems()) {
            if (!target.equals(normalize(existing.getContent()))) {
                continue;
            }
            if (sameItemIds(existing.getLinkedItems(), itemIds)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private static boolean sameItemIds(List<ClothingItem> linkedItems,
                                       List<Long> itemIds) {
        Set<Long> existingIds = new TreeSet<>();
        if (linkedItems != null) {
            for (ClothingItem item : linkedItems) {
                if (item != null && item.getId() > 0) {
                    existingIds.add(item.getId());
                }
            }
        }
        Set<Long> newIds = new TreeSet<>();
        if (itemIds != null) {
            for (Long id : itemIds) {
                if (id != null && id > 0) {
                    newIds.add(id);
                }
            }
        }
        return existingIds.equals(newIds);
    }
}
