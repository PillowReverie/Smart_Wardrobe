package com.example.wardrobe;

import android.os.Bundle;

import com.example.wardrobe.util.UcropUiHelper;
import com.yalantis.ucrop.UCropActivity;

/**
 * 项目自己的裁剪页。
 *
 * <p>继承 UCropActivity 只为了在页面创建完成后立即执行本地适配：
 * 隐藏缩放页签、启用双指捏合、约束裁剪框到图片范围，并处理状态栏内边距。
 * 裁剪结果的读取方式与 UCrop 原生页面完全一致。</p>
 */
public class CropActivity extends UCropActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UcropUiHelper.apply(this);
    }
}
