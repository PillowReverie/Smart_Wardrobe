package com.example.wardrobe.network;

import android.content.Context;
import android.util.Log;

import com.example.wardrobe.R;
import com.example.wardrobe.network.dto.ChatMessage;
import com.example.wardrobe.network.dto.ChatRequest;
import com.example.wardrobe.network.dto.ChatResponse;
import com.example.wardrobe.network.dto.VisionChatMessage;
import com.example.wardrobe.network.dto.VisionChatRequest;
import com.example.wardrobe.network.dto.VisionContentPart;
import com.example.wardrobe.util.ApiConfig;
import com.example.wardrobe.util.AiTextFormatter;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Response;

/**
 * 通用大模型服务。
 *
 * <p>负责：
 * 1. 检查用户是否配置 Base URL、Key 和模型名称；
 * 2. 拼接 /chat/completions 完整地址；
 * 3. 构造 Authorization: Bearer 和标准 OpenAI messages 请求；
 * 4. 使用 enqueue 异步请求并解析 choices[0].message.content；
 * 5. 返回 Call 给 Activity 保存，以便页面销毁时取消。</p>
 *
 * <p>不在源码中绑定任何模型厂商，DeepSeek、Kimi、智谱、通义等只要兼容
 * OpenAI Chat Completions 格式即可切换。</p>
 */
public final class LLMService {

    private static final String TAG = "LLMService";
    /**
     * 三套搭配需要更强的风格发散，同时给推理型模型预留足够的思考 token。
     * 推理模型的 reasoning 内容也计入 max_tokens，1200 很容易在思考阶段
     * 就被截断，导致正文 content 为空。
     */
    private static final double TEMPERATURE = 0.9;
    /**
     * 三套穿搭正文通常只需要 300~600 个 token。DeepSeek 新版模型默认会
     * 先输出 reasoning_content，思考内容同样计入 max_tokens；复杂衣橱
     * Prompt 曾出现 3072 个 token 全部被思考耗尽、正文为空的情况。
     * 因此 DeepSeek 官方接口会显式关闭 thinking，其他厂商保持原参数。
     */
    private static final int MAX_TOKENS = 3072;
    private static final double VISION_TEMPERATURE = 0.1;
    private static final int VISION_MAX_TOKENS = 300;
    private static volatile LLMService instance;

    private LLMService() {
        // 单例，禁止外部实例化。
    }

    public static LLMService getInstance() {
        if (instance == null) {
            synchronized (LLMService.class) {
                if (instance == null) {
                    instance = new LLMService();
                }
            }
        }
        return instance;
    }

    public interface Callback {
        void onSuccess(String content);

        void onError(String message);
    }

    /**
     * 发起一次异步大模型请求。
     *
     * @return Retrofit Call，调用页面可在 onDestroy 中 cancel；配置不完整时返回 null。
     */
    public Call<ChatResponse> generate(Context context,
                                       String systemPrompt,
                                       String userPrompt,
                                       Callback callback) {
        return generate(
                context,
                systemPrompt,
                userPrompt,
                TEMPERATURE,
                MAX_TOKENS,
                callback);
    }

    /**
     * 使用自定义生成参数，供简短资料描述等低发散度任务复用。
     */
    public Call<ChatResponse> generate(Context context,
                                       String systemPrompt,
                                       String userPrompt,
                                       double temperature,
                                       int maxTokens,
                                       Callback callback) {
        Context appContext = context.getApplicationContext();
        if (!ApiConfig.isLlmConfigured(appContext)) {
            callback.onError(appContext.getString(R.string.ai_need_llm_key));
            return null;
        }

        String url = ApiConfig.getLlmChatUrl(appContext);
        if (url.isEmpty()) {
            callback.onError(appContext.getString(R.string.ai_need_llm_key));
            return null;
        }

        String model = ApiConfig.getLlmModelName(appContext);
        String apiKey = ApiConfig.getLlmApiKey(appContext);
        // DeepSeek 官方接口支持 thinking 开关。关闭思考后模型直接输出正文，
        // 既避免 max_tokens 被推理内容耗尽，也能明显缩短响应时间。
        // 其他兼容 OpenAI 的厂商不发送该字段，防止不认识该参数的接口返回 400。
        ChatRequest.Thinking thinking = isDeepSeekEndpoint(url)
                ? ChatRequest.Thinking.disabled() : null;
        ChatRequest request = new ChatRequest(
                model,
                Arrays.asList(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(userPrompt)),
                temperature,
                maxTokens,
                thinking);

        final Call<ChatResponse> call;
        try {
            call = ApiClient.getLlmApi().createChatCompletion(
                    url,
                    "Bearer " + apiKey,
                    "application/json",
                    request);
        } catch (Exception exception) {
            Log.e(TAG, "创建大模型请求失败", exception);
            callback.onError(appContext.getString(
                    R.string.ai_generation_failed));
            return null;
        }

        enqueueResponse(appContext, call, callback);
        return call;
    }

