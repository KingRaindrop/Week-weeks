package com.weekweeks.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import java.util.List;

/** A single lesson edit never exposes the semester-wide name, weeks, or delete action. */
final class LessonChangeDialog {
    interface Host {
        List<ScheduleConflicts.Hit> review(LessonSchedule.Change change);
        String save(LessonSchedule.Change change);
    }
    final AlertDialog dialog;
    private final Activity activity;
    private final Course course;
    private final EditText room;
    private final Spinner first,last;
    private final Button date;
    private final TextView error;
    private final Host host;
    private final SettingsDialogStyle style;
    private final int start,end;
    private int target;
    private DatePickerDialog picker;
    private AlertDialog confirmation;
    private String childKind;
    private ConflictListView conflictList;

    LessonChangeDialog(Activity activity,Course occurrence,int start,int end,int periodCount,Bundle state,Host host) {
        this.activity=activity; this.course=occurrence; this.start=start; this.end=end; this.host=host;
        target=state==null?occurrence.actualDate:state.getInt("target",occurrence.actualDate);
        style=SettingsDialogStyle.of(activity); LinearLayout body=style.body(), fields=style.group(); body.addView(fields);
        fields.addView(style.caption(activity.getString(R.string.scope_dates,ScheduleDay.label(occurrence.sourceDate))));
        fields.addView(style.heading(activity.getString(R.string.scope_on_date)));
        date=new Button(activity); date.setTag("lesson:date"); style.action(date,false); date.setText(ScheduleDay.label(target));
        fields.addView(date); date.setOnClickListener(v->pickDate(null));
        String[] options=new String[periodCount]; for(int i=0;i<options.length;i++) options[i]=activity.getString(R.string.main_period_n,i+1);
        first=periodField(fields,R.string.scope_start_period,options,state==null?occurrence.period:state.getInt("first"));
        last=periodField(fields,R.string.scope_end_period,options,state==null?occurrence.endPeriod:state.getInt("last"));
        room=new EditText(activity); room.setSingleLine(true); room.setId(View.generateViewId()); room.setTag("lesson:room");
        TextView label=style.heading(activity.getString(R.string.sheet_location)); label.setLabelFor(room.getId()); fields.addView(label);
        style.field(room); room.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        room.setText(state==null?occurrence.room:state.getString("room",occurrence.room)); fields.addView(room);
        error=style.caption(""); error.setTextColor(style.colors.error); error.setVisibility(View.GONE); fields.addView(error);
        dialog=style.builder().setTitle(R.string.scope_lesson_edit).setView(style.scrollBody(body))
                .setNegativeButton(R.string.action_cancel,null).setPositiveButton(R.string.action_save,null)
                .setNeutralButton(R.string.scope_stop,null).create();
    }
    private Spinner periodField(LinearLayout fields,int res,String[] values,int n) {
        TextView label=style.heading(activity.getString(res)); fields.addView(label);
        Spinner spinner=new Spinner(activity); spinner.setId(View.generateViewId()); label.setLabelFor(spinner.getId());
        spinner.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,values));
        spinner.setMinimumHeight((int)(48*activity.getResources().getDisplayMetrics().density));
        spinner.setSelection(Math.max(0,Math.min(values.length-1,n-1))); fields.addView(spinner); return spinner;
    }
    void opened(Bundle state) {
        dialog.getButton(-1).setOnClickListener(v->review(false)); dialog.getButton(-3).setOnClickListener(v->review(true));
        if(state!=null && state.containsKey("picker")) pickDate(state.getBundle("picker"));
        else if(state!=null && state.containsKey("child")) review("stop".equals(state.getString("child")), state.getBundle("conflict_anchor"));
    }
    private void pickDate(Bundle restored) {
        if(picker!=null) return;
        DatePickerDialog child=new DatePickerDialog(activity,(v,y,m,d)->{target=ScheduleDay.of(y,m+1,d);date.setText(ScheduleDay.label(target));},target/10000,target/100%100-1,target%100);
        child.getDatePicker().setMinDate(ScheduleDay.millis(start)); child.getDatePicker().setMaxDate(ScheduleDay.millis(end));
        picker=child; child.setOnDismissListener(d->{if(picker==child)picker=null;}); child.show();
        if(restored!=null) child.onRestoreInstanceState(restored); AppFonts.install(child);
    }
    private void review(boolean cancel) { review(cancel, null); }

    private void review(boolean cancel, Bundle anchor) {
        if(confirmation!=null && confirmation.isShowing()) return;
        int a=first.getSelectedItemPosition()+1,b=last.getSelectedItemPosition()+1;
        if(!cancel && b<a){error(activity.getString(R.string.scope_period_order));return;}
        LessonSchedule.Change change=new LessonSchedule.Change(course.sourceDate,cancel?0:target,cancel?0:a,cancel?0:b,room.getText().toString().trim());
        List<ScheduleConflicts.Hit> hits = cancel ? null : host.review(change);
        if (!cancel && (hits == null || hits.isEmpty())) { commit(change); return; }
        childKind = cancel ? "stop" : "conflict";
        AlertDialog.Builder prompt = style.builder();
        if (cancel) prompt.setTitle(R.string.scope_stop).setMessage(activity.getString(R.string.scope_stop_confirm, ScheduleDay.label(course.actualDate)));
        else {
            conflictList = new ConflictListView(activity, hits, style.colors.text, anchor);
            prompt.setTitle(ConflictListView.title(activity, hits)).setView(conflictList);
        }
        AlertDialog child = prompt.setPositiveButton(cancel ? R.string.scope_stop : R.string.conflict_keep, (d,w) -> commit(change))
                .setNegativeButton(cancel ? R.string.action_cancel : R.string.conflict_back, null).create();
        confirmation = child;
        child.setOnDismissListener(d -> { if (confirmation == child) { confirmation = null; childKind = null; conflictList = null; } });
        child.show(); style.apply(child);
    }
    private void commit(LessonSchedule.Change change) { String failure=host.save(change); if(failure==null) dialog.dismiss(); else error(failure); }
    private void error(String message) { error.setText(message); error.setVisibility(View.VISIBLE); }
    Bundle capture() {
        Bundle b=new Bundle();b.putString("kind","lesson_change");b.putLong("id",course.id);b.putInt("source",course.sourceDate);
        b.putInt("target",target);b.putInt("first",first.getSelectedItemPosition()+1);b.putInt("last",last.getSelectedItemPosition()+1);b.putString("room",room.getText().toString());
        if(picker!=null && picker.isShowing()) b.putBundle("picker",picker.onSaveInstanceState());
        if (confirmation != null && confirmation.isShowing()) {
            b.putString("child", childKind);
            if (conflictList != null) b.putBundle("conflict_anchor", conflictList.anchor());
        }
        return b;
    }
    void closeChildren(){ if(picker!=null)picker.dismiss(); if(confirmation!=null)confirmation.dismiss(); }
}
