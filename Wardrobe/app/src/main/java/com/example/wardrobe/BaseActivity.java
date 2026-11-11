package com.example.wardrobe;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;

import com.example.wardrobe.util.UiUtils;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * 普通页面的统一基类。
 *
 * <p>集中处理沉浸式状态栏、根布局加载和系统栏内边距，避免每个页面
 * 重复写相同初始化代码。裁剪页继承第三方 UCropActivity，不在此列。</p>
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiUtils.enableImmersiveMode(this);
        setContentView(layoutRes());
        View root = findViewById(R.id.root);
        if (root != null) {
            UiUtils.applySystemBarInsets(root);
        }
    }

    @LayoutRes
    protected abstract int layoutRes();

    /**
     * 给带返回箭头的 MaterialToolbar 绑定统一的返回行为。
     */
    protected void bindBackToolbar() {
        View toolbar = findViewById(R.id.toolbar);
        if (toolbar instanceof MaterialToolbar) {
            ((MaterialToolbar) toolbar).setNavigationOnClickListener(
                    view -> finish());
        }
    }
}
