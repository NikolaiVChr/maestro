package com.digero.abcplayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ListIterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.digero.common.util.Pair;
import com.digero.common.util.Util;
import com.digero.maestro.abc.AbcMetadataSource;
import com.digero.maestro.abc.ExportFilenameTemplate.Variable;
import com.digero.maestro.view.SettingsDialog.MockMetadataSource;


public class SetFilenameTemplate {
	public static final String[] spaceReplaceChars = { " ", "", "_", "-" };
	public static final String[] spaceReplaceLabels = { "Don't Replace", "Remove Spaces", "_ (Underscore)",
			"- (Dash)" };

	public static class Settings {
		private String exportFilenamePattern;
		private String whitespaceReplaceText;
		private boolean partCountZeroPadded;

		private final Preferences prefs;

		private Settings(Preferences prefs) {
			this.prefs = prefs;
			exportFilenamePattern = prefs.get("exportFilenamePattern", "$PartCount - $SongTitle");
			whitespaceReplaceText = prefs.get("whitespaceReplaceText", " ");
			partCountZeroPadded = prefs.getBoolean("partCountZeroPadded", true);
		}

		public Settings(Settings source) {
			this.prefs = source.prefs;
			copyFrom(source);
		}

		private void save() {
			prefs.put("exportFilenamePattern", exportFilenamePattern);
			prefs.put("whitespaceReplaceText", whitespaceReplaceText);
			prefs.putBoolean("partCountZeroPadded", partCountZeroPadded);
		}

		private void copyFrom(Settings source) {
			this.exportFilenamePattern = source.exportFilenamePattern;
			this.whitespaceReplaceText = source.whitespaceReplaceText;
			this.partCountZeroPadded = source.partCountZeroPadded;
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
	
	private AbcMetadataSource metadata = new MockMetadataSource(null);
	private String filename = "my abc file.abc";
	private int index = 3;
	private SortedMap<String, Variable> variables;
	private Preferences prefsNode;
	private Settings settings;
	
	public SetFilenameTemplate(Preferences prefs) {
		this.prefsNode = prefs;
		this.settings = new Settings(prefsNode);
		
		Comparator<String> caseInsensitiveStringComparator = String::compareToIgnoreCase;
		
		variables = new TreeMap<>(caseInsensitiveStringComparator);
		
		variables.put("$FileName", new Variable("The song's original filename") {
			@Override
			public String getValue() {
				return filename;
			}
		});
		
		variables.put("$SongNumber", new Variable("The number position of the song in the setlist") {
			@Override
			public String getValue() {
				return String.format("%03d", index + 1);
			}
		});
		
		variables.put("$PartCount", new Variable("Number of parts in the ABC file") {
			@Override
			public String getValue() {
				return String.format("%02d",
						getMetadataSource().getActivePartCount());
			}
		});
		
		variables.put("$SongComposer", new Variable("The song composer/artist, as entered in the \"C:\" field") {
			@Override
			public String getValue() {
				return getMetadataSource().getComposer().trim();
			}
		});
		
		variables.put("$SongTranscriber", new Variable("The abc transcriber, as entered in the \"Z:\" field") {
			@Override
			public String getValue() {
				return getMetadataSource().getTranscriber().trim();
			}
		});
		
		variables.put("$SongLength", new Variable("The playing time of the song in mm_ss format") {
			@Override
			public String getValue() {
				return Util.formatDuration(getMetadataSource().getSongLengthMicros(), 0, '-');
			}
		});
		
		variables.put("$SongTitle", new Variable("The title of the song, as entered in the \"T:\" field") {
			@Override
			public String getValue() {
				return getMetadataSource().getSongTitle().trim();
			}
		});
	}
	
	public void setMetadataSource(AbcMetadataSource metadata) {
		this.metadata = metadata;
	}
	
	public void setIndex(int index) {
		this.index = index;
	}
	
	public void setFilename(String filename) {
		this.filename = filename;
	}
	
	public AbcMetadataSource getMetadataSource() {
		return metadata;
	}
	
	public String formatName() {
		return formatName(settings);
	}
	
	public String formatName(Settings settings) {
		String name = settings.getExportFilenamePattern();

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
				value = value.replaceAll("\\s+", settings.getWhitespaceReplaceText());
				name = name.substring(0, match.first) + value + name.substring(match.second);
			}
		}

		name += ".abc";

		return name;
	}
	
	public SortedMap<String, Variable> getVariables() {
		return Collections.unmodifiableSortedMap(variables);
	}
}
