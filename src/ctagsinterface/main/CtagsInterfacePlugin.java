package ctagsinterface.main;
import ise.plugin.nav.AutoJump;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.gjt.sp.jedit.ActionSet;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EditPlugin;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.gui.StatusBar;
import org.gjt.sp.jedit.io.VFSManager;
import org.gjt.sp.jedit.msg.PositionChanging;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.util.Task;
import org.gjt.sp.util.ThreadUtilities;

import ctagsinterface.dockables.Progress;
import ctagsinterface.dockables.TagList;
import ctagsinterface.index.QueryDialog;
import ctagsinterface.index.TagIndex;
import ctagsinterface.index.TagIndex.DocHandler;
import ctagsinterface.index.TagIndex.Origin;
import ctagsinterface.index.TagIndex.OriginType;
import ctagsinterface.jedit.BufferWatcher;
import ctagsinterface.jedit.TagCompletion;
import ctagsinterface.jedit.TagTooltip;
import ctagsinterface.main.Parser.TagHandler;
import ctagsinterface.options.ActionsOptionPane;
import ctagsinterface.options.GeneralOptionPane;
import ctagsinterface.options.ProjectsOptionPane;
import ctagsinterface.projects.ProjectDependencies;
import ctagsinterface.projects.ProjectWatcher;
import org.gjt.sp.jedit.Macros;

public class CtagsInterfacePlugin extends EditPlugin
{

	private static final String MISC_ORIGIN_ID = "temp";
	public static final String TAGS_UPDATED_BUFFER_PROP = "TagsUpdated";
	private static final String DOCKABLE = "ctags-interface-tag-list";
	static public final String OPTION = "options.CtagsInterface.";
	static public final String MESSAGE = "messages.CtagsInterface.";
	static public final String ACTION_SET = "Plugin: CtagsInterface - Actions";
	private static final String PROGRESS = "ctags-interface-progress";
	private static TagIndex index;
	private static BufferWatcher watcher;
	private static ProjectWatcher pvi;
	private static ActionSet actions;
	private static KindIconProvider iconProvider;
	private static Object bufferUpdateLock = new Object();
	private static HashMap<String, Task> tasks =
		new HashMap<String, Task>();
	private static LanguageMap langMap = new LanguageMap();

	public void start()
	{
		index = new TagIndex();
		watcher = new BufferWatcher(index);
		EditPlugin p = jEdit.getPlugin("projectviewer.ProjectPlugin",false);
		pvi = (p == null) ? null : new ProjectWatcher();
		actions = new ActionSet(ACTION_SET);
		updateActions();
		jEdit.addActionSet(actions);
		iconProvider = new KindIconProvider();
		TagTooltip.start();
	}

	public void stop()
	{
		TagTooltip.stop();
		if (pvi != null)
		{
			pvi.stop();
			pvi = null;
		}
		watcher.shutdown();
		watcher = null;
		index.close();
		index = null;
	}

	static public TagIndex getIndex()
	{
		return index;
	}

	static public ImageIcon getIcon(Tag tag)
	{
		return iconProvider.getIcon(tag);
	}

	static public Object getBufferUpdateLock()
	{
		return bufferUpdateLock;
	}

	static public Runner getRunner(Logger logger)
	{
		return new Runner(logger);
	}

	static public Parser getParser(Logger logger)
	{
		return new Parser(logger);
	}

	static public Logger getLogger(View view, String name)
	{
		Progress progress = getProgressDockable(view);
		return new Logger(name, progress);
	}

	static public LanguageMap getLangMap()
	{
		return langMap;
	}

	static public String getLanguage(View view)
	{
		LanguageMap langMap = getLangMap();
		String language = langMap.getLanguage(view);
		return language;
	}

	static public void updateActions()
	{
		actions.removeAllActions();
		QueryAction[] queries = ActionsOptionPane.loadActions();
		for (int i = 0; i < queries.length; i++)
			actions.addAction(queries[i]);
		actions.initKeyBindings();
	}

	static private class TagFileHandler implements TagHandler
	{
		private HashSet<String> files = new HashSet<String>();
		private Origin origin;
		private String originsStr;
		private Boolean remove = false;