    /**
     * 使用 OpenAI Vision 兼容格式识别衣物。
     *
     * <p>图片由调用方压缩成 Base64 JPEG，这里拼接 data URL。响应仍复用
     * 普通 ChatResponse，后续由 ClothingRecognitionParser 解析 JSON。</p>
     */
    public Call<ChatResponse> recognizeClothingImage(Context context,
                                                      String base64Image,
                                                      Callback callback) {
        Context appContext = context.getApplicationContext();
        if (!ApiConfig.isLlmConfigured(appContext)) {
            callback.onError(appContext.getString(R.string.ai_need_llm_key));
            return null;
        }
        if (base64Image == null || base64Image.trim().isEmpty()) {
            callback.onError(appContext.getString(
                    R.string.ai_vision_image_missing));
            return null;
        }
        String url = ApiConfig.getLlmChatUrl(appContext);
        if (url.isEmpty()) {
            callback.onError(appContext.getString(R.string.ai_need_llm_key));
            return null;
        }

        String prompt = "请识别图中的衣物，返回纯 JSON 格式（不要包含 Markdown 标记），"
                + "包含：name（名称，如：米白色高领毛衣）、"
                + "category（类别，只能是：上装/下装/外套/裙装/鞋履/配饰 之一）、"
                + "color（颜色）。只输出 JSON，不要解释。";
        String model = ApiConfig.getLlmModelName(appContext);
        String apiKey = ApiConfig.getLlmApiKey(appContext);
        ChatRequest.Thinking thinking = isDeepSeekEndpoint(url)
                ? ChatRequest.Thinking.disabled() : null;
        VisionChatRequest request = new VisionChatRequest(
                model,
                Collections.singletonList(new VisionChatMessage(
                        "user",
                        Arrays.asList(
                                VisionContentPart.text(prompt),
                                VisionContentPart.image(
                                        "data:image/jpeg;base64," + base64Image)))),
                VISION_TEMPERATURE,
                VISION_MAX_TOKENS,
                thinking);

        final Call<ChatResponse> call;
        try {
            call = ApiClient.getLlmApi().createVisionChatCompletion(
                    url,
                    "Bearer " + apiKey,
                    "application/json",
                    request);
        } catch (Exception exception) {
            Log.e(TAG, "创建视觉识别请求失败", exception);
            callback.onError(appContext.getString(
                    R.string.ai_generation_failed));
            return null;
        }
        enqueueResponse(appContext, call, callback);
        return call;
    }

