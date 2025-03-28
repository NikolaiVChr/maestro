package com.digero.abcplayer;

import static java.awt.Frame.getFrames;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.util.ParseException;
import com.digero.common.util.Version;
import com.digero.maestro.util.SaveUtil;
import com.digero.maestro.util.XmlUtil;

public class AbcPlaylistXmlCoder {
	
	public static final Version ABC_PLAYLIST_VERSION = new Version(3, 4, 0, 300);
	
	public static Document savePlaylistToXml(List<AbcInfo> abcs) {
		Document doc = XmlUtil.createDocument();
		doc.setXmlVersion("1.1");
		
		Element playlistEle = (Element)doc.appendChild(doc.createElement("playlist"));
		playlistEle.setAttribute("fileVersion", String.valueOf(ABC_PLAYLIST_VERSION));
		
		Element trackListEle = (Element)playlistEle.appendChild(doc.createElement("trackList"));
		
		for (AbcInfo inf : abcs) {
			Element trackEle = (Element)trackListEle.appendChild(doc.createElement("track"));
			for (File file : inf.getSourceFiles()) {
				SaveUtil.appendChildTextElement(trackEle, "location", file.getAbsolutePath());
			}
		}
		
		return doc;
	}
	
	public static List<List<File>> loadPlaylist(File playlistPath) throws ParseException {
		List<List<File>> files = new ArrayList<List<File>>();
		
		try {
			Document doc = XmlUtil.openDocument(playlistPath);
			Element playlistEle = XmlUtil.selectSingleElement(doc, "playlist");
			if (playlistEle == null) {
				throw new ParseException("Does not appear to be a valid AbcPlayer playlist.",
						playlistPath.getName());
			}
			Version fileVersion = SaveUtil.parseValue(playlistEle, "@fileVersion", ABC_PLAYLIST_VERSION);
			
			if (fileVersion.compareTo(ABC_PLAYLIST_VERSION) > 0) {
				JOptionPane.showMessageDialog(getFrames()[0],
						"This playlist was created using a newer version of AbcPlayer. It is suggested to upgrade AbcPlayer before loading this.",
						"Warning", JOptionPane.WARNING_MESSAGE);
			}
			
			Element trackListEle = XmlUtil.selectSingleElement(playlistEle, "trackList");
			if (trackListEle == null) {
				throw new ParseException("Does not appear to be a valid AbcPlayer playlist.",
						playlistPath.getName());
			}
			
			for (Element songEle : XmlUtil.selectElements(trackListEle, "track")) {
				List<File> songFiles = new ArrayList<File>();
				for (Element locationEle : XmlUtil.selectElements(songEle, "location")) {
					String loc = locationEle.getTextContent();
					songFiles.add(Paths.get(loc).toFile());
				}
				if (!songFiles.isEmpty()) {
					files.add(songFiles);
				}
			}
		} catch (Exception e) {
			throw new ParseException ("Failed to parse AbcPlayer playlist file.", playlistPath.getName());
		}
		
		return files;
	}
}