		public TagFileHandler(Origin origin)
		{
			this(origin, false);
		}

		public TagFileHandler(Origin origin, Boolean remove)
		{
			this.origin = origin;
			this.remove = remove;
		}

		public void processTag(Tag t)
		{
			String file = t.getFile();

			if(remove) {
				index.deleteTag(t);
				return;
			}

			String originStr = this.origin != null ? origin.toString() : "";

			if (! files.contains(file))
			{
				// Add the new origin to the current list of origins, if not
				// yet included.
				originsStr = index.getOriginsOfFile(file);
				originsStr = index.appendOrigin(originsStr, originStr);
				index.deleteTagsFromSourceFile(file);
				files.add(file);
			}


			index.insertTag(t, originsStr);
		}

	}

	// Adds a temporary tag file to the DB
	// Existing tags from source files in the tag file are removed first.
	static public void addTagFile(View view, String tagFile)
	{
		if (tagFile == null || tagFile.length() == 0)
			return;
		if(!new File(tagFile).exists())
			return;
		Logger logger = getLogger(view, "Adding tag file " + tagFile);
		Parser parser = getParser(logger);
		parser.parseTagFile(tagFile, new TagFileHandler(
			index.getOrigin(OriginType.TAGFILE, tagFile, true)));
	}

	// Action: Prompt for a temporary tag file to add to the DB
	static public void addTagFile(View view)
	{
		String tagFile = JOptionPane.showInputDialog("Tag file:");
		addTagFile(view, tagFile);
	}

	// Action: Add temporary tag file to the DB
	static public void addTagFile(String tagFile)
	{
		addTagFile(jEdit.getActiveView(), tagFile);
	}

	// Remove tags from tag file from the DB
	static public void deleteTagsFromTagFile(View view, String tagFilePath)
	{
		if (tagFilePath == null || tagFilePath.length() == 0)
			return;
		if(!new File(tagFilePath).exists())
			return;
		Logger logger = getLogger(view, "Removing tag file " + tagFilePath);
		index.deleteTagsOfOrigin(logger, index.getOrigin(OriginType.TAGFILE, tagFilePath, true));
	}

	// Action: Add current file to the DB with origin
	static public void addCurrentFile(View view)
	{
		String path = view.getBuffer().getPath();
		addFile(view, path);
	}

	static public void addFile(View view, String path)
	{
		addFile(view, path, index.getOrigin(TagIndex.OriginType.MISC, MISC_ORIGIN_ID, true));
	}

	static public void addFile(View view, String path, Origin origin)
	{
		Logger logger = getLogger(view, "Adding source file " + path);
		TagFileHandler handler = new TagFileHandler(origin, false);
		tagSourceTree(logger, path, handler);
	}

	// Action: update current file in the DB, doesn't add origin
	static public void updateCurrentFile(View view)
	{
		String path = view.getBuffer().getPath();
		updateFile(view, path);
	}

	static public void updateFile(View view, String path)
	{
		if (getIndex().getOriginsOfFile(path).isEmpty())
			return;
		Logger logger = getLogger(view, "Adding source file " + path);
		TagFileHandler handler = new TagFileHandler(null, false); // null runs ctags with same origins
		tagSourceTree(logger, path, handler);
	}

	// Action: remove current file from the DB
	static public void deleteCurrentFile(View view)
	{
		String path = view.getBuffer().getPath();
		deleteFile(view, path);
	}


	static public void deleteFile(View view, String path)
	{
		Logger logger = getLogger(view, "Removing source file " + path);
		index.deleteTagsFromSourceFileOfOrigin(logger, path);
	}

	static public void deleteAll(View view)
	{
		String msg = MESSAGE + "removeAll";
		if( JOptionPane.OK_OPTION !=
			JOptionPane.showConfirmDialog(view,
				jEdit.getProperty(msg),
				"Remove all tags",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE))
			return;

		for(OriginType type : OriginType.values()) {
			Vector<String> origins = new Vector<String>();
			getIndex().getOrigins(type, origins);
			for (int i = 0; i < origins.size(); i++) {
				String id = origins.get(i);
				Origin origin = getIndex().getOrigin(type, id, false);
				Logger logger = getLogger(view, "Deleting " + type.name + " " + id);
				deleteOrigin(logger, type, id, false);
				//getIndex().deleteTagsOfOrigin(logger, origin);
				//getIndex().deleteOriginOfOrigin(logger, origin);
			}
		}
	}

