package com.weekweeks.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Regression checks for the six interaction fixes. Fictional data, isolated audit package only. */
public final class InteractionFixDeviceTest extends Instrumentation {
    private Activity current;
    private final List<Activity> activities = new ArrayList<>();
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Throwable failure = null;
        try {
            check("com.weekweeks.app.audit".equals(getTargetContext().getPackageName()), "Refusing production target");
            check(android.os.Build.VERSION.SDK_INT >= 29, "WindowInspector probe requires API29+");
            current = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activities.add(current); waitForIdleSync();
            holidayCellDate();
            overlappingImportedCourses();
            courseDirtyCollision();
            settingsDraftSurvives("showLanguageDialog", true);
            settingsDraftSurvives("showAccentColorDialog", false);
            eventDatePickerSurvives();
        } catch (Throwable error) { failure = error; }
        finally {
            try { ui(() -> { for (Activity a : activities) if (!a.isDestroyed()) a.finish(); }); waitForIdleSync(); }
            catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
        }
        Bundle result = new Bundle();
        result.putString("stream", failure == null
                ? "INTERACTION FIXES PASS: six defects fixed; draft cancellation and course selection checks passed.\n"
                : "INTERACTION FIXES FAILED\n" + android.util.Log.getStackTraceString(failure));
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void holidayCellDate() throws Throwable {
        ui(() -> {
            long end = (Long) call(current, "calendarEndMillis", new Class<?>[0]);
            call(current, "browsePastelDate", new Class<?>[]{long.class}, AgendaDates.shift(end, 35));
            PastelTimetableView view = (PastelTimetableView) field(current, "pastelTimetable");
            check((Boolean) field(view, "outsideSemester"), "Expected holiday week");
            int today = (Integer) call(current, "currentSchoolDay", new Class<?>[0]);
            int selectedDay = (today + 2) % 7;
            long expected = ((long[]) field(view, "dayDateMillis"))[selectedDay];
            ViewGroup grid = (ViewGroup) field(view, "grid");
            String[] days = current.getResources().getStringArray(R.array.view_days);
            String description = current.getString(R.string.view_cell_outside_term_desc, days[selectedDay]);
            View target = findDescription(grid, description);
            check(target != null && target.performLongClick(), "Holiday cell long click failed: " + description);
            PastelCourseSheet sheet = (PastelCourseSheet) field(current, "activeEventEditor");
            check(sheet != null && sheet.isShowing(), "Holiday long press did not open event editor");
            PersonalEvent draft = (PersonalEvent) field(sheet, "activeEventDraft");
            check(draft.dayMillis() == expected, "Holiday cell lost the selected date");
            status("PASS holiday blank cell date=" + CalendarSetupDialog.formatDate(expected)
                    + " matches event date=" + CalendarSetupDialog.formatDate(draft.dayMillis())
                    + "; clicked weekday=" + selectedDay + " current weekday=" + today);
            sheet.dismissForDestroy();
        });
        waitForIdleSync();
    }

