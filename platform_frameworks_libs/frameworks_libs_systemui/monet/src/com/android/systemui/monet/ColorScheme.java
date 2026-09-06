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


import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.annotation.Size;
import android.app.WallpaperColors;
import android.content.theming.ThemeStyle;
import android.graphics.Color;

import com.android.internal.graphics.ColorUtils;

import com.google.ux.material.libmonet.dynamiccolor.ColorSpec.SpecVersion;
import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme;
import com.google.ux.material.libmonet.dynamiccolor.DynamicScheme.Platform;
import com.google.ux.material.libmonet.hct.Hct;
import com.google.ux.material.libmonet.scheme.SchemeCmf;
import com.google.ux.material.libmonet.scheme.SchemeContent;
import com.google.ux.material.libmonet.scheme.SchemeExpressive;
import com.google.ux.material.libmonet.scheme.SchemeFruitSalad;
import com.google.ux.material.libmonet.scheme.SchemeMonochrome;
import com.google.ux.material.libmonet.scheme.SchemeNeutral;
import com.google.ux.material.libmonet.scheme.SchemeRainbow;
import com.google.ux.material.libmonet.scheme.SchemeTonalSpot;
import com.google.ux.material.libmonet.scheme.SchemeVibrant;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @deprecated Please use com.google.ux.material.libmonet.dynamiccolor.MaterialDynamicColors instead
 */
@Deprecated
public class ColorScheme {
    public static final int GOOGLE_BLUE = 0xFF1b6ef3;
    public static final float CONTRAST = 0.0f;
    private static final float ACCENT1_CHROMA = 48.0f;
    private static final int MIN_CHROMA = 5;

    @ColorInt
    private final List<Integer> mSeeds;
    private final boolean mIsDark;
    @ThemeStyle.Type
    private final int mStyle;
    private final DynamicScheme mMaterialScheme;
    private final TonalPalette mAccent1;
    private final TonalPalette mAccent2;
    private final TonalPalette mAccent3;
    private final TonalPalette mNeutral1;
    private final TonalPalette mNeutral2;
    private final TonalPalette mError;
    private final List<Hct> mProposedSeedHcts;
    private final double mContrast;


    public ColorScheme(@ColorInt int seed, boolean isDark, @ThemeStyle.Type int style,
            double contrastLevel, SpecVersion specVersion, Platform platform) {
        this(List.of(seed), isDark, style, contrastLevel, specVersion, platform);
    }

    public ColorScheme(@NonNull @Size(min = 1) List<Integer> seeds, boolean isDark,
            @ThemeStyle.Type int style,
            double contrastLevel, SpecVersion specVersion, Platform platform) {

        this.mSeeds = seeds;
        this.mIsDark = isDark;
        this.mStyle = style;
        this.mContrast = contrastLevel;

        mProposedSeedHcts = seeds.stream().map(Hct::fromInt).toList();

        List<Hct> seedHcts = mSeeds.stream().map(seed -> {
            Hct proposedSeedHct = Hct.fromInt(seed);

            return Hct.fromInt(
                    seed == Color.TRANSPARENT
                            ? GOOGLE_BLUE
                            : (style != ThemeStyle.CONTENT
                                    && proposedSeedHct.getChroma() < 5
                                    ? GOOGLE_BLUE
                                    : seed));
        }).toList();

        mMaterialScheme = switch (style) {
            case ThemeStyle.SPRITZ -> new SchemeNeutral(seedHcts, isDark, contrastLevel,
                    specVersion,
                    platform);
            case ThemeStyle.TONAL_SPOT -> new SchemeTonalSpot(seedHcts, isDark, contrastLevel,
                    specVersion, platform);
            case ThemeStyle.VIBRANT -> new SchemeVibrant(seedHcts, isDark, contrastLevel,
                    specVersion, platform);
            case ThemeStyle.EXPRESSIVE -> new SchemeExpressive(seedHcts, isDark, contrastLevel,
                    specVersion, platform);
            case ThemeStyle.RAINBOW -> new SchemeRainbow(seedHcts, isDark, contrastLevel,
                    specVersion, platform);
            case ThemeStyle.FRUIT_SALAD -> new SchemeFruitSalad(seedHcts, isDark, contrastLevel,
                    specVersion, platform);
            case ThemeStyle.CONTENT -> new SchemeContent(seedHcts, isDark, contrastLevel,
                    specVersion, platform);
            case ThemeStyle.MONOCHROMATIC -> new SchemeMonochrome(seedHcts, isDark, contrastLevel,
                    specVersion, platform);
            case ThemeStyle.CMF -> new SchemeCmf(seedHcts, isDark, contrastLevel,
                    specVersion, platform);


            // SystemUI Schemes
            case ThemeStyle.CLOCK -> new SchemeClock(seedHcts.getFirst(), isDark, contrastLevel);
            case ThemeStyle.CLOCK_VIBRANT -> new SchemeClockVibrant(seedHcts.getFirst(), isDark,
                    contrastLevel);
            default -> throw new IllegalArgumentException("Unknown style: " + style);
        };

        mAccent1 = new TonalPalette(mMaterialScheme.primaryPalette);
        mAccent2 = new TonalPalette(mMaterialScheme.secondaryPalette);
        mAccent3 = new TonalPalette(mMaterialScheme.tertiaryPalette);
        mNeutral1 = new TonalPalette(mMaterialScheme.neutralPalette);
        mNeutral2 = new TonalPalette(mMaterialScheme.neutralVariantPalette);
        mError = new TonalPalette(mMaterialScheme.errorPalette);
    }

