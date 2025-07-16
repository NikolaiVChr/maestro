package com.digero.common.abc;

import java.util.ArrayList;
import java.util.List;

import com.digero.common.util.Version;

public class VersionsWithIssues {
	
	private static final List<IssueVersion> abcVersionsWithIssues = new ArrayList<>();
	private static final List<IssueVersion> msxVersionsWithIssues = new ArrayList<>();
	
	static {
		// These are issues that can have exported corrupted abc files in some way.
		abcVersionsWithIssues.add(new IssueVersion(new Version(3,4,4),
				"Notes can be missing if they had no rest inbetween them and were same pitch."));//beta
		abcVersionsWithIssues.add(new IssueVersion(new Version(3,3,9), new Version(3,3,13),
				"Song might have wrong tempo until first tempochange."));//beta
		abcVersionsWithIssues.add(new IssueVersion(new Version(3,3,7),
				"Notes can be missing."));
		abcVersionsWithIssues.add(new IssueVersion(new Version(3,3,6),
				"Possible issue with delayed start or ending."));
		abcVersionsWithIssues.add(new IssueVersion(new Version(3,2,0),
				"Song duration in part names can be longer than actual song length."));
		abcVersionsWithIssues.add(new IssueVersion(new Version(3,1,9), new Version(3,1,10),
				"Notes can be missing."));
		abcVersionsWithIssues.add(new IssueVersion(new Version(2,5,0,118),
				"Initial silence might not have been removed."));//beta of auto exporter
		abcVersionsWithIssues.add(new IssueVersion(new Version(3,6,6), new Version(4,0,11),
				"Notes can be missing or out of rhythm (if exported with organic)."));//beta of organic
		abcVersionsWithIssues.add(new IssueVersion(new Version(4,2,4), new Version(4,2,9),
				"Parts can be silenced in lotro (if exported with organic single-stage and poly 6+)."));
		abcVersionsWithIssues.add(new IssueVersion(new Version(4,2,10),
				"Rare chance of ties having rest between them in organic single-stage."));
		abcVersionsWithIssues.add(new IssueVersion(new Version(4,2,14), new Version(4,2,16),
				"Notes can be cut short (if exported with organic and poly 6+)."));//beta
		abcVersionsWithIssues.add(new IssueVersion(new Version(4,3,0),
				"Parts main volumes can be corrupted, check project also."));

		// these are issues that can have corrupted project files in some way:
		msxVersionsWithIssues.add(new IssueVersion(new Version(4,3,0),
				"Main volumes can be changed due to bug in 4.3.0, it's suggested you check all volumes."));
	}
	
	/**
	 * 
	 * @param abcVersionText a string like: "Maestro v2.5.1"
	 * @return null if the abc should be fine, a string if there can be an issue.
	 */
	static public String check(String abcVersionText) {
		String versionText = abcVersionText.replace("Maestro v", "");
		Version version = Version.parseVersion(versionText);
		return check(version);
	}
	
	/**
	 * 
	 * @param version The version to check for possible abc issues.
	 * @return null if the abc should be fine, a string if there can be an issue.
	 */
	static public String check(Version version) {
		if (version == null) return null;
		for (IssueVersion issue : abcVersionsWithIssues) {
			if (version.compareTo(issue.begin) >= 0 && version.compareTo(issue.end) <= 0) {
				return issue.info;
			}
		}
		return null;
	}

	/**
	 *
	 * @param version The version to check for possible project issues.
	 * @return null if the msx should be fine, a string if there can be an issue.
	 */
	static public String checkProject(Version version) {
		if (version == null) return null;
		for (IssueVersion issue : msxVersionsWithIssues) {
			if (version.compareTo(issue.begin) >= 0 && version.compareTo(issue.end) <= 0) {
				return issue.info;
			}
		}
		return null;
	}
	
	static class IssueVersion {
		public final Version begin;
		public final Version end;
		public final String info;
		
		public IssueVersion (Version version, String info) {
			this.begin = version;
			this.end = version;
			this.info = info;			
		}
		
		public IssueVersion (Version begin, Version end, String info) {
			this.begin = begin;
			this.end = end;
			this.info = info;			
		}
	}
}