	public static void jumpToTags(final View view, List<Tag> tags)
	{
		if (tags == null || tags.size() == 0)
		{
			JOptionPane.showMessageDialog(view, "No tags found");
			return;
		}
		if (tags.size() > 1)
		{
			view.getDockableWindowManager().showDockableWindow(DOCKABLE);
			JComponent c = view.getDockableWindowManager().getDockable(DOCKABLE);
			TagList tl = (TagList) c;
			tl.setTags(tags);
			return;
		}
		Tag t = tags.get(0);
		jumpToTag(view, t);
	}

	// Action: Add all projects to the database
	public static void tagAllProjects(View view)
	{
		if (pvi == null)
		{
			JOptionPane.showMessageDialog(view, "Project support disabled.");
			return;
		}
		Vector<String> allProjects = pvi.getProjects();
		Vector<String> dbProjects = ProjectsOptionPane.getProjects();
		for (int i = 0; i < allProjects.size(); i++)
		{
			String project = allProjects.get(i);
			Logger logger = getLogger(view, "Project " + project);
			if (! dbProjects.contains(project))
				insertOrigin(logger, OriginType.PROJECT, project);
		}
	}

	// Action: Search for a tag containing a substring
	public static void searchTagBySubstring(final View view)
	{
		new QuickSearchTagDialog(view, QuickSearchTagDialog.Mode.SUBSTRING);
	}

	// Action: Search for a tag by prefix
	public static void searchTagByPrefix(final View view)
	{
		new QuickSearchTagDialog(view, QuickSearchTagDialog.Mode.PREFIX);
	}

	public static Vector<Tag> runScopedQuery(View view, String q,
		int maxResults)
	{
		if (GeneralOptionPane.getMatchLanguage())
			q += " AND " + index.getLangNameQuery(getLanguage(view));

		boolean projectScope = (pvi != null &&
			(ProjectsOptionPane.getSearchActiveProjectOnly() ||
			 ProjectsOptionPane.getSearchActiveProjectFirst() ||
			 ProjectsOptionPane.getSearchActiveProjectAndDeps()));
		Vector<Tag> tags = new Vector<Tag>();
		String project = projectScope ? pvi.getActiveProject(view) : null;
		if (project != null && projectScope)
		{
			Vector<Origin> origins = new Vector<Origin>();
			if (ProjectsOptionPane.getSearchActiveProjectAndDeps())
			{
				HashMap<String, Vector<String>> originsHash =
					ProjectDependencies.getDependencies(project);
				Vector<String> projects = originsHash.get(OriginType.PROJECT.name);
				if (projects == null)
				{
					projects = new Vector<String>();
					originsHash.put(OriginType.PROJECT.name, projects);
				}
				projects.add(project);
				for (Entry<String, Vector<String>> origin: originsHash.entrySet())
				{
					for (String s: origin.getValue())
					{
						origins.add(index.getOrigin(
							OriginType.fromString(origin.getKey()), s, false));
					}
				}
				index.runQueryInOrigins(q, origins, maxResults, tags);
			}
			else
			{
				origins.add(index.getOrigin(OriginType.PROJECT, project, false));
				index.runQueryInOrigins(q, origins, maxResults, tags);
				if (ProjectsOptionPane.getSearchActiveProjectFirst() &&
					tags.isEmpty())
				{
					index.runQuery(q, maxResults, tags);
				}
			}
		}
		else
			index.runQuery(q, maxResults, tags);
		return tags;
	}

	public static Vector<Tag> runScopedQuery(View view, String q)
	{
		return runScopedQuery(view, q, TagIndex.MAX_RESULTS);
	}

	public static Vector<Tag> queryScopedTag(View view, String tag)
	{
		if (tag == null || tag.length() == 0)
			return null;
		String q = index.getTagNameQuery(tag);
		return runScopedQuery(view, q);
	}

