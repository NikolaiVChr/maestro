package com.digero.maestro.midi;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.digero.common.midi.MidiUtils;
import com.digero.common.util.LyricLine;
import com.digero.common.util.Pair;
import com.digero.common.midi.MidiConstants;
import com.digero.maestro.midi.MidiText.TextFragment.Source;
import com.digero.maestro.midi.MidiText.TextFragment.Reaction;
import com.digero.maestro.midi.MidiText.TextFragment.Format;

public class MidiText {
	private static final Logger log = Logger.getLogger("import.midi.text");
	
	private static final Pattern KARAKAN_PART_PATTERN = Pattern.compile("\\[P(\\d+)]");
	private final SequenceDataCache cache;
	public TreeSet<TextFragment> text = new TreeSet<>();
	public Map<TextFragment.Format,Integer> textStats = new HashMap<>();
	public Map<Integer,Integer> trackStats = new HashMap<>();
	public String genre = "";
	public String artist = "";
	public String composer = "";
	public String duration = "";
	public String bpm = "";
	// language=ENGL means if no language specified, we prefer western charset,
	// language="" means only if language english is specified do we prefer western charset
	String language = "";
	Format lastType = null;

	private Map<Charset,Integer> csStats = new HashMap<>();
	private int csTotal = 0;
	
	public MidiText(SequenceDataCache cache) {
		this.cache = cache;
	}
	
	private String decode(byte[] data) {
		Pair<String, Charset> result = MidiUtils.decodeMidiText(data, 
				   "ENGL".equalsIgnoreCase(language)
				|| "EN".equalsIgnoreCase(language)
				|| "ENGLISH".equalsIgnoreCase(language)
				|| "FREN".equalsIgnoreCase(language)
				|| "FRENCH".equalsIgnoreCase(language)
				|| "FR".equalsIgnoreCase(language)
			);
		//addToCharsetScore(result.second);
		return result.first;
	}
	
