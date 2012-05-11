package ctagsinterface.main;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.Buffer;

import ctagsinterface.options.GeneralOptionPane;

import java.util.Vector;

/**
 * Mapper between file extensions and languages according to ctags
 * @author Tom Power
 */

public class LanguageMap
{

	private Vector<String[]> langMap = new Vector<String[]>();

	/**
	 * Calls ctags --list-maps and parses for language to extension mapping
	 * Adds arrays to langMap in form [extension][language]
	 */
	private void setLanguageMap()
	{
		String ctags = GeneralOptionPane.getCtags();
		try {
			Process proc = Runtime.getRuntime().exec(ctags + " --list-maps");
			InputStream inputStream = proc.getInputStream();
			InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				String[] lmLine = line.split("\\s+");
				String lmLang = lmLine[0].toLowerCase();
				for (int i = 1; i < lmLine.length; i++) {
					String[] temp = new String[2];
					temp[0] = lmLine[i].replace("*.", "");
					temp[1] = lmLang;
					langMap.add(temp);
				}
			}
		} catch (IOException io) {
			io.printStackTrace();
		}
	}

	public Vector<String[]> getLanguageMap()
	{
		if (langMap.isEmpty())
			setLanguageMap();
		return langMap;
	}

	/**
	 * Gets the language of the view passed according to ctags
	 * @param View view of interest
	 * @return String the language ctags maps the view's extension to if found, otherwise the view's extension
	 */
	public String getLanguage(View view)
	{
		if (langMap.isEmpty())
			setLanguageMap();
		Buffer b = view.getBuffer();
		String name = b.getName();
		String ext = name.substring(name.lastIndexOf(".") + 1);
		for (String[] lm : langMap) {
			if (lm[0].equals(ext))
				return lm[1];
		}
		return ext;
	}
}
