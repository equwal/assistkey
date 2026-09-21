package dev.equwal.assistkey.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemRecentsTest {

    /** Seen on the Viwoods reader when the Recents button was pressed. */
    @Test fun `the task manager of the Viwoods launcher is the recents of the system`() {
        assertTrue(Device.isSystemRecents("com.viwoods.launcher", "com.viwoods.launcher.task_manager.TaskManagerActivity"))
    }

    @Test fun `the home screen of the same launcher is not`() {
        assertFalse(Device.isSystemRecents("com.viwoods.launcher", "com.viwoods.launcher.main.LauncherMainActivity"))
    }

    @Test fun `stock Android recents count, other screens and Ink Recents do not`() {
        assertTrue(Device.isSystemRecents("com.android.launcher3", "com.android.quickstep.RecentsActivity"))
        assertTrue(Device.isSystemRecents("com.android.systemui", "com.android.systemui.recents.RecentsActivity"))
        assertFalse(Device.isSystemRecents("dev.equwal.inkrecents", "dev.equwal.inkrecents.RecentsActivity"))
        assertFalse(Device.isSystemRecents(null, null))
        assertFalse(Device.isSystemRecents("com.viwoods.launcher", null))
    }
}
