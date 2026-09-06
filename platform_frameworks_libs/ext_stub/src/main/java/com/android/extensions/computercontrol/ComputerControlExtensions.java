// Compile stub for android17 extensions.computercontrol.
package com.android.extensions.computercontrol;

import android.content.Context;
import java.util.concurrent.Executor;

public class ComputerControlExtensions {
    private ComputerControlExtensions(Context context) {}

    public static ComputerControlExtensions getInstance(Context context) {
        return new ComputerControlExtensions(context);
    }

    public void registerAutomatedPackageListener(Context context, Executor executor,
            AutomatedPackageListener listener) {}
    public void unregisterAutomatedPackageListener(Context context, AutomatedPackageListener listener) {}
}
