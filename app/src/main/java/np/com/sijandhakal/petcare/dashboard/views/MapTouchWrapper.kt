package np.com.sijandhakal.petcare.dashboard.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Wraps a map placed inside a scrolling screen. While a finger is on the map, the scroll view
 * is told not to intercept, so panning the map doesn't scroll the page instead.
 */
class MapTouchWrapper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(ev)
    }
}
