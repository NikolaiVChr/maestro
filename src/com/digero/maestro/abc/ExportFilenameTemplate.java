package com.digero.maestro.abc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.ListIterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.digero.common.util.Pair;
import com.digero.common.util.Util;
import com.digero.maestro.view.SettingsDialog.MockMetadataSource;

public class ExportFilenameTemplate {
	public static final String spaceReplaceChars4 = "RemoveAndCaps";
	public static final String[] spaceReplaceChars = { " ", "", "_", "-", spaceReplaceChars4 };
	public static final String[] spaceReplaceLabels = { "Don't Replace", "Remove Spaces", "_ (Underscore)",
			"- (Dash)", "Remove Spaces and Capitalize first letter" };

	public static class Settings {
		private boolean exportFilenamePatternEnabled;
		private boolean alwaysRegenerateFromPattern;
		private String exportFilenamePattern;
		private String whitespaceReplaceText;
		private boolean partCountZeroPadded;

		private final Preferences prefs;

		private Settings(Preferences prefs) {
			this.prefs = prefs;
			exportFilenamePatternEnabled = prefs.getBoolean("exportFilenamePatternEnabled", false);
			alwaysRegenerateFromPattern = prefs.getBoolean("alwaysRegenerateFromPattern", false);
			exportFilenamePattern = prefs.get("exportFilenamePattern", "$PartCount - $SongTitle");
			whitespaceReplaceText = prefs.get("whitespaceReplaceText", " ");
			partCountZeroPadded = prefs.getBoolean("partCountZeroPadded", true);
		}

		public Settings(Settings source) {
			this.prefs = source.prefs;
			copyFrom(source);
		}

		private void save() {
			prefs.putBoolean("exportFilenamePatternEnabled", exportFilenamePatternEnabled);
			prefs.putBoolean("alwaysRegenerateFromPattern", alwaysRegenerateFromPattern);
			prefs.put("exportFilenamePattern", exportFilenamePattern);
			prefs.put("whitespaceReplaceText", whitespaceReplaceText);
			prefs.putBoolean("partCountZeroPadded", partCountZeroPadded);
		}

		private void copyFrom(Settings source) {
			this.exportFilenamePatternEnabled = source.exportFilenamePatternEnabled;
			this.alwaysRegenerateFromPattern = source.alwaysRegenerateFromPattern;
			this.exportFilenamePattern = source.exportFilenamePattern;
			this.whitespaceReplaceText = source.whitespaceReplaceText;
			this.partCountZeroPadded = source.partCountZeroPadded;
		}

		public boolean isExportFilenamePatternEnabled() {
			return exportFilenamePatternEnabled;
		}

		public void setExportFilenamePatternEnabled(boolean exportFilenamePatternEnabled) {
			this.exportFilenamePatternEnabled = exportFilenamePatternEnabled;
		}

		public boolean shouldAlwaysRegenerateFromPattern() {
			return alwaysRegenerateFromPattern;
		}

		public void setAlwaysRegenerateFromPattern(boolean alwaysRegenerateFromPattern) {
			this.alwaysRegenerateFromPattern = alwaysRegenerateFromPattern;
		}

		public String getExportFilenamePattern() {
			return exportFilenamePattern;
		}

		public void setExportFilenamePattern(String exportFilenamePattern) {
			this.exportFilenamePattern = exportFilenamePattern;
		}

		public String getWhitespaceReplaceText() {
			return whitespaceReplaceText;
		}

		public void setWhitespaceReplaceText(String whitespaceReplaceText) {
			this.whitespaceReplaceText = whitespaceReplaceText;
		}

		public boolean isPartCountZeroPadded() {
			return partCountZeroPadded;
		}

		public void setPartCountZeroPadded(boolean zeroPadded) {
			partCountZeroPadded = zeroPadded;
		}

		public void restoreDefaults() {
			try {
				prefs.clear();
			} catch (BackingStoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			Settings fresh = new Settings(prefs);
			this.copyFrom(fresh);
		}
	}

	public abstract static class Variable {
		private String description;

		private Variable(String description) {
			this.description = description;
		}

		public abstract String getValue();

		public String getDescription() {
			return description;
		}

		@Override
		public String toString() {
			return getValue();
		}
	}

	private final Settings settings;

	private AbcMetadataSource metadata = null;

	private SortedMap<String, Variable> variables;

	public ExportFilenameTemplate(Preferences prefsNode) {
		this.settings = new Settings(prefsNode);

        variables = generateVariables();
    }