    private void overlappingImportedCourses() throws Throwable {
        long[][] clicked = {null};
        android.app.Dialog[] renderDialog = {null};
        ViewGroup[] gridHolder = {null};
        List<Course> candidate = new ArrayList<>();
        ui(() -> {
            String text = "Audit Math | 周一 | 1-2 | 1-16周 | Room A | Teacher A\n"
                    + "Audit English | 周一 | 1-2 | 1-16周 | Room B | Teacher B";
            TextTimetableParser.Result parsed = TextTimetableParser.parse(text, 24, 300);
            check(parsed.errors.isEmpty() && parsed.courses.size() == 2, "Overlap fixture did not parse");
            List<Course> imported = new ArrayList<>();
            for (PdfCourseParser.ParsedCourse item : parsed.courses) {
                Course c = new Course(); c.name=item.name; c.day=item.day; c.period=item.period;
                c.endPeriod=item.endPeriod; c.weeks=item.weeks; c.room=item.room; c.teacher=item.teacher;
                imported.add(c);
            }
            ImportPlanner.Result result = ImportPlanner.merge(candidate, imported, 300, 200,
                    (c, staged, sequence) -> c.id = 900001L + sequence);
            check(result.imported == 2 && result.skipped.isEmpty(), "Import now handles overlapping rows");
            PastelTimetableView.Actions actions = (PastelTimetableView.Actions) java.lang.reflect.Proxy.newProxyInstance(
                    PastelTimetableView.Actions.class.getClassLoader(), new Class[]{PastelTimetableView.Actions.class},
                    (proxy, method, args) -> { if (method.getName().equals("onCourses")) clicked[0] = (long[]) args[0]; return null; });
            PastelTimetableView view = new PastelTimetableView(current, false, null, actions);
            List<PastelTimetableView.Entry> entries = new ArrayList<>();
            for (Course c : candidate) {
                PastelTimetableView.Entry e = new PastelTimetableView.Entry();
                e.id=c.id; e.day=c.day; e.start=c.period; e.end=c.endPeriod; e.name=c.name;
                e.room=c.room; e.color=0xFFE2E9FC; entries.add(e);
            }
            view.bind(entries, java.util.Arrays.asList("08:00-08:45", "08:55-09:40"), "Audit week", true,
                    true, true, null, "", "", false, false);
            renderDialog[0] = new android.app.Dialog(current);
            renderDialog[0].setContentView(view); renderDialog[0].show();
            gridHolder[0] = (ViewGroup) field(view, "grid");
        });
        waitForIdleSync();
        saveViewScreenshot(gridHolder[0], "overlap-grid.png");
        ui(() -> {
            ViewGroup grid = gridHolder[0];
            View card = grid.getChildAt(grid.getChildCount()-1);
            android.graphics.Rect ra = new android.graphics.Rect();
            card.getHitRect(ra);
            check(!ra.isEmpty(), "Overlap group has no hit region");
            check(card.getContentDescription().toString().contains("Audit Math")
                    && card.getContentDescription().toString().contains("Audit English"), "Group omits course names");
            long at = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent down = android.view.MotionEvent.obtain(at, at, android.view.MotionEvent.ACTION_DOWN,
                    ra.exactCenterX(), ra.exactCenterY(), 0);
            android.view.MotionEvent up = android.view.MotionEvent.obtain(at, at+30, android.view.MotionEvent.ACTION_UP,
                    ra.exactCenterX(), ra.exactCenterY(), 0);
            grid.dispatchTouchEvent(down); grid.dispatchTouchEvent(up); down.recycle(); up.recycle();
        });
        waitForIdleSync();
        ui(() -> {
            renderDialog[0].dismiss();
            check(clicked[0] != null && clicked[0].length == 2
                    && clicked[0][0] == candidate.get(0).id && clicked[0][1] == candidate.get(1).id,
                    "Overlap group did not expose both courses");
            String preview = (String) call(current, "importPreviewText", new Class<?>[]{List.class}, candidate);
            check(preview.contains(current.getString(R.string.import_overlap_explanation)),
                    "Import preview did not warn about the conflict");
        });
        waitForIdleSync();
        @SuppressWarnings("unchecked") List<Course> live = (List<Course>) field(current, "courses");
        ui(() -> live.addAll(candidate));
        try {
            for (int index = 0; index < 2; index++) {
                final int chosen = index;
                ui(() -> {
                    call(current, "showOverlappingCourses", new Class<?>[]{long[].class}, (Object) clicked[0]);
                    AlertDialog picker = ownedDialog();
                    check(picker.getListView().getCount() == 2, "Course picker lost a row");
                    ListView list = picker.getListView();
                    list.performItemClick(null, chosen, list.getAdapter().getItemId(chosen));
                });
                waitForIdleSync();
                ui(() -> {
                    check((Long) field(current, "activeCourseDetailId") == candidate.get(chosen).id,
                            "Course picker opened the wrong detail");
                    PastelCourseSheet detail = (PastelCourseSheet) field(current, "activeCourseDetail");
                    check(detail != null && detail.isShowing(), "Selected course detail did not open");
                    detail.dismissForDestroy();
                });
                waitForIdleSync();
            }
        } finally { ui(() -> live.removeAll(candidate)); }
        status("PASS overlapping imports warn before saving; one shared grid entry exposes both courses; both details open");
    }

    private static View findDescription(View root, String description) {
        if (description.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i=0; i<((ViewGroup)root).getChildCount(); i++) {
            View found=findDescription(((ViewGroup)root).getChildAt(i), description); if(found!=null) return found;
        }
        return null;
    }

