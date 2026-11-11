package com.weekweeks.app;

import android.content.Context;
import android.content.res.Configuration;
import android.app.Instrumentation;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;

/** Framework-only device tests; use a separate applicationId for non-destructive audits. */
public class CourseCardDeviceTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            testLayoutAndActionsAcrossConfigurations();
            assertTrue("Legacy two-line room clipping must reproduce", legacyClipCount > 0);
            result.putString("stream", "PASS: 648 layout/action configurations and badge resizing; legacy clipping reproduced in " + legacyClipCount + " configurations\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable t) {
            result.putString("stream", android.util.Log.getStackTraceString(t));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private int legacyClipCount;
    private Instrumentation getInstrumentation() { return this; }
    private void assertTrue(String message, boolean condition) { if (!condition) throw new AssertionError(message); }
    private void assertEquals(String message, int expected, int actual) { assertTrue(message, expected == actual); }
    private Object field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private void set(Object owner, String name, Object value) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    public void testLayoutAndActionsAcrossConfigurations() throws Throwable {
        final Throwable[] failure = new Throwable[1];
        getInstrumentation().runOnMainSync(() -> {
            try {
                for (boolean dark : new boolean[]{false, true})
                    for (float scale : new float[]{1f, 1.5f, 2f})
                        for (String language : new String[]{"zh", "zh-TW", "en"})
                            for (int width : new int[]{280, 360, 600})
                                for (int periods : new int[]{1, 2, 4})
                                    for (boolean teacher : new boolean[]{false, true})
                                        for (boolean finished : new boolean[]{false, true})
                                            verify(dark, scale, language, width, periods, teacher, finished);
            } catch (Throwable t) { failure[0] = t; }
        });
        if (failure[0] != null) throw failure[0];
    }
    @SuppressWarnings("unchecked")
    private void verify(boolean dark, float scale, String language, int width, int periods, boolean teacher, boolean finished) throws Exception {
        Context base = getInstrumentation().getTargetContext();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = scale; config.setLocale(Locale.forLanguageTag(language));
        Context context = base.createConfigurationContext(config);
        int[] calls = new int[2];
        PastelTimetableView.Actions actions = (PastelTimetableView.Actions) Proxy.newProxyInstance(
                PastelTimetableView.Actions.class.getClassLoader(), new Class[]{PastelTimetableView.Actions.class},
                (p, m, a) -> { if (m.getName().equals("onCourse")) calls[0]++; if (m.getName().equals("onEditCourse")) calls[1]++; return null; });
        PastelTimetableView view = new PastelTimetableView(context, dark, null, actions);
        PastelTimetableView.Entry e = new PastelTimetableView.Entry();
        e.id = 71; e.day = 0; e.start = 1; e.end = periods; e.color = 0xFF446FA6;
        e.name = "Long course 高等数学与线性代数"; e.room = "Long room 教学大楼A101";
        e.teacherLabel = teacher ? "Professor 张老师" : "";
        ((List<PastelTimetableView.Entry>)field(view, "entries")).add(e);
        List<String> times = (List<String>)field(view, "times"); times.clear();
        for (int i = 0; i < 11; i++) times.add("08:00–08:45");
        set(view, "dayDateMillis", new long[]{946684800000L,946771200000L,946857600000L,946944000000L,947030400000L,947116800000L,947203200000L});
        if (!finished) set(view, "dayDateMillis", new long[]{4102444800000L,4102531200000L,4102617600000L,4102704000000L,4102790400000L,4102876800000L,4102963200000L});
        ViewGroup grid = (ViewGroup)field(view, "grid");
        int w = Math.round(width * context.getResources().getDisplayMetrics().density);
        grid.layout(0, 0, w, 3000);
        Method rebuild = grid.getClass().getDeclaredMethod("rebuild"); rebuild.setAccessible(true); rebuild.invoke(grid);
        grid.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(10000, View.MeasureSpec.AT_MOST));
        grid.layout(0, 0, w, grid.getMeasuredHeight());
        ViewGroup card = (ViewGroup)grid.getChildAt(grid.getChildCount()-1);
        String key = dark+"/"+scale+"/"+language+"/"+width+"/"+periods+"/"+teacher+"/"+finished;
        checkBounds(card, card.getHeight()-card.getPaddingBottom(), key);
        assertTrue(key, finished == card.getContentDescription().toString().contains(context.getString(R.string.course_finished_label)));
        Method badgeFactory = PastelTimetableView.class.getDeclaredMethod("finishedBadge", PastelTimetableView.Entry.class, boolean.class);
        badgeFactory.setAccessible(true);
        TextView badge = (TextView)badgeFactory.invoke(view, e, false);
        badge.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int badgeWidth : new int[]{600, 20, 600}) {
            badge.measure(View.MeasureSpec.makeMeasureSpec(badgeWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST));
            badge.layout(0, 0, badgeWidth, badge.getMeasuredHeight());
            assertTrue(key+" badge resize", badgeWidth == 20 ? "✓".contentEquals(badge.getText()) : badge.getText().toString().contains(context.getString(R.string.course_finished_label)));
        }
        card.performClick(); card.performLongClick();
        assertEquals(key, 1, calls[0]); assertEquals(key, 1, calls[1]);
        if (periods == 1 && !teacher) {
            TextView room = (TextView)card.getChildAt(1);
            TextView oldTitle = (TextView)field(card, "title");
            set(card, "title", null); // Disable the new measured-height guard for the old-layout comparison.
            oldTitle.setMaxLines(2);
            room.setMaxLines(2); // Reproduce the old layout, only inside this detached test tree.
            card.measure(View.MeasureSpec.makeMeasureSpec(card.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(card.getHeight(), View.MeasureSpec.EXACTLY));
            card.layout(card.getLeft(), card.getTop(), card.getRight(), card.getBottom());
            int visibleLines = Math.min(room.getMaxLines(), room.getLayout().getLineCount());
            if (room.getLayout().getLineBottom(visibleLines - 1) > room.getHeight()) legacyClipCount++;
        }
    }
    private void checkBounds(ViewGroup parent, int bottom, String key) {
        for (int i=0; i<parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView) {
                TextView t = (TextView)child;
                assertTrue(key+" clipped bottom: "+t.getText()+" "+child.getBottom()+">"+bottom, child.getBottom() <= bottom);
                assertTrue(key+" no text width: "+t.getText(), child.getWidth() > 0);
                if (t.getLayout()!=null) {
                    int visibleLines = Math.min(t.getMaxLines(), t.getLayout().getLineCount());
                    int visibleHeight = t.getLayout().getLineBottom(visibleLines - 1);
                    assertTrue(key+" visible text height: "+t.getText()+" "+visibleHeight+">"+t.getHeight(), visibleHeight <= t.getHeight());
                }
            }
            if (child instanceof ViewGroup) checkBounds((ViewGroup)child, Math.min(child.getHeight(), bottom-child.getTop()), key);
        }
    }
}