    public ExportFilenameTemplate(ExportFilenameTemplate orig, AbcMetadataSource metadataCopy) {
        this.settings = orig.getSettingsCopy();
        this.metadata = metadataCopy;
        variables = generateVariables();
    }

    private SortedMap<String, Variable> generateVariables() {
        Comparator<String> caseInsensitiveStringComparator = String::compareToIgnoreCase;

        SortedMap<String, Variable> vars = new TreeMap<>(caseInsensitiveStringComparator);

        variables.put("$SongTitle", new Variable("The title of the song, as entered in the \"T:\" field") {
            @Override
            public String getValue() {
                return getMetadataSource().getSongTitle().trim();
            }
        });
        variables.put("$SongLength", new Variable("The playing time of the song in mm_ss format") {
            @Override
            public String getValue() {
                return Util.formatDuration(getMetadataSource().getSongLengthMicros(), 0, '-');
            }
        });
        variables.put("$SongComposer", new Variable("The song composer/artist, as entered in the \"C:\" field") {
            @Override
            public String getValue() {
                return getMetadataSource().getComposer().trim();
            }
        });
        variables.put("$SongTranscriber", new Variable("Your name, as entered in the \"Z:\" field") {
            @Override
            public String getValue() {
                return getMetadataSource().getTranscriber().trim();
            }
        });
        variables.put("$PartCount", new Variable("Number of parts in the ABC file") {
            @Override
            public String getValue() {
                return String.format(settings.partCountZeroPadded ? "%02d" : "%d",
                        getMetadataSource().getActivePartCount());
            }
        });
        variables.put("$SourceFile", new Variable("Source file name (midi or ABC)") {
            @Override
            public String getValue() {
                String name = getMetadataSource().getSourceFilename();
                return name.substring(0, name.lastIndexOf('.'));
            }
        });
        return vars;
    }

    public Settings getSettingsCopy() {
		return new Settings(settings);
	}

	public void setSettings(Settings settings) {
		this.settings.copyFrom(settings);
		this.settings.save();
	}

	public AbcMetadataSource getMetadataSource() {
		if (metadata == null)
			metadata = new MockMetadataSource(null);

		return metadata;
	}

	public void setMetadataSource(AbcMetadataSource metadata) {
		this.metadata = metadata;
	}

	public SortedMap<String, Variable> getVariables() {
		return Collections.unmodifiableSortedMap(variables);
	}

	public boolean isEnabled() {
		return settings.isExportFilenamePatternEnabled();
	}

	public boolean shouldRegenerateFilename() {
		return settings.isExportFilenamePatternEnabled() && settings.shouldAlwaysRegenerateFromPattern();
	}

	public String formatName() {
		return formatName(settings);
	}

	public String formatName(ExportFilenameTemplate.Settings settings) {
		String name = settings.getExportFilenamePattern();

		// hacky but it works - save and restore later
		boolean zeroPad = this.settings.partCountZeroPadded;
		this.settings.partCountZeroPadded = settings.partCountZeroPadded;

		// Find all variables starting with $
		Pattern regex = Pattern.compile("\\$[A-Za-z]+");
		Matcher matcher = regex.matcher(name);

		ArrayList<Pair<Integer, Integer>> matches = new ArrayList<>();
		while (matcher.find()) {
			matches.add(new Pair<>(matcher.start(), matcher.end()));
		}

		ListIterator<Pair<Integer, Integer>> reverseIter = matches.listIterator(matches.size());
		while (reverseIter.hasPrevious()) {
			Pair<Integer, Integer> match = reverseIter.previous();
			Variable var = variables.get(name.substring(match.first, match.second));
			if (var != null) {
				String value = var.getValue();
				if (value != null) {
					if (ExportFilenameTemplate.spaceReplaceChars4.equals(settings.getWhitespaceReplaceText())) {
						value = Arrays.stream(value.trim().split("\\s+"))
								.filter(word -> !word.isEmpty())
				                .map(word -> word.length() == 1? word.toUpperCase() : Character.toUpperCase(word.charAt(0)) + word.substring(1))
				                .collect(Collectors.joining(""));
					} else {
						value = value.replaceAll("\\s+", settings.getWhitespaceReplaceText());
					}
				} else {
					value = "";
				}
				name = name.substring(0, match.first) + value + name.substring(match.second);
			}
		}

		this.settings.partCountZeroPadded = zeroPad;

		name += Util.ABC_FILE_EXTENSION;

		return name;
	}

	public void restoreDefaultSettings() {
		settings.restoreDefaults();
	}
}