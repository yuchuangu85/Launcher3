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

import static com.google.ux.material.libmonet.dynamiccolor.ToneDeltaPair.DeltaConstraint.FARTHER;
import static com.google.ux.material.libmonet.dynamiccolor.TonePolarity.RELATIVE_LIGHTER;

import com.google.ux.material.libmonet.dynamiccolor.ContrastCurve;
import com.google.ux.material.libmonet.dynamiccolor.DynamicColor;
import com.google.ux.material.libmonet.dynamiccolor.MaterialDynamicColors;
import com.google.ux.material.libmonet.dynamiccolor.ToneDeltaPair;
import com.google.ux.material.libmonet.dynamiccolor.TonePolarity;
import com.google.ux.material.libmonet.hct.Hct;
import com.google.ux.material.libmonet.palettes.TonalPalette;
import com.google.ux.material.libmonet.utils.MathUtils;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class CustomDynamicColors {
    private final MaterialDynamicColors mMdc;
    public final List<Supplier<DynamicColor>> allColors;

    public CustomDynamicColors() {
        this.mMdc = new MaterialDynamicColors();
        allColors = Arrays.asList(
                this::widgetBackground,
                this::clockHour,
                this::clockMinute,
                this::clockSecond,
                this::weatherTemp,
                this::themeApp,
                this::onThemeApp,
                this::themeAppRing,
                this::themeNotif,
                this::brandA,
                this::brandB,
                this::brandC,
                this::brandD,
                this::underSurface,
                this::shadeActive,
                this::onShadeActive,
                this::onShadeActiveVariant,
                this::shadeInactive,
                this::onShadeInactive,
                this::onShadeInactiveVariant,
                this::shadeDisabled,
                this::overviewBackground,
                this::surfaceEffect0,
                this::surfaceEffect1,
                this::surfaceEffect2,
                this::surfaceEffect3,
                this::surfaceEffect0Fallback
        );
    }

    // CLOCK COLORS
    public DynamicColor widgetBackground() {
        return new DynamicColor.Builder()
                .setName("widget_background")
                .setPalette((s) -> s.secondaryPalette)
                .setTone((s) -> s.isDark ? 20.0 : 95.0)
                .setIsBackground(true)
                .build();
    }

    public DynamicColor clockHour() {
        return new DynamicColor.Builder()
                .setName("clock_hour")
                .setPalette((s) -> s.isDark ? s.primaryPalette : s.secondaryPalette)
                .setTone((s) -> s.isDark ? 80.0 : 30.0)
                .setIsBackground(false)
                .setBackground((s) -> widgetBackground())
                .setContrastCurve((s) -> new ContrastCurve(4.0, 4.0, 5.0, 15.0))
                .setToneDeltaPair((s) -> new ToneDeltaPair(clockHour(), clockMinute(), 10.0,
                        TonePolarity.DARKER, ToneDeltaPair.DeltaConstraint.FARTHER))
                .build();
    }

    public DynamicColor clockMinute() {
        return new DynamicColor.Builder()
                .setName("clock_minute")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> s.isDark ? 90.0 : 40.0)
                .setIsBackground(false)
                .setBackground((s) -> widgetBackground())
                .setContrastCurve((s) -> new ContrastCurve(6.5, 6.5, 10.0, 15.0))
                .build();
    }

    public DynamicColor clockSecond() {
        return new DynamicColor.Builder()
                .setName("clock_second")
                .setPalette((s) -> s.tertiaryPalette)
                .setTone((s) -> s.isDark ? 90.0 : 40.0)
                .setIsBackground(false)
                .setBackground((s) -> widgetBackground())
                .setContrastCurve((s) -> new ContrastCurve(5.0, 5.0, 70.0, 11.0))
                .build();
    }

    public DynamicColor weatherTemp() {
        return new DynamicColor.Builder()
                .setName("weather_temp")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> s.isDark ? 80.0 : 40.0)
                .setIsBackground(false)
                .setBackground((s) -> widgetBackground())
                .setContrastCurve((s) -> new ContrastCurve(5.0, 5.0, 70.0, 11.0))
                .build();
    }

    // THEME APP ICONS
    public DynamicColor themeApp() {
        return new DynamicColor.Builder()
                .setName("theme_app")
                .setPalette((s) -> s.isDark ? s.secondaryPalette : s.primaryPalette)
                .setTone((s) -> s.isDark ? switch (s.variant) {
                                case TONAL_SPOT, EXPRESSIVE -> tMinC(s.primaryPalette, 20.0, 93.0);
                                case VIBRANT -> tMinC(s.primaryPalette, 66.0, 93.0);
                                default -> 20.0;
                            } : switch (s.variant) {
                                case TONAL_SPOT -> tMaxC(s.primaryPalette, 0.0, 90.0);
                                case EXPRESSIVE -> Hct.isCyan(s.primaryPalette.getHue())
                                        ? 88.0
                                        : tMaxC(s.primaryPalette, 78.0, 90.0);
                                case VIBRANT -> Hct.isCyan(s.primaryPalette.getHue())
                                        ? 88.0
                                        : tMaxC(s.primaryPalette, 0.0, 66.0);
                                default -> 90.0;
                            }
                )
                .setIsBackground(true)
                .build();
    }

    public DynamicColor onThemeApp() {
        return new DynamicColor.Builder()
                .setName("on_theme_app")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> s.isDark ? 80.0 : 30.0)
                .setIsBackground(false)
                .setBackground((s) -> themeApp())
                .setContrastCurve((s) -> new ContrastCurve(7, 7, 11, 21))
                .build();
    }

    public DynamicColor themeAppRing() {
        return new DynamicColor.Builder()
                .setName("theme_app_ring")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> switch (s.variant) {
                    case TONAL_SPOT, EXPRESSIVE -> tMaxC(s.primaryPalette, 0.0, 70.0);
                    case VIBRANT -> tMaxC(s.primaryPalette);
                    default -> 70.0;
                })
                .setIsBackground(true)
                .setBackground((s) -> mMdc.surfaceContainerHigh())
                .setContrastCurve((s) -> new ContrastCurve(1.8, 1.8, 3.0, 4.5))
                .build();
    }

    public DynamicColor themeNotif() {
        return new DynamicColor.Builder()
                .setName("theme_notif")
                .setPalette((s) -> s.tertiaryPalette)
                .setTone((s) -> tMinC(s.tertiaryPalette, 80.0, 93))
                .setBackground((s) -> themeAppRing())
                .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 1.0, 1.0))
                .setToneDeltaPair((s) -> new ToneDeltaPair(themeNotif(), themeAppRing(), 5.0,
                        RELATIVE_LIGHTER, FARTHER))
                .build();
    }

    // SUPER G COLORS
    public DynamicColor brandA() {
        return new DynamicColor.Builder()
                .setName("brand_a")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> s.isDark ? 80.0 : 40.0)
                .setBackground((s) -> mMdc.surfaceContainerLow())
                .setContrastCurve((s) -> s.isDark ? new ContrastCurve(10.0, 10.0, 12.0, 13.0)
                        : new ContrastCurve(6.0, 6.0, 9.0, 12.0))
                .build();
    }

    public DynamicColor brandB() {
        return new DynamicColor.Builder()
                .setName("brand_b")
                .setPalette((s) -> s.secondaryPalette)
                .setTone((s) -> s.isDark ? 98.0 : 70.0)
                .setBackground((s) -> mMdc.surfaceContainerLow())
                .setContrastCurve((s) -> s.isDark ? new ContrastCurve(16.0, 16.0, 16.5, 17.0)
                        : new ContrastCurve(2.0, 2.0, 3.0, 4.5))
                .build();
    }

    public DynamicColor brandC() {
        return new DynamicColor.Builder()
                .setName("brand_c")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> s.isDark ? 60.0 : 50.0)
                .setBackground((s) -> mMdc.surfaceContainerLow())
                .setContrastCurve((s) -> s.isDark ? new ContrastCurve(6.0, 6.0, 9.0, 11.0)
                        : new ContrastCurve(4.0, 4.0, 7.0, 8.0))
                .build();
    }

    public DynamicColor brandD() {
        return new DynamicColor.Builder()
                .setName("brand_d")
                .setPalette((s) -> s.tertiaryPalette)
                .setTone((s) -> s.isDark ? 90.0 : 59.0)
                .setBackground((s) -> mMdc.surfaceContainerLow())
                .setContrastCurve((s) -> s.isDark ? new ContrastCurve(13.0, 13.0, 14.0, 15.0)
                        : new ContrastCurve(3.0, 3.0, 4.5, 6.0))
                .build();
    }

    // QUICK SETTING TILES
    public DynamicColor underSurface() {
        return new DynamicColor.Builder()
                .setName("under_surface")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> 0.0)
                .setIsBackground(true)
                .build();
    }

    public DynamicColor shadeActive() {
        return new DynamicColor.Builder()
                .setName("shade_active")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> 90.0)
                .setIsBackground(true)
                .setBackground((s) -> underSurface())
                .setContrastCurve((s) -> new ContrastCurve(3.0, 3.0, 4.5, 7.0))
                .setToneDeltaPair((s) -> new ToneDeltaPair(shadeActive(), shadeInactive(), 30.0,
                        TonePolarity.LIGHTER, ToneDeltaPair.DeltaConstraint.FARTHER))
                .build();
    }

    public DynamicColor onShadeActive() {
        return new DynamicColor.Builder()
                .setName("on_shade_active")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> 10.0)
                .setIsBackground(false)
                .setBackground((s) -> shadeActive())
                .setContrastCurve((s) -> new ContrastCurve(4.5, 4.5, 7.0, 11.0))
                .setToneDeltaPair(
                        (s) -> new ToneDeltaPair(onShadeActive(), onShadeActiveVariant(), 20.0,
                                TonePolarity.RELATIVE_LIGHTER,
                                ToneDeltaPair.DeltaConstraint.FARTHER))
                .build();
    }

    public DynamicColor onShadeActiveVariant() {
        return new DynamicColor.Builder()
                .setName("on_shade_active_variant")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> 30.0)
                .setIsBackground(false)
                .setBackground((s) -> shadeActive())
                .setContrastCurve((s) -> new ContrastCurve(4.5, 4.5, 7.0, 11.0))
                .build();
    }

    public DynamicColor shadeInactive() {
        return new DynamicColor.Builder()
                .setName("shade_inactive")
                .setPalette((s) -> s.neutralPalette)
                .setTone((s) -> 20.0)
                .setIsBackground(true)
                .setBackground((s) -> underSurface())
                .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 1.0, 1.0))
                .setToneDeltaPair((s) -> new ToneDeltaPair(shadeInactive(), shadeDisabled(), 15.0,
                        TonePolarity.LIGHTER, ToneDeltaPair.DeltaConstraint.FARTHER))
                .build();
    }

    public DynamicColor onShadeInactive() {
        return new DynamicColor.Builder()
                .setName("on_shade_inactive")
                .setPalette((s) -> s.neutralVariantPalette)
                .setTone((s) -> 90.0)
                .setIsBackground(false)
                .setBackground((s) -> shadeInactive())
                .setContrastCurve((s) -> new ContrastCurve(4.5, 4.5, 7.0, 11.0))
                .setToneDeltaPair(
                        (s) -> new ToneDeltaPair(onShadeInactive(), onShadeInactiveVariant(), 10.0,
                                TonePolarity.RELATIVE_LIGHTER,
                                ToneDeltaPair.DeltaConstraint.FARTHER))
                .build();
    }

    public DynamicColor onShadeInactiveVariant() {
        return new DynamicColor.Builder()
                .setName("on_shade_inactive_variant")
                .setPalette((s) -> s.neutralVariantPalette)
                .setTone((s) -> 80.0)
                .setIsBackground(false)
                .setBackground((s) -> shadeInactive())
                .setContrastCurve((s) -> new ContrastCurve(4.5, 4.5, 7.0, 11.0))
                .build();
    }

    public DynamicColor shadeDisabled() {
        return new DynamicColor.Builder()
                .setName("shade_disabled")
                .setPalette((s) -> s.neutralPalette)
                .setTone((s) -> 4.0)
                .setIsBackground(false)
                .setBackground((s) -> underSurface())
                .setContrastCurve((s) -> new ContrastCurve(1.0, 1.0, 1.0, 1.0))
                .build();
    }

    public DynamicColor overviewBackground() {
        return new DynamicColor.Builder()
                .setName("overview_background")
                .setPalette((s) -> s.neutralVariantPalette)
                .setTone((s) -> s.isDark ? 35.0 : 80.0)
                .setIsBackground(true)
                .build();
    }


    public DynamicColor surfaceEffect0() {
        return new DynamicColor.Builder()
                .setName("surface_effect_0")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> s.isDark ? 20.0 : 90.0)
                .setIsBackground(true)
                .setOpacity((s)-> .5)
                .build();
    }

    public DynamicColor surfaceEffect1() {
        return new DynamicColor.Builder()
                .setName("surface_effect_1")
                .setPalette((s) -> s.neutralPalette)
                .setTone((s) -> s.isDark ? 6.0 : 98.0)
                .setIsBackground(true)
                .setOpacity((s)-> .54)
                .build();
    }

    public DynamicColor surfaceEffect2() {
        return new DynamicColor.Builder()
                .setName("surface_effect_2")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> s.isDark ? 90.0 : 100.0)
                .setIsBackground(true)
                .setOpacity((s)-> s.isDark ? .15 : .32)
                .build();
    }

    public DynamicColor surfaceEffect3() {
        return new DynamicColor.Builder()
                .setName("surface_effect_3")
                .setPalette((s) -> s.primaryPalette)
                .setTone((s) -> s.isDark ? 90.0 : 40.0)
                .setIsBackground(true)
                .setOpacity((s)-> s.isDark ? .10 : .15)
                .build();
    }

    public DynamicColor surfaceEffect0Fallback() {
        return new DynamicColor.Builder()
                .setName("surface_effect_0_fallback")
                .setPalette((s) -> s.secondaryPalette)
                .setTone((s) -> s.isDark ? 20.0 : 80.0)
                .setIsBackground(true)
                .build();
    }

    private static double findBestToneForChroma(
            double hue, double chroma, double tone, boolean byDecreasingTone) {
        double answer = tone;
        Hct bestCandidate = Hct.from(hue, chroma, answer);
        while (bestCandidate.getChroma() < chroma) {
            if (tone < 0 || tone > 100) {
                break;
            }
            tone += byDecreasingTone ? -1.0 : 1.0;
            Hct newCandidate = Hct.from(hue, chroma, tone);
            if (bestCandidate.getChroma() < newCandidate.getChroma()) {
                bestCandidate = newCandidate;
                answer = tone;
            }
        }
        return answer;
    }

    private static double tMaxC(TonalPalette palette) {
        return tMaxC(palette, 0, 100);
    }

    private static double tMaxC(TonalPalette palette, double lowerBound, double upperBound) {
        return tMaxC(palette, lowerBound, upperBound, 1);
    }

    private static double tMaxC(
            TonalPalette palette, double lowerBound, double upperBound, double chromaMultiplier) {
        double answer =
                findBestToneForChroma(palette.getHue(), palette.getChroma() * chromaMultiplier, 100,
                        true);
        return MathUtils.clampDouble(lowerBound, upperBound, answer);
    }

    private static double tMinC(TonalPalette palette) {
        return tMinC(palette, 0, 100);
    }

    private static double tMinC(TonalPalette palette, double lowerBound, double upperBound) {
        double answer = findBestToneForChroma(palette.getHue(), palette.getChroma(), 0, false);
        return MathUtils.clampDouble(lowerBound, upperBound, answer);
    }

}
