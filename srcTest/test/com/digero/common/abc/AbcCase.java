package com.digero.common.abc;

import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One named ABC input for the snapshot tests: one or more in-memory ABC "files", the profiles to convert it with and
 * optional tweaks to {@link AbcToMidi.Params} (stereo, instrument overrides).
 *
 * @param name     Unique; also the snapshot file name, so keep it to [a-z0-9_].
 * @param sources  The ABC files, converted together in this order (like opening several files in the ABC Player).
 * @param profiles The profiles to convert with; one snapshot section per profile.
 * @param tweak    Applied to the Params after the profile.
 */
record AbcCase(String name, List<Source> sources, Set<Profile> profiles, Consumer<AbcToMidi.Params> tweak) {

	record Source(String fileName, List<String> lines) {
	}

	AbcCase {
		if (!name.matches("[a-z0-9_]+"))
			throw new IllegalArgumentException("Case name must match [a-z0-9_]+: " + name);
		sources = List.copyOf(sources);
		profiles = Collections.unmodifiableSet(EnumSet.copyOf(profiles));
	}

	/** A case with a single ABC file, given line by line, converted with all profiles. */
	static AbcCase of(String name, String... lines) {
		return new AbcCase(name, List.of(new Source(name + ".abc", List.of(lines))), EnumSet.allOf(Profile.class),
				p -> {
				});
	}

	/** Restricts the case to the given profiles (e.g. to avoid the random cowbell notes of the LotRO profiles). */
	AbcCase only(Profile first, Profile... rest) {
		return new AbcCase(name, sources, EnumSet.of(first, rest), tweak);
	}

	/** Adds a tweak to the Params, applied after the profile. */
	AbcCase with(Consumer<AbcToMidi.Params> extraTweak) {
		return new AbcCase(name, sources, profiles, tweak.andThen(extraTweak));
	}

	/** Adds another ABC file that is converted together with the existing ones. */
	AbcCase plusFile(String fileName, String... lines) {
		List<Source> more = new ArrayList<>(sources);
		more.add(new Source(fileName, List.of(lines)));
		return new AbcCase(name, more, profiles, tweak);
	}

	List<FileAndData> filesData() {
		List<FileAndData> data = new ArrayList<>();
		for (Source source : sources)
			data.add(new FileAndData(new File(source.fileName()), source.lines()));
		return data;
	}

	/** Concatenates line arrays; used to build a tune from a header and a body. */
	static String[] concat(String[]... parts) {
		return Arrays.stream(parts).flatMap(Arrays::stream).toArray(String[]::new);
	}

	@Override
	public String toString() {
		return name;
	}
}