    /**
     * 普通文本和视觉请求共用的异步响应处理。
     */
    private void enqueueResponse(Context appContext,
                                 Call<ChatResponse> call,
                                 Callback callback) {
        call.enqueue(new retrofit2.Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> requestCall,
                                   Response<ChatResponse> response) {
                if (requestCall.isCanceled()) {
                    return;
                }
                if (response.code() == 401 || response.code() == 403) {
                    logHttpError(response);
                    callback.onError(appContext.getString(
                            R.string.ai_auth_failed));
                    return;
                }
                if (response.code() == 429) {
                    logHttpError(response);
                    callback.onError(appContext.getString(
                            R.string.ai_rate_limited));
                    return;
                }
                if (!response.isSuccessful()) {
                    String serverMessage = logHttpError(response);
                    callback.onError(buildHttpErrorMessage(
                            appContext, response.code(), serverMessage));
                    return;
                }

                ChatResponse body = response.body();
                if (body == null) {
                    callback.onError(appContext.getString(
                            R.string.ai_generation_failed));
                    return;
                }
                String content = AiTextFormatter.clean(body.getFirstContent());
                if (content.isEmpty()) {
                    String serverMessage = body.getErrorMessage();
                    Log.e(TAG, "大模型返回空正文：finishReason="
                            + body.getFinishReason()
                            + "，reasoningLength=" + body.getReasoningLength()
                            + "，usage=" + body.getUsageSummary()
                            + "，serverMessage=" + serverMessage);
                    if (!serverMessage.isEmpty()) {
                        Log.e(TAG, "服务端错误详情：" + serverMessage);
                    }
                    if ("length".equalsIgnoreCase(body.getFinishReason())) {
                        callback.onError(appContext.getString(
                                R.string.ai_output_truncated));
                        return;
                    }
                    callback.onError(appContext.getString(
                            R.string.ai_empty_response));
                    return;
                }

                callback.onSuccess(content);
            }

            @Override
            public void onFailure(Call<ChatResponse> requestCall,
                                  Throwable throwable) {
                if (requestCall.isCanceled()) {
                    return;
                }
                Log.e(TAG, "大模型请求异常：" + throwable.getClass().getSimpleName()
                        + "，message=" + throwable.getMessage(), throwable);
                boolean timeout = throwable instanceof SocketTimeoutException
                        || throwable instanceof InterruptedIOException;
                callback.onError(appContext.getString(timeout
                        ? R.string.ai_timeout
                        : R.string.ai_generation_failed));
            }
        });
    }

    /**
     * 打印服务端 ErrorBody。429 限流、额度不足、模型名错误等信息通常只在
     * 响应体中返回，仅记录状态码无法定位问题。
     */
    private String logHttpError(Response<?> response) {
        String errorBody = "";
        if (response.errorBody() != null) {
            try {
                errorBody = response.errorBody().string();
            } catch (IOException exception) {
                errorBody = "<读取 ErrorBody 失败>";
            }
        }
        Log.e(TAG, "大模型 HTTP 错误：code=" + response.code()
                + "，message=" + response.message()
                + "，errorBody=" + errorBody);
        return extractServerMessage(errorBody);
    }

    /**
     * 从错误响应体中提取 error.message。
     *
     * <p>OpenAI 兼容服务在 400 时会返回“模型不存在 / 参数不合法”等具体原因，
     * 只显示统一文案会让用户无法判断是模型名、Key 还是 Base URL 的问题。</p>
     */
    private String extractServerMessage(String errorBody) {
        if (errorBody == null || errorBody.trim().isEmpty()) {
            return "";
        }
        try {
            ChatResponse parsed = new Gson()
                    .fromJson(errorBody, ChatResponse.class);
            return parsed == null ? "" : parsed.getErrorMessage();
        } catch (RuntimeException exception) {
            Log.e(TAG, "解析错误响应体失败", exception);
            return "";
        }
    }

    /**
     * 把 HTTP 状态码转换成用户能直接照着排查的提示。
     */
    private String buildHttpErrorMessage(Context context,
                                         int code,
                                         String serverMessage) {
        String detail = compactMessage(serverMessage);
        if (code == 400 || code == 422) {
            return detail.isEmpty()
                    ? context.getString(R.string.ai_invalid_request_simple)
                    : context.getString(R.string.ai_invalid_request, detail);
        }
        if (code == 402) {
            return context.getString(R.string.ai_insufficient_balance);
        }
        if (code == 404) {
            return context.getString(R.string.ai_endpoint_not_found);
        }
        if (code >= 500) {
            return context.getString(R.string.ai_server_error, code);
        }
        return detail.isEmpty()
                ? context.getString(R.string.ai_http_failed, code)
                : context.getString(R.string.ai_http_failed_detail,
                        code, detail);
    }

    /**
     * Toast 不适合展示大段 JSON，这里压缩空白并截断到 120 字。
     */
    private String compactMessage(String message) {
        if (message == null) {
            return "";
        }
        String compact = message.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 120) {
            return compact;
        }
        return compact.substring(0, 117) + "...";
    }

    /**
     * 只对 DeepSeek 官方域名发送 thinking 参数。
     *
     * <p>用户可能通过第三方 OpenAI 兼容网关调用 DeepSeek 模型，这类网关
     * 未必认识 thinking 字段。域名判断比模型名判断更保守，避免把请求
     * 变成某些网关无法解析的 400。</p>
     */
    private boolean isDeepSeekEndpoint(String url) {
        return url != null
                && url.toLowerCase(Locale.ROOT).contains("deepseek");
    }
}
