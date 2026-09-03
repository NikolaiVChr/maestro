package com.digero.common.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies apostrophe escaping and placeholder consistency in the uitext*.properties
 * bundles against the way {@link UIText} consumes them:
 *   - UIText.get(key, args...) with args.length > 0 runs the value through MessageFormat,
 *     where a single ' opens a quoted region and doubling ('') is required.
 *   - UIText.get(key) (no args) returns the value verbatim, where '' would show literally.
 *
 * Whether a given key is formatted is a call-site decision, not something visible in the
 * .properties file. So the "template" direction (placeholder present) is enforced strictly;
 * the "raw" direction is only a heuristic warning with an allow-list. The third test checks
 * that translations reference the same MessageFormat argument indices as the base bundle.
 */
class UITextEscapingTest {

    private static final String BASE_BUNDLE_NAME = "uitext.properties";
    private static final String BASE_BUNDLE_RESOURCE = "/" + BASE_BUNDLE_NAME;

    /**
     * Matches an intended MessageFormat placeholder and captures its argument index:
     * {0}, {1,number}, {12,date,short}, etc. Deliberately quote-agnostic so that a value
     * the author meant as a template is recognised even when a lone ' would (buggily) hide
     * the placeholder from MessageFormat itself.
     */
    private static final Pattern ARGUMENT_INDEX = Pattern.compile("\\{(\\d+)");

