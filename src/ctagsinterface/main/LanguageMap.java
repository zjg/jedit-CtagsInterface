package ctagsinterface.main;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.Buffer;

import java.util.Vector;

public class LanguageMap
{

    private Vector<String[]> langMap;

    public LanguageMap()
    {
        langMap = new Vector<String[]>();
        try {
            Process proc = Runtime.getRuntime().exec("ctags --list-maps");
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
        return langMap;
    }

    public String getLanguage(View view)
    {
        Buffer b = view.getBuffer();
        String name = b.getName();
        String ext = name.substring(name.lastIndexOf(".") + 1);
        for (String[] lm : langMap) {
            if (lm[0].equals(ext)) {
                return lm[1];
            }
        }
        return ext;
    }
}
