// Compile stub for android17 extensions.computercontrol — mirrors
// frameworks/base/libs/computercontrol AutomatedPackageListener (interface).
package com.android.extensions.computercontrol;

import android.os.UserHandle;

import java.util.List;

public interface AutomatedPackageListener {
    /**
     * Called when the set of automated packages for a specific user and session owner has changed.
     *
     * @param automatingPackage The name of the package that owns the ComputerControlSession
     * @param automatedPackages The names of the packages being automated. May be empty,
     *   indicating that automation has stopped for all previously automated packages.
     * @param user The UserHandle of the profile of the automated packages.
     */
    void onAutomatedPackagesChanged(
            String automatingPackage,
            List<String> automatedPackages,
            UserHandle user);
}