	void collectTxt(long tick, byte[] data, int metaType, int track) {
		boolean valid = false;
		int offset = 0;		
		TextFragment fragment = new TextFragment();
		switch (metaType) {
			case MidiConstants.META_TEXT:
				fragment.source = Source.TEXT;
				break;
			case MidiConstants.META_LYRIC:
				fragment.source = Source.LYRIC;
				break;
			case MidiConstants.META_MARKER:
				fragment.source = Source.MARK;
				break;
			case MidiConstants.META_CUE_POINT:
				fragment.source = Source.CUE;
				break;
			case MidiConstants.META_M_LIVE:
				fragment.source = Source.MLIVE;
				break;
		}
		//System.out.println("tick="+tick+" txt: "+MidiUtils.formatBytesHexOnly(data)+" type="+fragment.source+" track="+track);
		fragment.track = track;
		fragment.tick = tick;
		if (data != null && data.length > 0) {
			if (fragment.source == Source.MLIVE) {
				valid = false;
				offset = 1;
				switch (data[0]) {
					case MidiConstants.M_LIVE_GENRE:
						genre = decode(Arrays.copyOfRange(data, offset, data.length));
						fragment.prefix = "Genre: ";
						valid = data.length - offset > 0;
						break;
					case MidiConstants.M_LIVE_ARTIST:
						artist = decode(Arrays.copyOfRange(data, offset, data.length));
						fragment.prefix = "Artist: ";
						valid = data.length - offset > 0;
						break;
					case MidiConstants.M_LIVE_COMPOSER:
						composer = decode(Arrays.copyOfRange(data, offset, data.length));
						fragment.prefix = "Composer: ";
						valid = data.length - offset > 0;
						break;
					case MidiConstants.M_LIVE_DURATION:
						duration = decode(Arrays.copyOfRange(data, offset, data.length));
						fragment.prefix = "Duration: ";
						break;
					case MidiConstants.M_LIVE_BPM:
						bpm = decode(Arrays.copyOfRange(data, offset, data.length));
						fragment.prefix = "BPM: ";
						break;
					default:
						log.severe("Unknown M-LIVE tag: "+data[0]+" -- "+ decode(Arrays.copyOfRange(data, offset, data.length)));
						return;
				}
				
				fragment.format = Format.UNKNOWN;
				fragment.reaction = Reaction.META_LINE;
				fragment.sylineBytes = Arrays.copyOfRange(data, offset, data.length);
			} else if (data[0] == (byte) '<' && fragment.source == Source.LYRIC) {
				valid = true;
				offset = 1;
				fragment.format = Format.SOLTON;
				fragment.reaction = Reaction.LINE;
				fragment.sylineBytes = Arrays.copyOfRange(data, offset, data.length);
			} else if (data[0] == (byte) '%' && fragment.source == Source.LYRIC) {
				valid = true;
				offset = 1;
				fragment.format = Format.SOLTON;
				fragment.reaction = Reaction.CHORD;
				fragment.sylineBytes = Arrays.copyOfRange(data, offset, data.length);
			} else if (data[0] == (byte) '%' && fragment.source == Source.TEXT) {
				valid = true;
				offset = 1;
				fragment.format = Format.TUNE1000;
				fragment.reaction = Reaction.CHORD;
				fragment.sylineBytes = Arrays.copyOfRange(data, offset, data.length);
			} else if (data[0] == (byte) '\n' && fragment.source == Source.LYRIC && data.length == 1) {
				valid = true;
				offset = 1;
				fragment.format = Format.TUNE1000;
				fragment.reaction = Reaction.CLEAR_NEW;
			} else if (data[0] == (byte) '\r' && fragment.source == Source.LYRIC && data.length == 1) {
				valid = true;
				offset = 1;
				fragment.format = Format.TUNE1000;
				fragment.reaction = Reaction.NEWLINE_NEW;
			} else {
				boolean allowStitch = false;
				if (data.length >= 2 && data[0] == (byte) '*' && data[1] == (byte) ' ') {
					valid = true;
					fragment.reaction = Reaction.INFO;
					offset = 2;
					fragment.format = Format.UNKNOWN;
					if (data.length == 6 && data[2] == (byte) 'E' && data[3] == (byte) 'N' && data[4] == (byte) 'G' && data[5] == (byte) 'L') {
						fragment.reaction = Reaction.LANGUAGE;
					}
				} else if (data[0] == (byte) '@' && data.length >= 2) {
					valid = true;
					switch (data[1]) {
						case 'K':
						case 'k':
							valid = data.length > 2;
							offset = 2;
							fragment.reaction = Reaction.RIGHTS;
							fragment.format = fragment.source == Source.TEXT?Format.SOFT_KARAOKE:Format.TUNE1000;
							break;
						case 'L':
						case 'l':
							valid = data.length > 2;
							offset = 2;
							fragment.reaction = Reaction.LANGUAGE;
							fragment.format = fragment.source == Source.TEXT?Format.SOFT_KARAOKE:Format.TUNE1000;
							break;
						case 'T':
						case 't':
							valid = data.length > 2;
							offset = 2;
							fragment.reaction = Reaction.TITLE;
							fragment.format = fragment.source == Source.TEXT?Format.SOFT_KARAOKE:Format.TUNE1000;
							break;
						case 'W':
						case 'w':
							// Not officially part of the spec, but used by some
							// lyrics editors.
							valid = data.length > 2;
							offset = 2;
							fragment.reaction = Reaction.WRITER;
							fragment.format = fragment.source == Source.TEXT?Format.SOFT_KARAOKE:Format.TUNE1000;
							break;
						case 'I':
						case 'i':
							valid = data.length > 2;
							offset = 2;
							fragment.reaction = Reaction.INFO;
							fragment.format = fragment.source == Source.TEXT?Format.SOFT_KARAOKE:Format.TUNE1000;
							break;
						case 'V':
						case 'v':
							valid = data.length > 2;
							offset = 2;
							fragment.reaction = Reaction.VERSION;
							fragment.format = fragment.source == Source.TEXT?Format.SOFT_KARAOKE:Format.TUNE1000;
							break;
						default:
							fragment.reaction = Reaction.META_LINE;
							fragment.format = Format.UNKNOWN;
							break;
					}
				} else if (data[0] == (byte) '/' && fragment.source == Source.TEXT) {
					valid = true;
					offset = 1;
					fragment.reaction = Reaction.NEWLINE_OLD;
					fragment.format = Format.SOFT_KARAOKE;
					log.finest("Newline: "+MidiUtils.formatBytesHexOnly(Arrays.copyOfRange(data, offset, data.length)));
					allowStitch = true;
				} else if (data[0] == (byte) '\\' && fragment.source == Source.TEXT) {
					valid = true;
					offset = 1;
					fragment.reaction = Reaction.CLEAR_OLD;
					fragment.format = Format.SOFT_KARAOKE;
					log.finest("Clear: "+MidiUtils.formatBytesHexOnly(data));
				} else if (data[0] == (byte) '/' && fragment.source == Source.LYRIC) {
					valid = true;
					offset = 1;
					fragment.reaction = Reaction.NEWLINE_NEW;
					fragment.format = Format.UNKNOWN;
					allowStitch = true;
				} else if (data[0] == (byte) '\\' && fragment.source == Source.LYRIC) {
					valid = true;
					offset = 1;
					fragment.reaction = Reaction.CLEAR_NEW;
					fragment.format = Format.UNKNOWN;
					log.fine("Clear: "+MidiUtils.formatBytesHexOnly(data));
				} else {
					valid = true;
					fragment.reaction = Reaction.SYLLABLE;
					fragment.format = fragment.source == Source.TEXT?Format.SOFT_KARAOKE:Format.TUNE1000;
					if (lastType != null && fragment.format != lastType) fragment.reaction = Reaction.NEWLINE_NEW;//should maybe be old
					lastType = fragment.format;
					//log.severe("Syllable: "+MidiUtils.formatBytesHexOnly(data));
				}
				// Check if this is a Newline_new event
				// and if the prev fragment ended with a hyphen.
				if (allowStitch && !text.isEmpty()) {
					TextFragment last = text.getLast();

					// Check if the last fragment ended with '-' (ASCII 45)
					if (last.sylineBytes != null && last.sylineBytes.length > 0 &&
							last.sylineBytes[last.sylineBytes.length - 1] == (byte)'-') {
						// In old kar files a newline was often inserted to
						// break long lines so they fit on screen.
						// So if we find the last syllable ended with hyphen,
						// we assume that a sentence was broken up, and we stitch it.

						// Found hyphen. Stitching them together:
						if (fragment.sylineBytes != null && fragment.sylineBytes.length > 0) {
							fragment.reaction = Reaction.SYLLABLE; // Downgrade to a simple syllable
							log.fine("Merged hyphenated newline (converted to syllable): " + last);
						}
					}
				}
				if (valid) {
					if (data.length - offset > 0) {
						int end = data.length;
						if (data[data.length-1] == (byte)'\r') {
							if (data.length < 3 || data[data.length-2] != (byte)'-') {
								if (fragment.reaction == Reaction.SYLLABLE) fragment.reaction = Reaction.NEWLINE_AFTER;
							}
							end = Math.max(offset, end - 1);
						}
						byte[] content = Arrays.copyOfRange(data, offset, end);
						fragment.sylineBytes = content;
						
					    if (fragment.reaction == Reaction.LANGUAGE) language = decode(fragment.sylineBytes); 
					}					
				}
			}
		}

		if (valid) {
			// We skip mark and cue as they mostly do not hold lyrics
			if (fragment.source == Source.CUE || fragment.source == Source.MARK) {
				log.info(fragment.toString());
			} else {
				log.finer(MidiUtils.formatBytesHexOnly(data));
                //fragment.syline = decode(fragment.sylineBytes);//for debug
				log.fine(fragment.toString());
				increaseTextStats(fragment.format);
				boolean ok = text.add(fragment);
				if (!ok) log.warning("Dropped syllable");
				addToTrackScore(track, fragment);
			}
		}
	}
	
