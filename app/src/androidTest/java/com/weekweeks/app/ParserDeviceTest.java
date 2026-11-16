package com.weekweeks.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Android-runtime parser probes. Does not launch UI or read/write timetable storage.
 * Build using -I tools/course-audit.init.gradle -PparserAudit=true, then run:
 * adb shell am instrument -w com.weekweeks.app.audit.test/com.weekweeks.app.ParserDeviceTest
 * Require the PARSER PASS marker; successful adb transport alone is not a test pass.
 */
public final class ParserDeviceTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check("com.weekweeks.app.audit".equals(getTargetContext().getPackageName()), "Use isolated audit package");
            TimetableFileParser.Result xlsx = parse(asset("timetable-list-template.xlsx"), "template.xlsx");
            TimetableFileParser.Result csv = parse(asset("timetable-list-template.csv"), "template.csv");
            check(xlsx.courses.size() == 3 && csv.courses.size() == 3, "Template counts");
            for (int n = 0; n < 3; n++) check(equal(xlsx.courses.get(n), csv.courses.get(n)), "Template fields " + n);
            check("高等数学".equals(xlsx.courses.get(0).name), "UTF-8 SAX text");
            check("1-8周,10-16周".equals(xlsx.courses.get(2).weeks), "Quoted CSV weeks");
            TimetableFileParser.Result pdf = parse(asset("synthetic-timetable.pdf"), "synthetic-timetable.pdf");
            check(pdf.courses.size() == 4, "Synthetic PDF course count");
            java.util.List<String> rows = new java.util.ArrayList<>();
            for (PdfCourseParser.ParsedCourse course : pdf.courses) {
                rows.add(course.day + "\t" + course.period + "\t" + course.endPeriod + "\t" + course.weeks
                        + "\t" + course.room + "\t" + course.teacher + "\t" + course.name);
            }
            java.util.Collections.sort(rows);
            check(rows.equals(java.util.Arrays.asList(
                    "0\t1\t2\t1-16周\t示例楼A101\t示例教师甲\t虚构数学",
                    "1\t3\t4\t1-16周单周\t示例楼B202\t示例教师乙\t虚构英语",
                    "2\t5\t6\t1-8周,10-16周\t示例楼C303\t示例教师丙\t虚构实验",
                    "3\t7\t8\t2-16周双周\t示例楼D404\t示例教师丁\t虚构设计")),
                    "Synthetic PDF fields, including cross-page course title");
            String text = "课程名,星期,节次\n高等数学,周一,1-2";
            check("高等数学".equals(parse(text.getBytes(Charset.forName("GB18030")), "gb.csv").courses.get(0).name), "GB18030 decoder");
            byte[] utf16 = text.getBytes(StandardCharsets.UTF_16LE);
            byte[] bom = new byte[utf16.length + 2]; bom[0] = (byte) 0xff; bom[1] = (byte) 0xfe;
            System.arraycopy(utf16, 0, bom, 2, utf16.length);
            check(parse(bom, "utf16.csv").courses.size() == 1, "UTF-16 BOM decoder");
            rejects((text + "\nBroken,Mondayish,3").getBytes(StandardCharsets.UTF_8), "bad.csv");
            rejects(workbook(StandardCharsets.UTF_8), "dtd.xlsx");
            rejects(workbook(StandardCharsets.UTF_16LE), "encoding.xlsx");
            result.putString("stream", "PARSER PASS: Android XLSX SAX/ZIP, synthetic PDF full fields and cross-page title, CSV full-field equivalence, GB18030/UTF-16, all-or-nothing errors and XML DTD/encoding rejection. No UI/file-provider coverage.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private byte[] asset(String name) throws Exception {
        try (InputStream in = getContext().getAssets().open("parser-fixtures/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }
    private static TimetableFileParser.Result parse(byte[] bytes, String name) throws Exception {
        return TimetableFileParser.parse(bytes, name, 16, 1024 * 1024, 500);
    }
    private static void rejects(byte[] bytes, String name) throws Exception {
        try { parse(bytes, name); } catch (ImportException expected) { return; }
        throw new AssertionError("Unsafe or malformed input accepted: " + name);
    }
    private byte[] workbook(Charset charset) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.ZipInputStream input = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(asset("timetable-list-template.xlsx")));
                ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry entry; byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                ByteArrayOutputStream data = new ByteArrayOutputStream(); int read;
                while ((read = input.read(buffer)) != -1) data.write(buffer, 0, read);
                byte[] bytes = data.toByteArray();
                if ("xl/workbook.xml".equals(entry.getName())) {
                    String xml = new String(bytes, StandardCharsets.UTF_8).replaceFirst("^<\\?xml[^>]*\\?>", "");
                    bytes = ("<!DOCTYPE workbook [<!ENTITY injected \"x\">]>" + xml).getBytes(charset);
                }
                zip.putNextEntry(new ZipEntry(entry.getName())); zip.write(bytes); zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
    private static boolean equal(PdfCourseParser.ParsedCourse a, PdfCourseParser.ParsedCourse b) {
        return a.day == b.day && a.period == b.period && a.endPeriod == b.endPeriod
                && a.name.equals(b.name) && a.weeks.equals(b.weeks)
                && a.room.equals(b.room) && a.teacher.equals(b.teacher);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
