package com.digero.maestro.abc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Element;

import com.digero.common.midi.Note;
import com.digero.common.util.ParseException;
import com.digero.common.util.Version;
import com.digero.maestro.util.SaveUtil;

public class AbcHelper {

	static Integer[] matchNick(String nick, String title) {
		if (title.contains(nick)) {
			int startingPosition = title.indexOf(nick);
			int endingPosition = startingPosition + nick.length();
            return new Integer[]{ startingPosition, endingPosition };
		}
		return null;
	}

	static int map(long value, long leftMin, long leftMax, int rightMin, int rightMax) {
		// since left range can span a rather big number we use big decimal to make sure its division is done proper
		
		// Figure out how 'wide' each range is
		BigDecimal leftSpan = BigDecimal.valueOf(leftMax-leftMin);
		BigDecimal rightSpan = BigDecimal.valueOf(rightMax-rightMin);

		// Convert the left range into a 0-1 range (float)
		// The result will have 10 decimal places of precision
		BigDecimal valueScaled = BigDecimal.valueOf(value-leftMin).divide(leftSpan, 10, RoundingMode.HALF_UP);

		// Convert the 0-1 range into a value in the right range.
		return rightMin + valueScaled.multiply(rightSpan).intValue();
	}

	static PartSection generatePartSection(Element sectionEle, Version fileVersion) throws ParseException, XPathExpressionException {
		PartSection ps = new PartSection();
		if (fileVersion.compareTo(new Version(3, 3, 4, 300)) < 0) {
			ps.startBar = SaveUtil.parseValue(sectionEle, "startBar", 0);
			ps.endBar = SaveUtil.parseValue(sectionEle, "endBar", 0);
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
		ps.fromPitch = Note.fromId(SaveUtil.parseValue(sectionEle, "fromPitch", Note.C0.id));
		ps.toPitch = Note.fromId(SaveUtil.parseValue(sectionEle, "toPitch", Note.MAX.id));
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

	static void appendIfNotPercussion(PartSection partSection, Element sectionElement, boolean isPercussion) {
		if (!isPercussion) {
			if (partSection.doubling[0])
				SaveUtil.appendChildTextElement(sectionElement, "double2OctDown",
						String.valueOf(partSection.doubling[0]));
			if (partSection.doubling[1])
				SaveUtil.appendChildTextElement(sectionElement, "double1OctDown",
						String.valueOf(partSection.doubling[1]));
			if (partSection.doubling[2])
				SaveUtil.appendChildTextElement(sectionElement, "double1OctUp",
						String.valueOf(partSection.doubling[2]));
			if (partSection.doubling[3])
				SaveUtil.appendChildTextElement(sectionElement, "double2OctUp",
						String.valueOf(partSection.doubling[3]));
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