	private boolean decodeKarakan(TextFragment fragment) {
		String syllable = fragment.syline;
		Matcher m = KARAKAN_PART_PATTERN.matcher(syllable);
		List<Integer> parts = new ArrayList<>();
	    while (m.find()) {
	        parts.add(Integer.parseInt(m.group(1)));
		}
	    if (!parts.isEmpty() && !parts.contains(1)) {
	    	// not vocal #1
	    	fragment.format = Format.KARAKAN;
	    	return false;
	    } else if (!parts.isEmpty()) {
	    	syllable = syllable.substring(4);
	    	fragment.format = Format.KARAKAN;
	    }
	    return true;
	}

	void collectSysex(long tick, byte[] message, int track) {
		// untested as I didn't find any midi files with embedded MIDISOFT lyrics
		if (MidiUtils.isSysexLyrics(message)) {
			if ((message[6] & 0xFF) == 0x08) {
				TextFragment fragment = new TextFragment();
				fragment.format = Format.MIDISOFT;
				fragment.source = Source.SYSEX;
				fragment.track = track;
				fragment.reaction = Reaction.SYNC;
				fragment.tick = tick;
				increaseTextStats(fragment.format);
				text.add(fragment);
			} else {
				boolean valid = false;
				TextFragment fragment = new TextFragment();
				switch (message[6] & 0xFF) {
					case 0x04:
						valid = true;
						fragment.reaction = Reaction.FIRST;
						break;
					case 0x05:
						valid = true;
						fragment.reaction = Reaction.SECOND;
						break;
					case 0x06:
						valid = true;
						fragment.reaction = Reaction.THIRD;
						break;
					case 0x07:
						valid = true;
						fragment.reaction = Reaction.FOURTH;
						break;
					case 0x01:
						valid = true;
						fragment.reaction = Reaction.CHORD;
						break;
				}
				if (valid) {
                    fragment.sylineBytes = Arrays.copyOfRange(message, 7, message.length - 1);
					fragment.format = Format.MIDISOFT;
					fragment.source = Source.SYSEX;
					fragment.track = track;
					fragment.tick = tick;
					increaseTextStats(fragment.format);
					text.add(fragment);
				}
			}
		}
	}
	
