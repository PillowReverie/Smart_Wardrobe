package com.example.wardrobe.util;

import android.content.Context;

import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.TravelIntent;
import com.example.wardrobe.network.LLMService;
import com.example.wardrobe.network.dto.ChatResponse;

import java.util.List;

import retrofit2.Call;

/**
 * 异地游玩编排器。
 *
 * <p>流程：大模型解析城市/日期/天数 → 查询目的地完整 7 天天气 →
 * 拼接 Prompt → 生成打包清单。缓存与 UI 状态由 MainViewModel 统一管理。</p>
 */
public final class TravelPlanner {

    public interface Callback {
        void onInvalidInput(String message);

        void onSuccess(String content, String scene);

        void onError(String message);
    }

    private final TravelIntentParser intentParser = new TravelIntentParser();
    private Call<ChatResponse> aiCall;
    private int requestToken;
    private boolean running;

    public void plan(Context context,
                     String rawInput,
                     String gender,
                     List<ClothingItem> items,
                     Callback callback) {
        if (running) {
            return;
        }
        running = true;
        final int token = ++requestToken;
        final String scene = AiOutfitCache.normalizeScene(rawInput);

        intentParser.parse(context, rawInput, new TravelIntentParser.Callback() {
            @Override
            public void onSuccess(TravelIntent intent) {
                if (token != requestToken) {
                    return;
                }
                fetchWeather(context, intent, scene, gender,
                        items, token, callback);
            }

            @Override
            public void onInvalidInput(String message) {
                if (token != requestToken) {
                    return;
                }
                running = false;
                callback.onInvalidInput(message);
            }

            @Override
            public void onError(String message) {
                if (token != requestToken) {
                    return;
                }
                running = false;
                callback.onError(message);
            }
        });
    }

    private void fetchWeather(Context context,
                              TravelIntent intent,
                              String scene,
                              String gender,
                              List<ClothingItem> items,
                              int token,
                              Callback callback) {
        TravelWeatherService.fetch(context, intent.getCity(),
                new TravelWeatherService.Callback() {
                    @Override
                    public void onSuccess(String cityName,
                                          String weatherSummary) {
                        if (token != requestToken) {
                            return;
                        }
                        String prompt = PromptBuilder.buildTravelPrompt(
                                cityName,
                                intent.getStartDate(),
                                intent.getDays(),
                                weatherSummary,
                                items,
                                gender);
                        aiCall = LLMService.getInstance().generate(
                                context,
                                PromptBuilder.buildTravelSystemPrompt(),
                                prompt,
                                new LLMService.Callback() {
                                    @Override
                                    public void onSuccess(String content) {
                                        if (token != requestToken) {
                                            return;
                                        }
                                        running = false;
                                        aiCall = null;
                                        callback.onSuccess(content, scene);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        if (token != requestToken) {
                                            return;
                                        }
                                        running = false;
                                        aiCall = null;
                                        callback.onError(message);
                                    }
                                });
                    }

                    @Override
                    public void onError(String message) {
                        if (token != requestToken) {
                            return;
                        }
                        running = false;
                        callback.onError(message);
                    }
                });
    }

    public void cancel() {
        requestToken++;
        running = false;
        intentParser.cancel();
        if (aiCall != null) {
            aiCall.cancel();
            aiCall = null;
        }
        TravelWeatherService.cancel();
    }
}
