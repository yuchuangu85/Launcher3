package com.android.systemui.shared.system;

import android.app.ITaskStackListener;

/** Standalone fallback: the privileged task-stack callback registration is unavailable. */
public final class TaskStackChangeListeners {
    private static final TaskStackChangeListeners INSTANCE = new TaskStackChangeListeners();

    private TaskStackChangeListeners() {}

    public static TaskStackChangeListeners getInstance() {
        return INSTANCE;
    }

    public void registerTaskStackListener(TaskStackChangeListener listener) {}

    public void unregisterTaskStackListener(TaskStackChangeListener listener) {}

    public ITaskStackListener getListenerImpl() {
        return null;
    }
}
