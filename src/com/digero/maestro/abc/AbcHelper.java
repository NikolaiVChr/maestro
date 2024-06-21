package com.digero.maestro.abc;

import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Element;

import com.digero.common.midi.Note;
import com.digero.common.util.ParseException;
import com.digero.maestro.util.SaveUtil;

public class AbcHelper {

	static Integer[] matchNick(String nick, String title) {
		if (title.contains(nick)) {
			int startingPosition = title.indexOf(nick);
			int endingPosition = startingPosition + nick.length();
			Integer[] result = { startingPosition, endingPosition };
			return result;
		}
		return null;
	}

	static int map(long value, long leftMin, long leftMax, int rightMin, int rightMax) {
		// Figure out how 'wide' each range is
		long leftSpan = leftMax - leftMin;
		int rightSpan = rightMax - rightMin;

		// Convert the left range into a 0-1 range (float)
		double valueScaled = (value - leftMin) / (double) leftSpan;

		// Convert the 0-1 range into a value in the right range.
		return (int) (rightMin + (valueScaled * rightSpan));
	}

	static PartSection generatePartSection(Element sectionEle) throws ParseException, XPathExpressionException {
		PartSection ps = new PartSection();
		ps.startBar = SaveUtil.parseValue(sectionEle, "startBar", 0);
		ps.endBar = SaveUtil.parseValue(sectionEle, "endBar", 0);
		ps.volumeStep = SaveUtil.parseValue(sectionEle, "volumeStep", 0);
		ps.octaveStep = SaveUtil.parseValue(sectionEle, "octaveStep", 0);
		ps.silence = SaveUtil.parseValue(sectionEle, "silence", false);
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
