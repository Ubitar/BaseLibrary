package com.common.ui.activity;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.databinding.ViewDataBinding;
import androidx.fragment.app.Fragment;

import com.common.ui.adapter.BaseActionBarAdapter;
import com.common.ui.delegate.BaseDelegate;
import com.common.ui.dialog.LoadingDialog;
import com.common.ui.fragment.BaseFragment;
import com.gyf.barlibrary.ImmersionBar;
import com.huang.lib.util.ActivityManager;
import com.huang.lib.util.KeyboardUtil;
import com.huang.lib.util.SoftInputUtil;
import com.huang.lib.util.T;
import com.noober.background.BackgroundLibrary;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by laohuang on 2018/9/9.
 */

public abstract class BaseActivity<S extends BaseDelegate> extends BaseSwipeBackActivity {
    protected S viewDelegate;

    protected boolean isDoubleBack;
    protected boolean autoHideKeyBoard = true;
    protected long curMillsTime;

    protected ImmersionBar immersionBar;
    protected Unbinder unbinder;
    protected ActivityManager manager = ActivityManager.getManager();

    protected BaseActionBarAdapter actionBarAdapter;

    protected LoadingDialog loadingDialog;

    public BaseActivity() {
        try {
            viewDelegate = this.getDelegateClass().newInstance();
        } catch (InstantiationException var2) {
            throw new RuntimeException("createMainView IDelegate error");
        } catch (IllegalAccessException var3) {
            throw new RuntimeException("createMainView IDelegate error");
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        BackgroundLibrary.inject(this);
        super.onCreate(savedInstanceState);

        createMainBinding(getLayoutInflater(), null, savedInstanceState);

        ViewGroup rootView = viewDelegate.onCreateView();
        setContentView(rootView);

        unbinder = ButterKnife.bind(this);

        immersionBar = ImmersionBar.with(this);
        immersionBar.statusBarDarkFont(true)
                .keyboardEnable(true, WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                .navigationBarColor(android.R.color.white).init();

        initActionBarAdapter(rootView);

        viewDelegate.initWidget();

        manager.addActivity(this);
    }

    //使onActivityResult能够传到fragment
    @SuppressLint("RestrictedApi")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        onActivityResultForFragment(getSupportFragmentManager().getFragments(), requestCode, resultCode, data);
    }

    @SuppressLint("RestrictedApi")
    private void onActivityResultForFragment(List<Fragment> rootFragmentList, int requestCode, int resultCode, Intent data) {
        if (rootFragmentList != null) {
            for (Fragment fragment : rootFragmentList) {
                if (fragment == null) continue;
                fragment.onActivityResult(requestCode, resultCode, data);
                onActivityResultForFragment(fragment.getChildFragmentManager().getFragments(), requestCode, resultCode, data);
            }
        }
    }

    @Override
    public void onBackPressedSupport() {
        if (isDoubleBack && getSupportFragmentManager().getBackStackEntryCount() <= 1) {
            if (System.currentTimeMillis() - curMillsTime < 1500) super.onBackPressedSupport();
            else {
                curMillsTime = System.currentTimeMillis();
                T.showShort("再点击一次退出");
            }
        } else super.onBackPressedSupport();
    }

    @Override
    public void finish() {
        SoftInputUtil.hideSoftKeyboard(this);
        super.finish();
        overridePendingTransition(com.resource.R.anim.anim_no, com.resource.R.anim.anim_to_right_close);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewDelegate.onSupportVisible();
    }

    @Override
    protected void onPause() {
        viewDelegate.onSupportInvisible();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        viewDelegate.onDestroyWidget();
        if (actionBarAdapter != null) actionBarAdapter.release();
        actionBarAdapter = null;
        unbinder.unbind();
        unbinder = null;
        viewDelegate = null;
        immersionBar.destroy();
        manager.finishActivity(this);
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (autoHideKeyBoard && ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (KeyboardUtil.isShouldHideInput(v, ev)) {
                SoftInputUtil.hideKeyboardWithView(v);
            }
        }
        return super.dispatchTouchEvent(ev);
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (onKeyDownForFragment(getSupportFragmentManager().getFragments(), keyCode, event))
            return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (onKeyUpForFragment(getSupportFragmentManager().getFragments(), keyCode, event))
            return true;
        return super.onKeyUp(keyCode, event);
    }

    private Boolean onKeyDownForFragment(List<Fragment> rootFragmentList, int keyCode, KeyEvent event) {
        if (rootFragmentList != null) {
            for (Fragment fragment : rootFragmentList) {
                if (fragment == null) continue;
                if (fragment instanceof BaseFragment) {
                    if (((BaseFragment) fragment).onKeyDown(keyCode, event))
                        return true;
                    else
                        return onKeyDownForFragment(fragment.getChildFragmentManager().getFragments(), keyCode, event);
                }
            }
        }
        return false;
    }

    private Boolean onKeyUpForFragment(List<Fragment> rootFragmentList, int keyCode, KeyEvent event) {
        if (rootFragmentList != null) {
            for (Fragment fragment : rootFragmentList) {
                if (fragment == null) continue;
                if (fragment instanceof BaseFragment) {
                    if (((BaseFragment) fragment).onKeyDown(keyCode, event))
                        return true;
                    else
                        return onKeyUpForFragment(fragment.getChildFragmentManager().getFragments(), keyCode, event);
                }
            }
        }
        return false;
    }


    public boolean onCreateOptionsMenu(Menu menu) {
        if (this.viewDelegate.getOptionsMenuId() != 0) {
            this.getMenuInflater().inflate(this.viewDelegate.getOptionsMenuId(), menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    protected <D extends ViewDataBinding> D
    createMainBinding(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return this.viewDelegate.createMainView(inflater, container, savedInstanceState);
    }

    private void initActionBarAdapter(ViewGroup viewGroup) {
        Class<BaseActionBarAdapter> adapterClass = getActionBarAdapter();
        if (adapterClass != null) {
            try {
                actionBarAdapter = adapterClass.newInstance();
                actionBarAdapter.injectView(viewGroup);
            } catch (IllegalAccessException | InstantiationException e) {
                e.printStackTrace();
            }
        }
    }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (this.viewDelegate == null) {
            try {
                viewDelegate = this.getDelegateClass().newInstance();
                createMainBinding(this.getLayoutInflater(), null, savedInstanceState);
            } catch (InstantiationException var3) {
                throw new RuntimeException("createMainView IDelegate error");
            } catch (IllegalAccessException var4) {
                throw new RuntimeException("createMainView IDelegate error");
            }
        }
    }

    public boolean isDoubleBack() {
        return isDoubleBack;
    }

    public void setDoubleBack(boolean doubleBack) {
        isDoubleBack = doubleBack;
    }

    public boolean isAutoHideKeyBoard() {
        return autoHideKeyBoard;
    }

    public void setAutoHideKeyBoard(boolean autoHideKeyBoard) {
        this.autoHideKeyBoard = autoHideKeyBoard;
    }

    public void showLoading() {
        showLoading("加载中");
    }

    public void showLoading(String message) {
        showLoading(message, true, null);
    }

    public void showLoading(String message, boolean cancelable, DialogInterface.
            OnDismissListener cancelListener) {
        if (isDestroyed() || isFinishing() || loadingDialog != null) return;
        loadingDialog = new LoadingDialog.Builder()
                .setCancelable(cancelable)
                .setText(message)
                .setOnDismissListener(cancelListener)
                .build();
        loadingDialog.show(getSupportFragmentManager());
    }

    public void hideLoading() {
        if (isDestroyed() || isFinishing()) return;
        if (loadingDialog != null) loadingDialog.dismissAllowingStateLoss();
        loadingDialog = null;
    }

    protected BaseActivity getActivity() {
        return this;
    }

    /**
     * 设置头部适配器
     */
    protected Class getActionBarAdapter() {
        return null;
    }

    protected abstract Class<S> getDelegateClass();


}