	private void increaseTextStats(Format format) {
		Integer count = textStats.get(format);
		if (count == null) count = 0;
		count++;
		textStats.put(format, count);
	}

	/**
	 * 
	 * @return string describing count of how many of each lyrics format was seen in song.
	 */
	public String getTextStats() {
		StringBuilder str = new StringBuilder();
		for (Format type : Format.values()) {
			Integer count = textStats.get(type);
			if (count != null) {
				if (!str.isEmpty()) str.append(", ");
				str.append(type).append(": ").append(count);
			}
		}
		return str.toString();
	}
	
	/**
	 * 
	 * @param track a fragment was seen in certain track, increase the counter for number of fragments in that track.
	 */
	private void addToTrackScore(int track, TextFragment frag) {
		Integer curr = trackStats.get(track);
		if (curr == null) curr = 0;
		if (frag.syline != null && (
			   frag.reaction == Reaction.LINE
			|| frag.reaction == Reaction.FIRST
			|| frag.reaction == Reaction.SECOND
			|| frag.reaction == Reaction.THIRD
			|| frag.reaction == Reaction.FOURTH
			|| frag.reaction == Reaction.SYLLABLE
			|| frag.reaction == Reaction.NEWLINE_NEW
			|| frag.reaction == Reaction.NEWLINE_OLD
			|| frag.reaction == Reaction.CLEAR_NEW
			|| frag.reaction == Reaction.CLEAR_OLD
			|| frag.reaction == Reaction.NEWLINE_AFTER)) {
				curr += frag.sylineBytes.length;
				//curr++;
		}
		trackStats.put(track, curr);
	}
	
	/**
	 * Calculate which track has the most lyrics and return that.
	 * We simply ignore the other tracks, they often for chorus or duets.
	 * Or sometimes they are just copies.
	 */
	private int calcWinningTrack() {
		int winner = 0;
		int count = 0;
		for (Entry<Integer, Integer> entry : trackStats.entrySet()) {
			if (entry.getValue() >= count) {
				winner = entry.getKey();
				count = entry.getValue();
			}
			log.info("Track "+entry.getKey()+" text score: "+entry.getValue());
		}
		return winner;
	}
	
