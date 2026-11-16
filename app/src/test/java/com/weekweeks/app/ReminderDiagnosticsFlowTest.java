package com.weekweeks.app;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

/** Structural integration guards; Android delivery is exercised separately on service doubles/devices. */
public class ReminderDiagnosticsFlowTest {
    private Path main() {
        return Files.isDirectory(Path.of("src/main")) ? Path.of("src/main") : Path.of("app/src/main");
    }
    private String java(String file) throws Exception {
        return Files.readString(main().resolve("java/com/weekweeks/app/" + file + ".java"));
    }
    private Set<String> resources(String locale) throws Exception {
        NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(main().resolve("res/" + locale + "/strings_reminder_diagnostics.xml").toFile())
                .getElementsByTagName("string");
        Set<String> names = new HashSet<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String name = nodes.item(i).getAttributes().getNamedItem("name").getNodeValue();
            assertTrue("Duplicate diagnostic resource " + name, names.add(name));
            assertFalse(nodes.item(i).getTextContent().isBlank());
        }
        return names;
    }
    @Test public void allThreeLanguagesHaveExactlyTheSameDiagnosticEntries() throws Exception {
        Set<String> base = resources("values");
        assertTrue(base.size() >= 60);
        assertEquals(base, resources("values-en"));
        assertEquals(base, resources("values-zh-rTW"));
    }
    @Test public void diagnosticAlarmBranchesBeforeProductionButNeverRemovesGenerationGuard() throws Exception {
        String receiver = java("ClassReminderReceiver");
        assertTrue(receiver.indexOf("ReminderDiagnostics.isTestIntent(intent)")
                < receiver.indexOf("ReminderScheduler.scheduleNextFromBroadcast(context, intent)"));
        assertTrue(receiver.contains("ReminderDiagnostics.recordPost(context"));
        String scheduler = java("ReminderScheduler");
        assertTrue(scheduler.contains("generation != current.generation"));
        assertTrue(scheduler.contains("ReminderDiagnostics.recordReceive(context"));
        assertTrue(scheduler.contains("ReminderDiagnostics.recordSchedule(context"));
        assertTrue(java("ReminderBootReceiver").contains("ReminderDiagnostics.onSystemRestore(context)"));
    }
    @Test public void userTestUsesRealAlarmAndExistingChannelRatherThanForegroundTimerOrNewChannel() throws Exception {
        String diagnostics = java("ReminderDiagnostics");
        assertTrue(diagnostics.contains("DEFAULT_TEST_DELAY_MILLIS = 60_000L"));
        assertTrue(diagnostics.contains("alarm.setExactAndAllowWhileIdle"));
        assertTrue(diagnostics.contains("alarm.setAndAllowWhileIdle"));
        assertTrue(diagnostics.contains("ClassReminderReceiver.CHANNEL_ID"));
        assertFalse(diagnostics.contains("new NotificationChannel("));
        assertFalse(diagnostics.contains("postDelayed("));
        assertFalse(diagnostics.contains("replaceSchedule("));
        assertTrue(diagnostics.contains("manager.notify(TEST_TAG, TEST_ID, notification)"));
    }
    @Test public void dialogProvidesControlledCreateAndCleansUpRefresh() throws Exception {
        String dialog = java("ReminderStatusDialog");
        assertTrue(dialog.contains("static Dialog create(Activity activity, Runnable onClosed)"));
        assertTrue(dialog.contains("handler.removeCallbacks(update)"));
        assertTrue(dialog.contains("onClosed.run()"));
        assertTrue(dialog.contains("ReminderDiagnostics.sendImmediateTest(activity)"));
        assertTrue(dialog.contains("ReminderDiagnostics.scheduleTest(activity, ReminderDiagnostics.DEFAULT_TEST_DELAY_MILLIS)"));
        assertTrue(dialog.contains("ReminderDiagnostics.appNotificationSettings(activity)"));
        assertTrue(dialog.contains("ReminderDiagnostics.channelSettings(activity)"));
        assertTrue(dialog.contains("ReminderDiagnostics.exactAlarmSettings(activity)"));
    }
}
