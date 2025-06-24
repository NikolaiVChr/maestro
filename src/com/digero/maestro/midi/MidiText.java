package com.digero.maestro.midi;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.digero.common.midi.MidiUtils;
import com.digero.common.util.Pair;
import com.digero.common.midi.MidiConstants;
import com.digero.maestro.midi.MidiText.TextFragment.Source;
import com.digero.maestro.midi.MidiText.TextFragment.Reaction;
import com.digero.maestro.midi.MidiText.TextFragment.Format;

public class MidiText {
	private static final Logger log = Logger.getLogger("import.midi.text");
	
	private static final Pattern KARAKAN_PART_PATTERN = Pattern.compile("\\[P(\\d+)]");
	private SequenceDataCache cache;
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
	
	private void reEncodeAll(Iterator<TextFragment> iterator, Charset cs) {
		log.info("Re-decoding all text with "+cs.name());
		while (iterator.hasNext()) {
			TextFragment fragment = iterator.next();
			if (fragment.sylineBytes == null || fragment.sylineBytes.length == 0) continue;
			String text = new String(fragment.sylineBytes, cs);
			fragment.syline = text;
		}
	}
	
	private void addToCharsetScore(Charset cs) {
		Integer curr = csStats.get(cs);
		if (curr == null) curr = 0;
		curr++;
		csStats.put(cs, curr);
		csTotal++;
	}
	
	private Charset calcWinningCS() {
		Charset winner = null;
		int count = 0;
		for (Entry<Charset, Integer> entry : csStats.entrySet()) {
			if (entry.getValue() >= count && entry.getValue() > csTotal/2) {
				winner = entry.getKey();
				count = entry.getValue();
			}
			log.info("Track "+entry.getKey()+" text score: "+entry.getValue());
		}
		return winner;
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
						valid = true;
						break;
					case MidiConstants.M_LIVE_ARTIST:
						artist = decode(Arrays.copyOfRange(data, offset, data.length));
						fragment.prefix = "Artist: ";
						valid = true;
						break;
					case MidiConstants.M_LIVE_COMPOSER:
						composer = decode(Arrays.copyOfRange(data, offset, data.length));
						fragment.prefix = "Composer: ";
						valid = true;
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
						return;
				}
				
