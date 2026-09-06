/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.monet;

import static com.android.systemui.monet.TonalPalette.SHADE_KEYS;

import android.util.Pair;

import com.google.ux.material.libmonet.dynamiccolor.DynamicColor;
import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme;
import com.google.ux.material.libmonet.dynamiccolor.MaterialDynamicColors;
import com.google.ux.material.libmonet.palettes.TonalPalette;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DynamicColors {
    /**
     * Gets all DynamicColor tokens for the neutral palettes.
     */
    public static List<Pair<String, DynamicColor>> getAllNeutralPalette() {
        List<Pair<String, Function<DynamicScheme, TonalPalette>>> neutralPaletteMap = Arrays.asList(
                new Pair<>("neutral1", (s) -> s.neutralPalette),
                new Pair<>("neutral2", (s) -> s.neutralVariantPalette)
        );
        // Call the helper method with the specific neutral palettes
        return generatePaletteColors(neutralPaletteMap);
    }

    /**
     * Gets all DynamicColor tokens for the accent palettes.
     */
    public static List<Pair<String, DynamicColor>> getAllAccentPalette() {
        List<Pair<String, Function<DynamicScheme, TonalPalette>>> accentPaletteMap = Arrays.asList(
                new Pair<>("accent1", (s) -> s.primaryPalette),
                new Pair<>("accent2", (s) -> s.secondaryPalette),
                new Pair<>("accent3", (s) -> s.tertiaryPalette)
        );
        // Call the helper method with the specific accent palettes
        return generatePaletteColors(accentPaletteMap);
    }

    /**
     * Gets all DynamicColor tokens for the error palette
     */
    public static List<Pair<String, DynamicColor>> getAllErrorPalette() {
        List<Pair<String, Function<DynamicScheme, TonalPalette>>> errorPaletteMap = Arrays.asList(
                new Pair<>("error", (s) -> s.errorPalette)
        );

        return generatePaletteColors(errorPaletteMap);
    }

    /**
     * List of all public Dynamic Color (Light and Dark) resources
     *
     * @return List of pairs of Resource Names / DynamicColor
     */
    public static List<Pair<String, DynamicColor>> getAllDynamicColorsMapped() {
        MaterialDynamicColors mdc = new MaterialDynamicColors();
        return generateSysUINames(mdc.allDynamicColors().stream().filter(
                dc -> !dc.get().name.contains("fixed")).toList());
    }

    /**
     * List of all public Static Color resources
     *
     * @return List of pairs of Resource Names / DynamicColor @return
     */
    public static List<Pair<String, DynamicColor>> getFixedColorsMapped() {
        MaterialDynamicColors mdc = new MaterialDynamicColors();

        return generateSysUINames(mdc.allDynamicColors().stream().filter(
                dc -> dc.get().name.contains("fixed")).toList());
    }

    /**
     * List of all private SystemUI Color resources
     *
     * @return List of pairs of Resource Names / DynamicColor
     */
    public static List<Pair<String, DynamicColor>> getCustomColorsMapped() {
        CustomDynamicColors customMdc = new CustomDynamicColors();
        return generateSysUINames(customMdc.allColors);
    }

    private static List<Pair<String, DynamicColor>> generateSysUINames(
            List<Supplier<DynamicColor>> allColors) {
        List<Pair<String, DynamicColor>> list = new ArrayList<>();

        for (Supplier<DynamicColor> supplier : allColors) {
            DynamicColor dynamicColor = supplier.get();
            String name = dynamicColor.name;

            // Fix tokens containing `palette_key_color` for SysUI requirements:
            // In SysUI palette_key_color should come first in the token name;
            String paletteMark = "palette_key_color";
            if (name.contains("_" + paletteMark)) {
                name = paletteMark + "_" + name.replace("_" + paletteMark, "");
            }

            list.add(new Pair(name, dynamicColor));
        }

        list.sort(Comparator.comparing(pair -> pair.first));
        return list;
    }

    private static List<Pair<String, DynamicColor>> generatePaletteColors(
            List<Pair<String, Function<DynamicScheme, TonalPalette>>> paletteMap) {

        return paletteMap.stream()
                .flatMap(palettePair -> {
                    String paletteName = palettePair.first;
                    Function<DynamicScheme, TonalPalette> paletteExtractor = palettePair.second;

                    // Stream over the shades for the current palette
                    return SHADE_KEYS.stream().map(shade -> {
                        String tokenName = paletteName + "_" + shade;

                        DynamicColor token = new DynamicColor(
                                /* name= */ tokenName,
                                /* palette= */ paletteExtractor,
                                /* tone= */ (s) -> (double) ((1000.0f - shade) / 10f),
                                /* isBackground= */ true,
                                /* background= */ null,
                                /* secondBackground= */ null,
                                /* contrastCurve= */ null,
                                /* toneDeltaPair= */ null);

                        return new Pair<>(tokenName, token);
                    });
                })
                .collect(Collectors.toList());
    }
}
