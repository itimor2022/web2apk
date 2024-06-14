package com.obs.yl

import android.app.Activity
import android.view.MotionEvent

class SwipeBackHelper(private val mActivity: Activity) {

    /** 屏幕宽度 */
    private val screenWidth = mActivity.resources.displayMetrics.widthPixels

    /** 标记 - 是否是从左侧侧滑 */
    private var slideFromStartSide = false

    /** 标记 - 是否是从右侧侧滑 */
    private var slideFromEndSide = false

    private var backPressed = false

    /** 按下 x 轴位置 */
    private var mTouchStartX = 0f

    /** 标记 - 是否允许侧滑返回 */
    var swipeBackEnable = true

    /**
     * 拦截[Activity]事件分发并处理侧滑返回
     *
     * @param ev 事件对象
     *
     * @return 是否拦截
     */
    fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!swipeBackEnable) {
            return false
        }
        return when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                // 按下
                mTouchStartX = ev.rawX
                backPressed = false
                if (ev.rawX < screenWidth * 0.05) {
                    slideFromStartSide = true
                } else if (ev.rawX > screenWidth * (1 - 0.05)) {
                    slideFromEndSide = true
                }
                slideFromStartSide || slideFromEndSide
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 抬起，取消
                if (slideFromStartSide || slideFromEndSide) {
                    slideFromStartSide = false
                    slideFromEndSide = false
                }
                false
            }

            MotionEvent.ACTION_MOVE -> {
                // 移动
                if (slideFromStartSide || slideFromEndSide) {
                    if (!backPressed) {
                        mActivity.onBackPressed()
                        backPressed = true
                    }
                    true
                } else {
                    false
                }
            }

            else -> {
                // 其他
                false
            }
        }
    }
}

/**
 * 拦截[Activity]事件分发
 *
 * @param ev 事件对象
 * @param activityDispatch 处理正常分发代码块
 *
 * @return 是否拦截事件
 */
fun SwipeBackHelper?.dispatchTouchEvent(ev: MotionEvent, activityDispatch: () -> Boolean): Boolean {
    return if (null != this && dispatchTouchEvent(ev)) {
        true
    } else {
        activityDispatch.invoke()
    }
}