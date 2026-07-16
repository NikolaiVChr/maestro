package com.digero.maestro.view;

import java.util.Objects;
import org.jetbrains.annotations.NonNls;
import com.digero.common.view.UIText;

public enum TimingMode {
        ORGANIC_MULTISTAGE2 (UIText.get("maestro.timing.organic.multi.stage.2"),true, true, false,false,false,"Organic Multistage 2", true),
        ORGANIC_MULTISTAGE (UIText.get("maestro.timing.organic.multi.stage"),true, true, false,false,false,"Organic Multistage", false),
        ORGANIC_SINGLESTAGE (UIText.get("maestro.timing.organic.single.stage"), true, false, false,false,false,"Organic Singlestage", false),
        MIX (UIText.get("maestro.timing.mix.timings"), false, false, true,false,false,"Mix Timings", false),
        MIX_SWING (UIText.get("maestro.timing.mix.timings.swing"), false, false, true,true,false,"Mix Timings Swing/Triplet", false),
        MIX_PRIO (UIText.get("maestro.timing.mix.timings.combine.priorities"), false, false, true,false,true,"Mix Timings Combine Priorities", false),
        MIX_SWING_PRIO (UIText.get("maestro.timing.mix.timings.swing.combine.priorities"), false, false, true,true,true,"Mix Timings Swing/Triplet Combine Priorities", false),
        LEGACY (UIText.get("maestro.timing.legacy.timings"), false, false, false,false,false,"Legacy", false),
        LEGACY_SWING (UIText.get("maestro.timing.legacy.timings.swing"), false, false, false,true,false,"Legacy Swing/Triplet", false),
        ;

        public final boolean organic;
        public final boolean multistage;
        public final boolean mixTimings;
        public final boolean swing;
        public final boolean priority;
        public final String info;
        public final String settingsString;// use this for settings prefs. And never change the strings.
        public final boolean upgraded;

        TimingMode(String info, boolean organic, boolean multistage, boolean mixTimings, boolean swing, boolean priority, @NonNls String settings, boolean upgraded) {
            this.info = info;
            this.organic = organic;
            this.multistage = multistage;
            this.mixTimings = mixTimings;
            this.swing = swing;
            this.priority = priority;
            this.settingsString = settings;
            this.upgraded = upgraded;
        }

        public static TimingMode getFromSettings(String defaultTiming) {
            Objects.requireNonNull(defaultTiming);
            for (TimingMode timing : TimingMode.values()) {
                if (timing.settingsString.equals(defaultTiming)) {
                    return timing;
                }
            }
            return MIX;
        }

        String getTooltip() {
            return switch (this) {
                case ORGANIC_MULTISTAGE2 -> UIText.get("maestro.tip.multi2");
                case ORGANIC_MULTISTAGE -> UIText.get("maestro.tip.multi1");
                case ORGANIC_SINGLESTAGE -> UIText.get("maestro.tip.single");
                case LEGACY -> UIText.get("maestro.tip.legacy");
                case LEGACY_SWING -> UIText.get("maestro.tip.legacy.swing");
                case MIX_SWING_PRIO -> UIText.get("maestro.tip.mix.swing.prio");
                case MIX -> UIText.get("maestro.tip.mix");
                case MIX_SWING -> UIText.get("maestro.tip.mix.swing");
                case MIX_PRIO -> UIText.get("maestro.tip.mix.prio");
                default -> null;
            };
        }

        static TimingMode getInstance(boolean organic, boolean multistage, boolean mixTimings, boolean swing, boolean priority, boolean upgraded) {
            if (organic) {
                if (multistage) {
                    if (upgraded) return ORGANIC_MULTISTAGE2;
                    return ORGANIC_MULTISTAGE;
                }
                else return ORGANIC_SINGLESTAGE;
            } else if (mixTimings) {
                if (swing) {
                    if (priority) return MIX_SWING_PRIO;
                    else return MIX_SWING;
                } else {
                    if (priority) return MIX_PRIO;
                    else return MIX;
                }
            } else {
                if (swing) return LEGACY_SWING;
                else return LEGACY;
            }
        }

        @Override
        public String toString() {
            return info;
        }
    }
