package com.example.wardrobe.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.wardrobe.data.WardrobeRepository;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.ui.state.WardrobeListState;

import java.util.Collections;
import java.util.List;

/**
 * 衣橱页 ViewModel。
 *
 * <p>负责 Tab、类别、搜索关键词三种筛选状态的合并，并在内部执行
 * 300ms 搜索防抖。SQLite 查询由共享仓库在后台线程完成，配置变化后
 * ViewModel 会自动保留最后一次筛选条件。</p>
 */
public class WardrobeViewModel extends AndroidViewModel {

    private static final long SEARCH_DEBOUNCE_MS = 300L;

    private final WardrobeRepository repository;
    private final MediatorLiveData<WardrobeListState> listState =
            new MediatorLiveData<>();
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private boolean travelTab;
    private String category;
    private String keyword = "";
    private int queryGeneration;

    private final Runnable searchRunnable = this::executeSearch;

    public WardrobeViewModel(@NonNull Application application) {
        super(application);
        repository = WardrobeRepository.getInstance(application);
        listState.setValue(buildLoadingState(Collections.emptyList()));
        listState.addSource(repository.getAllItems(), ignored -> executeSearch());
    }

    public LiveData<WardrobeListState> getListState() {
        return listState;
    }

    public LiveData<Boolean> getMutationInFlight() {
        return repository.getTravelMutationInFlight();
    }

    public void refresh() {
        repository.refreshAll();
        executeSearch();
    }

    public void setTravelTab(boolean travelTab) {
        if (this.travelTab == travelTab) {
            return;
        }
        this.travelTab = travelTab;
        executeSearch();
    }

    public void setCategory(String category) {
        this.category = category;
        executeSearch();
    }

    /**
     * 输入框每次变化都重置计时器，只有停止输入 300ms 后才查询 SQLite。
     */
    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword.trim();
        mainHandler.removeCallbacks(searchRunnable);
        mainHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
    }

    public void toggleTravel(ClothingItem item,
                             WardrobeRepository.ResultCallback<Integer> callback) {
        if (item == null || item.getId() <= 0) {
            return;
        }
        repository.setItemTravel(
                item.getId(), !item.isTravel(), callback);
    }

    public void clearTravel(
            WardrobeRepository.ResultCallback<Integer> callback) {
        repository.clearTravelItems(callback);
    }

    private void executeSearch() {
        final int generation = ++queryGeneration;
        final boolean requestTravelTab = travelTab;
        final String requestKeyword = keyword;
        listState.setValue(buildLoadingState(
                currentItemsOrEmpty()));
        repository.searchItems(category, requestKeyword, requestTravelTab,
                new WardrobeRepository.ResultCallback<List<ClothingItem>>() {
                    @Override
                    public void onSuccess(List<ClothingItem> result) {
                        if (generation != queryGeneration) {
                            return;
                        }
                        List<ClothingItem> all = repository
                                .getAllItemsSnapshot();
                        int travelCount = 0;
                        for (ClothingItem item : all) {
                            if (item.isTravel()) {
                                travelCount++;
                            }
                        }
                        listState.setValue(new WardrobeListState(
                                result,
                                all.size(),
                                travelCount,
                                requestTravelTab,
                                requestKeyword,
                                false,
                                ""
                        ));
                    }

                    @Override
                    public void onError(String message) {
                        if (generation != queryGeneration) {
                            return;
                        }
                        listState.setValue(new WardrobeListState(
                                Collections.emptyList(),
                                repository.getAllItemsSnapshot().size(),
                                repository.getTravelItemsSnapshot().size(),
                                requestTravelTab,
                                requestKeyword,
                                false,
                                message
                        ));
                    }
                });
    }

    private WardrobeListState buildLoadingState(List<ClothingItem> items) {
        return new WardrobeListState(
                items,
                repository.getAllItemsSnapshot().size(),
                repository.getTravelItemsSnapshot().size(),
                travelTab,
                keyword,
                true,
                ""
        );
    }

    private List<ClothingItem> currentItemsOrEmpty() {
        WardrobeListState current = listState.getValue();
        return current == null
                ? Collections.emptyList() : current.getItems();
    }

    @Override
    protected void onCleared() {
        mainHandler.removeCallbacks(searchRunnable);
        super.onCleared();
    }
}