	public String getText() {
		StringBuilder str = new StringBuilder();
		
		if (text.isEmpty()) {
			//important for abc tool that decoder don't get called,
			//since its not present in its jar.
			return str.toString();
		}
		int mainTrack = calcWinningTrack();
        log.fine("Winning lyrics track: "+mainTrack);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		/*
		if (!cache.getCopyright().isEmpty()) {
		 
			str += "MIDI copyright: "+cache.getCopyright()+"\n";
		}
		*/
		Reaction prev = null;
		byte[] prevClear = {};
		for (TextFragment fraction : text) {
			switch (fraction.reaction) {
				case LINE:
				case FIRST:
				case SECOND:
				case THIRD:
				case FOURTH:
					if (fraction.track != mainTrack) break;
					if (fraction.reaction == Reaction.FIRST) bytes.write(0x0A);
					writeTrimmed(bytes, fraction.sylineBytes);
					bytes.write(0x0A);
					break;
				case SYLLABLE:
					if (fraction.track != mainTrack) break;
					writeTrimmed(bytes, fraction.sylineBytes);
					break;
				case NEWLINE_OLD:
					if (fraction.track != mainTrack) break;
					if (prev != Reaction.CLEAR_OLD || prevClear.length > 0) {
						bytes.write(0x0A);
					}
					writeTrimmed(bytes, fraction.sylineBytes);
					break;
				case NEWLINE_NEW:
					if (fraction.track != mainTrack) break;
					if (prev != Reaction.CLEAR_NEW || prevClear.length > 0) {
						bytes.write(0x0A);
					}
					writeTrimmed(bytes, fraction.sylineBytes);
					break;
				case NEWLINE_AFTER:
					if (fraction.track != mainTrack) break;
					writeTrimmed(bytes, fraction.sylineBytes);
					bytes.write(0x0A);
					break;
				case CLEAR_NEW:
				case CLEAR_OLD:
					bytes.write(0x0A);
					bytes.write(0x0A);
					if (fraction.track != mainTrack) break;
					writeTrimmed(bytes, fraction.sylineBytes);
					prevClear = fraction.sylineBytes;
					break;
				case TITLE:
					str.append("Title: ").append(decode(fraction.sylineBytes)).append("\n");
					break;
				case RIGHTS:
					//str += "Lyrics copyright: "+decode(fraction.sylineBytes)+"\n";
					break;
				case LANGUAGE:
					str.append("Language: ").append(decode(fraction.sylineBytes)).append("\n");
					break;
				case INFO:
					str.append("Info: ").append(decode(fraction.sylineBytes)).append("\n");
				case META_LINE:
					str.append(fraction.prefix).append(decode(fraction.sylineBytes)).append("\n");
				default:
					break;
			}
			prev = fraction.reaction;
		}
		str.append(cleanSyllable(decode(bytes.toByteArray())));
		return str.toString();
	}