    @Test
    void templatesMustEscapeApostrophes() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Path file : discoverBundleFiles()) {
            for (var entry : load(file).entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (!looksLikeTemplate(value)) continue;

                // 1) A template must at least be a parseable MessageFormat pattern.
                try {
                    new MessageFormat(value);
                } catch (IllegalArgumentException e) {
                    violations.add(describe(file, key, value,
                            "is not a valid MessageFormat pattern: " + e.getMessage()));
                    continue;
                }

                // 2) No placeholder may be trapped inside a quoted region. A lone (mis-escaped)
                //    apostrophe opens a quote that MessageFormat treats as literal text, silently
                //    swallowing the {n} arguments after it. Deliberate literal-quoting such as
                //    '<', '>' or '{' is fine: those regions simply contain no {n}, so nothing is lost.
                Set<Integer> swallowed = swallowedPlaceholderIndices(value);
                if (!swallowed.isEmpty()) {
                    violations.add(describe(file, key, value,
                            "has placeholder(s) " + swallowed + " trapped inside a quoted region; "
                                    + "a lone ' opened a quote MessageFormat will treat as literal text. "
                                    + "Double the intended-literal apostrophe as '' (leave deliberate "
                                    + "'<' / '{' literal-quoting as is)"));
                }
            }
        }
        if (!violations.isEmpty()) {
            fail(report("Template values with broken apostrophe escaping", violations));
        }
    }

    /**
     * Opposite direction, heuristic only. A value with no {n} placeholder is *probably* returned
     * verbatim by UIText.get(key), where '' would render as two literal apostrophes. But
     * UIText.get(key, args...) still runs MessageFormat even when the value has no braces, so a
     * doubled apostrophe there is correct. Suppress those known-good keys via the allow-list below.
     */
    @Test
    void nonTemplatesShouldNotDoubleApostrophes() throws Exception {
        // Keys that ARE passed through MessageFormat despite having no {n} placeholder.
        Set<String> formattedWithoutPlaceholders = Set.of(
                // "example.key.formatted.but.no.braces"
        );

        List<String> violations = new ArrayList<>();
        for (Path file : discoverBundleFiles()) {
            for (var entry : load(file).entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (looksLikeTemplate(value)) continue;
                if (formattedWithoutPlaceholders.contains(key)) continue;

                if (value.contains("''")) {
                    violations.add(describe(file, key, value,
                            "has '' but no placeholder; if this key is only ever read raw it will "
                            + "show two apostrophes. If it is formatted with args, add it to the "
                            + "allow-list in this test"));
                }
            }
        }
        if (!violations.isEmpty()) {
            fail(report("Non-template values with doubled apostrophes", violations));
        }
    }

    /**
     * Every localized bundle must reference the same set of MessageFormat argument indices as the
     * base bundle for each shared key. A translation that drops {1} silently loses that argument at
     * runtime; one that introduces an index the base never had will render a raw {n} (or throw)
     * because the caller only ever supplies the base's argument count. Keys absent from a
     * translation are skipped: ResourceBundle falls back to the base value, so no mismatch occurs.
     */
    @Test
    void placeholdersMustMatchAcrossLocales() throws Exception {
        Path baseFile = null;
        List<Path> localized = new ArrayList<>();
        for (Path f : discoverBundleFiles()) {
            if (BASE_BUNDLE_NAME.equals(f.getFileName().toString())) baseFile = f;
            else localized.add(f);
        }
        assertNotNull(baseFile,
                "Could not find base bundle " + BASE_BUNDLE_NAME + " among the discovered files.");

        // Precompute the base argument-index set for every key that actually has placeholders.
        Map<String, Set<Integer>> baseArgs = new TreeMap<>();
        for (var entry : load(baseFile).entrySet()) {
            Set<Integer> args = extractArgumentIndices(entry.getValue());
            if (!args.isEmpty()) baseArgs.put(entry.getKey(), args);
        }

        List<String> violations = new ArrayList<>();
        for (Path file : localized) {
            Map<String, String> translation = load(file);
            for (var entry : baseArgs.entrySet()) {
                String key = entry.getKey();
                Set<Integer> expected = entry.getValue();

                String translatedValue = translation.get(key);
                if (translatedValue == null) continue; // falls back to base value at runtime

                Set<Integer> actual = extractArgumentIndices(translatedValue);
                if (actual.equals(expected)) continue;

                Set<Integer> missing = new TreeSet<>(expected);
                missing.removeAll(actual);
                Set<Integer> unexpected = new TreeSet<>(actual);
                unexpected.removeAll(expected);

                StringBuilder msg = new StringBuilder("placeholder set ").append(actual)
                        .append(" does not match base ").append(expected);
                if (!missing.isEmpty()) msg.append("; missing ").append(missing);
                if (!unexpected.isEmpty()) msg.append("; unexpected ").append(unexpected);

                violations.add(describe(file, key, translatedValue, msg.toString()));
            }
        }
        if (!violations.isEmpty()) {
            fail(report("Placeholder sets that diverge from the base bundle", violations));
        }
    }

    // ---- helpers -----------------------------------------------------------

    private static boolean looksLikeTemplate(String value) {
        return ARGUMENT_INDEX.matcher(value).find();
    }

    private static Set<Integer> extractArgumentIndices(String value) {
        Set<Integer> indices = new TreeSet<>();
        Matcher m = ARGUMENT_INDEX.matcher(value);
        while (m.find()) {
            indices.add(Integer.parseInt(m.group(1)));
        }
        return indices;
    }

    /**
     * Argument indices the author wrote as {n} but that fall inside a MessageFormat quoted region,
     * so they are NOT live placeholders at runtime. A non-empty result means a lone/mis-escaped
     * apostrophe opened a quote that swallowed them. The rare inverse false positive — a genuinely
     * literal "{0}" the author quoted on purpose — would land here too; allow-list that key if it
     * ever occurs.
     */
    private static Set<Integer> swallowedPlaceholderIndices(String value) {
        Set<Integer> intended = extractArgumentIndices(value);
        Set<Integer> live = extractArgumentIndices(stripQuotedRegions(value));
        Set<Integer> swallowed = new TreeSet<>(intended);
        swallowed.removeAll(live);
        return swallowed;
    }

    /**
     * Removes every MessageFormat quoted region, mirroring the parser's own global quote handling:
     * '' is always a literal apostrophe (state unchanged); any other ' toggles quote state, across
     * subformats. What remains is exactly the text MessageFormat treats as live pattern.
     */
    private static String stripQuotedRegions(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean inQuote = false;
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == '\'') {
                if (i + 1 < value.length() && value.charAt(i + 1) == '\'') {
                    out.append('\'');   // literal apostrophe, quote state unchanged
                    i += 2;
                    continue;
                }
                inQuote = !inQuote;     // lone quote toggles the region
                i++;
                continue;
            }
            if (!inQuote) {
                out.append(c);
            }
            i++;
        }
        return out.toString();
    }

    private static List<Path> discoverBundleFiles() throws Exception {
        URL base = UITextEscapingTest.class.getResource(BASE_BUNDLE_RESOURCE);
        assertNotNull(base, "Could not find " + BASE_BUNDLE_RESOURCE + " on the test classpath. "
                + "Ensure resources are copied to the build output before tests run.");
        if (!"file".equals(base.getProtocol())) {
            fail("Expected the bundle on the filesystem during tests but found: " + base
                    + ". Run tests against exploded resources (the normal Maven/Gradle layout).");
        }
        Path baseFile = Path.of(base.toURI());
        Path dir = baseFile.getParent();
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "uitext*.properties")) {
            for (Path p : stream) files.add(p);
        }
        Collections.sort(files);
        return files;
    }

    /** Loads a single file exactly as the runtime ResourceBundle would decode it (UTF-8). */
    private static TreeMap<String, String> load(Path file) throws IOException {
        TreeMap<String, String> map = new TreeMap<>();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            PropertyResourceBundle bundle = new PropertyResourceBundle(reader);
            Enumeration<String> keys = bundle.getKeys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                map.put(key, bundle.getString(key));
            }
        }
        return map;
    }

    private static String describe(Path file, String key, String value, String problem) {
        return file.getFileName() + " [" + key + "] " + problem + "\n        value = " + value;
    }

    private static String report(String title, List<String> violations) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append(" (").append(violations.size()).append("):\n");
        for (String v : violations) sb.append("  - ").append(v).append('\n');
        return sb.toString();
    }
}