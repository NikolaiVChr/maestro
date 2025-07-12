package com.digero.maestro.abc;

import java.io.File;
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

import com.digero.common.abc.LotroInstrument;
import com.digero.common.util.Pair;
import com.digero.common.util.Util;
import com.digero.maestro.view.SettingsDialog.MockMetadataSource;

public class PartNameTemplate {

	
	public static class Settings {
		public static final String spaceReplaceChars4 = "RemoveAndCaps";
		public static final String[] spaceReplaceChars = { " ", "", "_", "-", spaceReplaceChars4 };
		public static final String[] spaceReplaceLabels = { "Don't Replace", "Remove Spaces", "_ (Underscore)",
				"- (Dash)", "Remove Spaces and Capitalize first letter" };
		
		private String whitespaceReplaceText;
		
		private String partNamePattern;

		private final Preferences prefs;

		private Settings(Preferences prefs) {
			this.prefs = prefs;
			partNamePattern = prefs.get("partNamePattern", "$SongTitle ($SongLength) - $PartName");
			whitespaceReplaceText = prefs.get("whitespaceReplaceText", " ");
		}

		public Settings(Settings source) {
			this.prefs = source.prefs;
			copyFrom(source);
		}

		private void save(Preferences prefs) {
			prefs.put("partNamePattern", partNamePattern);
			prefs.put("whitespaceReplaceText", whitespaceReplaceText); 
		}

		private void copyFrom(Settings source) {
			this.partNamePattern = source.partNamePattern;
			this.whitespaceReplaceText = source.whitespaceReplaceText;
		}

		public String getPartNamePattern() {
			return partNamePattern;
		}

		public void setPartNamePattern(String partNamePattern) {
			this.partNamePattern = partNamePattern;
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
		
		public String getWhitespaceReplaceText() {
			return whitespaceReplaceText;
		}

		public void setWhitespaceReplaceText(String whitespaceReplaceText) {
			this.whitespaceReplaceText = whitespaceReplaceText;
		}
	}

	public abstract static class Variable {
		private final String description;

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
	private final Preferences prefsNode;

	private AbcMetadataSource metadata = null;
	private AbcPartMetadataSource currentAbcPart;

	private final SortedMap<String, Variable> variables;

	public PartNameTemplate(Preferences prefsNode) {
		this.prefsNode = prefsNode;
		this.settings = new Settings(prefsNode);

		Comparator<String> caseInsensitiveStringComparator = String::compareToIgnoreCase;

		variables = new TreeMap<>(caseInsensitiveStringComparator);

		variables.put("$SongTitle", new Variable("The title of the song, as entered in the \"T:\" field") {
			@Override
			public String getValue() {
				return getMetadataSource().getSongTitle().trim();
			}
		});
		variables.put("$SongLength", new Variable("The playing time of the song in mm:ss format") {
			@Override
			public String getValue() {
				return Util.formatDuration(getMetadataSource().getSongLengthMicros());
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
		variables.put("$PartName", new Variable("The part name for the individual ABC part") {
			@Override
			public String getValue() {
				if (currentAbcPart == null)
					return "";

				return currentAbcPart.getTitle().trim();
			}
		});
		variables.put("$PartNumber", new Variable("The part number for the individual ABC part") {
			@Override
			public String getValue() {
				if (currentAbcPart == null)
					return "0";

				return String.valueOf(currentAbcPart.getPartNumber());
			}
		});
		variables.put("$PartCount", new Variable("Number of parts in the ABC file") {
			@Override
			public String getValue() {
				return String.format("%d",
						getMetadataSource().getActivePartCount());
			}
		});
		variables.put("$PartInstrument", new Variable("The instrument for the individual ABC part") {
			@Override
			public String getValue() {
				if (currentAbcPart == null)
					return LotroInstrument.DEFAULT_INSTRUMENT.toString();

				return currentAbcPart.getInstrument().toString();
			}
		});
		variables.put("$FilePath",
				new Variable("The path to the ABC file including the ABC file name, "
						+ "if it is in a subdirectory of the LOTRO/Music directory.\n"
						+ "If the file is saved directly in the LOTRO/Music directory, "
						+ "this will be the same as <b>$FileName</b>.") {
					@Override
					public String getValue() {
						if (getMetadataSource().getExportFile() == null)
							return "";

						File root = Util.getLotroMusicPath(true);
						String saveFileName = Util.fileNameWithoutExtension(getMetadataSource().getExportFile());

						StringBuilder path = new StringBuilder(saveFileName);
						boolean foundRoot = false;
						for (File file = getMetadataSource().getExportFile().getParentFile(); file != null; file = file
								.getParentFile()) {
							if (root.equals(file)) {
								foundRoot = true;
								break;
							}
							path.insert(0, file.getName() + "/");
						}

						if (foundRoot)
							return path.toString();

						return saveFileName;
					}
				});
		variables.put("$FileName", new Variable("The name of the ABC file, without the .abc extension") {
			@Override
			public String getValue() {
				if (getMetadataSource().getExportFile() == null)
					return "";

				return Util.fileNameWithoutExtension(getMetadataSource().getExportFile());
			}
		});
	}

	public Settings getSettingsCopy() {
		return new Settings(settings);
	}

	public void setSettings(Settings settings) {
		this.settings.copyFrom(settings);
		this.settings.save(prefsNode);
	}

	public AbcMetadataSource getMetadataSource() {
		if (metadata == null)
			metadata = new MockMetadataSource(null);

		return metadata;
	}

	public void setMetadataSource(AbcMetadataSource metadata) {
		this.metadata = metadata;
	}

	public void setCurrentAbcPart(AbcPartMetadataSource currentAbcPart) {
		this.currentAbcPart = currentAbcPart;
	}

	public AbcPartMetadataSource getCurrentAbcPart() {
		return currentAbcPart;
	}

	public SortedMap<String, Variable> getVariables() {
		return Collections.unmodifiableSortedMap(variables);
	}

	public String formatName(AbcPartMetadataSource currentAbcPart) {
		return formatName(settings.getPartNamePattern(), currentAbcPart, settings.getWhitespaceReplaceText());
	}

	public String formatName(String partNamePattern, AbcPartMetadataSource currentAbcPart, String whiteSpaceReplaceChars) {
		this.currentAbcPart = currentAbcPart;
		String name = partNamePattern;

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
				if (var.getValue() != null) {
					if (Settings.spaceReplaceChars4.equals(whiteSpaceReplaceChars)) {
						name = name.substring(0, match.first) + 
								Arrays.stream(var.getValue().split("\\s+"))
								.filter(word -> !word.isEmpty())
				                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
				                .collect(Collectors.joining(""))
				                + name.substring(match.second);
					} else {
						name = name.substring(0, match.first) + var.getValue().replaceAll("\\s+", whiteSpaceReplaceChars) + name.substring(match.second);
					}
				}
			}
		}

		this.currentAbcPart = null;
		return name;
	}

	public void restoreDefaultSettings() {
		settings.restoreDefaults();
	}
}