	public List<LyricLine> getStructuredLyrics() {
		List<LyricLine> lines = new ArrayList<>();
		if (text.isEmpty()) {
			return lines;
		}

		int mainTrack = calcWinningTrack();
		ByteArrayOutputStream lineBytes = new ByteArrayOutputStream();
		long currentLineTick = -1L;
		long endTick = -1L;

		// Container for metadata to be added at the top
		StringBuilder metaBlock = new StringBuilder();

		Reaction prev = null;

		String metaLine = "";
		String metaLinePrev = "";
		for (TextFragment fraction : text) {
			// Collect metadata
			if (isMetadata(fraction.reaction)) {
                metaLine = switch (fraction.reaction) {
                    case TITLE -> "Title: " + decode(fraction.sylineBytes);
                    case LANGUAGE -> "Language: " + decode(fraction.sylineBytes);
                    case INFO -> "Info: " + decode(fraction.sylineBytes);
                    case META_LINE -> fraction.prefix + decode(fraction.sylineBytes);
                    case RIGHTS -> "Lyrics Copyrights: " + decode(fraction.sylineBytes);
					case WRITER -> "Writer: " + decode(fraction.sylineBytes);
					case VERSION -> "Version: " + decode(fraction.sylineBytes);
					default -> "";
                };

				if (!metaLine.isBlank() && !metaLinePrev.equals(metaLine)) {
					//the prev check is for the same meta in different tracks
					if (!metaBlock.isEmpty()) metaBlock.append("\n");
					metaBlock.append(metaLine);
				}
				metaLinePrev = metaLine;
				continue;
			}

			if (fraction.track != mainTrack) continue;

			boolean isNewlineBefore = false;
			boolean isNewlineAfter = false;
			boolean processContent = false;

			//System.out.println("Processing fragment: "+fraction.reaction+" bytes="+fraction.sylineBytes.length+" containsVisibleContent="+containsVisibleContent(fraction.sylineBytes, fraction.sylineBytes.length)+" content="+ MidiUtils.formatBytesHexOnly(fraction.sylineBytes)+" tick="+fraction.tick);
			switch (fraction.reaction) {
				case LINE:
				case FIRST:
				case SECOND:
				case THIRD:
				case FOURTH:
					isNewlineBefore = true;
					processContent = true;
					break;
				case SYLLABLE:
					processContent = true;
					break;
				case NEWLINE_OLD:
				case NEWLINE_NEW:
					if (prev != Reaction.CLEAR_NEW && prev != Reaction.CLEAR_OLD) {
						isNewlineBefore = true;
					}
					processContent = true;
					break;
				case NEWLINE_AFTER:
					processContent = true;
					isNewlineAfter = true;
					break;
				case CLEAR_NEW:
				case CLEAR_OLD:
					isNewlineBefore = true;
					processContent = true;
					break;
				default:
					break;
			}

			// flush previous line (Allow empty lines)
			if (isNewlineBefore) {
				// If we are flushing, and lineBytes is empty, it's an empty line.
				// We use the currentLineTick if set, otherwise the fraction's tick (for blank lines).
				long tick = (currentLineTick != -1L) ? currentLineTick : fraction.tick;
				long lastSyllableTick = (endTick != -1) ? endTick : tick;
				String decodedLine = cleanSyllable(decode(lineBytes.toByteArray()));
				lines.add(new LyricLine(tick, decodedLine.trim(), lastSyllableTick));
				//System.out.println("Newline before: "+decodedLine+" endTick="+endTick+" tick="+tick+" currentLineTick="+currentLineTick+" fraction.tick="+fraction.tick);
				lineBytes.reset();
				endTick = -1L;
				currentLineTick = -1L;
			}

			// Check effective length (ignoring null terminators)
			int effectiveLength = 0;
			if (fraction.sylineBytes != null) {
				effectiveLength = fraction.sylineBytes.length;
				while (effectiveLength > 0 && fraction.sylineBytes[effectiveLength - 1] == 0) {
					effectiveLength--;
				}
			}

			// Capture tick for new line
			if (currentLineTick == -1 && processContent && effectiveLength > 0) {
				currentLineTick = fraction.tick;
			}

			// Append content
			if (processContent && effectiveLength > 0) {
				writeTrimmed(lineBytes, fraction.sylineBytes);

				if (containsVisibleContent(fraction.sylineBytes, fraction.sylineBytes.length)) endTick = fraction.tick;
			}

			// Flush immediately if newline is AFTER
			if (isNewlineAfter) {
				long tick = (currentLineTick != -1L) ? currentLineTick : fraction.tick;
				long lastSyllableTick = (endTick != -1L) ? endTick : tick;
				String decodedLine = cleanSyllable(decode(lineBytes.toByteArray()));
				//System.out.println("Newline after: "+decodedLine+" endTick="+endTick+" tick="+tick+" currentLineTick="+currentLineTick+" fraction.tick="+fraction.tick);
				lines.add(new LyricLine(tick, decodedLine.trim(), lastSyllableTick));

				lineBytes.reset();
				endTick = -1L;
				currentLineTick = -1L;
			}

			prev = fraction.reaction;
		}

		// Flush remaining buffer
		if (lineBytes.size() > 0) {
			String decodedLine = cleanSyllable(decode(lineBytes.toByteArray()));
			long lastSyllableTick = (endTick != -1L) ? endTick : Math.max(0L, currentLineTick);
			// Allow last line to be added even if effectively empty, to match file structure
			lines.add(new LyricLine(Math.max(0L, currentLineTick), decodedLine.trim(), lastSyllableTick));
		}

		if (!metaBlock.isEmpty()) {
			lines.addFirst(new LyricLine(0L, metaBlock.toString(), 0L));
		} else {
			//make sure there always is a meta-block in the first slot.
			lines.addFirst(new LyricLine(0L, "", 0L));
		}

		return lines;
	}

	private boolean isMetadata(Reaction r) {
		return r == Reaction.TITLE || r == Reaction.RIGHTS || r == Reaction.LANGUAGE ||
				r == Reaction.INFO || r == Reaction.META_LINE || r == Reaction.VERSION || r == Reaction.WRITER;
	}