    private void courseDirtyCollision() throws Throwable {
        Course course = new Course();
        course.id = 991810L; course.name = "Audit unsaved guard"; course.day = 0;
        course.period = 1; course.endPeriod = 1; course.weeks = "1-16";
        course.room = "A|B"; course.teacher = "C"; course.color = 0xFF446FA6;
        PastelCourseSheet[] sheet = new PastelCourseSheet[1];
        ui(() -> {
            call(current, "showPastelCourseEditor", new Class<?>[]{Course.class,int.class,int.class}, course,0,1);
            sheet[0] = (PastelCourseSheet) field(current,"activeCourseEditor");
            check(sheet[0] != null && sheet[0].isShowing(), "Course editor did not open");
            EditText[] inputs = (EditText[]) field(sheet[0],"activeCourseInputs");
            check("A|B".contentEquals(inputs[2].getText()) && "C".contentEquals(inputs[3].getText()), "Wrong baseline inputs");
            inputs[2].setText("A"); inputs[3].setText("B|C");
            ((BottomSheetDialog)field(sheet[0],"mainDialog")).cancel();
            check(field(sheet[0],"confirmation") != null && sheet[0].isShowing(), "Collision bypassed discard guard");
            ((BottomSheetDialog)field(sheet[0],"confirmation")).cancel();
        });
        waitForIdleSync();
        ui(() -> check(sheet[0].isShowing(), "Cancel discard closed the course editor"));
        ui(() -> {
            PastelCourseSheet restored = (PastelCourseSheet) field(current, "activeCourseEditor");
            check(restored != null && restored.isShowing(), "Dirty course editor was closed");
            ((BottomSheetDialog)field(restored,"mainDialog")).cancel();
            check(field(restored,"confirmation") != null, "Cancel discard lost the dirty baseline");
            restored.dismissForDestroy();
        });
        waitForIdleSync();
        status("PASS pipe-containing course changes prompt before discard; declining discard keeps the edited form");
        ui(() -> {
            call(current, "showPastelCourseEditor", new Class<?>[]{Course.class,int.class,int.class}, course,0,1);
            PastelCourseSheet control = (PastelCourseSheet)field(current,"activeCourseEditor");
            ((EditText[])field(control,"activeCourseInputs"))[2].setText("ordinary changed room");
            ((BottomSheetDialog)field(control,"mainDialog")).cancel();
            check(control.isShowing() && field(control,"confirmation") != null, "Ordinary dirty control failed");
            control.dismissForDestroy();
        });
        waitForIdleSync();
        status("CONTROL ordinary course edit does prompt before discarding");
    }

    private void settingsDraftSurvives(String method, boolean language) throws Throwable {
        String beforeLanguage = AppLanguage.current(current);
        int[] beforeAccent = new int[1];
        int[] selected = {-1};
        ui(() -> {
            beforeAccent[0] = (Integer)call(current,"accentPresetIndex",new Class<?>[0]);
            call(current,method,new Class<?>[0]);
            AlertDialog dialog = ownedDialog();
            if (language) {
                ListView list = dialog.getListView();
                int target = (list.getCheckedItemPosition()+1)%list.getCount();
                selected[0] = target;
                list.performItemClick(null,target,list.getAdapter().getItemId(target));
                check(list.getCheckedItemPosition()==target,"Language tentative selection failed");
            } else {
                GridLayout grid = find(dialog.getWindow().getDecorView(),GridLayout.class);
                check(grid != null && grid.getChildCount()>1,"Accent grid absent");
                int target = (beforeAccent[0]+1)%grid.getChildCount();
                selected[0] = target;
                check(grid.getChildAt(target).performClick(),"Accent tentative click failed");
            }
            Bundle saved = new Bundle(); call(current,"onSaveInstanceState",new Class<?>[]{Bundle.class},saved);
            check(saved.getBundle("settings_draft") != null
                    && saved.getBundle("settings_draft").getInt("selected", -1) == selected[0],"Tentative choice was not captured");
        });
        recreate();
        ui(() -> {
            AlertDialog restored = ownedDialog();
            if (language) check(restored.getListView().getCheckedItemPosition()==selected[0], "Language choice was lost");
            else {
                GridLayout grid = find(restored.getWindow().getDecorView(), GridLayout.class);
                check(grid != null && grid.getChildAt(selected[0]).getContentDescription().toString()
                        .contains(current.getString(R.string.main_selected_short_suffix)), "Accent choice was lost");
            }
            check(beforeLanguage.equals(AppLanguage.current(current)),"Language changed without confirmation");
            check(beforeAccent[0] == (Integer)call(current,"accentPresetIndex",new Class<?>[0]),"Accent changed without confirmation");
            restored.cancel();
        });
        waitForIdleSync();
        recreate();
        ui(() -> {
            check(((List<?>)field(current,"ownedDialogs")).isEmpty(), "Cancelled settings popup revived");
            check(beforeLanguage.equals(AppLanguage.current(current)), "Cancelled language persisted");
            check(beforeAccent[0] == (Integer)call(current,"accentPresetIndex",new Class<?>[0]), "Cancelled accent persisted");
        });
        status("PASS " + (language?"language":"accent") + " tentative selection survives rotation; cancellation does not save or reopen it");
    }

