package com.indieuser.drawer

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.Scroller
import androidx.annotation.IntDef
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import java.lang.reflect.Field
import kotlin.math.abs

/** A DrawerView gives nice drawer effects with responsiveness and better design adjustments. It's based on ViewPager and
 * Actually has following benefits over default drawer view.
 *
 * `Responsiveness`: You can use this for all of your screen sizes. In fact, It can show multiple pages at once, based on [setDrawerMode]
 *
 * `Effects`: You can use [setDrawerEffect] which opens a lot of animations and styles for drawer positioning.
 *
 * Also, it gives access to open drawers within the content view. Just like the one in Discord.
 *
 * You can also set these values in xml. Not only that, you can override the values for `app:drawerMode` as of `center` 1, `custom` 4 and other as their name suggests.
 *
 * You need to specify the `contentPage`. Although you can specify the initially `selectedPage` (by default it's contentPage)
 * */
open class DrawerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewPager(context, attrs) {
    private var mainPage: Int
    private var selectedPage: Int
    private var dMode = MODE_CENTER
    init {
        adapter = DrawerAdapter()
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DrawerView)
        mainPage = typedArray.getInt(R.styleable.DrawerView_contentPage, 1)
        selectedPage = typedArray.getInt(R.styleable.DrawerView_selectedPage, mainPage)
        setDrawerEffect(when (typedArray.getInt(R.styleable.DrawerView_drawerEffect, 0)) {
            0 -> NoDrawerEffect()
            2 -> DefaultDrawerEffect()
            3 -> ZoomDrawerEffect()
            4 -> Zoom3dDrawerEffect()
            else  -> SlideDrawerEffect()
        })
        setDrawerMode(typedArray.getInt(R.styleable.DrawerView_drawerMode, MODE_CENTER))
        typedArray.recycle()
        postInitViewPager()
    }

    /** Set Content Page, by default it's 1 and can be set in xml using 'contentPage'*/
    fun setContentPage (position: Int) {
        if (position in 0 until  childCount) mainPage = position
    }
    /** get Content Page Position. */
    fun getContentPagePosition (): Int = mainPage
    /** get content Page. Can be used to check `page == getContentPage()` when using [setPageTransformer] */
    fun getContentPage (): View = getChildAt(mainPage)

    private fun updateMainPage () {
        if (childCount == 0 || selectedPage < 0) return
        else if (selectedPage >= childCount) return
        setCurrentItem(selectedPage, false)
        selectedPage = -1
    }

    private fun postInitViewPager() {
        try {
            val scrollerField: Field = ViewPager::class.java.getDeclaredField("mScroller")
            scrollerField.isAccessible = true
            val interpolator: Interpolator = DecelerateInterpolator()

            val customScroller = CustomScroller(context, interpolator)
            scrollerField.set(this, customScroller)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        adapter?.notifyDataSetChanged()
        updateMainPage()
        offscreenPageLimit = childCount
    }
    override fun onViewRemoved(child: View?) {
        super.onViewRemoved(child)
        adapter?.notifyDataSetChanged()
        updateMainPage()
        offscreenPageLimit = childCount
    }

    private fun getChildWidth (i: Int) : Float {
        return when {
            dMode == MODE_CENTER -> if (i == mainPage) 1f else 0.85f
            dMode == MODE_TWO || (childCount == 2 && dMode == MODE_THREE) -> if (i == mainPage) 0.7f else 0.3f
            dMode == MODE_THREE && childCount > 2 -> if (i == mainPage) 0.5f else 0.25f
            else -> getChildLPWidth(i)
        }
    }
    private fun getChildLPWidth (i: Int) : Float {
        val v = getChildAt(i)
        val params = v.layoutParams as LayoutParams
        return params.widthPercent
    }

    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams {
        return LayoutParams(context, attrs)
    }
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is LayoutParams
    }

    @IntDef(MODE_CENTER, MODE_TWO, MODE_THREE, MODE_CUSTOM)
    annotation class DrawerMode
    /** There are three built-in drawer modes and you can also use your own by just using [MODE_CUSTOM]
     *
     * Applicable Modes:
     *
     * [MODE_CENTER]: Main Content in the center, and drawers at left and right (outside the screen)
     *
     * [MODE_TWO]: Main Content in the center, and drawers at left and right (one in inside, one is outside)
     *
     * [MODE_THREE]: Main Content in the center, and drawers at left and right (inside the screen)
     *
     * [MODE_CUSTOM]: Specified in the layout params of the child: i.e, [LayoutParams.widthPercent] between `0f` and `1f`
     * */
    fun setDrawerMode (@DrawerMode mode:Int) {
        dMode = mode
        if (mode == MODE_TWO || mode == MODE_THREE) setDrawerEffect(NoDrawerEffect())
        invalidate()
    }
    /** You can create your own drawer effect using [DrawerEffect] or you can try one of these
     *
     * [SlideDrawerEffect]
     *
     * [DefaultDrawerEffect]
     *
     * [ZoomDrawerEffect]
     *
     * [Zoom3dDrawerEffect]
     *
     * [NoDrawerEffect]
     * */
    fun setDrawerEffect (effect: DrawerEffect) {
        setPageTransformer(false) { v, offset -> effect.transform(v, getPagePosition(v), offset) }
    }

    private inner class DrawerAdapter : PagerAdapter() {
        override fun getPageWidth(position: Int): Float {
            return getChildWidth(position)
        }
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            return getChildAt(position)
        }
        override fun getCount(): Int {
            return childCount
        }
        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view == `object`
        }
    }
    inner class LayoutParams(context: Context, attrs: AttributeSet?) : ViewPager.LayoutParams(context, attrs) {
        var widthPercent: Float
        init {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DrawerView_Layout)
            widthPercent = typedArray.getFloat(R.styleable.DrawerView_Layout_widthPercent, 1f)
            typedArray.recycle()
        }
    }
    private inner class CustomScroller(context: Context, interpolator: Interpolator) : Scroller(context, interpolator) {

        private val scrollDuration = 200
        override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
            super.startScroll(startX, startY, dx, dy, scrollDuration)
        }
        override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int) {
            super.startScroll(startX, startY, dx, dy, scrollDuration)
        }
    }

    companion object {
        const val MODE_CENTER = 1
        const val MODE_TWO = 2
        const val MODE_THREE = 3
        const val MODE_CUSTOM = 4
    }

    /** Gives you a nice `traditional` drawer effect which is the default `effect` of System `DrawerLayout` */
    inner class DefaultDrawerEffect : DrawerEffect() {
        override fun transform(v: View, position: Int, offset: Float) {
            v.translationZ = 1+offset
            if (position != mainPage) return
            v.translationZ = 0f
            v.alpha = 1 - abs(offset)
            v.translationX = v.width * -offset
        }
    }
    /** Gives you a nice `traditional` drawer effect with scaling down content. */
    inner class ZoomDrawerEffect : DrawerEffect() {
        override fun transform (v: View, position: Int, offset: Float) {
            v.translationZ = 1+offset
            if (position != mainPage) return
            v.scaleX = 1f - abs(offset)/5
            v.scaleY = 1f - abs(offset)/5
            v.translationZ = 0f
//        page.alpha = 1 - abs(position)
            v.translationX = v.width/11 * -offset
        }
    }
    /** Gives you a nice `3D` drawer effect with scaling down and rotation of content. */
    inner class Zoom3dDrawerEffect : DrawerEffect() {
        override fun transform(v: View, position: Int, offset: Float) {
            v.translationZ = 1+offset
            if (position != mainPage) return
            v.scaleX = 1f - abs(offset)/5
            v.scaleY = 1f - abs(offset)/5
            v.translationZ = 0f
//        v.alpha = 1 - abs(offset)
            v.translationX = v.width/6 * -offset
            v.rotationY = -offset*30
        }
    }
}

private fun getPagePosition (v: View) : Int {
    val vg = v.parent as ViewGroup
    val count = vg.childCount
    for (i in 0 until count) if (vg.getChildAt(i) == v) return i
    return -1
}

/** Clears any drawer effect previously applied. Which makes it use default ViewPager scrolling effect. */
class NoDrawerEffect : DrawerEffect() {
    override fun transform (v: View, position: Int, offset: Float) {}
}
/** Gives you a nice `alpha` drawer effect which fades the content when scrolled */
class SlideDrawerEffect : DrawerEffect() {
    override fun transform (v: View, position: Int, offset: Float) {
        v.alpha = 1.25f - abs(offset)
    }
}

abstract class DrawerEffect {
    abstract fun transform (v: View, position: Int, offset: Float)
}