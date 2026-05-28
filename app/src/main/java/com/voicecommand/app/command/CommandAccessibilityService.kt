package com.voicecommand.app.command

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CommandAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: CommandAccessibilityService? = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // canRetrieveWindowContent is declared in res/xml/accessibility_service_config.xml.
        // Nothing extra needed here — the XML config covers flags and capabilities.
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Finds the best song-result card in the currently active window and taps it.
     *
     * Strategy:
     *  1. Collect every clickable node below the top 20% of the screen (skips the
     *     search bar, back button, and navigation icons) with a minimum card size.
     *  2. Sort by Y position (topmost = closest to "best match" in most music apps).
     *  3. Prefer the first node whose subtree contains the searched song name — this
     *     reliably picks the song card rather than an artist/album/playlist card that
     *     happens to appear above it.
     *  4. Fall back to the topmost card if no text match is found.
     *
     * Returns true if a node was tapped, false if results aren't visible yet
     * (caller retries after a short delay).
     */
    fun clickFirstSearchResult(songName: String): Boolean {
        val root = rootInActiveWindow ?: return false

        val density = resources.displayMetrics.density
        val screenH = resources.displayMetrics.heightPixels
        val skipTop = screenH * 0.20f          // ignore top 20% (back btn, search bar)
        val minH    = (56  * density).toInt()  // song cards are >= 56 dp tall
        val minW    = (120 * density).toInt()  // song cards are >= 120 dp wide

        val hits = mutableListOf<Pair<AccessibilityNodeInfo, Int>>()
        collectClickable(root, hits, skipTop, minH, minW)

        if (hits.isEmpty()) {
            root.recycle()
            return false
        }

        hits.sortBy { it.second }  // topmost first

        // Prefer the first node whose subtree contains the song name
        val queryLower = songName.lowercase().trim()
        val target = hits.firstOrNull { (node, _) -> subtreeContainsText(node, queryLower) }
            ?: hits.first()  // fallback: topmost candidate

        val clicked = target.first.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        root.recycle()
        return clicked
    }

    /** Returns true if [node] or any descendant has text/contentDescription containing [query]. */
    private fun subtreeContainsText(node: AccessibilityNodeInfo, query: String): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (text.contains(query) || desc.contains(query)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (subtreeContainsText(child, query)) return true
        }
        return false
    }

    private fun collectClickable(
        node: AccessibilityNodeInfo,
        hits: MutableList<Pair<AccessibilityNodeInfo, Int>>,
        skipTop: Float,
        minH: Int,
        minW: Int
    ) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (node.isClickable &&
            bounds.top  > skipTop &&
            bounds.height() >= minH &&
            bounds.width()  >= minW
        ) {
            hits.add(Pair(node, bounds.top))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickable(child, hits, skipTop, minH, minW)
        }
    }
}