    private void eventDatePickerSurvives() throws Throwable {
        int[] original = new int[3];
        int[] tentative = new int[3];
        ui(() -> {
            call(current,"showPersonalEventEditor",new Class<?>[]{PersonalEvent.class},(Object)null);
            PastelCourseSheet sheet = (PastelCourseSheet)field(current,"activeEventEditor");
            check(sheet != null,"Event editor absent");
            ((EditText[])field(sheet,"activeEventInputs"))[0].setText("Date picker audit draft");
            PersonalEvent draft = (PersonalEvent)field(sheet,"activeEventDraft");
            original[0]=draft.year; original[1]=draft.month; original[2]=draft.day;
            TextView choose = findText(((BottomSheetDialog)field(sheet,"mainDialog")).getWindow().getDecorView(),
                    current.getString(R.string.sheet_pick_date));
            check(choose != null && choose.performClick(),"Date picker action failed");
        });
        waitForIdleSync();
        ui(() -> {
            DatePicker picker = activeDatePicker(); check(picker != null,"Date picker not attached");
            Calendar date=Calendar.getInstance(); date.set(original[0],original[1]-1,original[2]); date.add(Calendar.DAY_OF_YEAR,10);
            picker.updateDate(date.get(Calendar.YEAR),date.get(Calendar.MONTH),date.get(Calendar.DAY_OF_MONTH));
            tentative[0]=picker.getYear(); tentative[1]=picker.getMonth()+1; tentative[2]=picker.getDayOfMonth();
            check(picker.getDayOfMonth()!=original[2] || picker.getMonth()+1!=original[1],"Date did not change");
            status("DATE tentative="+picker.getYear()+"-"+(picker.getMonth()+1)+"-"+picker.getDayOfMonth());
        });
        recreate();
        ui(() -> {
            PastelCourseSheet restored = (PastelCourseSheet)field(current,"activeEventEditor");
            check(restored!=null && restored.isShowing(),"Parent event draft not restored");
            check("Date picker audit draft".contentEquals(((EditText[])field(restored,"activeEventInputs"))[0].getText()),"Event text lost");
            DatePicker picker = activeDatePicker();
            check(picker != null && picker.getYear()==tentative[0] && picker.getMonth()+1==tentative[1]
                    && picker.getDayOfMonth()==tentative[2], "Tentative date was not restored");
            PersonalEvent draft=(PersonalEvent)field(restored,"activeEventDraft");
            check(draft.year==original[0] && draft.month==original[1] && draft.day==original[2],"Tentative date unexpectedly restored");
            check(((List<?>)field(current,"ownedDialogs")).isEmpty(), "Child date picker took primary ownership");
            android.app.DatePickerDialog dialog = (android.app.DatePickerDialog) field(restored,"activeEventDatePicker");
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
        });
        waitForIdleSync();
        ui(() -> {
            PastelCourseSheet sheet = (PastelCourseSheet) field(current, "activeEventEditor");
            PersonalEvent draft = (PersonalEvent) field(sheet,"activeEventDraft");
            check(draft.year==original[0] && draft.month==original[1] && draft.day==original[2], "Cancel date applied tentative date");
        });
        recreate();
        ui(() -> {
            check(activeDatePicker()==null, "Cancelled date picker revived");
            PastelCourseSheet sheet=(PastelCourseSheet)field(current,"activeEventEditor");
            TextView choose=findText(((BottomSheetDialog)field(sheet,"mainDialog")).getWindow().getDecorView(),current.getString(R.string.sheet_pick_date));
            check(choose != null && choose.performClick(),"Date picker did not reopen");
        });
        waitForIdleSync();
        ui(() -> {
            PastelCourseSheet sheet=(PastelCourseSheet)field(current,"activeEventEditor");
            DatePicker picker=activeDatePicker(); check(picker != null,"Reopened picker is not attached");
            picker.updateDate(tentative[0],tentative[1]-1,tentative[2]);
            ((android.app.DatePickerDialog)field(sheet,"activeEventDatePicker"))
                    .getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        });
        waitForIdleSync();
        ui(() -> {
            PastelCourseSheet sheet=(PastelCourseSheet)field(current,"activeEventEditor");
            PersonalEvent draft=(PersonalEvent)field(sheet,"activeEventDraft");
            check(draft.year==tentative[0] && draft.month==tentative[1] && draft.day==tentative[2], "Confirm date did not apply selection");
            sheet.dismissForDestroy();
        });
        waitForIdleSync();
        ui(() -> check(activeDatePicker()==null, "Child date picker leaked after parent close"));
        status("PASS event date picker and tentative date survive rotation; cancel preserves original date, confirm applies selection, close cleans child");
    }

