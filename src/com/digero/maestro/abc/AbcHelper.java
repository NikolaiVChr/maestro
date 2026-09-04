package com.digero.maestro.abc;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Element;

import com.digero.common.midi.Note;
import com.digero.common.util.FileParseException;
import com.digero.common.util.Version;
import com.digero.maestro.util.SaveUtil;

public class AbcHelper {

	static Integer[] matchNick(String nick, String title) {
		if (nick == null || nick.isEmpty() || title == null)
			return null;

		Matcher m = Pattern.compile(Pattern.quote(nick),
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(title);
		if (!m.find())
			return null;

		return new Integer[]{ m.start(), m.end() };
	}

	@SuppressWarnings("HardCodedStringLiteral")
	static PartSection loadPartSectionFromXML(Element sectionEle, Version fileVersion) throws FileParseException, XPathExpressionException {
		PartSection ps = new PartSection();
		if (fileVersion.compareTo(new Version(3, 3, 4, 300)) < 0) {
			ps.startBar = (float)SaveUtil.parseValue(sectionEle, "startBar", 0);
			ps.endBar = (float)SaveUtil.parseValue(sectionEle, "endBar", 0);
			ps.startBar -= 1.0f;
		} else {
			ps.startBar = SaveUtil.parseValue(sectionEle, "startBar", 0.0f);
			ps.endBar = SaveUtil.parseValue(sectionEle, "endBar", 0.0f);
		}
		ps.volumeStep = SaveUtil.parseValue(sectionEle, "volumeStep", 0);
		ps.octaveStep = SaveUtil.parseValue(sectionEle, "octaveStep", 0);
		ps.silence = SaveUtil.parseValue(sectionEle, "silence", false);
		ps.legato = SaveUtil.parseValue(sectionEle, "legato", false);
		ps.doubling[0] = SaveUtil.parseValue(sectionEle, "double2OctDown", false);
		ps.doubling[1] = SaveUtil.parseValue(sectionEle, "double1OctDown", false);
		ps.doubling[2] = SaveUtil.parseValue(sectionEle, "double1OctUp", false);
		ps.doubling[3] = SaveUtil.parseValue(sectionEle, "double2OctUp", false);
		ps.resetVelocities = SaveUtil.parseValue(sectionEle, "resetVelocities", false);
		ps.fromPitch = Note.fromId(SaveUtil.parseValue(sectionEle, "fromPitch", AbcPart.minDefault.id));
		if (ps.fromPitch == null) ps.fromPitch = AbcPart.minDefault;
		ps.toPitch = Note.fromId(SaveUtil.parseValue(sectionEle, "toPitch", Note.MAX.id));
		if (ps.toPitch == null) ps.toPitch = Note.MAX;
		boolean fadeout = SaveUtil.parseValue(sectionEle, "fadeout", false);
		int fade = SaveUtil.parseValue(sectionEle, "fade", 0);
		if (fade != 0) {
			ps.fade = fade;
		} else {
			// backwards compatibility
			ps.fade = (fadeout ? 100 : 0);
		}		
		ps.dialogLine = SaveUtil.parseValue(sectionEle, "dialogLine", -1);
		return ps;
	}

	@SuppressWarnings("HardCodedStringLiteral")
	static void saveDoublingToXML(PartSection partSection, Element sectionElement, boolean skip) {
		if (!skip) {
			if (partSection.doubling[0])
				SaveUtil.appendChildTextElement(sectionElement, "double2OctDown", String.valueOf(true));
			if (partSection.doubling[1])
				SaveUtil.appendChildTextElement(sectionElement, "double1OctDown",	String.valueOf(true));
			if (partSection.doubling[2])
				SaveUtil.appendChildTextElement(sectionElement, "double1OctUp", String.valueOf(true));
			if (partSection.doubling[3])
				SaveUtil.appendChildTextElement(sectionElement, "double2OctUp", String.valueOf(true));
		}
	}

	static void setTypeNumbers(List<AbcPart> instrParts) {
		int index = 1;
		for (AbcPart part : instrParts) {
			if (part.getEnabledTrackCount() == 0) {
				part.setTypeNumber(0);
			} else {
				if (part.setTypeNumber(index)) {
					index++;
				} else {
					// System.out.println(" failure, -1 was set");
				}
			}
		}
	}

}
