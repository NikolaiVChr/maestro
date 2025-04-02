package com.digero.abcplayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.ListIterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.digero.abcplayer.view.PlaylistSetExportWizard.SetExportSettings;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.util.Pair;
import com.digero.maestro.abc.ExportFilenameTemplate;

public class SetFilenameTemplate {
	
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
	
	private AbcInfo info = AbcInfo.getDummyAbcInfo();
	private String filename = "my abc file.abc";
	private int index = 3;
	private SortedMap<String, Variable> variables;
	private SetExportSettings settings;
	
	public SetFilenameTemplate(SetExportSettings settings) {
		this.settings = settings;
		
		Comparator<String> caseInsensitiveStringComparator = String::compareToIgnoreCase;
		
		variables = new TreeMap<>(caseInsensitiveStringComparator);
		
		variables.put("$FileName", new Variable("The song's original filename") {
			@Override
			public String getValue() {
				return filename.endsWith(".abc") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
			}
		});
		
		variables.put("$SongIndex", new Variable("The number position of the song in the setlist") {
			@Override
			public String getValue() {
				return String.format("%03d", index + 1);
			}
		});
		
		variables.put("$PartCount", new Variable("Number of parts in the ABC file") {
			@Override
			public String getValue() {
				return String.format("%02d", info.getPartCount());
			}
		});
		
		variables.put("$SongComposer", new Variable("The song composer/artist, as entered in the \"C:\" field") {
			@Override
			public String getValue() {
				return info.getComposer();
			}
		});
		
		variables.put("$SongTranscriber", new Variable("The abc transcriber, as entered in the \"Z:\" field") {
			@Override
			public String getValue() {
				return info.getTranscriber();
			}
		});
		
		variables.put("$SongLength", new Variable("The playing time of the song in mm_ss format") {
			@Override
			public String getValue() {
				return info.getSongDurationStr().replace(":", "-");
			}
		});
		
		variables.put("$SongTitle", new Variable("The title of the song, as entered in the \"T:\" field") {
			@Override
			public String getValue() {
				return info.getTitle();
			}
		});
	}
	
	public void setAbcInfo(AbcInfo info) {
		this.info = info;
	}
	
	public void setIndex(int index) {
		this.index = index;
	}
	
	public void setFilename(String filename) {
		this.filename = filename;
	}
	
	public AbcInfo getAbcInfo() {
		return info;
	}
	
	public String formatName() {
		return formatName(settings);
	}
	
	public String formatName(SetExportSettings settings) {
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
				if (ExportFilenameTemplate.spaceReplaceChars4.equals(settings.getWhitespaceReplaceText())) {
					value = Arrays.stream(value.trim().split("\\s+"))
			                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
			                .collect(Collectors.joining(""));
				} else {
					value = value.replaceAll("\\s+", settings.getWhitespaceReplaceText());
				}
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