    public ColorScheme(@ColorInt int seed, boolean isDark, @ThemeStyle.Type int style,
            double contrastLevel) {
        this(seed, isDark, style, contrastLevel, SpecVersion.SPEC_2026,
                DynamicScheme.DEFAULT_PLATFORM);
    }

    public ColorScheme(@ColorInt int seed, boolean darkTheme) {
        this(seed, darkTheme, ThemeStyle.TONAL_SPOT);
    }

    public ColorScheme(@ColorInt int seed, boolean darkTheme, @ThemeStyle.Type int style) {
        this(seed, darkTheme, style, 0.0);
    }

    public ColorScheme(WallpaperColors wallpaperColors, boolean darkTheme,
            @ThemeStyle.Type int style) {
        this(getSeedColors(wallpaperColors, style != ThemeStyle.CONTENT), darkTheme, style, 0.0,
                SpecVersion.SPEC_2026, DynamicScheme.DEFAULT_PLATFORM);
    }

    public ColorScheme(WallpaperColors wallpaperColors, boolean darkTheme) {
        this(wallpaperColors, darkTheme, ThemeStyle.TONAL_SPOT);
    }

    public int getBackgroundColor() {
        return ColorUtils.setAlphaComponent(mIsDark
                ? mNeutral1.getS700()
                : mNeutral1.getS10(), 0xFF);
    }

    public int getAccentColor() {
        return ColorUtils.setAlphaComponent(mIsDark
                ? mAccent1.getS100()
                : mAccent1.getS500(), 0xFF);
    }

    public double getSeedTone() {
        return 1000d - mProposedSeedHcts.getFirst().getTone() * 10d;
    }

    public int getSeed() {
        return mSeeds.getFirst();
    }

    public List<Integer> getSeeds() {
        return mSeeds;
    }

    @ThemeStyle.Type
    public int getStyle() {
        return mStyle;
    }

    public DynamicScheme getMaterialScheme() {
        return mMaterialScheme;
    }

    public TonalPalette getAccent1() {
        return mAccent1;
    }

    public TonalPalette getAccent2() {
        return mAccent2;
    }

    public TonalPalette getAccent3() {
        return mAccent3;
    }

    public TonalPalette getNeutral1() {
        return mNeutral1;
    }

    public TonalPalette getNeutral2() {
        return mNeutral2;
    }

    public TonalPalette getError() {
        return mError;
    }

    @Override
    public String toString() {
        return "ColorScheme {\n"
                + "  seed colors: " + mSeeds.stream().map(ColorScheme::stringForColor).collect(
                Collectors.joining(", ")) + "\n"
                + "  style: " + mStyle + "\n"
                + "  palettes: \n"
                + "  " + humanReadable("PRIMARY", mAccent1.allShades) + "\n"
                + "  " + humanReadable("SECONDARY", mAccent2.allShades) + "\n"
                + "  " + humanReadable("TERTIARY", mAccent3.allShades) + "\n"
                + "  " + humanReadable("NEUTRAL", mNeutral1.allShades) + "\n"
                + "  " + humanReadable("NEUTRAL VARIANT", mNeutral2.allShades) + "\n"
                + "}";
    }