	private boolean containsVisibleContent(byte[] bytes, int length) {
		if (bytes == null || length <= 0) return false;
		for (int i = 0; i < length; i++) {
			// Check for chars > 32 (Space).
			// This filters out spaces, nulls, tabs, and newlines.
			if ((bytes[i] & 0xFF) > 32) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Call me after decoding syllable/line
	 *
     */
	private String cleanSyllable(String str) {
		str = str.replace("STARTAKKORD", "");
		str = str.replace("|C:|", "");//chorus start
		str = str.replace("|:C|", "");//chorus end (normally in later syllable than start)

		// Split by whitespace to handle words individually.
		// We use a regex lookaround to keep the delimiters so we can reconstruct the spacing perfectly.
		// ((?<=\s)|(?=\s)) splits *around* whitespace but keeps the whitespace as tokens.
		String[] tokens = str.split("((?<=\\s)|(?=\\s))");

		StringBuilder sb = new StringBuilder();
		for (String token : tokens) {
			// If the token is just whitespace, preserve it
			if (token.isBlank()) {
				sb.append(token);
				continue;
			}

			// Check if this specific word looks like a URL
			if (token.contains("://")) {
				// It is a URL, append as is
				sb.append(token);
			} else {
				// It is normal text, replace all slashes with newlines
				sb.append(token.replace("/", "\n"));
			}
		}
		return sb.toString();
	}
	
	private static void writeTrimmed(ByteArrayOutputStream out, byte[] data) {
	    int end = data.length;
	    while (end > 0 && data[end - 1] == (byte)0x00) {
	        end--;
	    }
	    out.write(data, 0, end);
	}

	public static class TextFragment implements Comparable<TextFragment> {
		long tick;
		Format format;
		Source source;
		String syline = "";//  syllable/line
		int track;
		Reaction reaction;
		
		String prefix = "";
		byte[] sylineBytes = {};
		
		@Override
		public String toString() {
			return format+": "+reaction+" ("+source+") '"+syline+"' in track "+track+" tick="+tick+(sylineBytes==null?"":(" hex:"+MidiUtils.formatBytesHexOnly(sylineBytes)));
		}
		
		public enum Reaction {
			// the order matters
			TITLE,
			RIGHTS,
			LANGUAGE,
			INFO,
			VERSION,
			WRITER,
			FIRST,// first full line
			SECOND,
			THIRD,
			FOURTH,
			
			LINE,// full line
			CLEAR_OLD,//clear screen
			NEWLINE_OLD,// newline before syllable
			SYLLABLE,
			NEWLINE_AFTER,// newline after syllable
			CLEAR_NEW,//clear screen
			NEWLINE_NEW,// newline
			CHORD,
			SYNC // highlight next full line
			, META_LINE // M-LIVE
			
			// in modern Tune1000 kar, newline is sometimes at same tick as syllable, meaning syllable first, then newline.
			// in older Soft Karaoke kar, newline is sometimes at same tick as syllable, meaning newline first, then syllable.
		}
		public enum Source {
			// which kind of midi event that was source of this TextFragment.
			SYSEX,
			LYRIC,
			TEXT,
			MARK, 
			CUE,
			MLIVE
		}
		public enum Format {
			SOFT_KARAOKE,
			TUNE1000,// kinda version 2 of soft karaoke
			SOLTON,// very simple
			MIDISOFT,// sysex based
			KARAKAN,// variation of Tune1000
			UNKNOWN// does not adhere to any standard
		}
		
		@Override
		public int compareTo(TextFragment o) {
			// to be added to treeset compareTo() must never return 0
			int c = Long.compare(tick, o.tick);
			if (c == 0) {
				c = reaction.compareTo(o.reaction);
				if (c == 0) {
					c = format.compareTo(o.format);
					if (c == 0) {
						c = source.compareTo(o.source);
						if (c == 0) {
							c = Integer.compare(track, o.track);
							if (c == 0) {
								c = format.compareTo(o.format);
								if (c == 0) {
									c = syline.compareTo(o.syline);
									if (c == 0) {
										return 1;
									}
								}
							}
						}
					}
				}
			}
			return c;
		}
		
		@Override
		public boolean equals(Object o) {
			return false;			
		}
	}
}
