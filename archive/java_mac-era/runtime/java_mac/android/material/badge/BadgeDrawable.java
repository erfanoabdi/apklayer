// v1.0.101 -- real BadgeDrawable. Previously a 1-line stub.
// Mirrors com.google.android.material.badge.BadgeDrawable enough to
// render a red circle + optional number for Material's notification-
// count / unread-badge pattern. Attaches itself to a host view's
// overlay so the host's normal draw pass picks it up.
//
// Triggered by F-Droid's AppUpdateStatusManager (Updates tab pending
// count), generic chat apps' unread message counts, etc.
//
// AOSP source for reference:
//   https://github.com/material-components/material-components-android
//     /blob/master/lib/java/com/google/android/material/badge/BadgeDrawable.java
//
// Sizing convention (px, with our ~3x density assumption):
//   - dot-only:    16dp diameter
//   - with number: 16dp tall, width grows with digit count
//   - corner-anchored to host's top-right with 8dp inset
package android.material.badge;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class BadgeDrawable extends Drawable {
    private static final int DEFAULT_COLOR = 0xFFB3261E; // M3 error / "1 update" red
    private static final int TEXT_COLOR    = 0xFFFFFFFF; // white digits

    private int  number       = -1;       // -1 = "no number, render dot only"
    private int  maxCharCount = 4;
    private int  backgroundColor = DEFAULT_COLOR;
    private int  badgeTextColor  = TEXT_COLOR;
    private int  horizontalOffset = 0;
    private int  verticalOffset   = 0;

    public BadgeDrawable() {}

    /** Material's preferred factory; just news up an instance.
     *  Context is unused (no theme attribute resolution yet). */
    public static BadgeDrawable create(android.content.Context ctx) {
        return new BadgeDrawable();
    }
    public static BadgeDrawable createFromResource(android.content.Context ctx, int xmlResId) {
        return new BadgeDrawable();
    }

    public int  getNumber()              { return number; }
    public void setNumber(int n)         { this.number = n; invalidateSelf(); }
    public void clearNumber()            { this.number = -1; invalidateSelf(); }
    public boolean hasNumber()           { return number >= 0; }

    public int  getMaxCharacterCount()   { return maxCharCount; }
    public void setMaxCharacterCount(int n) { this.maxCharCount = Math.max(1, n); }

    public int  getBackgroundColor()     { return backgroundColor; }
    public void setBackgroundColor(int c){ this.backgroundColor = c; invalidateSelf(); }
    public int  getBadgeTextColor()      { return badgeTextColor; }
    public void setBadgeTextColor(int c) { this.badgeTextColor = c; invalidateSelf(); }

    public int  getHorizontalOffset()    { return horizontalOffset; }
    public void setHorizontalOffset(int o){ this.horizontalOffset = o; }
    public int  getVerticalOffset()      { return verticalOffset; }
    public void setVerticalOffset(int o) { this.verticalOffset = o; }

    /** Material calls this when the anchor view's bounds change to
     *  reposition the badge. We re-derive bounds based on the anchor's
     *  current right/top + offsets. customBadgeParent is the parent the
     *  badge should align within; ignored when null. */
    public void updateBadgeCoordinates(android.view.View anchor) {
        if (anchor == null) return;
        // Position at the top-right corner of the anchor view, inset
        // by ~4dp so the badge sits flush against the icon's edge.
        int ax = anchor.getRight();
        int ay = anchor.getTop();
        int size  = hasNumber() ? 56 : 32;   // tall enough for digit
        int width = hasNumber() ? Math.max(size, 24 + 18 * numberDigits()) : size;
        int left = ax - width  + 8 + horizontalOffset;
        int top  = ay - 8 - verticalOffset;
        setBounds(left, top, left + width, top + size);
    }
    public void updateBadgeCoordinates(android.view.View anchor, android.view.View customParent) {
        updateBadgeCoordinates(anchor);
    }

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null) return;
        Rect b = getBounds();
        if (b.isEmpty()) return;
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);
        p.setColor(backgroundColor);
        // Pill / circle background: corner radius == height/2.
        float r = b.height() / 2f;
        canvas.drawRoundRect(
            new android.graphics.RectF(b.left, b.top, b.right, b.bottom),
            r, r, p);
        if (hasNumber()) {
            String txt = displayString();
            p.setColor(badgeTextColor);
            p.setTextSize(b.height() * 0.6f);
            // Approximate vertical-center: AWT baseline is at ascent+top;
            // descent~=size*0.2, so center text by adding height/4.
            float tx = b.left + (b.width() - p.getTextSize() * (txt.length() * 0.55f)) / 2f;
            float ty = b.top  + b.height() / 2f + p.getTextSize() / 3f;
            canvas.drawText(txt, tx, ty, p);
        }
    }

    private int numberDigits() {
        int n = number;
        if (n <= 0) return 1;
        int d = 0;
        while (n > 0) { d++; n /= 10; }
        return d;
    }
    private String displayString() {
        int cap = (int) Math.pow(10, maxCharCount) - 1;
        return number > cap ? (cap + "+") : Integer.toString(number);
    }

    @Override public int  getOpacity() { return -3; /* TRANSLUCENT */ }
    @Override public void setAlpha(int a) {
        backgroundColor = (backgroundColor & 0x00FFFFFF) | ((a & 0xFF) << 24);
        invalidateSelf();
    }
    @Override public void setColorFilter(android.graphics.ColorFilter cf) {}
    @Override public int  getIntrinsicWidth()  { return hasNumber() ? 56 : 32; }
    @Override public int  getIntrinsicHeight() { return hasNumber() ? 56 : 32; }
}