    /**
     * Identifies a color to create a color scheme from.
     *
     * @param wallpaperColors Colors extracted from an image via quantization.
     * @param filter          If false, allow colors that have low chroma, creating grayscale
     *                        themes.
     * @return ARGB int representing the color
     */
    @ColorInt
    public static int getSeedColor(WallpaperColors wallpaperColors, boolean filter) {
        return getSeedColors(wallpaperColors, filter).get(0);
    }

    /**
     * Identifies a color to create a color scheme from. Defaults Filter to TRUE
     *
     * @param wallpaperColors Colors extracted from an image via quantization.
     * @return ARGB int representing the color
     */
    public static int getSeedColor(WallpaperColors wallpaperColors) {
        return getSeedColor(wallpaperColors, true);
    }

    /**
     * Filters and ranks colors from WallpaperColors.
     *
     * @param wallpaperColors Colors extracted from an image via quantization.
     * @param filter          If false, allow colors that have low chroma, creating grayscale
     *                        themes.
     * @return List of ARGB ints, ordered from highest scoring to lowest.
     */
    public static List<Integer> getSeedColors(WallpaperColors wallpaperColors, boolean filter) {
        double totalPopulation = wallpaperColors.getAllColors().values().stream().mapToInt(
                Integer::intValue).sum();
        boolean totalPopulationMeaningless = (totalPopulation == 0.0);

        if (totalPopulationMeaningless) {
            // WallpaperColors with a population of 0 indicate the colors didn't come from
            // quantization. Instead of scoring, trust the ordering of the provided primary
            // secondary/tertiary colors.
            //
            // In this case, the colors are usually from a Live Wallpaper.
            List<Integer> distinctColors = wallpaperColors.getMainColors().stream()
                    .map(Color::toArgb)
                    .distinct()
                    .filter(color -> !filter || Hct.fromInt(color).getChroma() >= MIN_CHROMA)
                    .collect(Collectors.toList());
            if (distinctColors.isEmpty()) {
                return List.of(GOOGLE_BLUE);
            }
            return distinctColors;
        }

        Map<Integer, Double> intToProportion = wallpaperColors.getAllColors().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().doubleValue() / totalPopulation));
        Map<Integer, Hct> intToHct = wallpaperColors.getAllColors().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Hct.fromInt(entry.getKey())));

        // Get an array with 360 slots. A slot contains the percentage of colors with that hue.
        List<Double> hueProportions = huePopulations(intToHct, intToProportion, filter);
        // Map each color to the percentage of the image with its hue.
        Map<Integer, Double> intToHueProportion = wallpaperColors.getAllColors().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    Hct hct = intToHct.get(entry.getKey());
                    int hue = (int) Math.round(hct.getHue());
                    double proportion = 0.0;
                    for (int i = hue - 15; i <= hue + 15; i++) {
                        proportion += hueProportions.get(wrapDegrees(i));
                    }
                    return proportion;
                }));
        // Remove any inappropriate seed colors. For example, low chroma colors look grayscale
        // raising their chroma will turn them to a much louder color that may not have been
        // in the image.
        Map<Integer, Hct> filteredIntToHct = filter
                ? intToHct
                .entrySet()
                .stream()
                .filter(entry -> {
                    Hct hct = entry.getValue();
                    double proportion = intToHueProportion.get(entry.getKey());
                    return hct.getChroma() >= MIN_CHROMA && proportion > 0.01;
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                : intToHct;
        // Sort the colors by score, from high to low.
        List<Map.Entry<Integer, Double>> intToScore = filteredIntToHct.entrySet().stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(),
                        score(entry.getValue(), intToHueProportion.get(entry.getKey()))))
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        // Go through the colors, from high score to low score.
        // If the color is distinct in hue from colors picked so far, pick the color.
        // Iteratively decrease the amount of hue distinctness required, thus ensuring we
        // maximize difference between colors.
        int minimumHueDistance = 15;
        List<Integer> seeds = new ArrayList<>();
        for (int i = 90; i >= minimumHueDistance; i--) {
            seeds.clear();
            for (Map.Entry<Integer, Double> entry : intToScore) {
                int currentColor = entry.getKey();
                int finalI = i;
                boolean existingSeedNearby = seeds.stream().anyMatch(seed -> {
                    double hueA = intToHct.get(currentColor).getHue();
                    double hueB = intToHct.get(seed).getHue();
                    return hueDiff(hueA, hueB) < finalI;
                });
                if (existingSeedNearby) {
                    continue;
                }
                seeds.add(currentColor);
                if (seeds.size() >= 4) {
                    break;
                }
            }
            if (!seeds.isEmpty()) {
                break;
            }
        }

        if (seeds.isEmpty()) {
            // Use gBlue 500 if there are 0 colors
            seeds.add(GOOGLE_BLUE);
        }

        return seeds;
    }

    /**
     * Checks is this ColorScheme is equivalent to another
     *
     * @param otherScheme The other ColorScheme to compare with
     */
    public boolean hasSamePalette(ColorScheme otherScheme) {
        return this.getAccent1().allShadesMapped
                .equals(otherScheme.getAccent1().allShadesMapped)
                && this.getAccent2().allShadesMapped
                .equals(otherScheme.getAccent2().allShadesMapped)
                && this.getAccent3().allShadesMapped
                .equals(otherScheme.getAccent3().allShadesMapped)
                && this.getNeutral1().allShadesMapped
                .equals(otherScheme.getNeutral1().allShadesMapped)
                && this.getNeutral2().allShadesMapped
                .equals(otherScheme.getNeutral2().allShadesMapped)
                && this.getError().allShadesMapped
                .equals(otherScheme.getError().allShadesMapped);
    }

    /**
     * Checks is this ColorScheme is equivalent to another
     *
     * @param otherScheme The other ColorScheme to compare with
     */
    public boolean hasSameProperties(ColorScheme otherScheme) {
        if (otherScheme.mStyle != this.mStyle) return false;
        if (otherScheme.mIsDark != this.mIsDark) return false;
        if (otherScheme.mContrast != this.mContrast) return false;
        if (!otherScheme.mSeeds.equals(this.mSeeds)) return false;
        if (otherScheme.mMaterialScheme.variant != this.mMaterialScheme.variant) return false;
        if (otherScheme.mMaterialScheme.platform != this.mMaterialScheme.platform) return false;
        return true;
    }

    /**
     * Filters and ranks colors from WallpaperColors. Defaults Filter to TRUE
     *
     * @param newWallpaperColors Colors extracted from an image via quantization.
     *                           themes.
     * @return List of ARGB ints, ordered from highest scoring to lowest.
     */
    public static List<Integer> getSeedColors(WallpaperColors newWallpaperColors) {
        return getSeedColors(newWallpaperColors, true);
    }

    private static int wrapDegrees(int degrees) {
        if (degrees < 0) {
            return (degrees % 360) + 360;
        } else if (degrees >= 360) {
            return degrees % 360;
        } else {
            return degrees;
        }
    }

    private static double hueDiff(double a, double b) {
        double diff = Math.abs(a - b);
        if (diff > 180f) {
            // 0 and 360 are the same hue. If hue difference is greater than 180, subtract from 360
            // to account for the circularity.
            diff = 360f - diff;
        }
        return diff;
    }

    private static String stringForColor(int color) {
        int width = 4;
        Hct hct = Hct.fromInt(color);
        String h = "H" + String.format("%" + width + "s", Math.round(hct.getHue()));
        String c = "C" + String.format("%" + width + "s", Math.round(hct.getChroma()));
        String t = "T" + String.format("%" + width + "s", Math.round(hct.getTone()));
        String hex = Integer.toHexString(color & 0xffffff).toUpperCase();
        return h + c + t + " = #" + hex;
    }

    private static String humanReadable(String paletteName, List<Integer> colors) {
        return paletteName + "\n"
                + colors
                .stream()
                .map(ColorScheme::stringForColor)
                .collect(Collectors.joining("\n"));
    }

    private static double score(Hct hct, double proportion) {
        double proportionScore = 0.7 * 100.0 * proportion;
        double chromaScore = hct.getChroma() < ACCENT1_CHROMA
                ? 0.1 * (hct.getChroma() - ACCENT1_CHROMA)
                : 0.3 * (hct.getChroma() - ACCENT1_CHROMA);
        return chromaScore + proportionScore;
    }

    private static List<Double> huePopulations(Map<Integer, Hct> hctByColor,
            Map<Integer, Double> populationByColor, boolean filter) {
        List<Double> huePopulation = new ArrayList<>(Collections.nCopies(360, 0.0));

        for (Map.Entry<Integer, Double> entry : populationByColor.entrySet()) {
            double population = entry.getValue();
            Hct hct = hctByColor.get(entry.getKey());
            int hue = (int) Math.round(hct.getHue()) % 360;
            if (filter && hct.getChroma() <= MIN_CHROMA) {
                continue;
            }
            huePopulation.set(hue, huePopulation.get(hue) + population);
        }

        return huePopulation;
    }
}