				fragment.format = Format.UNKNOWN;
				fragment.reaction = Reaction.LINE;
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
				if (data[0] == (byte) '@' && data.length >= 2) {
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
							// not 100% sure what the W tag means, so we treat it as I
							log.info(decode(data));
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
							fragment.reaction = Reaction.LINE;
							fragment.format = Format.UNKNOWN;
							break;
					}
				} else if (data[0] == (byte) '/' && fragment.source == Source.TEXT && data.length > 0) {
					valid = true;
					offset = 1;
					fragment.reaction = Reaction.NEWLINE_OLD;
					fragment.format = Format.SOFT_KARAOKE;
					log.finest("Newline: "+MidiUtils.formatBytesHexOnly(Arrays.copyOfRange(data, offset, data.length)));
				} else if (data[0] == (byte) '\\' && fragment.source == Source.TEXT && data.length > 0) {
					valid = true;
					offset = 1;
					fragment.reaction = Reaction.CLEAR_OLD;
					fragment.format = Format.SOFT_KARAOKE;
					log.finest("Clear: "+MidiUtils.formatBytesHexOnly(data));
				} else if (data[0] == (byte) '/' && fragment.source == Source.LYRIC && data.length > 0) {
					valid = true;
					offset = 1;
					fragment.reaction = Reaction.NEWLINE_NEW;
					fragment.format = Format.UNKNOWN;
				} else if (data[0] == (byte) '\\' && fragment.source == Source.LYRIC && data.length > 0) {
					valid = true;
					offset = 1;
					fragment.reaction = Reaction.CLEAR_NEW;
					fragment.format = Format.UNKNOWN;
					log.fine("Clear: "+MidiUtils.formatBytesHexOnly(data));
				} else if (data.length > 0) {
					valid = true;
					fragment.reaction = Reaction.SYLLABLE;
					fragment.format = fragment.source == Source.TEXT?Format.SOFT_KARAOKE:Format.TUNE1000;
					if (lastType != null && fragment.format != lastType) fragment.reaction = Reaction.NEWLINE_NEW;//should maybe be old
					lastType = fragment.format;
					//log.severe("Syllable: "+MidiUtils.formatBytesHexOnly(data));
				}
				if (valid) {
					if (data.length - offset > 0) {
						int end = data.length;
						if (data[data.length-1] == (byte)'\r') {
							if (fragment.reaction == Reaction.SYLLABLE) fragment.reaction = Reaction.NEWLINE_AFTER;
							end = Math.max(offset, end-1);
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
	    if (parts.size() > 0 && !parts.contains(1)) {
	    	// not vocal #1
	    	fragment.format = Format.KARAKAN;
	    	return false;
	    } else if (parts.size() > 0) {
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
					byte[] data = Arrays.copyOfRange(message, 7, message.length - 1);
					fragment.sylineBytes = data;
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
		String str = "";
		for (Format type : Format.values()) {
			Integer count = textStats.get(type);
			if (count != null) {
				if (!str.isEmpty()) str += ", "; 
				str += type+": "+count; 
			}
		}
		return str;
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
		int mainTrack = calcWinningTrack();
		/*
		Charset mainCharset = calcWinningCS();
		if (mainCharset != null) reEncodeAll(text.iterator(), mainCharset);
		*/
		String str = "";
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
				case Reaction.LINE:
				case Reaction.FIRST:
				case Reaction.SECOND:
				case Reaction.THIRD:
				case Reaction.FOURTH:
					if (fraction.track != mainTrack) break;
					if (fraction.reaction == Reaction.FIRST) bytes.write(0x0A);
					writeTrimmed(bytes, fraction.sylineBytes);
					bytes.write(0x0A);
					break;
				case Reaction.SYLLABLE:
					if (fraction.track != mainTrack) break;
					writeTrimmed(bytes, fraction.sylineBytes);
					break;
				case Reaction.NEWLINE_OLD:
					if (fraction.track != mainTrack) break;
					if (prev != Reaction.CLEAR_OLD || prevClear.length > 0) {
						bytes.write(0x0A);
					}
					writeTrimmed(bytes, fraction.sylineBytes);
					break;
				case Reaction.NEWLINE_NEW:
					if (fraction.track != mainTrack) break;
					if (prev != Reaction.CLEAR_NEW || prevClear.length > 0) {
						bytes.write(0x0A);
					}
					writeTrimmed(bytes, fraction.sylineBytes);
					break;
				case Reaction.NEWLINE_AFTER:
					if (fraction.track != mainTrack) break;
					writeTrimmed(bytes, fraction.sylineBytes);
					bytes.write(0x0A);
					break;
				case Reaction.CLEAR_NEW:
				case Reaction.CLEAR_OLD:
					bytes.write(0x0A);
					bytes.write(0x0A);
					if (fraction.track != mainTrack) break;
					writeTrimmed(bytes, fraction.sylineBytes);
					prevClear = fraction.sylineBytes;
					break;
				case Reaction.TITLE:
					str += "Title: "+decode(fraction.sylineBytes)+"\n";
					break;
				case Reaction.RIGHTS:
					//str += "Lyrics copyright: "+decode(fraction.sylineBytes)+"\n";
					break;
				case Reaction.LANGUAGE:
					str += "Language: "+decode(fraction.sylineBytes)+"\n";
					break;
				case Reaction.INFO:
					str += "Info: "+decode(fraction.sylineBytes)+"\n";
				default:
					break;
			}
			prev = fraction.reaction;
		}
		str += cleanSyllable(decode(bytes.toByteArray()));
		return str;
	}
	
	/**
	 * Call me after decoding syllable/line
	 * 
	 * @param str
	 * @return
	 */
	private String cleanSyllable(String str) {
		str = str.replace("STARTAKKORD", "");
		str = str.replace("|C:|", "");//chorus start
		str = str.replace("|:C|", "");//chorus end (normally in later syllable than start)
		str = str.replace("/", "\n");//newline command in middle of text (modern kar)
		return str;
	}
	
	private static void writeTrimmed(ByteArrayOutputStream out, byte[] data) {
	    int end = data.length;
	    while (end > 0 && data[end - 1] == (byte)0x00) {
	        end--;
	    }
	    out.write(data, 0, end);
	}

	public class TextFragment implements Comparable<TextFragment> {
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
