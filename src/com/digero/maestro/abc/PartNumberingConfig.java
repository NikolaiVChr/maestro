package com.digero.maestro.abc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.StringTokenizer;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.util.ParseException;
import com.digero.maestro.MaestroMain;

public class PartNumberingConfig
{
	public int increment = -1;
	public HashMap<LotroInstrument, Integer> firstPartMap = new HashMap<>();
	
	public PartNumberingConfig() {}
	
	public PartNumberingConfig(int increment, HashMap<LotroInstrument, Integer> firstPartMap)
	{
		this.increment = increment;
		this.firstPartMap = firstPartMap;
	}
	
	public void save(File outputFile) throws IOException
	{
		PrintStream out = new PrintStream(outputFile);
		out.println("% Part Numbering Config");
		out.println("% Created using " + MaestroMain.APP_NAME + " v" + MaestroMain.APP_VERSION);
		out.println("% Format:");
		out.println("% INCREMENT => [Increment Number (must be 1 or 10)]");
		out.println("% [Instrument] => [First Part Number]");
		out.println("% First part numbers are in the range 0 to 10 if INCREMENT is 10");
		out.println("% Or in the range 0 to 999 if INCREMENT is 1");
		out.println("% Comments begin with %");
		out.println();
		
		out.println("INCREMENT => " + increment);
		out.println();
		for (LotroInstrument key : firstPartMap.keySet())
		{
			out.println(key.name() + " => " + firstPartMap.get(key));
		}
		out.close();
	}
	
	public void load(File inputFile) throws IOException, ParseException
	{
		String fn = inputFile.getName();
		FileInputStream inputStream = new FileInputStream(inputFile);
		String line;
		int lineNo = 0;
		int increment = -1;
		HashMap<LotroInstrument, Integer> map = new HashMap<LotroInstrument, Integer>();
		
		try (BufferedReader r = new BufferedReader(new InputStreamReader(inputStream)))
		{
			
			while ((line = r.readLine()) != null)
			{
				lineNo++;
				
				int commentIndex = line.indexOf('%');
				line = (commentIndex >= 0? line.substring(0, commentIndex) : line).trim();
				if (line.isEmpty())
					continue;
				
				StringTokenizer tokenizer = new StringTokenizer(line, " \t=>");
				String key = tokenizer.nextToken();
				String value = tokenizer.nextToken();
				if (tokenizer.hasMoreTokens()) {
					throw new ParseException("Invalid line (too many tokens)", fn, lineNo);
				}
				
				LotroInstrument instrument = null;
				for (LotroInstrument ins : LotroInstrument.values())
				{
					if (key.equals(ins.name()))
					{
						instrument = ins;
					}
				}
				
				if (instrument != null)
				{
					map.put(instrument, Integer.parseInt(value));
				}
				else if (key.equals("INCREMENT"))
				{
					increment = Integer.parseInt(value);
					if (increment != 10 && increment != 1)
					{
						throw new ParseException("Invalid value of INCREMENT " + increment + ". Should be 1 or 10", fn, lineNo);
					}
				}
			}
			
			if (increment == -1)
			{
				throw new ParseException("No INCREMENT value was found in config.", fn);
			}
			
			for (LotroInstrument key : map.keySet())
			{
				int val = map.get(key);
				if ((increment == 10 && (val < 0 || val > 10)) ||
					(increment == 1 && (val < 0 || val > 999)))
				{
					throw new ParseException("Instrument " + key.name() + " has an out-of-range first part number " + val, fn);
				}
			}
		}
		
		this.increment = increment;
		this.firstPartMap = map;
	}
}