    private void saveViewScreenshot(View view, String name) throws Throwable {
        ui(() -> {
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    view.getWidth(), view.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                    new java.io.File(getTargetContext().getExternalFilesDir(null), name))) {
                view.draw(new android.graphics.Canvas(bitmap));
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            } finally { bitmap.recycle(); }
        });
    }

    private void recreate() throws Throwable {
        Activity old=current;
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        try {
            boolean landscape = old.getResources().getConfiguration().orientation
                    != android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            ui(() -> old.setRequestedOrientation(landscape
                    ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            current=waitForMonitorWithTimeout(monitor,10000);
            check(current!=null && current!=old,"Activity not recreated"); activities.add(current); waitForIdleSync();
            check(current.getResources().getConfiguration().orientation == (landscape
                    ? android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    : android.content.res.Configuration.ORIENTATION_PORTRAIT),"Orientation did not actually change");
            status("Actual Activity orientation changed to " + (landscape ? "landscape" : "portrait"));
        } finally { removeMonitor(monitor); }
    }
    private AlertDialog ownedDialog() throws Exception {
        List<?> owned=(List<?>)field(current,"ownedDialogs"); check(owned.size()==1,"Expected one owned popup");
        return (AlertDialog)owned.get(0);
    }
    private DatePicker activeDatePicker() {
        for(View root:WindowInspector.getGlobalWindowViews()) {
            if(!root.isAttachedToWindow()) continue;
            DatePicker picker=find(root,DatePicker.class); if(picker!=null && picker.isShown()) return picker;
        }
        return null;
    }
    private static <T extends View> T find(View root,Class<T> type) {
        if(type.isInstance(root)) return type.cast(root);
        if(root instanceof ViewGroup) for(int n=0;n<((ViewGroup)root).getChildCount();n++) {
            T found=find(((ViewGroup)root).getChildAt(n),type); if(found!=null) return found;
        }
        return null;
    }
    private static TextView findText(View root,String text) {
        if(root instanceof TextView && text.contentEquals(((TextView)root).getText())) return (TextView)root;
        if(root instanceof ViewGroup) for(int n=0;n<((ViewGroup)root).getChildCount();n++) {
            TextView found=findText(((ViewGroup)root).getChildAt(n),text); if(found!=null) return found;
        }
        return null;
    }
    private interface Checked { void run() throws Throwable; }
    private void ui(Checked step) throws Throwable {
        Throwable[] error={null}; runOnMainSync(()->{try{step.run();}catch(Throwable t){error[0]=t;}});
        if(error[0]!=null) throw error[0];
    }
    private static Object field(Object owner,String name) throws Exception {
        Class<?> type=owner.getClass();
        while(type!=null){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException e){type=type.getSuperclass();}}
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object owner,String name,Class<?>[] types,Object... args) throws Exception {
        Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void status(String message){Bundle b=new Bundle();b.putString("stream","INTERACTION FIXES "+message+"\n");sendStatus(0,b);}
}