	// Context menu action from FSB
	public static void addFromFSB(View view, String directory)
	{
		// FSB adds a trailing directory separator, that Ctags cannot handle
		if (directory.endsWith(File.separator))
			directory = directory.substring(0, directory.length() - 1);
		Logger logger = CtagsInterfacePlugin.getLogger(jEdit.getActiveView(),
			OriginType.DIRECTORY.name + " " + directory);
		insertOrigin(logger, OriginType.DIRECTORY, directory);
	}

	// Action: Jump to the selected tag (or tag at caret).
	public static void jumpToTag(final View view)
	{
		String tag = getDestinationTag(view);
		if (tag == null || tag.length() == 0)
		{
			JOptionPane.showMessageDialog(
				view, "No tag selected nor identified at caret");
			return;
		}
		Vector<Tag> tags = queryScopedTag(view, tag);
		if (tags == null)
			return;
		jumpToTags(view, tags);
	}

	// Actions: Offer code completion options from the DB
	public static void completeFromDb(final View view)
	{
		String prefix = getCompletionPrefix(view);
		if (prefix == null)
			return;
		TagCompletion.complete(view, prefix);
	}

	// Run a query on the database and display the results (for debugging)
	public static void runQuery(final View view)
	{
		String q = JOptionPane.showInputDialog("Enter query:");
		if (q == null)
			return;
		System.err.println("-- Executing query: " + q);
		index.runQuery(q, 1000000, new DocHandler()
		{
			public void handle(Document doc)
			{
				String s = "";
				for (Fieldable f: doc.getFields())
					s += f.name() + ":" + f.stringValue() + " ";
				System.err.println(s);
			}
		});
	}

	// Show a query dialog
	public static void showQueryDialog(final View view)
	{
		new QueryDialog(view);
	}

	// Returns the prefix for code completion
	public static String getCompletionPrefix(View view)
	{
		String tag = view.getTextArea().getSelectedText();
		if (tag == null || tag.length() == 0)
			tag = getTagUpToCaret(view);
		return tag;
	}

	private static String getTagUpToCaret(View view)
	{
		JEditTextArea ta = view.getTextArea();
		int line = ta.getCaretLine();
		int index = ta.getCaretPosition() - ta.getLineStartOffset(line);
		String text = ta.getLineText(line);
		Pattern pat = Pattern.compile(GeneralOptionPane.getPattern());
		Matcher m = pat.matcher(text);
		int end = -1;
		int start = -1;
		String selected = "";
		while (end < index)
		{
			if (! m.find())
				return null;
			end = m.end();
			start = m.start();
			selected = m.group();
		}
		if (start > index || selected.length() == 0)
			return null;
		return selected.substring(0, selected.length() - (end - index));
	}

	// Returns the tag to jump to: The selected tag or the one at the caret.
	static public String getDestinationTag(View view)
	{
		String tag = view.getTextArea().getSelectedText();
		if (tag == null || tag.length() == 0)
			tag = getTagAtCaret(view);
		return tag;
	}

	// Returns the tag at the caret.
	static private String getTagAtCaret(View view)
	{
		JEditTextArea ta = view.getTextArea();
		int line = ta.getCaretLine();
		int index = ta.getCaretPosition() - ta.getLineStartOffset(line);
		return getTagAt(ta, line, index);
	}

	public static String getTagAt(JEditTextArea textArea, int line,
		int offsetInLine)
	{
		String text = textArea.getLineText(line);
		Pattern pat = Pattern.compile(GeneralOptionPane.getPattern());
		Matcher m = pat.matcher(text);
		int end = -1;
		int start = -1;
		String selected = "";
		while (end <= offsetInLine)
		{
			if (! m.find())
				return null;
			end = m.end();
			start = m.start();
			selected = m.group();
		}
		if (start > offsetInLine || selected.length() == 0)
			return null;
		return selected;
	}

	// Jumps to the specified location
	public static void jumpTo(final View view, final String file,
		final int line, final boolean sendAutoJumpStarted)
	{
		Runnable r = new Runnable() {
			public void run() {
				jumpToDirect(view, file, line, sendAutoJumpStarted);
			}
		};
		if (SwingUtilities.isEventDispatchThread())
			r.run();
		else
			SwingUtilities.invokeLater(r);
	}

