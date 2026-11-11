package com.example.wardrobe.util;

import android.content.Context;

import com.example.wardrobe.R;
import com.example.wardrobe.model.TravelIntent;
import com.example.wardrobe.network.LLMService;
import com.example.wardrobe.network.dto.ChatResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;

/**
 * 使用大模型从自然语言中提取城市、出发日期和天数。
 *
 * <p>“明天”“下周三”这类相对时间交给模型按今天日期换算；模型不可用
 * 或返回格式异常时，用本地正则做一次兜底。</p>
 */
public final class TravelIntentParser {

    public interface Callback {
        void onSuccess(TravelIntent intent);

        void onInvalidInput(String message);

        void onError(String message);
    }

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{1,2})[.\\-/月](\\d{1,2})");

    private Call<ChatResponse> call;

    public void parse(Context context, String input, Callback callback) {
        String today = LocalDate.now().format(DATE_FORMAT);
        call = LLMService.getInstance().generate(
                context,
                PromptBuilder.buildTravelIntentSystemPrompt(today),
                PromptBuilder.buildTravelIntentUserPrompt(input),
                0.1,
                256,
                new LLMService.Callback() {
                    @Override
                    public void onSuccess(String content) {
                        TravelIntent intent = parseJson(content, today);
                        if (intent == null) {
                            intent = fallback(input, today);
                        }
                        if (intent == null) {
                            callback.onInvalidInput(context.getString(
                                    R.string.travel_parse_error));
                        } else {
                            callback.onSuccess(intent);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        TravelIntent intent = fallback(input, today);
                        if (intent != null) {
                            callback.onSuccess(intent);
                        } else {
                            callback.onError(message);
                        }
                    }
                });
    }

    private TravelIntent parseJson(String content, String today) {
        if (content == null) {
            return null;
        }
        String cleaned = content.replace("```json", "")
                .replace("```", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonObject object = JsonParser.parseString(
                    cleaned.substring(start, end + 1)).getAsJsonObject();
            String city = object.has("city")
                    ? object.get("city").getAsString().trim() : "";
            String startDate = object.has("start_date")
                    ? object.get("start_date").getAsString().trim() : today;
            int days = object.has("days")
                    ? object.get("days").getAsInt() : 1;
            return buildIntent(city, startDate, days, today);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 本地兜底：提取城市、天数以及 11.10 这类日期。
     */
    private TravelIntent fallback(String input, String today) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            return null;
        }
        String startDate = today;
        Matcher dateMatcher = DATE_PATTERN.matcher(text);
        if (dateMatcher.find()) {
            startDate = toNextDate(
                    dateMatcher.group(1), dateMatcher.group(2), today);
            text = text.substring(0, dateMatcher.start()) + " "
                    + text.substring(dateMatcher.end());
        }
        TravelRequestParser.Request request =
                TravelRequestParser.parse(text);
        if (request == null) {
            return null;
        }
        return buildIntent(request.getCity(), startDate,
                request.getDays(), today);
    }

    private TravelIntent buildIntent(String city,
                                     String startDate,
                                     int days,
                                     String today) {
        if (city == null || city.trim().isEmpty()) {
            return null;
        }
        String validDate = startDate;
        try {
            LocalDate.parse(validDate, DATE_FORMAT);
        } catch (Exception exception) {
            validDate = today;
        }
        // 超过 7 天不报错，只保留一个合理上限，天气仍固定取 7 天。
        int safeDays = Math.max(1, Math.min(days, 30));
        return new TravelIntent(city.trim(), validDate, safeDays);
    }

    private String toNextDate(String monthText, String dayText, String today) {
        try {
            int month = Integer.parseInt(monthText);
            int day = Integer.parseInt(dayText);
            LocalDate current = LocalDate.parse(today, DATE_FORMAT);
            LocalDate candidate = LocalDate.of(
                    current.getYear(), month, day);
            if (candidate.isBefore(current)) {
                candidate = candidate.plusYears(1);
            }
            return candidate.format(DATE_FORMAT);
        } catch (Exception exception) {
            return today;
        }
    }

    public void cancel() {
        if (call != null) {
            call.cancel();
            call = null;
        }
    }
}
