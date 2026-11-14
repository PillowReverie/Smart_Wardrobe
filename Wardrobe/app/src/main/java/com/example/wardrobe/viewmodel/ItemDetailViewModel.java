package com.example.wardrobe.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.wardrobe.R;
import com.example.wardrobe.data.OutfitRepository;
import com.example.wardrobe.data.WardrobeRepository;
import com.example.wardrobe.data.WeatherRepository;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.WeatherInfo;
import com.example.wardrobe.ui.state.ItemDetailUiState;
import com.example.wardrobe.util.ImageStore;
import com.example.wardrobe.util.StyleProfileManager;

/**
 * 衣物详情 ViewModel。
 *
 * <p>负责读取衣物、准备天气、调用单品搭配仓库和删除衣物。Activity 只渲染
 * ItemDetailUiState，并处理保存、编辑页跳转和确认对话框。</p>
 */
public class ItemDetailViewModel extends AndroidViewModel {

    public interface DeleteCallback {
        void onDeleted();

        void onError(String message);
    }

    private final WardrobeRepository wardrobeRepository;
    private final WeatherRepository weatherRepository =
            new WeatherRepository();
    private final OutfitRepository outfitRepository;
    private final MutableLiveData<ItemDetailUiState> uiState =
            new MutableLiveData<>(new ItemDetailUiState.Builder().build());

    private long itemId;
    private boolean pendingGeneration;
    private long messageSequence;

    public ItemDetailViewModel(@NonNull Application application) {
        super(application);
        wardrobeRepository = WardrobeRepository.getInstance(application);
        outfitRepository = new OutfitRepository(application);
    }

    public LiveData<ItemDetailUiState> getUiState() {
        return uiState;
    }

    public void loadItem(long itemId) {
        this.itemId = itemId;
        if (itemId <= 0) {
            publishMessage(getApplication().getString(
                    R.string.item_not_found));
            return;
        }
        wardrobeRepository.getItemById(itemId,
                new WardrobeRepository.ResultCallback<ClothingItem>() {
                    @Override
                    public void onSuccess(ClothingItem item) {
                        if (item == null) {
                            publishMessage(getApplication().getString(
                                    R.string.item_not_found));
                            return;
                        }
                        publish(getState().buildUpon().item(item));
                    }

                    @Override
                    public void onError(String message) {
                        publishMessage(message);
                    }
                });
    }

    public void generateOutfit() {
        ClothingItem item = getState().getItem();
        if (item == null || getState().isAiLoading()) {
            return;
        }
        WeatherInfo weather = getState().getWeather();
        if (weather != null) {
            generateWithWeather(item, weather);
            return;
        }
        pendingGeneration = true;
        publish(getState().buildUpon().aiLoading(true));
        weatherRepository.refresh(getApplication(), false,
                new WeatherRepository.Callback() {
                    @Override
                    public void onLocationName(String locationName) {
                    }

                    @Override
                    public void onWeather(WeatherInfo weather, String alert) {
                        publish(getState().buildUpon().weather(weather));
                    }

                    @Override
                    public void onError(String message) {
                        pendingGeneration = false;
                        finishWithError(message);
                    }

                    @Override
                    public void onFinished() {
                        if (!pendingGeneration) {
                            return;
                        }
                        pendingGeneration = false;
                        ClothingItem current = getState().getItem();
                        WeatherInfo currentWeather = getState().getWeather();
                        if (current != null && currentWeather != null) {
                            generateWithWeather(current, currentWeather);
                        } else if (getState().isAiLoading()) {
                            finishWithError(getApplication().getString(
                                    R.string.ai_need_weather));
                        }
                    }
                });
    }

    private void generateWithWeather(ClothingItem item,
                                     WeatherInfo weather) {
        publish(getState().buildUpon().aiLoading(true));
        outfitRepository.generateItem(
                getApplication(),
                item,
                weather,
                StyleProfileManager.getGender(getApplication()),
                new OutfitRepository.Callback() {
                    @Override
                    public void onSuccess(OutfitRepository.OutfitResult result) {
                        publish(getState().buildUpon()
                                .aiLoading(false)
                                .aiContent(result.getContent())
                                .plans(result.getPlans()));
                    }

                    @Override
                    public void onNoCache() {
                        publish(getState().buildUpon().aiLoading(false));
                    }

                    @Override
                    public void onError(String message) {
                        finishWithError(message);
                    }
                });
    }

    public void deleteCurrent(DeleteCallback callback) {
        ClothingItem item = getState().getItem();
        if (item == null) {
            callback.onError(getApplication().getString(
                    R.string.item_not_found));
            return;
        }
        wardrobeRepository.deleteItem(item.getId(),
                new WardrobeRepository.ResultCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer rows) {
                        if (rows != null && rows > 0) {
                            ImageStore.deleteImage(
                                    getApplication(), item.getImagePath());
                            callback.onDeleted();
                        } else {
                            callback.onError(getApplication().getString(
                                    R.string.delete_failed));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    public void cancelActiveRequests() {
        outfitRepository.cancel();
        weatherRepository.cancel();
    }

    private void finishWithError(String message) {
        ItemDetailUiState state = getState();
        ItemDetailUiState.Builder builder = state.buildUpon()
                .aiLoading(false);
        if (state.getPlans().isEmpty()
                && state.getAiContent().trim().isEmpty()) {
            builder.aiContent(message);
        }
        publish(builder);
        publishMessage(message);
    }

    private ItemDetailUiState getState() {
        ItemDetailUiState state = uiState.getValue();
        return state == null
                ? new ItemDetailUiState.Builder().build() : state;
    }

    private void publish(ItemDetailUiState.Builder builder) {
        uiState.setValue(builder.build());
    }

    private void publishMessage(String message) {
        uiState.setValue(getState().buildUpon()
                .message(message, ++messageSequence).build());
    }

    @Override
    protected void onCleared() {
        cancelActiveRequests();
        super.onCleared();
    }
}
