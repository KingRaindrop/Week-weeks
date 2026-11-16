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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Observation probes, not claims that the observed bugs are fixed. Audit package only. */
public final class InteractionAuditDeviceTest extends Instrumentation {
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
            courseDirtyCollision();
            lostSettingsDraft("showLanguageDialog", true);
            lostSettingsDraft("showAccentColorDialog", false);
            lostEventDatePicker();
        } catch (Throwable error) { failure = error; }
        finally {
            try { ui(() -> { for (Activity a : activities) if (!a.isDestroyed()) a.finish(); }); waitForIdleSync(); }
            catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
        }
        Bundle result = new Bundle();
        result.putString("stream", failure == null
                ? "INTERACTION AUDIT COMPLETE: four suspected problems reproduced; this is NOT a bug-free PASS. No course/event save or settings confirmation was invoked.\n"
                : "INTERACTION AUDIT ERROR\n" + android.util.Log.getStackTraceString(failure));
        finish(failure == null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void courseDirtyCollision() throws Throwable {
        Course course = new Course();
        course.id = 991810L; course.name = "Audit unsaved guard"; course.day = 0;
        course.period = 1; course.endPeriod = 1; course.weeks = "1-16";
        course.room = "A|B"; course.teacher = "C"; course.color = 0xFF446FA6;
        PastelCourseSheet[] sheet = new PastelCourseSheet[1];
        CountDownLatch closed = new CountDownLatch(1);
        ui(() -> {
            call(current, "showPastelCourseEditor", new Class<?>[]{Course.class,int.class,int.class}, course,0,1);
            sheet[0] = (PastelCourseSheet) field(current,"activeCourseEditor");
            check(sheet[0] != null && sheet[0].isShowing(), "Course editor did not open");
            Runnable previous = sheet[0].onDismissed;
            sheet[0].onDismissed = () -> { if (previous != null) previous.run(); closed.countDown(); };
            EditText[] inputs = (EditText[]) field(sheet[0],"activeCourseInputs");
            check("A|B".contentEquals(inputs[2].getText()) && "C".contentEquals(inputs[3].getText()), "Wrong baseline inputs");
            inputs[2].setText("A"); inputs[3].setText("B|C");
            ((BottomSheetDialog)field(sheet[0],"mainDialog")).cancel();
            check(field(sheet[0],"confirmation") == null, "Collision unexpectedly prompted discard");
        });
        check(closed.await(10,TimeUnit.SECONDS), "Collision editor did not close"); waitForIdleSync();
        ui(() -> check(!sheet[0].isShowing(), "Collision editor still visible"));
        status("CONFIRMED course room A|B / teacher C -> room A / teacher B|C: cancel closes without discard confirmation");
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

    private void lostSettingsDraft(String method, boolean language) throws Throwable {
        String beforeLanguage = AppLanguage.current(current);
        int[] beforeAccent = new int[1];
        ui(() -> {
            beforeAccent[0] = (Integer)call(current,"accentPresetIndex",new Class<?>[0]);
            call(current,method,new Class<?>[0]);
            AlertDialog dialog = ownedDialog();
            if (language) {
                ListView list = dialog.getListView();
                int target = (list.getCheckedItemPosition()+1)%list.getCount();
                list.performItemClick(null,target,list.getAdapter().getItemId(target));
                check(list.getCheckedItemPosition()==target,"Language tentative selection failed");
            } else {
                GridLayout grid = find(dialog.getWindow().getDecorView(),GridLayout.class);
                check(grid != null && grid.getChildCount()>1,"Accent grid absent");
                int target = (beforeAccent[0]+1)%grid.getChildCount();
                check(grid.getChildAt(target).performClick(),"Accent tentative click failed");
            }
            Bundle saved = new Bundle(); call(current,"onSaveInstanceState",new Class<?>[]{Bundle.class},saved);
            check(!saved.containsKey("settings_draft"),"Settings unexpectedly captured draft");
        });
        recreate();
        ui(() -> {
            check(((List<?>)field(current,"ownedDialogs")).isEmpty(),"Settings popup unexpectedly restored");
            check(beforeLanguage.equals(AppLanguage.current(current)),"Language changed without confirmation");
            check(beforeAccent[0] == (Integer)call(current,"accentPresetIndex",new Class<?>[0]),"Accent changed without confirmation");
        });
        status("CONFIRMED " + (language?"language":"accent") + " popup and tentative selection disappear after Activity recreation; saved setting unchanged");
    }

    private void lostEventDatePicker() throws Throwable {
        int[] original = new int[3];
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
            check(picker.getDayOfMonth()!=original[2] || picker.getMonth()+1!=original[1],"Date did not change");
            status("DATE tentative="+picker.getYear()+"-"+(picker.getMonth()+1)+"-"+picker.getDayOfMonth());
        });
        recreate();
        ui(() -> {
            PastelCourseSheet restored = (PastelCourseSheet)field(current,"activeEventEditor");
            check(restored!=null && restored.isShowing(),"Parent event draft not restored");
            check("Date picker audit draft".contentEquals(((EditText[])field(restored,"activeEventInputs"))[0].getText()),"Event text lost");
            check(activeDatePicker()==null,"Date picker unexpectedly restored");
            PersonalEvent draft=(PersonalEvent)field(restored,"activeEventDraft");
            check(draft.year==original[0] && draft.month==original[1] && draft.day==original[2],"Tentative date unexpectedly restored");
            status("CONFIRMED event date picker disappears on recreation and tentative date reverts; parent draft text survives");
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
    private void status(String message){Bundle b=new Bundle();b.putString("stream","INTERACTION AUDIT "+message+"\n");sendStatus(0,b);}
}