	private static void jumpToDirect(final View view, String file,
		final int line, boolean sendAutoJumpStarted)
	{
		final EditPlugin p = jEdit.getPlugin("ise.plugin.nav.NavigatorPlugin",false);
		if (sendAutoJumpStarted && (p != null))
			AutoJumpSender.sendAutoJump(view, AutoJump.STARTED);
		Buffer b = view.getBuffer();
		if (b == null || (! b.getPath().equals(file)) ||
			(view.getTextArea().getCaretLine() != line - 1))
		{
			EditBus.send(new PositionChanging(view.getEditPane()));
		}
		Buffer buffer = jEdit.openFile(view, file);
		if (buffer == null) {
			System.err.println("Unable to open: " + file);
			return;
		}
		VFSManager.runInAWTThread(new Runnable() {
			public void run() {
				try {
					view.getTextArea().setCaretPosition(
						view.getTextArea().getLineStartOffset(line - 1));
					if (p != null)
						AutoJumpSender.sendAutoJump(view, AutoJump.ENDED);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	// Jumps to the specified tag
	public static void jumpToTag(final View view, final Tag tag)
	{
		String file = tag.getFile();
		if (file == null)
			return;
		int line = tag.getLine();
		if (line < 1)
			return;
		final String path = tag.getFile();
		// If "update on load" is used, the file tags will be updated when
		// the buffer is loaded, so delay the jump. Otherwise, jump now.
		if ((! GeneralOptionPane.getUpdateOnLoad()) || (jEdit.getBuffer(path) != null))
		{
			jumpTo(view, path, tag.getLine(), true);
			return;
		}
		// Send the "AutoJump.STARTED" event before opening the new buffer
		final EditPlugin p = jEdit.getPlugin("ise.plugin.nav.NavigatorPlugin",false);
		if (p != null)
			AutoJumpSender.sendAutoJump(view, AutoJump.STARTED);
		final long time = System.currentTimeMillis();
		final Buffer buffer = jEdit.openFile(view, path);
		if (buffer == null)
		{
			System.err.println("Unable to open: " + path);
			return;
		}
		Runnable delayJump = new Runnable() {
			public void run() {
				synchronized(bufferUpdateLock) {
					while (true) {
						Long l = (Long) buffer.getProperty(TAGS_UPDATED_BUFFER_PROP);
						if ((l != null) && (l.longValue() > time))
							break;
						try {
							bufferUpdateLock.wait();
						} catch (InterruptedException e) {}
					}
				}
				Tag updatedTag = getUpdatedTag(tag);
				jumpTo(view, path, updatedTag.getLine(), false);
			}
		};
		Thread bgTask = new Thread(delayJump);
		bgTask.start();

	}

	private static Tag getUpdatedTag(Tag tag)
	{
		Vector<Tag> tags = new Vector<Tag>();
		index.getIdenticalTags(tag, tags);
		if ((tags == null) || tags.isEmpty())
			return tag;
		return tags.get(0);
	}
	// Jumps to the specified location
	public static void jumpToOffset(final View view, String file,
		final int offset)
	{
		final EditPlugin p = jEdit.getPlugin("ise.plugin.nav.NavigatorPlugin",false);
		if (p != null)
			AutoJumpSender.sendAutoJump(view, AutoJump.STARTED);
		Buffer buffer = jEdit.openFile(view, file);
		if (buffer == null) {
			System.err.println("Unable to open: " + file);
			return;
		}
		VFSManager.runInAWTThread(new Runnable() {
			public void run() {
				view.getTextArea().setCaretPosition(offset);
				if (p != null)
					AutoJumpSender.sendAutoJump(view, AutoJump.ENDED);
			}
		});
	}

	// Updates the given origins in the DB
	static public void updateOrigins(OriginType type, Vector<String> names)
	{
		// Remove obsolete origins
		Vector<String> current = new Vector<String>();
		index.getOrigins(type, current);
		View view = jEdit.getActiveView();
		for (int i = 0; i < current.size(); i++)
		{
			String name = current.get(i);
			if (! names.contains(name))
			{
				Logger logger = getLogger(view, "Delete " + type.name + " " + name);
				deleteOrigin(logger, type, name);
			}
		}
		// Add new origins
		for (int i = 0; i < names.size(); i++)
		{
			String name = names.get(i);
			if (! current.contains(name))
			{
				Logger logger = getLogger(view, type.name + " " + name);
				insertOrigin(logger, type, name);
			}
		}
	}

	// Refreshes the given origin in the DB
	static public void refreshOrigin(OriginType type, String name)
	{
		Origin origin = index.getOrigin(type, name, false);
		View view = jEdit.getActiveView();
		Logger logger = getLogger(view, name);
		index.deleteTagsOfOrigin(logger, origin);
		tagOrigin(logger, origin);
	}

	public static void deleteOrigin(OriginType type,
		String name)
	{
		View view = jEdit.getActiveView();
		Logger logger = getLogger(view, "Delete " + type.name + " " + name);
		deleteOrigin(logger, type, name);
	}



	// Deletes an origin with all associated data from the DB
	public static void deleteOrigin(final Logger logger, final OriginType type,
		final String name)
	{
		deleteOrigin(logger, type, name, true);
	}

	// Deletes an origin with all associated data from the DB
	public static void deleteOrigin(final Logger logger, final OriginType type,
		final String name, Boolean threaded)
	{
		if (!threaded) {
			deleteOrigin(logger, index.getOrigin(type, name, false));
			return;
		}

		addWorkRequest(name, new Task()
		{
			public void _run()
			{
				deleteOrigin(logger, index.getOrigin(type, name, false));
			}
		});
	}

	private static void deleteOrigin(final Logger logger, final Origin origin)
	{
		index.deleteOrigin(logger, origin);
		if (pvi != null && origin.type == OriginType.PROJECT)
			pvi.updateWatchers();
	}

	// Inserts a new origin to the DB, runs Ctags on it and adds the tags
	// to the DB.
	public static void insertOrigin(Logger logger, OriginType type, String name)
	{
		Origin origin = index.getOrigin(type, name, true);
		tagOrigin(logger, origin);
		if (pvi != null && type == OriginType.PROJECT)
			pvi.updateWatchers();
	}
	// Runs Ctags on the specified origin and adds the tags to the DB.
	private static void tagOrigin(Logger logger, Origin origin)
	{
		TagFileHandler handler = new TagFileHandler(origin);
		switch (origin.type)
		{
		case PROJECT: tagProject(logger, origin.id, handler); break;
		case DIRECTORY: tagSourceTree(logger, origin.id, handler); break;
		case ARCHIVE: tagArchive(logger, origin.id, handler); break;
		}
	}

	private static void addWorkRequest(String originId, Task task)
	{
		Task current = tasks.get(originId);
		if (current != null)
			current.cancel();
		tasks.put(originId, task);
		ThreadUtilities.runInBackground(task);
	}

	private static Progress getProgressDockable(View view)
	{
		DockableWindowManager dwm = view.getDockableWindowManager();
		if (GeneralOptionPane.getShowProgress()) {
			dwm.showDockableWindow(PROGRESS);
		}

		Progress win = (Progress) dwm.getDockable(PROGRESS);
		/*
		 * This is horrible. But getDockable() returns null if the
		 * dockable window is not instantiated yet, even if it
		 * really exists.
		 */
		if (win == null) {
			win = new Progress(view);
		}
		return win;
	}

	private static StatusBar getStatusBar()
	{
		View v = jEdit.getActiveView();
		if (v == null)
			return null;
		return v.getStatus();
	}
	private static void setStatusMessage(String msg) {
		StatusBar s = getStatusBar();
		if (s == null)
			return;
		s.setMessage(msg);
	}
	private static void removeStatusMessage() {
		StatusBar s = getStatusBar();
		if (s == null)
			return;
		s.setMessage("");
	}

	private static void parseTagFile(Runner runner, Parser parser,
		String tagFile, TagHandler handler)
	{
		parser.parseTagFile(tagFile, handler);
		runner.releaseFile(tagFile);
	}

	/* Source file support */

	public static void tagSourceFile(final String file)
	{
		setStatusMessage("Tagging file: " + file);
		final Object syncObject = new Object();
		final boolean [] done = { false };
		addWorkRequest(file, new Task()
		{
			public void _run()
			{
				// Get the file origins, to restore when updating the file.
				final String originsStr = index.getOriginsOfFile(file);
				index.deleteTagsFromSourceFile(file);
				Runner runner = getRunner(null);
				String tagFile = runner.runOnFile(file);
				TagHandler handler = new TagHandler()
				{
					public void processTag(Tag t)
					{
						index.insertTag(t, originsStr);
					}
				};
				Parser parser = getParser(null);
				parseTagFile(runner, parser, tagFile, handler);
				synchronized (syncObject)
				{
					done[0] = true;
					syncObject.notifyAll();
				}
			}
		});
		synchronized (syncObject)
		{
			if (! done[0])
			{
				try { syncObject.wait(); }
				catch (InterruptedException e) { e.printStackTrace(); }
			}
		}
		removeStatusMessage();
	}

	/* Archive support */

	public static void tagArchive(final Logger logger, final String archive,
		final TagHandler handler)
	{
		setStatusMessage("Tagging archive: " + archive);
		addWorkRequest(archive, new Task()
		{
			public void _run()
			{
				HashMap<String, String> localToVFS =
					new HashMap<String, String>();
				Runner runner = getRunner(logger);
				String tagFile = runner.runOnArchive(archive, localToVFS);
				if (tagFile == null)
					return;
				Parser parser = getParser(logger);
				parser.setSourcePathMapping(localToVFS);
				parseTagFile(runner, parser, tagFile, handler);
			}
		});
		removeStatusMessage();
	}

	/* Source tree support */

	// Runs Ctags on a source tree and add the tags and associated data to the DB
	public static void tagSourceTree(final Logger logger, final String tree,
		final TagHandler handler)
	{
		setStatusMessage("Tagging source tree: " + tree);
		addWorkRequest(tree, new Task() {
			public void _run() {
				Runner runner = getRunner(logger);
				String tagFile = runner.runOnTree(tree);
				Parser parser = getParser(logger);
				parseTagFile(runner, parser, tagFile, handler);
			}
		});
		removeStatusMessage();
	}

	/* Project support */

	public static ProjectWatcher getProjectWatcher() {
		return pvi;
	}

	// Runs Ctags on a list of files and add the tags and associated data to the DB
	private static void tagFiles(Logger logger, Vector<String> files,
		TagHandler handler)
	{
		Runner runner = getRunner(logger);
		Parser parser = getParser(logger);
		String tagFile = runner.runOnFiles(files);
		parseTagFile(runner, parser, tagFile, handler);
	}

	// Runs Ctags on a project and inserts the tags and associated data to the DB
	public static void tagProject(final Logger logger, final String project,
		final TagHandler handler)
	{
		if (pvi == null)
			return;
		setStatusMessage("Tagging project: " + project);
		addWorkRequest(project, new Task() {
			public void _run() {
				Vector<String> files = pvi.getFiles(project);
				if (files == null)
					JOptionPane.showMessageDialog(jEdit.getActiveView(),
						"Cannot find project named '" + project + "'.");
				else
					tagFiles(logger, files, handler);
			}
		});
		removeStatusMessage();
	}
	public static void updateProject(String project, Vector<String> added,
		Vector<String> removed)
	{
		setStatusMessage("Updating project: " + project);
		Origin origin = index.getOrigin(OriginType.PROJECT, project, true);
		View view = jEdit.getActiveView();
		Logger logger = getLogger(view, "Project " + project);
		if (origin != null && removed != null && (! removed.isEmpty()))
			index.deleteTagsOfOrigin(logger, origin);
		if ((added != null) && (! added.isEmpty()))
		{
			TagHandler handler = new TagFileHandler(origin);
			tagFiles(logger, added, handler);
		}
		removeStatusMessage();
	}

	/*
	 * Interface for other plugins
	 */

	public static Vector<Tag> queryTag(String tag)
	{
		Vector<Tag> tags = new Vector<Tag>();
		index.queryTag(tag, tags);
		return tags;
	}
	public static Vector<Tag> query(String query, int maxResults)
	{
		Vector<Tag> tags = new Vector<Tag>();
		index.queryTags(query, maxResults, tags);
		return tags;
	}
	public static String getTagNameQuery(String tag)
	{
		return index.getTagNameQuery(tag);
	}
}
