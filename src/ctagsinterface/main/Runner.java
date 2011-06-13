package ctagsinterface.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

import ctagsinterface.options.GeneralOptionPane;

public class Runner
{
	private static final int UNKNOWN_NUMBER_OF_FILES = -1;
	static public final String MESSAGE = CtagsInterfacePlugin.MESSAGE;
	static public final String TAGGING = MESSAGE + "tagging";
	private static final String SPACES = "\\s+";
	private static Set<String> tempFiles;
	private Logger logger;

	static {
		tempFiles = new HashSet<String>();
	}

	public Runner(Logger logger)
	{
		this.logger = logger;
	}

	// Runs Ctags on a single file. Returns the tag file.
	public String runOnFile(String file) {
		Vector<String> what = new Vector<String>();
		what.add(file);
		return run(what, 1);
	}
	
	// Runs Ctags on an archive. Returns the tag file.
	public String runOnArchive(String archive,
		HashMap<String, String> localToVFS)
	{
		Vector<String> files = VFSHelper.listArchive(archive, true);
		String tagFile = getTempFile("tags");
		// The same tag file is used for the entire archive. Results
		// for each of the source files in the archive are appended
		// to the tag file. Hence, if the tag file exists when the
		// archive tagging begins, it should be deleted.
		new File(tagFile).delete();
		int cnt = 0;
		for (String file: files) {
			String local = runOnVFS(file, tagFile);
			if (local != null)
				localToVFS.put(local, file);
			cnt++;
			Log.log(Log.DEBUG, getClass(), "Progress: " + cnt +
				" out of " + files.size());
		}
		return tagFile;
	}
	// Runs Ctags on a source tree. Returns the tag file.
	public String runOnTree(String tree) {
		Vector<String> what = new Vector<String>();
		what.add("-R");
		what.add(tree);
		return run(what, UNKNOWN_NUMBER_OF_FILES);
	}
	// Runs Ctags on a list of files. Returns the tag file.
	public String runOnFiles(Vector<String> files) {
		String fileList = getTempFile("files");
		try {
			PrintWriter w = new PrintWriter(new FileWriter(fileList));
			for (int i = 0; i < files.size(); i++)
				w.println(files.get(i));
			w.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Could not create the file list file for Ctags");
			return null;
		}
		Vector<String> what = new Vector<String>();
		what.add("-L");
		what.add(fileList);
		String tagFile = run(what, files.size());
		releaseFile(fileList);
		return tagFile;
	}
	// Tag file no longer needed
	public void releaseFile(String file) {
		synchronized (tempFiles) {
			tempFiles.remove(file);
		}
	}
	private File getLocalCopyOfVFS(String vfsPath) {
		String sourceFileName = VFSHelper.getFileName(vfsPath);
		String prefix = sourceFileName, suffix = "";
		int sepPos = sourceFileName.lastIndexOf(".");
		if (sepPos >= 0) {
			prefix = sourceFileName.substring(0, sepPos);
			if (prefix.length() < 3) // Limitation of createTempFile()
				prefix = prefix + "000";
			suffix = sourceFileName.substring(sepPos);
		}
		try {
			File localCopy = File.createTempFile(prefix, suffix);
			localCopy.createNewFile();
			VFSHelper.copy(vfsPath, localCopy.getPath());
			return localCopy;
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	// Runs Ctags on the given VFS file path, by making a local copy
	// and running Ctags on it. Returns the name of the local copy.
	private String runOnVFS(String path, String tagFile) {
		Log.log(Log.DEBUG, getClass(), "Tagging VFS path: '" + path);
		long start = System.currentTimeMillis();
		String ctags = GeneralOptionPane.getCtags();
		String cmd = GeneralOptionPane.getCmd();
		File localCopy = getLocalCopyOfVFS(path);
		if (localCopy == null) {
			Log.log(Log.DEBUG, getClass(), "Tagging aborted - no local copy.");
			return null;
		}
		Log.log(Log.DEBUG, getClass(), "Local copy: '" + localCopy.getPath() + "'");
		Vector<String> cmdLine = new Vector<String>();
		cmdLine.add(ctags);
		cmdLine.add("-f");
		cmdLine.add(tagFile);
		cmdLine.add("--append=yes");
		String [] customOptions = cmd.split(SPACES);
		for (int i = 0; i < customOptions.length; i++)
			cmdLine.add(customOptions[i]);
		cmdLine.add(localCopy.getPath());
		String [] args = new String[cmdLine.size()]; 
		cmdLine.toArray(args);
		try {
			Process p = Runtime.getRuntime().exec(args);
			p.waitFor();
			long end = System.currentTimeMillis();
			Log.log(Log.DEBUG, getClass(), "Tagging took "
					+ (end - start) * .001 + " seconds.");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		localCopy.delete();
		return localCopy.getPath();
	}
	private String run(Vector<String> what, int numFiles) {
		String ctags = GeneralOptionPane.getCtags();
		String cmd = GeneralOptionPane.getCmd();
		String tagFile = getTempFile("tags");
		Vector<String> cmdLine = new Vector<String>();
		cmdLine.add(ctags);
		cmdLine.add("--verbose");	// Allow progress tracking
		cmdLine.add("--sort=no");	// Avoid sorting to improve performance
		cmdLine.add("-f");
		cmdLine.add(tagFile);
		String [] customOptions = cmd.split(SPACES);
		for (int i = 0; i < customOptions.length; i++)
			cmdLine.add(customOptions[i]);
		cmdLine.addAll(what);
		String [] args = new String[cmdLine.size()]; 
		cmdLine.toArray(args);
		if (logger != null)
		{
			logger.beginTask(jEdit.getProperty(TAGGING));
			if (numFiles != UNKNOWN_NUMBER_OF_FILES)
				logger.setProgressParams(0, numFiles);
		}
		Process p = null;
		StreamConsumer osc = null, esc = null;
		try {
			p = Runtime.getRuntime().exec(args);
			osc = new StreamConsumer(p.getInputStream());
			osc.start();
			esc = new StreamConsumer(p.getErrorStream());
			esc.start();
			p.waitFor();
		} catch (IOException e) {
			e.printStackTrace();
			tagFile = null;
		} catch (InterruptedException e) {
			if (p != null)
				p.destroy();
			// The output and error streams should close as a result of
			// killing the process, and the reader threads should get an
			// exception and end.
			e.printStackTrace();
			tagFile = null;
		} finally {
			if (logger != null)
				logger.endTask();
		}
		return tagFile;
	}
	private void addProgressMessage(String s)
	{
		if (logger != null)
			logger.log(s);
	}
	private void addProgress(String s, int value)
	{
		if (logger != null)
		{
			logger.log(s);
			logger.setProgress(value);
		}
	}
	private String getTempDirectory() {
		return jEdit.getSettingsDirectory() + "/CtagsInterface";
	}
	private String getTempFile(String prefix) {
		synchronized (tempFiles) {
			String tempDir = getTempDirectory();
			File d = new File(tempDir);
			if (! d.exists())
				d.mkdirs();
			for (int i = 0; i < 100; i++) {
				String path = tempDir + "/" + prefix + i; 
				if (tempFiles.add(path))
					return path;
			}
		}
		return null; 
	}

	private class StreamConsumer extends Thread
	{
		private InputStream is;
		private final Pattern progressLine = Pattern.compile(
			"^(opening|ignoring).*", Pattern.CASE_INSENSITIVE);

		public StreamConsumer(InputStream is)
		{
			this.is = is;
		}
		public void run()
		{
			try
			{
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				String line;
				int processed = 0;
				do
				{
					line = br.readLine();
					if (line != null)
					{
						Matcher m = progressLine.matcher(line);
						if (m.matches())
							addProgress(line, ++processed);
						else
							addProgressMessage(line);
					}
				}
				while (line != null);
			}
			catch (IOException ioe)
			{
				ioe.printStackTrace();  
			}
		}
	}
}
